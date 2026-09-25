package com.siparo.customer;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CustomerDto {
    private UUID id;
    private String phoneNumber;
    private String fullName;
    private String email;
    private boolean notifyOrderUpdates;
    private boolean notifyCampaigns;
    private boolean notifyRestaurantNews;
}
