package com.siparo.order;

import com.siparo.menu.MenuItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Sipariş kalemi: ürün adı, birim fiyat ve seçenekler sipariş anındaki hâliyle saklanır. */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false)
    private String menuItemName;

    @Column(nullable = false)
    private int quantity;

    /** Seçenekler dahil birim fiyat. */
    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal optionsTotal = BigDecimal.ZERO;

    /** [{"id","groupName","name","priceDelta"}] */
    private String optionsJson;

    @Column(length = 300)
    private String note;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
