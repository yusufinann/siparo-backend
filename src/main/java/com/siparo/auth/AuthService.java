package com.siparo.auth;

import com.siparo.common.exception.BusinessException;
import com.siparo.common.security.CustomUserDetails;
import com.siparo.common.security.JwtService;

import com.siparo.common.util.PhoneNumbers;
import com.siparo.customer.Customer;
import com.siparo.customer.CustomerRepository;
import com.siparo.delivery.Courier;
import com.siparo.delivery.CourierRepository;

import com.siparo.restaurant.Restaurant;
import com.siparo.restaurant.RestaurantRepository;
import com.siparo.restaurant.RestaurantService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {





    private final CustomerRepository customerRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantService restaurantService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;



    private final RefreshTokenService refreshTokens;
    private final LoginAttemptLimiter loginLimiter;
    private final CourierRepository courierRepository;

    /** {@code refreshToken} yalnızca mobil oturumlarda (müşteri, kurye) verilir; işletme web oturumunda null'dır. */
    public record Session(String token, String role, String refreshToken) {
        public Session(String token, String role) {
            this(token, role, null);
        }
    }

    // ---------- Müşteri ----------

    @Transactional
    public Session registerCustomer(AuthRequests.CustomerRegister request) {
        String phone = PhoneNumbers.normalize(request.phoneNumber());
        if (findCustomer(phone).isPresent()) {
            throw new BusinessException("PHONE_ALREADY_REGISTERED", "Phone number is already registered", HttpStatus.CONFLICT);
        }
        Customer customer = new Customer();
        customer.setPhoneNumber(phone);
        customer.setFullName(request.fullName().trim());
        customer.setEmail(request.email() == null || request.email().isBlank() ? null : request.email().trim().toLowerCase(java.util.Locale.ROOT));
        customer.setPasswordHash(passwordEncoder.encode(request.password()));
        LocalDateTime acceptedAt = LocalDateTime.now();
        customer.setTermsAcceptedAt(acceptedAt);
        customer.setKvkkAcceptedAt(acceptedAt);
        customer = customerRepository.save(customer);
        return customerSession(customer);
    }

    @Transactional
    public Session loginCustomer(AuthRequests.Login request) {
        String limitKey = "CUSTOMER:" + PhoneNumbers.normalize(request.phoneNumber());
        loginLimiter.check(limitKey);
        Optional<Customer> customer = findCustomer(PhoneNumbers.normalize(request.phoneNumber()))
                .or(() -> customerRepository.findByPhoneNumber(request.phoneNumber().trim()))
                .filter(found -> !found.isDeleted() && found.getPasswordHash() != null)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()));
        if (customer.isEmpty()) {
            loginLimiter.recordFailure(limitKey);
            throw invalidCredentials();
        }
        loginLimiter.recordSuccess(limitKey);
        return customerSession(customer.get());
    }

    private Optional<Customer> findCustomer(String phone) {
        return customerRepository.findByPhoneNumber(phone);
    }

    public Session customerSession(Customer customer) {
        CustomUserDetails user = new CustomUserDetails(customer.getId().toString(), customer.getPhoneNumber(), "", null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        return new Session(jwtService.generateMobileToken(user), "ROLE_CUSTOMER",
                refreshTokens.issue(RefreshTokenService.OWNER_CUSTOMER, customer.getId()));
    }

    // ---------- Kurye ----------

    /** Kurye oturumu (kimlik doğrulaması CourierService'te yapılır). */
    @Transactional
    public Session courierSession(Courier courier) {
        return new Session(jwtService.generateMobileToken(courierDetails(courier)), "ROLE_COURIER",
                refreshTokens.issue(RefreshTokenService.OWNER_COURIER, courier.getId()));
    }

    private CustomUserDetails courierDetails(Courier courier) {
        return new CustomUserDetails(courier.getId().toString(), courier.getPhoneNumber(), "",
                courier.getRestaurant().getId().toString(), List.of(new SimpleGrantedAuthority("ROLE_COURIER")));
    }

    // ---------- Mobil oturum yenileme ----------

    /**
     * Yenileme token'ını döndürür; yeni erişim + yenileme token'ı verir. Hesap silinmiş/pasifse tüm oturumları kapanır
     * (INVALID_REFRESH_TOKEN, 401).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Session refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken);
        RefreshTokenService.Owner owner = rotation.owner();
        if (RefreshTokenService.OWNER_CUSTOMER.equals(owner.type())) {
            Customer customer = customerRepository.findById(owner.id()).filter(found -> !found.isDeleted())
                    .orElseThrow(() -> revokeAndReject(owner));
            CustomUserDetails user = new CustomUserDetails(customer.getId().toString(), customer.getPhoneNumber(), "", null,
                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
            return new Session(jwtService.generateMobileToken(user), "ROLE_CUSTOMER", rotation.refreshToken());
        }
        Courier courier = courierRepository.findById(owner.id()).filter(Courier::isActive)
                .orElseThrow(() -> revokeAndReject(owner));
        return new Session(jwtService.generateMobileToken(courierDetails(courier)), "ROLE_COURIER", rotation.refreshToken());
    }

    /** Çıkış: bu cihazın yenileme token'ı iptal edilir. */
    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    /** Şifre değişikliğinden sonra: diğer cihazlardaki oturumlar kapanır, bu cihaza yeni oturum verilir. */
    @Transactional
    public Session reissueCustomerSession(UUID customerId) {
        refreshTokens.revokeAll(RefreshTokenService.OWNER_CUSTOMER, customerId);
        Customer customer = customerRepository.findById(customerId).filter(found -> !found.isDeleted())
                .orElseThrow(this::invalidCredentials);
        return customerSession(customer);
    }

    private BusinessException revokeAndReject(RefreshTokenService.Owner owner) {
        refreshTokens.revokeAll(owner.type(), owner.id());
        return new BusinessException("INVALID_REFRESH_TOKEN", "Session has expired", HttpStatus.UNAUTHORIZED);
    }

    // ---------- İşletme ----------

    @Transactional
    public Session registerRestaurant(AuthRequests.RestaurantRegister request) {
        String phone = PhoneNumbers.normalize(request.phoneNumber());
        if (restaurantRepository.existsByOwnerPhone(phone)) {
            throw new BusinessException("PHONE_ALREADY_REGISTERED", "Phone number is already registered for another restaurant", HttpStatus.CONFLICT);
        }
        Restaurant restaurant = new Restaurant();
        restaurant.setPublicCode(restaurantService.newPublicCode());
        restaurant.setName(request.restaurantName().trim());
        restaurant.setPhone(phone);
        restaurant.setOwnerPhone(phone);
        restaurant.setEmail(request.email() == null || request.email().isBlank() ? null : request.email().trim().toLowerCase(java.util.Locale.ROOT));
        restaurant.setPasswordHash(passwordEncoder.encode(request.password()));
        restaurant.setStatus("ACTIVE");
        restaurant.setMinOrderAmount(BigDecimal.ZERO);
        restaurant.setDeliveryFee(BigDecimal.ZERO);
        restaurant = restaurantRepository.save(restaurant);
        return restaurantSession(restaurant);
    }

    @Transactional(readOnly = true)
    public Session loginRestaurant(AuthRequests.Login request) {
        String limitKey = "RESTAURANT:" + PhoneNumbers.normalize(request.phoneNumber());
        loginLimiter.check(limitKey);
        Optional<Restaurant> restaurant = restaurantRepository.findFirstByOwnerPhone(PhoneNumbers.normalize(request.phoneNumber()))
                .or(() -> restaurantRepository.findFirstByOwnerPhone(request.phoneNumber().trim()))
                .filter(found -> found.getPasswordHash() != null && passwordEncoder.matches(request.password(), found.getPasswordHash()));
        if (restaurant.isEmpty()) {
            loginLimiter.recordFailure(limitKey);
            throw invalidCredentials();
        }
        loginLimiter.recordSuccess(limitKey);
        return restaurantSession(restaurant.get());
    }

    public Session restaurantSession(Restaurant restaurant) {
        CustomUserDetails user = new CustomUserDetails("admin-" + restaurant.getId(), restaurant.getOwnerPhone(), "",
                restaurant.getId().toString(), List.of(new SimpleGrantedAuthority("ROLE_RESTAURANT_ADMIN")));
        return new Session(jwtService.generateToken(user), "ROLE_RESTAURANT_ADMIN");
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "Invalid phone number or password", HttpStatus.UNAUTHORIZED);
    }

}
