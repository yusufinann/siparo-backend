package com.siparo.auth;

import com.siparo.common.exception.BusinessException;
import com.siparo.common.security.CustomUserDetails;
import com.siparo.common.security.JwtService;
import com.siparo.common.util.CodeGenerator;
import com.siparo.common.util.PhoneNumbers;
import com.siparo.customer.Customer;
import com.siparo.customer.CustomerRepository;
import com.siparo.platform.PlatformFeatures;
import com.siparo.restaurant.Restaurant;
import com.siparo.restaurant.RestaurantRepository;
import com.siparo.restaurant.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int RESET_CODE_MINUTES = 5;
    private static final int RESET_MAX_ATTEMPTS = 5;
    private static final int RESET_MAX_PER_HOUR = 3;

    private final CustomerRepository customerRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantService restaurantService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetCodeRepository resetCodeRepository;
    private final PlatformFeatures platformFeatures;
    private final ObjectProvider<SmsSender> smsSender;

    public record Session(String token, String role) {}

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
        customer.setEmail(request.email() == null || request.email().isBlank() ? null : request.email().trim());
        customer.setPasswordHash(passwordEncoder.encode(request.password()));
        LocalDateTime acceptedAt = LocalDateTime.now();
        customer.setTermsAcceptedAt(acceptedAt);
        customer.setKvkkAcceptedAt(acceptedAt);
        customer = customerRepository.save(customer);
        return customerSession(customer);
    }

    @Transactional(readOnly = true)
    public Session loginCustomer(AuthRequests.Login request) {
        Customer customer = findCustomer(PhoneNumbers.normalize(request.phoneNumber()))
                .or(() -> customerRepository.findByPhoneNumber(request.phoneNumber().trim()))
                .filter(found -> !found.isDeleted() && found.getPasswordHash() != null)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(this::invalidCredentials);
        return customerSession(customer);
    }

    private Optional<Customer> findCustomer(String phone) {
        return customerRepository.findByPhoneNumber(phone);
    }

    private Session customerSession(Customer customer) {
        CustomUserDetails user = new CustomUserDetails(customer.getId().toString(), customer.getPhoneNumber(), "", null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        return new Session(jwtService.generateToken(user), "ROLE_CUSTOMER");
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
        restaurant.setEmail(request.email() == null || request.email().isBlank() ? null : request.email().trim());
        restaurant.setPasswordHash(passwordEncoder.encode(request.password()));
        restaurant.setStatus("ACTIVE");
        restaurant.setMinOrderAmount(BigDecimal.ZERO);
        restaurant.setDeliveryFee(BigDecimal.ZERO);
        restaurant = restaurantRepository.save(restaurant);
        return restaurantSession(restaurant);
    }

    @Transactional(readOnly = true)
    public Session loginRestaurant(AuthRequests.Login request) {
        Restaurant restaurant = restaurantRepository.findFirstByOwnerPhone(PhoneNumbers.normalize(request.phoneNumber()))
                .or(() -> restaurantRepository.findFirstByOwnerPhone(request.phoneNumber().trim()))
                .filter(found -> found.getPasswordHash() != null && passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(this::invalidCredentials);
        return restaurantSession(restaurant);
    }

    private Session restaurantSession(Restaurant restaurant) {
        CustomUserDetails user = new CustomUserDetails("admin-" + restaurant.getId(), restaurant.getOwnerPhone(), "",
                restaurant.getId().toString(), List.of(new SimpleGrantedAuthority("ROLE_RESTAURANT_ADMIN")));
        return new Session(jwtService.generateToken(user), "ROLE_RESTAURANT_ADMIN");
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "Invalid phone number or password", HttpStatus.UNAUTHORIZED);
    }

    // ---------- Şifre sıfırlama (SMS doğrulama) ----------

    /** Kayıtlı olmayan numara için de aynı yanıt döner (hesap varlığı sızdırılmaz). */
    @Transactional
    public Map<String, Object> requestPasswordReset(AuthRequests.ResetRequest request) {
        SmsSender sender = requireSms();
        String phone = PhoneNumbers.normalize(request.phoneNumber());
        if (resetCodeRepository.countByPhoneNumberAndCreatedAtAfter(phone, LocalDateTime.now().minusHours(1)) >= RESET_MAX_PER_HOUR) {
            throw new BusinessException("OTP_RATE_LIMITED", "Too many reset requests", HttpStatus.TOO_MANY_REQUESTS);
        }
        Optional<Customer> customer = findCustomer(phone).filter(found -> !found.isDeleted());
        if (customer.isPresent()) {
            String code = CodeGenerator.digits(6);
            PasswordResetCode reset = new PasswordResetCode();
            reset.setPhoneNumber(phone);
            reset.setCodeHash(passwordEncoder.encode(code));
            reset.setExpiresAt(LocalDateTime.now().plusMinutes(RESET_CODE_MINUTES));
            resetCodeRepository.save(reset);
            sender.send(phone, "Siparo dogrulama kodun: " + code + " (" + RESET_CODE_MINUTES + " dk gecerli)");
        }
        return Map.of("expiresInSeconds", RESET_CODE_MINUTES * 60);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public Session confirmPasswordReset(AuthRequests.ResetConfirm request) {
        requireSms();
        String phone = PhoneNumbers.normalize(request.phoneNumber());
        PasswordResetCode reset = resetCodeRepository.findFirstByPhoneNumberAndConsumedAtIsNullOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> new BusinessException("OTP_INVALID", "Verification code is invalid"));
        if (reset.getExpiresAt().isBefore(LocalDateTime.now()) || reset.getAttempts() >= RESET_MAX_ATTEMPTS) {
            throw new BusinessException("OTP_EXPIRED", "Verification code has expired");
        }
        if (!passwordEncoder.matches(request.code(), reset.getCodeHash())) {
            reset.setAttempts(reset.getAttempts() + 1);
            throw new BusinessException("OTP_INVALID", "Verification code is invalid");
        }
        Customer customer = findCustomer(phone).filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException("OTP_INVALID", "Verification code is invalid"));
        reset.setConsumedAt(LocalDateTime.now());
        customer.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        return customerSession(customer);
    }

    private SmsSender requireSms() {
        SmsSender sender = smsSender.getIfAvailable();
        if (!platformFeatures.passwordResetEnabled() || sender == null) {
            throw new BusinessException("FEATURE_UNAVAILABLE", "Password reset is not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return sender;
    }
}
