package com.luggagekeeper.keeper_app.store.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 카테고리 아이템 엔티티
 * Flutter의 CategoryItem 모델과 호환
 */
@Entity
@Table(name = "category_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_settings_id", nullable = false)
    @NotNull
    private StoreSettings storeSettings;

    @Column(nullable = false)
    @NotBlank
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;


    @Column(name = "icon_url")
    private String iconUrl;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // 편의 메서드들

    /**
     * 활성화 여부 확인
     */
    public boolean isActive() {
        return isActive != null && isActive;
    }
}