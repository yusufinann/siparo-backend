package com.siparo.auth;

import com.siparo.common.exception.BusinessException;
import com.siparo.customer.*;
import com.siparo.restaurant.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailPasswordResetServiceTest {
    EmailResetRepository resets;
    CustomerRepository customers;
    RestaurantRepository restaurants;
    PasswordResetMailService mail;
    RefreshTokenService refresh;
    AuthAbuseGuard guard;
    EmailPasswordResetService service;
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    Customer customer;
    AtomicReference<EmailReset> stored;
    String sentCode;
    @BeforeEach void setup() {
        resets = mock(EmailResetRepository.class); customers = mock(CustomerRepository.class);
        restaurants = mock(RestaurantRepository.class); mail = mock(PasswordResetMailService.class);
        refresh = mock(RefreshTokenService.class); guard = mock(AuthAbuseGuard.class);
        service = new EmailPasswordResetService(resets, customers, restaurants, encoder, mail, refresh, guard);
        ReflectionTestUtils.setField(service, "resetSecret", "test-only-secret-longer-than-32-characters");
        customer = new Customer(); customer.setEmail("user@example.com"); customer.setPasswordHash(encoder.encode("old-password"));
        when(customers.findByEmailIgnoreCase(customer.getEmail())).thenReturn(Optional.of(customer));
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));
        stored = new AtomicReference<>();
        when(resets.lockByKey(anyString())).thenAnswer(i -> Optional.ofNullable(stored.get()));
        when(resets.save(any())).thenAnswer(i -> { stored.set(i.getArgument(0)); return stored.get(); });
        when(resets.lockByGrant(anyString())).thenAnswer(i -> Optional.ofNullable(stored.get()).filter(r -> i.getArgument(0).equals(r.getGrantHash())));
        doAnswer(i -> { sentCode = i.getArgument(1); return null; }).when(mail).send(anyString(), anyString(), any());
    }
    Map<String, Object> request(String email) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            var response = service.request("customer", new ExtendedAuthRequests.ResetRequest(email, "tr"), "127.0.0.1");
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            return response;
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    String verify(String code) { return service.verify("customer", new ExtendedAuthRequests.Verify(customer.getEmail(), code), "127.0.0.1").get("resetToken"); }
    void code(String expected, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(expected));
    }
    @Test void existingAccountSendsAfterCommitAndStoresOnlyProtectedHash() {
        request(customer.getEmail()); assertThat(sentCode).matches("[0-9]{6}");
        assertThat(stored.get().getCodeHash()).isNotEqualTo(sentCode);
        assertThat(encoder.matches(sentCode, stored.get().getCodeHash())).isFalse();
    }
    @Test void unknownEmailHasSameResponseAndNoMail() {
        var known = request(customer.getEmail()); sentCode = null;
        var unknown = request("unknown@example.com");
        assertThat(unknown).isEqualTo(known); assertThat(sentCode).isNull(); assertThat(stored.get().getOwnerId()).isNull();
    }
    @Test void googleOnlyCannotCreatePasswordThroughReset() {
        customer.setPasswordHash(null); request(customer.getEmail());
        assertThat(sentCode).isNull(); assertThat(stored.get().getOwnerId()).isNull();
    }
    @Test void validCodeProducesOpaqueSingleUseGrant() {
        request(customer.getEmail()); var token = verify(sentCode);
        assertThat(token).hasSize(43); assertThat(stored.get().getGrantHash()).isNotEqualTo(token);
        code("RESET_CODE_INVALID", () -> verify(sentCode));
    }
    @Test void invalidCodeConsumesAttempt() {
        request(customer.getEmail()); code("RESET_CODE_INVALID", () -> verify("bad")); assertThat(stored.get().getAttempts()).isEqualTo(1);
    }
    @Test void expiredCodeFails() {
        request(customer.getEmail()); stored.get().setExpiresAt(LocalDateTime.now().minusSeconds(1)); code("RESET_CODE_EXPIRED", () -> verify(sentCode));
    }
    @Test void fifthFailedAttemptLocksCode() {
        request(customer.getEmail()); for (int i=0;i<4;i++) code("RESET_CODE_INVALID", () -> verify("bad"));
        code("RESET_TOO_MANY_ATTEMPTS", () -> verify("bad")); code("RESET_TOO_MANY_ATTEMPTS", () -> verify(sentCode));
    }
    @Test void resendInvalidatesOldCodeAndGrant() {
        request(customer.getEmail()); var old = sentCode; var token = verify(old);
        request(customer.getEmail());
        assertThat(stored.get().getGrantHash()).isNull();
        code("RESET_TOKEN_INVALID", () -> service.confirm("customer", new ExtendedAuthRequests.Confirm(token, "new-password"), "ip"));
        if (!old.equals(sentCode)) code("RESET_CODE_INVALID", () -> verify(old));
        assertThat(verify(sentCode)).isNotBlank();
    }
    @Test void resetChangesPasswordRevokesSessionsAndCannotBeReused() {
        request(customer.getEmail()); var oldCode=sentCode; var token=verify(sentCode);
        service.confirm("customer", new ExtendedAuthRequests.Confirm(token, "new-password"), "ip");
        assertThat(encoder.matches("new-password", customer.getPasswordHash())).isTrue();
        assertThat(customer.getSessionsInvalidBefore()).isNotNull();
        org.mockito.Mockito.verify(refresh).revokeAll("CUSTOMER", customer.getId());
        code("RESET_CODE_INVALID", () -> verify(oldCode));
        code("RESET_TOKEN_INVALID", () -> service.confirm("customer", new ExtendedAuthRequests.Confirm(token, "new-password"), "ip"));
    }
    @Test void expiredGrantFails() {
        request(customer.getEmail()); var token=verify(sentCode); stored.get().setGrantExpiresAt(LocalDateTime.now().minusSeconds(1));
        code("RESET_TOKEN_INVALID", () -> service.confirm("customer", new ExtendedAuthRequests.Confirm(token, "new-password"), "ip"));
    }
    @Test void wrongRoleCannotUseGrant() {
        request(customer.getEmail()); var token=verify(sentCode);
        code("RESET_TOKEN_INVALID", () -> service.confirm("admin", new ExtendedAuthRequests.Confirm(token, "new-password"), "ip"));
    }
    @Test void emailChangedSinceRequestFails() {
        request(customer.getEmail()); var token=verify(sentCode); customer.setEmail("changed@example.com");
        code("RESET_TOKEN_INVALID", () -> service.confirm("customer", new ExtendedAuthRequests.Confirm(token, "new-password"), "ip"));
    }
    @Test void rateLimitPreventsDelivery() {
        doThrow(new BusinessException("RESET_REQUEST_RATE_LIMITED", "limit")).when(guard).check(anyString(), anyInt(), anyInt(), anyString());
        code("RESET_REQUEST_RATE_LIMITED", () -> request(customer.getEmail())); org.mockito.Mockito.verify(mail, never()).send(anyString(), anyString(), any());
    }
}
