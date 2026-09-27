package com.siparo.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface EmailResetRepository extends JpaRepository<EmailReset, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from EmailReset r where r.resetKey = :key")
    Optional<EmailReset> lockByKey(@Param("key") String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from EmailReset r where r.grantHash = :hash")
    Optional<EmailReset> lockByGrant(@Param("hash") String hash);
}
