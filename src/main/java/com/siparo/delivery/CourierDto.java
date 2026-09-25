package com.siparo.delivery;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CourierDto {
    private UUID id;
    private UUID restaurantId;
    private String restaurantName;
    private String fullName;
    private String phoneNumber;
    private String status;
    private boolean active;
    private long activeDeliveries;
    private long completedToday;
}
