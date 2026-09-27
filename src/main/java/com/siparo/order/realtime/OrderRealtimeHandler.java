package com.siparo.order.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siparo.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRealtimeHandler extends TextWebSocketHandler {

    private static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "Unauthorized");
    private static final CloseStatus POLICY_VIOLATION = CloseStatus.POLICY_VIOLATION;
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final com.siparo.common.security.SessionRevocationService revocation;
    private final ConcurrentMap<String, String> sessionTokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, WebSocketSession>> restaurantSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> restaurantBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, WebSocketSession>> customerSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> customerBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, WebSocketSession>> courierSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> courierBySession = new ConcurrentHashMap<>();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(message.getPayload());
        } catch (Exception exception) {
            session.close(POLICY_VIOLATION);
            return;
        }

        if (!restaurantBySession.containsKey(session.getId()) && !customerBySession.containsKey(session.getId())
                && !courierBySession.containsKey(session.getId())) {
            authenticate(session, payload);
            return;
        }

        if ("PING".equals(payload.path("type").asText())) {
            send(session.getId(), new TextMessage("{\"type\":\"PONG\"}"));
            return;
        }

        session.close(POLICY_VIOLATION);
    }

    private void authenticate(WebSocketSession session, JsonNode payload) throws IOException {
        if (!"AUTH".equals(payload.path("type").asText())) {
            session.close(UNAUTHORIZED);
            return;
        }

        String token = payload.path("token").asText(null);
        try {
            String role = jwtService.extractClaim(token, claims -> claims.get("role", String.class));
            String idClaim = "ROLE_RESTAURANT_ADMIN".equals(role)
                    ? jwtService.extractRestaurantId(token) : jwtService.extractUserId(token);
            if ((!"ROLE_RESTAURANT_ADMIN".equals(role) && !"ROLE_CUSTOMER".equals(role) && !"ROLE_COURIER".equals(role))
                    || idClaim == null) {
                session.close(UNAUTHORIZED);
                return;
            }

            if (!revocation.valid(token)) { session.close(UNAUTHORIZED); return; }
            sessionTokens.put(session.getId(), token);
            UUID principalId = UUID.fromString(idClaim);
            WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                    session,
                    SEND_TIME_LIMIT_MS,
                    SEND_BUFFER_LIMIT_BYTES
            );
            ConcurrentMap<String, UUID> bySession = switch (role) {
                case "ROLE_CUSTOMER" -> customerBySession;
                case "ROLE_COURIER" -> courierBySession;
                default -> restaurantBySession;
            };
            ConcurrentMap<UUID, ConcurrentMap<String, WebSocketSession>> sessions = switch (role) {
                case "ROLE_CUSTOMER" -> customerSessions;
                case "ROLE_COURIER" -> courierSessions;
                default -> restaurantSessions;
            };
            bySession.put(session.getId(), principalId);
            sessions.computeIfAbsent(principalId, ignored -> new ConcurrentHashMap<>())
                    .put(session.getId(), safeSession);
            safeSession.sendMessage(new TextMessage("{\"type\":\"AUTHENTICATED\"}"));
        } catch (Exception exception) {
            session.close(UNAUTHORIZED);
        }
    }

    public void publishOrderCreated(UUID restaurantId, UUID orderId) {
        publish(restaurantSessions.get(restaurantId), Map.of(
                    "type", "ORDER_CREATED",
                    "orderId", orderId.toString(),
                    "occurredAt", Instant.now().toString()
            ));
    }

    public void publishOrderStatusChanged(OrderStatusChangedEvent event) {
        Map<String, Object> payload = Map.of(
                "type", "ORDER_STATUS_CHANGED",
                "orderId", event.orderId().toString(),
                "status", event.status(),
                "occurredAt", Instant.now().toString());
        publish(customerSessions.get(event.customerId()), payload);
        publish(restaurantSessions.get(event.restaurantId()), payload);
    }

    public void publishCourierDeliveryChanged(UUID courierId, UUID assignmentId, UUID orderId, String type) {
        publish(courierSessions.get(courierId), Map.of(
                "type", "DELIVERY_" + type,
                "assignmentId", assignmentId.toString(),
                "orderId", orderId.toString(),
                "occurredAt", Instant.now().toString()));
    }

    /** Müşterinin bildirim merkezine yeni kayıt düştü; açık uygulama toast + ses gösterir. */
    public void publishNotification(UUID customerId, Object notification) {
        publish(customerSessions.get(customerId), Map.of(
                "type", "NOTIFICATION_CREATED",
                "notification", notification,
                "occurredAt", Instant.now().toString()));
    }

    /** İşletme panosunu yenilemek için genel olay (örn. yeni müşteri sorunu). */
    public void publishRestaurantEvent(UUID restaurantId, String type, UUID orderId) {
        publish(restaurantSessions.get(restaurantId), Map.of(
                "type", type,
                "orderId", orderId == null ? "" : orderId.toString(),
                "occurredAt", Instant.now().toString()));
    }

    public void publishCourierLocationChanged(CourierLocationChangedEvent event) {
        Map<String, Object> location = new java.util.HashMap<>();
        location.put("latitude", event.latitude());
        location.put("longitude", event.longitude());
        location.put("accuracy", event.accuracy());
        location.put("heading", event.heading());
        location.put("speed", event.speed());
        location.put("recordedAt", event.recordedAt().toString());
        publish(customerSessions.get(event.customerId()), Map.of(
                "type", "COURIER_LOCATION_UPDATED",
                "orderId", event.orderId().toString(),
                "location", location));
    }

    private void publish(ConcurrentMap<String, WebSocketSession> sessions, Map<String, ?> event) {
        if (sessions == null || sessions.isEmpty()) return;
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception exception) {
            log.error("Could not serialize realtime order event", exception);
            return;
        }

        TextMessage message = new TextMessage(payload);
        sessions.forEach((sessionId, session) -> {
            try {
                if (!revocation.valid(sessionTokens.get(sessionId))) {
                    session.close(UNAUTHORIZED); removeSession(sessionId); return;
                }
                if (session.isOpen()) {
                    session.sendMessage(message);
                } else {
                    removeSession(sessionId);
                }
            } catch (Exception exception) {
                log.warn("Could not send order event to WebSocket session {}", sessionId);
                removeSession(sessionId);
                closeQuietly(session);
            }
        });
    }

    private void send(String sessionId, TextMessage message) throws IOException {
        UUID restaurantId = restaurantBySession.get(sessionId);
        WebSocketSession session = restaurantId == null
                ? null
                : restaurantSessions.getOrDefault(restaurantId, new ConcurrentHashMap<>()).get(sessionId);
        if (session == null) {
            UUID customerId = customerBySession.get(sessionId);
            session = customerId == null ? null
                    : customerSessions.getOrDefault(customerId, new ConcurrentHashMap<>()).get(sessionId);
        }
        if (session == null) {
            UUID courierId = courierBySession.get(sessionId);
            session = courierId == null ? null
                    : courierSessions.getOrDefault(courierId, new ConcurrentHashMap<>()).get(sessionId);
        }
        if (session != null && session.isOpen()) {
            if (!revocation.valid(sessionTokens.get(sessionId))) { session.close(UNAUTHORIZED); removeSession(sessionId); }
            else session.sendMessage(message);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        removeSession(session.getId());
        closeQuietly(session);
    }

    private void removeSession(String sessionId) {
        sessionTokens.remove(sessionId);
        UUID restaurantId = restaurantBySession.remove(sessionId);
        if (restaurantId != null) restaurantSessions.computeIfPresent(restaurantId, (ignored, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
        UUID customerId = customerBySession.remove(sessionId);
        if (customerId != null) customerSessions.computeIfPresent(customerId, (ignored, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
        UUID courierId = courierBySession.remove(sessionId);
        if (courierId != null) courierSessions.computeIfPresent(courierId, (ignored, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close();
        } catch (IOException ignored) {
            // Connection is already unusable.
        }
    }
}
