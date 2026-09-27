package com.siparo.auth;

import com.siparo.common.exception.BusinessException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

@Service
public class PasswordResetMailService {
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;
    private final String host;
    public PasswordResetMailService(ObjectProvider<JavaMailSender> senders, @Value("${siparo.auth.mail-from:}") String from,
                                    @Value("${spring.mail.host:}") String host) {
        this.senders = senders; this.from = from; this.host = host;
    }
    public void requireConfigured() {
        if (from.isBlank() || host.isBlank() || senders.getIfAvailable() == null)
            throw new BusinessException("FEATURE_UNAVAILABLE", "Email delivery is not configured", HttpStatus.SERVICE_UNAVAILABLE);
    }
    /** Async delivery keeps SMTP response time/account existence out of the public response. No secret logging. */
    @Async("authMailExecutor")
    public void send(String email, String code, String language) {
        try {
            var sender = senders.getObject();
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            boolean tr = "tr".equals(language);
            String title = tr ? "Şifre sıfırlama kodun" : "Your password reset code";
            String info = tr ? "Bu kod 10 dakika boyunca geçerlidir. Bu isteği siz yapmadıysanız bu mesajı dikkate almayabilirsiniz." : "This code is valid for 10 minutes. If you did not request this, you can ignore this email.";
            helper.setFrom(from); helper.setTo(email); helper.setSubject("Siparo — " + title);
            helper.setText("Siparo\n" + title + "\n" + code + "\n" + info,
                "<html><meta name=\"viewport\" content=\"width=device-width\"><body style=\"margin:0;background:#f5f6f8;font-family:Arial,sans-serif;color:#202124\"><div style=\"max-width:480px;margin:24px auto;padding:32px;background:white;border-radius:16px\"><h1 style=\"color:#ff6a00\">Siparo</h1><h2>" + title + "</h2><p style=\"font-size:32px;letter-spacing:8px;font-weight:bold\">" + code + "</p><p>" + info + "</p></div></body></html>");
            sender.send(message);
        } catch (Exception failure) {
            // Deliberately exclude exception bodies: SMTP providers can include recipient and message contents.
            org.slf4j.LoggerFactory.getLogger(getClass()).error("Password reset email delivery failed (type={})", failure.getClass().getSimpleName());
        }
    }
}
