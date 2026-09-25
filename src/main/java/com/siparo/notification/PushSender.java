package com.siparo.notification;

import java.util.List;
import java.util.Map;

/** Push gönderim sınırı. Sağlayıcı yapılandırılmadıysa {@link #isEnabled()} false döner ve hiçbir şey gönderilmez. */
public interface PushSender {
    record PushMessage(List<String> tokens, String title, String body, Map<String, String> data) {}

    boolean isEnabled();

    void send(PushMessage message);
}
