package com.siparo.order.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siparo.common.security.CustomUserDetails;
import com.siparo.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderRealtimeHandlerTest {

    private JwtService jwtService;
    private OrderRealtimeHandler handler;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "test_secret_key_that_is_long_enough_for_hmac_sha_256_signing");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 60_000L);
        handler = new OrderRealtimeHandler(new ObjectMapper(), jwtService);
    }

    @Test
    void authenticatesRestaurantAndPublishesOrderCreatedEvent() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        WebSocketSession session = session("session-1");
        String token = token("ROLE_RESTAURANT_ADMIN", restaurantId.toString());

        handler.handleTextMessage(session, authMessage(token));

        verify(session).sendMessage(argThat((WebSocketMessage<?> message) ->
                message.getPayload().toString().contains("AUTHENTICATED")));
        clearInvocations(session);

        handler.publishOrderCreated(restaurantId, orderId);

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getPayload())
                .contains("ORDER_CREATED")
                .contains(orderId.toString());
    }

    @Test
    void customerReceivesOnlyOwnOrderStatusAndLocation() throws Exception {
        WebSocketSession session = session("session-2");
        UUID customerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String token = token("ROLE_CUSTOMER", null, customerId);

        handler.handleTextMessage(session, authMessage(token));
        verify(session).sendMessage(argThat((WebSocketMessage<?> message) ->
                message.getPayload().toString().contains("AUTHENTICATED")));
        clearInvocations(session);

        handler.publishOrderStatusChanged(new OrderStatusChangedEvent(
                UUID.randomUUID(), otherCustomerId, orderId, "ON_THE_WAY"));
        org.mockito.Mockito.verifyNoInteractions(session);

        handler.publishOrderStatusChanged(new OrderStatusChangedEvent(
                UUID.randomUUID(), customerId, orderId, "ON_THE_WAY"));
        verify(session).sendMessage(argThat((WebSocketMessage<?> message) ->
                message.getPayload().toString().contains("ON_THE_WAY")));
        clearInvocations(session);

        handler.publishCourierLocationChanged(new CourierLocationChangedEvent(
                customerId, orderId, 41.0, 29.0, 5.0, null, null, java.time.Instant.now()));
        verify(session).sendMessage(argThat((WebSocketMessage<?> message) ->
                message.getPayload().toString().contains("COURIER_LOCATION_UPDATED")));
    }

    /** Kuryeler yalnızca kendi teslimat olaylarını alır (atama/iptal), müşteri veya restoran olaylarını almaz. */
    @Test
    void courierReceivesOnlyOwnDeliveryEvents() throws Exception {
        WebSocketSession session = session("session-3");
        UUID courierId = UUID.randomUUID();
        handler.handleTextMessage(session, authMessage(token("ROLE_COURIER", UUID.randomUUID().toString(), courierId)));
        verify(session).sendMessage(argThat((WebSocketMessage<?> message) ->
                message.getPayload().toString().contains("AUTHENTICATED")));
        clearInvocations(session);

        handler.publishCourierDeliveryChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ASSIGNED");
        org.mockito.Mockito.verifyNoInteractions(session);

        handler.publishCourierDeliveryChanged(courierId, UUID.randomUUID(), UUID.randomUUID(), "ASSIGNED");
        verify(session).sendMessage(argThat((WebSocketMessage<?> message) ->
                message.getPayload().toString().contains("DELIVERY_ASSIGNED")));
    }

    @Test
    void rejectsUnknownRole() throws Exception {
        WebSocketSession session = session("session-4");
        handler.handleTextMessage(session, authMessage(token("ROLE_UNKNOWN", null)));

        verify(session).close(argThat(status -> status.getCode() == 4401));
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private TextMessage authMessage(String token) throws Exception {
        return new TextMessage(new ObjectMapper().writeValueAsString(
                java.util.Map.of("type", "AUTH", "token", token)
        ));
    }

    private String token(String role, String restaurantId) {
        return token(role, restaurantId, UUID.randomUUID());
    }

    private String token(String role, String restaurantId, UUID userId) {
        CustomUserDetails user = new CustomUserDetails(
                userId.toString(),
                "test-user",
                "",
                restaurantId,
                List.of(new SimpleGrantedAuthority(role))
        );
        return jwtService.generateToken(user);
    }
}
