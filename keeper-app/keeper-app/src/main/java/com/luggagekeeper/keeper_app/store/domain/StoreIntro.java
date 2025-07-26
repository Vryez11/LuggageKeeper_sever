package com.luggagekeeper.keeper_app.store.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 매장 소개 정보 엔티티
 * Flutter의 StoreIntro 모델과 호환
 */
@Entity
@Table(name = "store_intros")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoreIntro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "store_id", nullable = false)
    @NotNull
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type")
    @NotNull
    private BusinessType type = BusinessType.OTHER;

    @ElementCollection
    @CollectionTable(name = "store_photos", joinColumns = @JoinColumn(name = "store_intro_id"))
    @Column(name = "photo_url")
    private List<String> photos = new ArrayList<>();

    @OneToMany(mappedBy = "storeIntro", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private List<MenuItem> menuItems = new ArrayList<>();

    @Column(columnDefinition = "TEXT", nullable = false)
    @NotNull
    private String description;

    // 기존 필드들 (호환성 유지)
    @Column(name = "introduction", columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "facilities", columnDefinition = "TEXT")
    private String facilities;

    @Column(name = "operating_hours", columnDefinition = "TEXT")
    private String operatingHours;

    @Column(name = "special_notes", columnDefinition = "TEXT")
    private String specialNotes;

    // 편의 메서드들

    /**
     * 메뉴 아이템 추가
     */
    public void addMenuItem(MenuItem menuItem) {
        menuItems.add(menuItem);
        menuItem.setStoreIntro(this);
        if (menuItem.getDisplayOrder() == null) {
            menuItem.setDisplayOrder(menuItems.size());
        }
    }

    /**
     * 메뉴 아이템 제거
     */
    public void removeMenuItem(MenuItem menuItem) {
        menuItems.remove(menuItem);
        menuItem.setStoreIntro(null);
    }

    /**
     * 사진 추가
     */
    public void addPhoto(String photoUrl) {
        if (photoUrl != null && !photoUrl.trim().isEmpty()) {
            photos.add(photoUrl.trim());
        }
    }

    /**
     * 사진 제거
     */
    public void removePhoto(String photoUrl) {
        photos.remove(photoUrl);
    }

    /**
     * 대표 사진 반환 (첫 번째 사진)
     */
    public String getMainPhoto() {
        return photos.isEmpty() ? null : photos.get(0);
    }

    /**
     * 제공 가능한 메뉴 아이템들만 반환
     */
    public List<MenuItem> getAvailableMenuItems() {
        return menuItems.stream()
                .filter(MenuItem::isAvailable)
                .toList();
    }

    /**
     * 총 메뉴 개수 반환
     */
    public int getTotalMenuCount() {
        return menuItems.size();
    }

    /**
     * 제공 가능한 메뉴 개수 반환
     */
    public int getAvailableMenuCount() {
        return getAvailableMenuItems().size();
    }

    /**
     * 메뉴 가격 범위 반환 (최저가 ~ 최고가)
     */
    public String getPriceRange() {
        List<MenuItem> availableItems = getAvailableMenuItems();
        if (availableItems.isEmpty()) {
            return "가격 정보 없음";
        }

        int minPrice = availableItems.stream()
                .mapToInt(MenuItem::getPrice)
                .min()
                .orElse(0);

        int maxPrice = availableItems.stream()
                .mapToInt(MenuItem::getPrice)
                .max()
                .orElse(0);

        if (minPrice == maxPrice) {
            return String.format("%,d원", minPrice);
        } else {
            return String.format("%,d원 ~ %,d원", minPrice, maxPrice);
        }
    }

    /**
     * 소개가 작성되었는지 확인
     */
    public boolean hasDescription() {
        return description != null && !description.trim().isEmpty();
    }

    /**
     * 사진이 등록되었는지 확인
     */
    public boolean hasPhotos() {
        return !photos.isEmpty();
    }

    /**
     * 메뉴가 등록되었는지 확인
     */
    public boolean hasMenuItems() {
        return !menuItems.isEmpty();
    }

    /**
     * 매장 소개가 완성되었는지 확인
     */
    public boolean isComplete() {
        return hasDescription() && hasPhotos() && hasMenuItems();
    }
}