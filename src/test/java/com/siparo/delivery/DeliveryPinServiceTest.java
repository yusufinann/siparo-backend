package com.siparo.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryPinServiceTest {
    private final DeliveryPinService service = new DeliveryPinService(
            new BCryptPasswordEncoder(),
            "test_delivery_pin_secret_long_enough"
    );

    @Test
    void generatedPinCanBeEncryptedDisplayedAndVerified() {
        String pin = service.generate();
        String encrypted = service.encrypt(pin);
        String hash = service.hash(pin);

        assertEquals(6, pin.length());
        assertTrue(pin.chars().allMatch(Character::isDigit));
        assertEquals(pin, service.decrypt(encrypted));
        assertTrue(service.matches(pin, hash));
        assertFalse(service.matches("000000".equals(pin) ? "111111" : "000000", hash));
    }
}
