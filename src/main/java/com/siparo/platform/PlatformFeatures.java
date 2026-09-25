package com.siparo.platform;

import com.siparo.notification.PushSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dış servislere bağlı özelliklerin açık/kapalı durumu. İstemciler bunu {@code GET /api/v1/public/config} ile okur ve
 * yapılandırılmamış özellikleri gizler (sahte akış gösterilmez).
 */
@Component
@RequiredArgsConstructor
public class PlatformFeatures {

    private final PushSender pushSender;

    @Value("${siparo.payments.provider:none}")
    private String paymentProvider;

    @Value("${siparo.sms.provider:none}")
    private String smsProvider;

    @Value("${siparo.public-web-url:}")
    private String publicWebUrl;

    @Value("${siparo.app-store-url:}")
    private String appStoreUrl;

    @Value("${siparo.play-store-url:}")
    private String playStoreUrl;

    @Value("${siparo.support.phone:}")
    private String supportPhone;

    @Value("${siparo.support.email:}")
    private String supportEmail;

    public boolean onlinePaymentEnabled() {
        return !"none".equalsIgnoreCase(paymentProvider) && !paymentProvider.isBlank();
    }

    public boolean passwordResetEnabled() {
        return !"none".equalsIgnoreCase(smsProvider) && !smsProvider.isBlank();
    }

    public boolean pushEnabled() {
        return pushSender.isEnabled();
    }

    public String smsProvider() {
        return smsProvider;
    }

    public String publicWebUrl() {
        return blankToNull(publicWebUrl);
    }

    public String appStoreUrl() {
        return blankToNull(appStoreUrl);
    }

    public String playStoreUrl() {
        return blankToNull(playStoreUrl);
    }

    public String supportPhone() {
        return blankToNull(supportPhone);
    }

    public String supportEmail() {
        return blankToNull(supportEmail);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
