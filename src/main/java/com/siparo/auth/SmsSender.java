package com.siparo.auth;

/**
 * SMS gönderim sınırı (şifre sıfırlama doğrulama kodu). Bir sağlayıcı (örn. Netgsm, İleti Merkezi, Twilio) bu arayüzü
 * uygulayan bir bean olarak eklenir ve {@code SMS_PROVIDER} ile etkinleştirilir. Sağlayıcı yoksa bean bulunmaz ve
 * şifre sıfırlama uçları FEATURE_UNAVAILABLE döner — sahte gönderim yapılmaz.
 */
public interface SmsSender {
    void send(String phoneNumber, String message);
}
