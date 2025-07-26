package com.luggagekeeper.keeper_app.store.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 메뉴 아이템 엔티티
 * Flutter의 MenuItem 모델과 호환
 */
@Entity
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_intro_id", nullable = false)
    @NotNull
    private StoreIntro storeIntro;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private Integer price; // 원 단위 가격

    @Column(columnDefinition = "TEXT")
    private String description; // 메뉴 소개 (선택)

    @Column(name = "photo_url")
    private String photoUrl; // 메뉴 사진 URL (선택)

    @Column(name = "display_order")
    private Integer displayOrder; // 메뉴 표시 순서

    @Column(name = "is_available")
    private Boolean isAvailable = true; // 메뉴 제공 가능 여부

    // 편의 메서드들

    /**
     * 가격을 원화 형식으로 반환
     */
    public String getFormattedPrice() {
        return String.format("%,d원", price);
    }

    /**
     * 메뉴 제공 가능 여부 확인
     */
    public boolean isAvailable() {
        return isAvailable != null && isAvailable;
    }
}