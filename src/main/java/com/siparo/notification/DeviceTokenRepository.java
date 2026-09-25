package com.siparo.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {
    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findAllByOwnerTypeAndOwnerId(String ownerType, UUID ownerId);

    @Modifying
    @Query("delete from DeviceToken t where t.ownerType = :ownerType and t.ownerId = :ownerId")
    void deleteAllByOwnerTypeAndOwnerId(@Param("ownerType") String ownerType, @Param("ownerId") UUID ownerId);

    @Modifying
    @Query("delete from DeviceToken t where t.token = :token and t.ownerType = :ownerType and t.ownerId = :ownerId")
    void deleteOwnedToken(@Param("token") String token, @Param("ownerType") String ownerType, @Param("ownerId") UUID ownerId);
}
