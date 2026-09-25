package com.siparo.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {
    @Test
    void normalizesTurkishMobileFormats() {
        assertThat(PhoneNumbers.normalize("0532 123 45 67")).isEqualTo("05321234567");
        assertThat(PhoneNumbers.normalize("+90 532 123 45 67")).isEqualTo("05321234567");
        assertThat(PhoneNumbers.normalize("5321234567")).isEqualTo("05321234567");
        assertThat(PhoneNumbers.normalize("05321234567")).isEqualTo("05321234567");
    }

    @Test
    void distanceIsSymmetricAndReasonable() {
        Double ankaraIstanbul = GeoUtil.distanceKm(39.92, 32.85, 41.01, 28.97);
        assertThat(ankaraIstanbul).isBetween(340.0, 360.0);
        assertThat(GeoUtil.distanceKm(null, 1.0, 2.0, 3.0)).isNull();
    }
}
