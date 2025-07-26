
package com.luggagekeeper.keeper_app.store.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 보관 아이템 엔티티
 * Flutter의 StorageItem 모델과 호환되는 구조
 */
@Entity
@Table(name = "storage_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StorageItem {

    @Id
    @Column(name = "id", length = 36)  // UUID 형태의 String ID로 변경 (Flutter와 호환)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    @NotNull
    private Store store;

    @NotBlank
    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "storage_location")
    private String storageLocation;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "expected_checkout_time")
    private LocalDateTime expectedCheckoutTime;

    @Column(name = "actual_checkout_time")
    private LocalDateTime actualCheckoutTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StorageStatus status = StorageStatus.STORED;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Flutter와 호환되는 추가 필드들 (필요시)
    @Column(name = "storage_fee")
    private Integer storageFee;  // 보관료

    @Column(name = "item_size")
    private String itemSize;     // 아이템 크기 (S, M, L, XL)

    @Column(name = "special_instructions")
    private String specialInstructions;  // 특별 지시사항

    @Column(name = "qr_code")
    private String qrCode;       // QR 코드 (픽업시 사용)

    public enum StorageStatus {
        STORED("보관중"),
        RETRIEVED("찾아감"),
        OVERDUE("연체"),
        PENDING("대기중"),
        CANCELLED("취소됨");

        private final String description;

        StorageStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (id == null) {
            // UUID 생성 (Flutter와 호환)
            id = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 편의 메서드들

    /**
     * 보관 중인지 확인
     */
    public boolean isStored() {
        return StorageStatus.STORED.equals(status);
    }

    /**
     * 찾아갔는지 확인
     */
    public boolean isRetrieved() {
        return StorageStatus.RETRIEVED.equals(status);
    }

    /**
     * 연체되었는지 확인
     */
    public boolean isOverdue() {
        return StorageStatus.OVERDUE.equals(status) ||
                (expectedCheckoutTime != null &&
                        LocalDateTime.now().isAfter(expectedCheckoutTime) &&
                        !isRetrieved());
    }

    /**
     * 보관 기간 계산 (시간 단위)
     */
    public long getStorageHours() {
        if (checkInTime == null) return 0;

        LocalDateTime endTime = actualCheckoutTime != null ?
                actualCheckoutTime : LocalDateTime.now();

        return java.time.Duration.between(checkInTime, endTime).toHours();
    }

    /**
     * 보관료 계산 (시간 기반)
     */
    public int calculateFee(int hourlyRate) {
        return (int) (getStorageHours() * hourlyRate);
    }

    /**
     * 고객 연락처 마스킹
     */
    public String getMaskedPhone() {
        if (customerPhone == null || customerPhone.length() < 8) {
            return customerPhone;
        }

        return customerPhone.substring(0, 3) + "****" +
                customerPhone.substring(customerPhone.length() - 4);
    }
}