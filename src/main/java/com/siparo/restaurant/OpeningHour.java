package com.siparo.restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

/** Gün başına tek aralık. {@code closesAt < opensAt} ise aralık gece yarısını aşar. dayOfWeek: 1=Pazartesi … 7=Pazar. */
@Entity
@Table(name = "restaurant_opening_hours")
@Getter
@Setter
@NoArgsConstructor
public class OpeningHour {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(nullable = false)
    private short dayOfWeek;

    @Column(nullable = false)
    private LocalTime opensAt;

    @Column(nullable = false)
    private LocalTime closesAt;
}
