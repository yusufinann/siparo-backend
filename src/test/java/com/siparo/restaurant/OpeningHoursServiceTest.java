package com.siparo.restaurant;

import com.siparo.common.util.BusinessClock;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpeningHoursServiceTest {
    private final OpeningHoursService service = new OpeningHoursService();

    private OpeningHour hour(int day, String opens, String closes) {
        OpeningHour hour = new OpeningHour();
        hour.setRestaurantId(UUID.randomUUID());
        hour.setDayOfWeek((short) day);
        hour.setOpensAt(LocalTime.parse(opens));
        hour.setClosesAt(LocalTime.parse(closes));
        return hour;
    }

    /** 2026-09-21 Pazartesi. */
    private ZonedDateTime monday(String time) {
        return ZonedDateTime.of(2026, 9, 21, LocalTime.parse(time).getHour(), LocalTime.parse(time).getMinute(), 0, 0, BusinessClock.ZONE);
    }

    @Test
    void noScheduleMeansOpen() {
        assertThat(service.state(List.of(), monday("03:00")).withinHours()).isTrue();
    }

    @Test
    void regularRange() {
        List<OpeningHour> hours = List.of(hour(1, "10:00", "22:00"));
        assertThat(service.state(hours, monday("09:59")).withinHours()).isFalse();
        assertThat(service.state(hours, monday("10:00")).withinHours()).isTrue();
        assertThat(service.state(hours, monday("22:00")).withinHours()).isFalse();
    }

    @Test
    void overnightRangeSpillsIntoNextDay() {
        // Pazar 18:00 – Pazartesi 02:00
        List<OpeningHour> hours = List.of(hour(7, "18:00", "02:00"));
        assertThat(service.state(hours, monday("01:30")).withinHours()).isTrue();
        assertThat(service.state(hours, monday("02:30")).withinHours()).isFalse();
    }

    @Test
    void nextOpeningIsReportedWhenClosed() {
        List<OpeningHour> hours = List.of(hour(1, "10:00", "22:00"), hour(2, "11:00", "22:00"));
        OpeningHoursService.OpenState state = service.state(hours, monday("23:00"));
        assertThat(state.withinHours()).isFalse();
        assertThat(state.nextOpeningAt()).isEqualTo(monday("11:00").plusDays(1).toInstant());
    }

    @Test
    void equalOpenAndCloseMeansAllDay() {
        assertThat(service.state(List.of(hour(1, "00:00", "00:00")), monday("04:00")).withinHours()).isTrue();
    }
}
