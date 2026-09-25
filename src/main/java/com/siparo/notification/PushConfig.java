package com.siparo.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Push sağlayıcısı: {@code PUSH_PROVIDER=expo} ile Expo Push API kullanılır (mağaza derlemelerinde FCM/APNs kimlik
 * bilgileri EAS'a yüklenmiş olmalıdır). Varsayılan {@code none}: bildirimler yalnızca uygulama içi bildirim merkezine yazılır.
 */
@Slf4j
@Configuration
public class PushConfig {
    private static final URI EXPO_PUSH_URL = URI.create("https://exp.host/--/api/v2/push/send");
    private static final int EXPO_BATCH_SIZE = 100;

    @Bean
    public PushSender pushSender(@Value("${siparo.push.provider:none}") String provider,
                                 @Value("${siparo.push.expo.access-token:}") String accessToken,
                                 ObjectMapper objectMapper) {
        if (!"expo".equalsIgnoreCase(provider)) {
            return new PushSender() {
                @Override
                public boolean isEnabled() {
                    return false;
                }

                @Override
                public void send(PushMessage message) {
                    // Sağlayıcı yok: bilinçli olarak hiçbir şey gönderilmez.
                }
            };
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return new PushSender() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void send(PushMessage message) {
                List<Map<String, Object>> payload = new ArrayList<>();
                for (String token : message.tokens()) {
                    payload.add(Map.of("to", token, "title", message.title(), "body", message.body(),
                            "data", message.data() == null ? Map.of() : message.data(), "sound", "default"));
                }
                for (int start = 0; start < payload.size(); start += EXPO_BATCH_SIZE) {
                    List<Map<String, Object>> batch = payload.subList(start, Math.min(payload.size(), start + EXPO_BATCH_SIZE));
                    try {
                        HttpRequest.Builder request = HttpRequest.newBuilder(EXPO_PUSH_URL)
                                .timeout(Duration.ofSeconds(10))
                                .header("Content-Type", "application/json")
                                .header("Accept", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(batch)));
                        if (accessToken != null && !accessToken.isBlank()) {
                            request.header("Authorization", "Bearer " + accessToken);
                        }
                        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() >= 300) {
                            log.warn("Expo push request failed with status {}: {}", response.statusCode(), response.body());
                        }
                    } catch (Exception exception) {
                        if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                        log.warn("Could not send push notification batch: {}", exception.getMessage());
                    }
                }
            }
        };
    }
}
