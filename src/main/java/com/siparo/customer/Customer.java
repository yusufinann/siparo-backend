package com.siparo.customer;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    private UUID id = UUID.randomUUID();

    private String phoneNumber;
    private String fullName;
    private String email;
    private String passwordHash;
    private String googleSubject;
    private LocalDateTime sessionsInvalidBefore;

    /** Kayıt sırasında zorunlu yasal onayların sunucu tarafındaki kanıt zamanı. */
    private LocalDateTime termsAcceptedAt;
    private LocalDateTime kvkkAcceptedAt;

    /** Hesap silindiğinde kişisel veriler anonimleştirilir ve oturum açılamaz. */
    private LocalDateTime deletedAt;

    private boolean notifyOrderUpdates = true;
    private boolean notifyCampaigns = true;
    private boolean notifyRestaurantNews = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
