package com.siparo.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {
    Optional<PasswordResetCode> findFirstByPhoneNumberAndConsumedAtIsNullOrderByCreatedAtDesc(String phoneNumber);

    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, LocalDateTime after);
}
