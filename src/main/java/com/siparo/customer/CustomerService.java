package com.siparo.customer;

import com.siparo.auth.AuthService;
import com.siparo.auth.RefreshTokenService;
import com.siparo.cart.CartRepository;
import com.siparo.common.exception.BusinessException;
import com.siparo.common.exception.ResourceNotFoundException;
import com.siparo.notification.DeviceTokenRepository;
import com.siparo.notification.NotificationRepository;
import com.siparo.order.OrderRepository;
import com.siparo.restaurant.OpeningHour;
import com.siparo.restaurant.Restaurant;
import com.siparo.restaurant.RestaurantDto;
import com.siparo.restaurant.RestaurantMapper;
import com.siparo.restaurant.RestaurantRepository;
import com.siparo.restaurant.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerRestaurantRepository customerRestaurantRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantService restaurantService;
    private final RestaurantMapper restaurantMapper;
    private final CustomerAddressRepository customerAddressRepository;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final AuthService authService;

    public record LinkResult(RestaurantDto restaurant, boolean alreadyAdded) {}

    // ---------- Profil ----------

    @Transactional(readOnly = true)
    public CustomerDto getCustomer(UUID customerId) {
        return mapToDto(findActive(customerId));
    }

    @Transactional
    public CustomerDto updateProfile(UUID customerId, CustomerRequests.UpdateProfile request) {
        Customer customer = findActive(customerId);
        customer.setFullName(request.fullName().trim());
        customer.setEmail(request.email() == null || request.email().isBlank() ? null : request.email().trim());
        return mapToDto(customerRepository.save(customer));
    }

    /** Şifre değişikliği ve oturum yenileme tek işlemde: diğer cihazların oturumları kapanır, bu cihaza yeni oturum verilir. */
    @Transactional
    public AuthService.Session changePassword(UUID customerId, CustomerRequests.ChangePassword request) {
        Customer customer = findActive(customerId);
        if (!passwordEncoder.matches(request.currentPassword(), customer.getPasswordHash())) {
            throw new BusinessException("CURRENT_PASSWORD_INCORRECT", "Current password is incorrect");
        }
        customer.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        return authService.reissueCustomerSession(customerId);
    }

    @Transactional
    public CustomerDto updatePreferences(UUID customerId, CustomerRequests.NotificationPreferences request) {
        Customer customer = findActive(customerId);
        customer.setNotifyOrderUpdates(request.orderUpdates());
        customer.setNotifyCampaigns(request.campaigns());
        customer.setNotifyRestaurantNews(request.restaurantNews());
        return mapToDto(customer);
    }

    /**
     * Hesap silme (mağaza gereksinimi): kişisel veriler anonimleştirilir, adres/sepet/bildirim/cihaz kayıtları silinir.
     * Siparişler restoranın muhasebe kaydı olarak kalır; müşteri adı ve telefonu siparişlerde artık görünmez.
     */
    @Transactional
    public void deleteAccount(UUID customerId, CustomerRequests.DeleteAccount request) {
        Customer customer = findActive(customerId);
        if (!passwordEncoder.matches(request.password(), customer.getPasswordHash())) {
            throw new BusinessException("CURRENT_PASSWORD_INCORRECT", "Password is incorrect");
        }
        customerAddressRepository.deleteAll(customerAddressRepository.findByCustomerId(customerId));
        cartRepository.findByCustomerId(customerId).ifPresent(cartRepository::delete);
        deviceTokenRepository.deleteAllByOwnerTypeAndOwnerId("CUSTOMER", customerId);
        refreshTokens.revokeAll(RefreshTokenService.OWNER_CUSTOMER, customerId);
        notificationRepository.deleteAllByCustomerId(customerId);
        LocalDateTime now = LocalDateTime.now();
        customerRestaurantRepository.findAllById_CustomerId(customerId).forEach(link -> {
            if (link.getRemovedAt() == null) link.setRemovedAt(now);
        });
        customer.setFullName(null);
        customer.setEmail(null);
        customer.setPhoneNumber("deleted-" + customerId);
        customer.setPasswordHash(null);
        customer.setDeletedAt(now);
    }

    public Customer findActive(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (customer.isDeleted()) {
            throw new BusinessException("ACCOUNT_DELETED", "Account has been deleted", HttpStatus.UNAUTHORIZED);
        }
        return customer;
    }

    // ---------- Restoranlarım ----------

    @Transactional
    public LinkResult linkCustomerToRestaurant(UUID customerId, UUID restaurantId, String source) {
        Restaurant restaurant = restaurantService.findOrThrow(restaurantId);
        return link(customerId, restaurant, source);
    }

    @Transactional
    public LinkResult linkByPublicCode(UUID customerId, String code, String source) {
        Restaurant restaurant = restaurantRepository.findByPublicCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new ResourceNotFoundException("RESTAURANT_NOT_FOUND", "Restaurant not found"));
        return link(customerId, restaurant, source);
    }

    private LinkResult link(UUID customerId, Restaurant restaurant, String source) {
        findActive(customerId);
        CustomerRestaurant.CustomerRestaurantId id = new CustomerRestaurant.CustomerRestaurantId(customerId, restaurant.getId());
        Optional<CustomerRestaurant> existing = customerRestaurantRepository.findById(id);
        boolean alreadyAdded = existing.isPresent() && existing.get().getRemovedAt() == null;
        if (existing.isPresent()) {
            existing.get().setRemovedAt(null);
        } else {
            CustomerRestaurant link = new CustomerRestaurant();
            link.setId(id);
            link.setSource(source == null ? "MANUAL" : source);
            customerRestaurantRepository.save(link);
        }
        return new LinkResult(restaurantMapper.toPublic(restaurant, restaurantService.hoursOf(restaurant.getId())), alreadyAdded);
    }

    /** Sıralama: favoriler, sonra en son sipariş verilenler, sonra en son eklenenler. */
    @Transactional(readOnly = true)
    public List<RestaurantDto> getCustomerRestaurants(UUID customerId) {
        List<CustomerRestaurant> links = customerRestaurantRepository.findAllById_CustomerIdAndRemovedAtIsNull(customerId);
        if (links.isEmpty()) return List.of();

        List<UUID> restaurantIds = links.stream().map(link -> link.getId().getRestaurantId()).toList();
        Map<UUID, Restaurant> restaurants = restaurantRepository.findAllById(restaurantIds).stream()
                .collect(Collectors.toMap(Restaurant::getId, Function.identity()));
        Map<UUID, List<OpeningHour>> hours = restaurantService.hoursOf(restaurantIds);
        Map<UUID, LocalDateTime> lastOrdered = new HashMap<>();
        for (Object[] row : orderRepository.findLastOrderDatesByCustomer(customerId)) {
            lastOrdered.put((UUID) row[0], (LocalDateTime) row[1]);
        }
        Optional<CustomerAddress> defaultAddress = defaultAddress(customerId);

        return links.stream()
                .filter(link -> restaurants.containsKey(link.getId().getRestaurantId()))
                .map(link -> {
                    Restaurant restaurant = restaurants.get(link.getId().getRestaurantId());
                    RestaurantDto dto = restaurantMapper.toPublic(restaurant, hours.getOrDefault(restaurant.getId(), List.of()));
                    dto.setFavorite(link.isFavorite());
                    dto.setSource(link.getSource());
                    dto.setAddedAt(link.getCreatedAt());
                    dto.setLastOrderedAt(lastOrdered.get(restaurant.getId()));
                    defaultAddress.ifPresent(address ->
                            restaurantMapper.applyCustomerLocation(dto, restaurant, address.getLatitude(), address.getLongitude()));
                    return dto;
                })
                .sorted(Comparator.comparing((RestaurantDto dto) -> !Boolean.TRUE.equals(dto.getFavorite()))
                        .thenComparing(RestaurantDto::getLastOrderedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RestaurantDto::getAddedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    public void setFavorite(UUID customerId, UUID restaurantId, boolean favorite) {
        activeLink(customerId, restaurantId).setFavorite(favorite);
    }

    @Transactional
    public void removeRestaurant(UUID customerId, UUID restaurantId) {
        CustomerRestaurant link = activeLink(customerId, restaurantId);
        link.setRemovedAt(LocalDateTime.now());
        link.setFavorite(false);
    }

    /** Restoran müşterinin listesinde değilse RESTAURANT_NOT_IN_LIST fırlatır. */
    @Transactional(readOnly = true)
    public void requireLinkedRestaurant(UUID customerId, UUID restaurantId) {
        activeLink(customerId, restaurantId);
    }

    private CustomerRestaurant activeLink(UUID customerId, UUID restaurantId) {
        return customerRestaurantRepository.findById(new CustomerRestaurant.CustomerRestaurantId(customerId, restaurantId))
                .filter(link -> link.getRemovedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("RESTAURANT_NOT_IN_LIST", "Restaurant is not in the customer's list"));
    }

    // ---------- Adresler ----------

    @Transactional(readOnly = true)
    public List<CustomerAddressDto> getCustomerAddresses(UUID customerId) {
        return customerAddressRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .sorted(Comparator.comparing((CustomerAddress address) -> !address.isDefault()))
                .map(this::mapAddressToDto)
                .collect(Collectors.toList());
    }

    public Optional<CustomerAddress> defaultAddress(UUID customerId) {
        List<CustomerAddress> addresses = customerAddressRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        return addresses.stream().filter(CustomerAddress::isDefault).findFirst()
                .or(() -> addresses.stream().findFirst());
    }

    @Transactional
    public CustomerAddressDto addCustomerAddress(UUID customerId, CreateCustomerAddressRequest request) {
        Customer customer = findActive(customerId);
        boolean first = customerAddressRepository.findByCustomerId(customerId).isEmpty();

        CustomerAddress address = new CustomerAddress();
        address.setCustomer(customer);
        apply(address, request);
        address.setDefault(first);
        customerAddressRepository.save(address);
        return mapAddressToDto(address);
    }

    @Transactional
    public CustomerAddressDto updateCustomerAddress(UUID customerId, UUID addressId, CreateCustomerAddressRequest request) {
        CustomerAddress address = ownedAddress(customerId, addressId);
        apply(address, request);
        customerAddressRepository.save(address);
        return mapAddressToDto(address);
    }

    @Transactional
    public void deleteCustomerAddress(UUID customerId, UUID addressId) {
        CustomerAddress address = ownedAddress(customerId, addressId);
        boolean wasDefault = address.isDefault();
        customerAddressRepository.delete(address);
        customerAddressRepository.flush();
        if (wasDefault) {
            customerAddressRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().findFirst()
                    .ifPresent(next -> next.setDefault(true));
        }
    }

    @Transactional
    public CustomerAddressDto setDefaultAddress(UUID customerId, UUID addressId) {
        CustomerAddress selected = ownedAddress(customerId, addressId);
        customerAddressRepository.findByCustomerId(customerId)
                .forEach(address -> address.setDefault(address.getId().equals(selected.getId())));
        return mapAddressToDto(selected);
    }

    public CustomerAddress ownedAddress(UUID customerId, UUID addressId) {
        return customerAddressRepository.findById(addressId)
                .filter(address -> address.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new ResourceNotFoundException("ADDRESS_NOT_FOUND", "Address not found"));
    }

    private void apply(CustomerAddress address, CreateCustomerAddressRequest request) {
        address.setTitle(request.getTitle().trim());
        address.setAddress(request.getAddress().trim());
        address.setAddressDetail(request.getAddressDetail() == null || request.getAddressDetail().isBlank()
                ? null : request.getAddressDetail().trim());
        address.setIcon(request.getIcon() != null ? request.getIcon() : "other");
        address.setLatitude(request.getLatitude());
        address.setLongitude(request.getLongitude());
    }

    private CustomerDto mapToDto(Customer customer) {
        CustomerDto dto = new CustomerDto();
        dto.setId(customer.getId());
        dto.setPhoneNumber(customer.getPhoneNumber());
        dto.setFullName(customer.getFullName());
        dto.setEmail(customer.getEmail());
        dto.setNotifyOrderUpdates(customer.isNotifyOrderUpdates());
        dto.setNotifyCampaigns(customer.isNotifyCampaigns());
        dto.setNotifyRestaurantNews(customer.isNotifyRestaurantNews());
        return dto;
    }

    public CustomerAddressDto mapAddressToDto(CustomerAddress address) {
        return CustomerAddressDto.builder()
                .id(address.getId())
                .title(address.getTitle())
                .address(address.getAddress())
                .addressDetail(address.getAddressDetail())
                .icon(address.getIcon())
                .isDefault(address.isDefault())
                .latitude(address.getLatitude())
                .longitude(address.getLongitude())
                .build();
    }
}
