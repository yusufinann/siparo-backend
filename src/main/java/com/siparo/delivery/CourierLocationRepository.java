package com.siparo.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface CourierLocationRepository extends JpaRepository<CourierLocationPoint, UUID> {
    long deleteByRecordedAtBefore(Instant cutoff);
}
