package com.luggagekeeper.keeper_app.store.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 통합된 매장(스토어) 엔티티 - 계정 정보, 매장 정보 및 다른 관련 설정 모델들을 통합
 * 백엔드와의 통신 및 고객용 앱으로의 데이터 전달에 효율적
 */
@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Store {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Email
    @NotBlank
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type")
    private BusinessType businessType;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @NotNull
    @Column(name = "has_completed_setup", nullable = false)
    private Boolean hasCompletedSetup = false;

    // 사업자 정보
    @Column(name = "business_number")
    private String businessNumber;    // 사업자 번호

    @Column(name = "business_name")
    private String businessName;      // 사업체명

    @Column(name = "representative_name")
    private String representativeName; // 대표자명

    // 매장 정보
    @Column(columnDefinition = "TEXT")
    private String address;           // 매장 주소

    @Column(name = "detail_address")
    private String detailAddress;     // 상세 주소

    // Hibernate 6.x 호환: Double 타입에는 scale 사용 안함
    @Column(name = "latitude")
    private Double latitude;          // 위도

    @Column(name = "longitude")
    private Double longitude;         // 경도

    @Column(columnDefinition = "TEXT")
    private String description;       // 매장 설명

    // 계좌 정보
    @Column(name = "bank_name")
    private String bankName;          // 은행명

    @Column(name = "account_number")
    private String accountNumber;     // 계좌번호

    @Column(name = "account_holder")
    private String accountHolder;     // 예금주

    // 연관 관계 매핑
    @OneToOne(mappedBy = "store", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private StoreIntro storeIntro;              // 매장 소개 정보

    @OneToOne(mappedBy = "store", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private StoreSettings storeSettings;        // 매장 전체 설정

    @OneToMany(mappedBy = "store", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<StorageItem> storageItems = new ArrayList<>();     // 보관 아이템 목록

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (hasCompletedSetup == null) {
            hasCompletedSetup = false;
        }
        if (id == null) {
            // UUID 생성
            id = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 편의 메서드들
    public void addStorageItem(StorageItem storageItem) {
        storageItems.add(storageItem);
        storageItem.setStore(this);
    }

    public void removeStorageItem(StorageItem storageItem) {
        storageItems.remove(storageItem);
        storageItem.setStore(null);
    }

    public void setStoreIntro(StoreIntro storeIntro) {
        this.storeIntro = storeIntro;
        if (storeIntro != null) {
            storeIntro.setStore(this);
        }
    }

    public void setStoreSettings(StoreSettings storeSettings) {
        this.storeSettings = storeSettings;
        if (storeSettings != null) {
            storeSettings.setStore(this);
        }
    }

    /**
     * 매장 설정 완료 여부 확인
     */
    public boolean isSetupComplete() {
        return hasCompletedSetup != null && hasCompletedSetup &&
                businessNumber != null &&
                address != null &&
                storeSettings != null;
    }

    /**
     * 매장 운영 가능 여부 확인
     */
    public boolean canOperate() {
        return isSetupComplete() &&
                latitude != null &&
                longitude != null &&
                bankName != null &&
                accountNumber != null;
    }

    /**
     * 좌표 설정 편의 메서드
     */
    public void setCoordinates(double lat, double lng) {
        this.latitude = lat;
        this.longitude = lng;
    }

    /**
     * 좌표 유효성 검사
     */
    public boolean hasValidCoordinates() {
        return latitude != null && longitude != null &&
                latitude >= -90.0 && latitude <= 90.0 &&
                longitude >= -180.0 && longitude <= 180.0;
    }

    /**
     * 두 좌표 간의 거리 계산 (km)
     */
    public double calculateDistance(double targetLat, double targetLng) {
        if (!hasValidCoordinates()) {
            return Double.MAX_VALUE;
        }

        double R = 6371; // 지구 반지름 (km)
        double dLat = Math.toRadians(targetLat - latitude);
        double dLon = Math.toRadians(targetLng - longitude);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(targetLat)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return R * c;
    }
}