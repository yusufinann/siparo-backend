package com.siparo.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, UUID> {
}
