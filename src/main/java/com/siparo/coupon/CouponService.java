package com.siparo.coupon;

import com.siparo.common.exception.BusinessException;
import com.siparo.common.exception.ResourceNotFoundException;
import com.siparo.common.util.BusinessClock;
import com.siparo.customer.CustomerRestaurant;
import com.siparo.customer.CustomerRestaurantRepository;
import com.siparo.order.OrderRepository;
import com.siparo.restaurant.Restaurant;
import com.siparo.restaurant.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponService {

    /** Süresi dolan kuponlar müşteriye bu kadar gün "Süresi doldu" sekmesinde gösterilir. */
    private static final int EXPIRED_VISIBLE_DAYS = 30;

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final CustomerRestaurantRepository customerRestaurantRepository;
    private final OrderRepository orderRepository;
    private final RestaurantService restaurantService;
    private final ApplicationEventPublisher eventPublisher;

    /** Kuponun belirli bir sepete uygulanma sonucu. {@code errorCode} null değilse kupon geçersizdir. */
    public record Evaluation(Coupon coupon, String errorCode, Map<String, Object> params, BigDecimal discount, boolean freeDelivery) {
        public boolean valid() {
            return errorCode == null;
        }
    }

    public record CouponPublishedEvent(UUID couponId, UUID restaurantId) {}

    // ---------- Müşteri ----------

    /** Yalnızca müşterinin listesindeki restoranların kuponları (keşif/pazaryeri değil). */
    @Transactional(readOnly = true)
    public List<CouponDto> listForCustomer(UUID customerId) {
        List<UUID> restaurantIds = customerRestaurantRepository.findAllById_CustomerIdAndRemovedAtIsNull(customerId).stream()
                .map(CustomerRestaurant::getId).map(CustomerRestaurant.CustomerRestaurantId::getRestaurantId).toList();
        if (restaurantIds.isEmpty()) return List.of();

        LocalDate today = BusinessClock.today();
        Map<UUID, Long> usedByCustomer = new HashMap<>();
        for (Object[] row : redemptionRepository.usageByCustomer(customerId)) {
            usedByCustomer.put((UUID) row[0], (Long) row[1]);
        }
        return couponRepository
                .findAllByRestaurantIdInAndExpiryDateGreaterThanEqualOrderByExpiryDateAsc(restaurantIds, today.minusDays(EXPIRED_VISIBLE_DAYS))
                .stream()
                .filter(coupon -> coupon.isActive() || usedByCustomer.containsKey(coupon.getId()))
                .filter(coupon -> coupon.getStartsOn() == null || !coupon.getStartsOn().isAfter(today))
                .map(coupon -> {
                    CouponDto dto = toDto(coupon);
                    long used = usedByCustomer.getOrDefault(coupon.getId(), 0L);
                    if (used >= coupon.getPerCustomerLimit()) dto.setState("USED");
                    else if (coupon.getExpiryDate().isBefore(today) || !coupon.isActive()) dto.setState("EXPIRED");
                    else dto.setState("AVAILABLE");
                    return dto;
                })
                .toList();
    }

    public Coupon findForRestaurant(UUID restaurantId, String code) {
        return couponRepository.findByRestaurantIdAndCodeIgnoreCase(restaurantId, code.trim())
                .orElseThrow(() -> new BusinessException("COUPON_NOT_FOUND", "Coupon not found for this restaurant"));
    }

    /** Sunucu tarafı kupon kuralları; istemcinin hesabına güvenilmez. */
    public Evaluation evaluate(Coupon coupon, UUID customerId, BigDecimal subtotal, BigDecimal deliveryFee, boolean delivery) {
        LocalDate today = BusinessClock.today();
        if (!coupon.isActive()) return invalid(coupon, "COUPON_INACTIVE", null);
        if (coupon.getStartsOn() != null && coupon.getStartsOn().isAfter(today)) return invalid(coupon, "COUPON_NOT_STARTED", null);
        if (coupon.getExpiryDate().isBefore(today)) return invalid(coupon, "COUPON_EXPIRED", null);
        if (coupon.getTotalLimit() != null && redemptionRepository.countByCouponId(coupon.getId()) >= coupon.getTotalLimit()) {
            return invalid(coupon, "COUPON_EXHAUSTED", null);
        }
        if (redemptionRepository.countByCouponIdAndCustomerId(coupon.getId(), customerId) >= coupon.getPerCustomerLimit()) {
            return invalid(coupon, "COUPON_ALREADY_USED", null);
        }
        if (coupon.isFirstOrderOnly()
                && orderRepository.countByCustomerIdAndRestaurantIdAndStatusNot(customerId, coupon.getRestaurant().getId(), "CANCELLED") > 0) {
            return invalid(coupon, "COUPON_FIRST_ORDER_ONLY", null);
        }
        if (coupon.getMinOrderAmount() != null && subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            return invalid(coupon, "COUPON_MIN_ORDER", Map.of("amount", coupon.getMinOrderAmount()));
        }
        return switch (coupon.getDiscountType()) {
            case "FREE_DELIVERY" -> delivery
                    ? new Evaluation(coupon, null, null, deliveryFee, true)
                    : invalid(coupon, "COUPON_DELIVERY_ONLY", null);
            case "PERCENT" -> {
                BigDecimal discount = subtotal.multiply(coupon.getDiscountPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (coupon.getMaxDiscountAmount() != null) discount = discount.min(coupon.getMaxDiscountAmount());
                yield new Evaluation(coupon, null, null, discount.min(subtotal), false);
            }
            default -> new Evaluation(coupon, null, null, coupon.getDiscountAmount().min(subtotal), false);
        };
    }

    private Evaluation invalid(Coupon coupon, String code, Map<String, Object> params) {
        return new Evaluation(coupon, code, params, BigDecimal.ZERO, false);
    }

    @Transactional
    public void recordRedemption(Coupon coupon, UUID customerId, UUID orderId, BigDecimal discount) {
        CouponRedemption redemption = new CouponRedemption();
        redemption.setCouponId(coupon.getId());
        redemption.setCustomerId(customerId);
        redemption.setOrderId(orderId);
        redemption.setDiscountAmount(discount);
        redemptionRepository.save(redemption);
    }

    /** İptal edilen siparişin kupon hakkı müşteriye geri verilir. */
    @Transactional
    public void releaseRedemption(UUID orderId) {
        redemptionRepository.deleteByOrderId(orderId);
    }

    // ---------- İşletme ----------

    @Transactional(readOnly = true)
    public List<CouponDto> listForRestaurant(UUID restaurantId) {
        List<Coupon> coupons = couponRepository.findAllByRestaurantIdOrderByCreatedAtDesc(restaurantId);
        Map<UUID, Object[]> usage = new HashMap<>();
        if (!coupons.isEmpty()) {
            for (Object[] row : redemptionRepository.usageByCoupon(coupons.stream().map(Coupon::getId).toList())) {
                usage.put((UUID) row[0], row);
            }
        }
        LocalDate today = BusinessClock.today();
        return coupons.stream().map(coupon -> {
            CouponDto dto = toDto(coupon);
            Object[] row = usage.get(coupon.getId());
            long count = row == null ? 0 : (Long) row[1];
            dto.setUsageCount(count);
            dto.setTotalDiscountGiven(row == null || row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2]);
            dto.setPerCustomerLimit(coupon.getPerCustomerLimit());
            dto.setTotalLimit(coupon.getTotalLimit());
            dto.setFirstOrderOnly(coupon.isFirstOrderOnly());
            dto.setActive(coupon.isActive());
            dto.setState(businessState(coupon, count, today));
            return dto;
        }).toList();
    }

    @Transactional
    public CouponDto create(UUID restaurantId, CouponRequest request) {
        Restaurant restaurant = restaurantService.findOrThrow(restaurantId);
        String code = request.code().trim().toUpperCase();
        if (couponRepository.existsByRestaurantIdAndCodeIgnoreCase(restaurantId, code)) {
            throw new BusinessException("COUPON_CODE_EXISTS", "A coupon with this code already exists");
        }
        Coupon coupon = new Coupon();
        coupon.setRestaurant(restaurant);
        coupon.setCode(code);
        apply(coupon, request);
        couponRepository.save(coupon);
        if (coupon.isActive() && (coupon.getStartsOn() == null || !coupon.getStartsOn().isAfter(BusinessClock.today()))) {
            eventPublisher.publishEvent(new CouponPublishedEvent(coupon.getId(), restaurantId));
        }
        return listForRestaurant(restaurantId).stream().filter(dto -> dto.getId().equals(coupon.getId())).findFirst().orElseThrow();
    }

    @Transactional
    public CouponDto update(UUID restaurantId, UUID couponId, CouponRequest request) {
        Coupon coupon = owned(restaurantId, couponId);
        String code = request.code().trim().toUpperCase();
        if (!coupon.getCode().equalsIgnoreCase(code)) {
            if (redemptionRepository.countByCouponId(couponId) > 0) {
                throw new BusinessException("COUPON_CODE_LOCKED", "Code cannot change after the coupon has been used");
            }
            if (couponRepository.existsByRestaurantIdAndCodeIgnoreCase(restaurantId, code)) {
                throw new BusinessException("COUPON_CODE_EXISTS", "A coupon with this code already exists");
            }
            coupon.setCode(code);
        }
        apply(coupon, request);
        return listForRestaurant(restaurantId).stream().filter(dto -> dto.getId().equals(couponId)).findFirst().orElseThrow();
    }

    @Transactional
    public void delete(UUID restaurantId, UUID couponId) {
        Coupon coupon = owned(restaurantId, couponId);
        if (redemptionRepository.countByCouponId(couponId) > 0) {
            coupon.setActive(false); // kullanım geçmişi korunur
        } else {
            couponRepository.delete(coupon);
        }
    }

    private void apply(Coupon coupon, CouponRequest request) {
        if (request.startsOn() != null && request.expiryDate().isBefore(request.startsOn())) {
            throw new BusinessException("INVALID_COUPON_DATES", "Expiry date must be after the start date");
        }
        switch (request.discountType()) {
            case "AMOUNT" -> {
                if (request.discountAmount() == null) throw new BusinessException("COUPON_AMOUNT_REQUIRED", "Discount amount is required");
            }
            case "PERCENT" -> {
                if (request.discountPercent() == null) throw new BusinessException("COUPON_PERCENT_REQUIRED", "Discount percent is required");
            }
            default -> { }
        }
        coupon.setTitle(request.title().trim());
        coupon.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountAmount("AMOUNT".equals(request.discountType()) ? request.discountAmount() : null);
        coupon.setDiscountPercent("PERCENT".equals(request.discountType()) ? request.discountPercent() : null);
        coupon.setMaxDiscountAmount("PERCENT".equals(request.discountType()) ? request.maxDiscountAmount() : null);
        coupon.setMinOrderAmount(request.minOrderAmount());
        coupon.setStartsOn(request.startsOn());
        coupon.setExpiryDate(request.expiryDate());
        coupon.setPerCustomerLimit(request.perCustomerLimit() == null ? 1 : request.perCustomerLimit());
        coupon.setTotalLimit(request.totalLimit());
        coupon.setFirstOrderOnly(Boolean.TRUE.equals(request.firstOrderOnly()));
        coupon.setActive(request.active() == null || request.active());
    }

    private Coupon owned(UUID restaurantId, UUID couponId) {
        return couponRepository.findByIdAndRestaurantId(couponId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("COUPON_NOT_FOUND", "Coupon not found"));
    }

    private String businessState(Coupon coupon, long usage, LocalDate today) {
        if (!coupon.isActive()) return "INACTIVE";
        if (coupon.getExpiryDate().isBefore(today)) return "EXPIRED";
        if (coupon.getStartsOn() != null && coupon.getStartsOn().isAfter(today)) return "SCHEDULED";
        if (coupon.getTotalLimit() != null && usage >= coupon.getTotalLimit()) return "EXHAUSTED";
        return "ACTIVE";
    }

    public CouponDto toDto(Coupon coupon) {
        CouponDto dto = new CouponDto();
        dto.setId(coupon.getId());
        dto.setRestaurantId(coupon.getRestaurant().getId());
        dto.setRestaurantName(coupon.getRestaurant().getName());
        dto.setRestaurantLogoUrl(coupon.getRestaurant().getLogoUrl());
        dto.setCode(coupon.getCode());
        dto.setTitle(coupon.getTitle());
        dto.setDescription(coupon.getDescription());
        dto.setDiscountType(coupon.getDiscountType());
        dto.setDiscountAmount(coupon.getDiscountAmount());
        dto.setDiscountPercent(coupon.getDiscountPercent());
        dto.setMaxDiscountAmount(coupon.getMaxDiscountAmount());
        dto.setMinOrderAmount(coupon.getMinOrderAmount());
        dto.setStartsOn(coupon.getStartsOn());
        dto.setExpiryDate(coupon.getExpiryDate());
        dto.setFirstOrderOnly(coupon.isFirstOrderOnly() ? Boolean.TRUE : null);
        return dto;
    }
}
