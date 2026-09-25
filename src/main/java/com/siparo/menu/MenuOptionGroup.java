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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Ürün seçenek grubu: örn. "Boyut" (zorunlu, tek seçim) veya "Ekstralar" (isteğe bağlı, çoklu seçim). */
@Entity
@Table(name = "menu_option_groups")
@Getter
@Setter
@NoArgsConstructor
public class MenuOptionGroup {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false, length = 100)
    private String name;

    private boolean required;

    private int minSelect;

    private int maxSelect = 1;

    private int displayOrder;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @BatchSize(size = 100)
    private List<MenuOption> options = new ArrayList<>();
}
