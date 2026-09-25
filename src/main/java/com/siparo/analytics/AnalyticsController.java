package com.siparo.analytics;

import com.siparo.common.PageDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESTAURANT_ADMIN') and @securityService.isRestaurantOwner(authentication, #restaurantId)")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/analytics/overview")
    public ResponseEntity<AnalyticsDtos.Overview> overview(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(analyticsService.overview(restaurantId));
    }

    @GetMapping("/analytics/timeseries")
    public ResponseEntity<List<AnalyticsDtos.DayPoint>> timeseries(@PathVariable UUID restaurantId,
                                                                    @RequestParam(defaultValue = "14") int days) {
        return ResponseEntity.ok(analyticsService.timeseries(restaurantId, days));
    }

    @GetMapping("/customers")
    public ResponseEntity<PageDto<AnalyticsDtos.CustomerRow>> customers(@PathVariable UUID restaurantId,
                                                                        @RequestParam(required = false) String q,
                                                                        @RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(analyticsService.customers(restaurantId, q, page, size));
    }
}
