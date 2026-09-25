package com.siparo.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {
    List<CustomerAddress> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<CustomerAddress> findByCustomerId(UUID customerId);
}
