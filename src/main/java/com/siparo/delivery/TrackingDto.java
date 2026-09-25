package com.siparo.delivery;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class TrackingDto {
    private boolean trackingAvailable;
    private String deliveryStatus;
    private CourierSummary courier;
    private DeliveryDto.LocationDto location;
    private Destination destination;
    private Instant estimatedDeliveryAt;
    private String deliveryPin;
    private String locationFreshness;

    @Getter
    @Setter
    public static class CourierSummary {
        private String displayName;
    }

    @Getter
    @Setter
    public static class Destination {
        private Double latitude;
        private Double longitude;
    }
}
