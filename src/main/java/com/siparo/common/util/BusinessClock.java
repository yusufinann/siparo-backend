package com.siparo.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** İş kurallarında kullanılan tek saat dilimi (restoranlar Türkiye saatine göre çalışır). */
public final class BusinessClock {
    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private BusinessClock() {}

    public static ZonedDateTime now() {
        return ZonedDateTime.now(ZONE);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * Veritabanındaki {@code LocalDateTime} damgaları JVM'nin yerel saatiyle yazılır (CreationTimestamp);
     * iş günü sınırını aynı referansa çevirir.
     */
    public static LocalDateTime startOfBusinessDay(LocalDate day) {
        return day.atStartOfDay(ZONE).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
