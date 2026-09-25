package com.siparo.menu;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
public class MenuItem {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private MenuCategory category;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    private String imageUrl;

    /** AVAILABLE veya OUT_OF_STOCK. */
    @Column(nullable = false)
    private String status;

    /** İşletmenin beyan ettiği önceki fiyat (üstü çizili gösterilir); yalnızca güncel fiyattan yüksekse geçerlidir. */
    private BigDecimal oldPrice;

    /** İşletmenin sepette "yanına iyi gider" olarak önerdiği ürün. */
    private Boolean isUpsell = false;

    private int displayOrder;

    /** Siparişte kullanılmış ürün silinemez; menüden kaldırıldığında arşivlenir. */
    private boolean archived;

    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @BatchSize(size = 50)
    private List<MenuOptionGroup> optionGroups = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public boolean isAvailable() {
        return !archived && "AVAILABLE".equalsIgnoreCase(status);
    }
}
