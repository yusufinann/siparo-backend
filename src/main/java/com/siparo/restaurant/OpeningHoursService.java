package com.siparo.restaurant;

import com.siparo.common.util.BusinessClock;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Haftalık çalışma saatlerinden "şu an açık mı" ve "ne zaman açılacak" hesabı (Türkiye saati). */
@Service
public class OpeningHoursService {

    public record OpenState(boolean hasSchedule, boolean withinHours, Instant nextOpeningAt) {}

    public OpenState state(List<OpeningHour> hours) {
        return state(hours, BusinessClock.now());
    }

    public OpenState state(List<OpeningHour> hours, ZonedDateTime now) {
        if (hours == null || hours.isEmpty()) {
            // Program tanımlanmadıysa restoran yalnızca "sipariş alıyor" anahtarıyla yönetilir.
            return new OpenState(false, true, null);
        }
        Map<Integer, OpeningHour> byDay = hours.stream()
                .collect(Collectors.toMap(h -> (int) h.getDayOfWeek(), Function.identity(), (a, b) -> a));
        boolean within = isWithin(byDay, now);
        return new OpenState(true, within, within ? null : nextOpening(byDay, now));
    }

    private boolean isWithin(Map<Integer, OpeningHour> byDay, ZonedDateTime now) {
        int today = now.getDayOfWeek().getValue();
        LocalTime time = now.toLocalTime();
        OpeningHour current = byDay.get(today);
        if (current != null) {
            LocalTime opens = current.getOpensAt();
            LocalTime closes = current.getClosesAt();
            if (opens.equals(closes)) return true; // 24 saat açık
            if (opens.isBefore(closes)) {
                if (!time.isBefore(opens) && time.isBefore(closes)) return true;
            } else if (!time.isBefore(opens)) {
                return true; // gece yarısını aşan aralığın bugünkü kısmı
            }
        }
        OpeningHour previous = byDay.get(today == 1 ? 7 : today - 1);
        return previous != null
                && previous.getClosesAt().isBefore(previous.getOpensAt())
                && time.isBefore(previous.getClosesAt());
    }

    private Instant nextOpening(Map<Integer, OpeningHour> byDay, ZonedDateTime now) {
        for (int offset = 0; offset <= 7; offset++) {
            ZonedDateTime day = now.plusDays(offset);
            OpeningHour hour = byDay.get(day.getDayOfWeek().getValue());
            if (hour == null) continue;
            ZonedDateTime opensAt = day.with(hour.getOpensAt()).withSecond(0).withNano(0);
            if (opensAt.isAfter(now)) return opensAt.toInstant();
        }
        return null;
    }
}
