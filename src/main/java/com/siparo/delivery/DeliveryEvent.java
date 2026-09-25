package com.siparo.delivery;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_events")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryEvent {
    @Id
    private UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_assignment_id", nullable = false)
    private DeliveryAssignment deliveryAssignment;
    @Column(nullable = false)
    private String type;
    @Column(nullable = false)
    private String actorType;
    @Column(nullable = false)
    private String actorId;
    private String reason;
    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;
    @CreationTimestamp
    private Instant createdAt;
}
