package com.siparo.auth;

import com.siparo.common.exception.BusinessException;
import com.siparo.customer.*;
import com.siparo.restaurant.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleAuthServiceTest {
    GoogleIdentityVerifier verifier = mock(GoogleIdentityVerifier.class);
    CustomerRepository customers = mock(CustomerRepository.class);
    RestaurantRepository restaurants = mock(RestaurantRepository.class);
    RestaurantService restaurantService = mock(RestaurantService.class);
    AuthService sessions = mock(AuthService.class);
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    AuthAbuseGuard guard = mock(AuthAbuseGuard.class);
    GoogleAuthService service = new GoogleAuthService(verifier, customers, restaurants, restaurantService, sessions, encoder, guard, mock(JdbcTemplate.class));
    Customer customer;
    @BeforeEach void setup() {
        when(verifier.verify(anyString(), eq("valid"))).thenReturn(new GoogleIdentityVerifier.Identity("google-sub", "user@example.com", "Verified Name"));
        customer = new Customer(); customer.setEmail("user@example.com"); customer.setPasswordHash(encoder.encode("local-password"));
    }
    ExtendedAuthRequests.Google request(String password) { return new ExtendedAuthRequests.Google("valid", password, "05321234567", "Business", true, true); }
    @Test void newCustomerUsesVerifiedIdentityAndNoLocalPassword() {
        service.login("customer", request(null), "ip");
        var capture = org.mockito.ArgumentCaptor.forClass(Customer.class); verify(customers).saveAndFlush(capture.capture());
        assertThat(capture.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(capture.getValue().getGoogleSubject()).isEqualTo("google-sub");
        assertThat(capture.getValue().getPasswordHash()).isNull();
        verify(sessions).customerSession(capture.getValue());
    }
    @Test void existingGoogleAccountUsesSubjectWithoutEmailRelinking() {
        when(customers.findByGoogleSubject("google-sub")).thenReturn(Optional.of(customer));
        service.login("customer", request(null), "ip");
        verify(customers, never()).findByEmailIgnoreCase(anyString()); verify(customers, never()).saveAndFlush(any());
        verify(sessions).customerSession(customer);
    }
    @Test void localEmailRequiresPasswordProof() {
        when(customers.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(customer));
        fails("GOOGLE_LINK_REQUIRED", () -> service.login("customer", request(null), "ip"));
        verifyNoInteractions(sessions);
    }
    @Test void wrongPasswordDoesNotLink() {
        when(customers.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(customer));
        fails("INVALID_CREDENTIALS", () -> service.login("customer", request("wrong"), "ip"));
        assertThat(customer.getGoogleSubject()).isNull();
    }
    @Test void correctPasswordLinksSameAccount() {
        when(customers.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(customer));
        service.login("customer", request("local-password"), "ip");
        verify(customers).saveAndFlush(customer); assertThat(customer.getGoogleSubject()).isEqualTo("google-sub");
        assertThat(encoder.matches("local-password", customer.getPasswordHash())).isTrue();
    }
    @Test void anotherGoogleIdentityCannotOverwriteLink() {
        customer.setGoogleSubject("other-sub"); when(customers.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(customer));
        fails("GOOGLE_LINK_DENIED", () -> service.login("customer", request("local-password"), "ip"));
    }
    @Test void existingPhoneCannotBeClaimed() {
        when(customers.findByPhoneNumber("05321234567")).thenReturn(Optional.of(customer));
        fails("GOOGLE_PHONE_CONFLICT", () -> service.login("customer", request(null), "ip"));
    }
    @Test void missingConsentRequiresOnboarding() {
        fails("GOOGLE_PROFILE_REQUIRED", () -> service.login("customer", new ExtendedAuthRequests.Google("valid", null, "05321234567", null, false, true), "ip"));
    }
    @Test void invalidTokenNeverQueriesAccounts() {
        when(verifier.verify("customer", "bad")).thenThrow(new BusinessException("GOOGLE_TOKEN_INVALID", "invalid"));
        fails("GOOGLE_TOKEN_INVALID", () -> service.login("customer", new ExtendedAuthRequests.Google("bad", null, null, null, null, null), "ip"));
        verifyNoInteractions(customers, restaurants, sessions);
    }
    @Test void businessCreatesRestaurantRoleOnly() {
        service.login("admin", request(null), "ip");
        verify(restaurants).saveAndFlush(any(Restaurant.class)); verify(sessions).restaurantSession(any()); verifyNoInteractions(customers);
    }
    private void fails(String code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
