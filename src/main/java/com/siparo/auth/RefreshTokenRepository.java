package com.siparo.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Aynı token'la eşzamanlı iki yenileme isteği sıraya girer (satır kilidi). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("update RefreshToken token set token.revokedAt = :now, token.replacedBy = null where token.ownerType = :ownerType and token.ownerId = :ownerId")
    int revokeAll(@Param("ownerType") String ownerType, @Param("ownerId") UUID ownerId, @Param("now") LocalDateTime now);
}
