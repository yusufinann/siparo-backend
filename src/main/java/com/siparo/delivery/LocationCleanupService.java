package com.siparo.delivery;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class LocationCleanupService {
    private final CourierLocationRepository locationRepository;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void removeExpiredLocations() {
        locationRepository.deleteByRecordedAtBefore(Instant.now().minus(72, ChronoUnit.HOURS));
    }
}
