package com.siparo.delivery;

import com.siparo.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class DeliveryPinService {
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final PasswordEncoder passwordEncoder;
    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeliveryPinService(PasswordEncoder passwordEncoder, @Value("${delivery.pin-secret}") String secret) {
        this.passwordEncoder = passwordEncoder;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize delivery PIN encryption", e);
        }
    }

    public String generate() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    public String hash(String pin) {
        return passwordEncoder.encode(pin);
    }

    public boolean matches(String pin, String hash) {
        return passwordEncoder.matches(pin, hash);
    }

    public String encrypt(String pin) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(pin.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt delivery PIN", e);
        }
    }

    public String decrypt(String encryptedPin) {
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedPin);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("Delivery PIN is unavailable");
        }
    }
}
