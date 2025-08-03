package com.luggagekeeper.keeper_app.settlement.domain;

import com.luggagekeeper.keeper_app.store.domain.Store;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 토스페이먼츠 셀러 정보를 저장하는 JPA 엔티티
 * 
 * 가게와 토스페이먼츠 지급대행 서비스 간의 연결 정보를 관리하는 도메인 모델입니다.
 * 가게가 토스페이먼츠 지급대행 서비스를 이용하기 위해서는 먼저 셀러로 등록되어야 하며,
 * 이 엔티티는 그 등록 정보와 승인 상태를 추적합니다.
 * 
 * 주요 기능:
 * - 토스페이먼츠 셀러 등록 정보 관리
 * - 사업자 타입별 승인 프로세스 추적
 * - 지급대행 가능 여부 판단
 * - 셀러 상태 변경 이력 관리
 * 
 * 비즈니스 규칙:
 * - 하나의 가게는 하나의 토스 셀러 정보만 가질 수 있음 (OneToOne)
 * - 개인사업자와 법인사업자는 서로 다른 승인 프로세스를 가짐
 * - 승인 완료 후에만 지급대행 서비스 이용 가능
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
@Entity
@Table(name = "toss_sellers", 
       indexes = {
           @Index(name = "idx_toss_seller_store_id", columnList = "store_id"),
           @Index(name = "idx_toss_seller_status", columnList = "status"),
           @Index(name = "idx_toss_seller_toss_id", columnList = "toss_seller_id"),
           @Index(name = "idx_toss_seller_registered_at", columnList = "registered_at")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_toss_seller_store", columnNames = "store_id"),
           @UniqueConstraint(name = "uk_toss_seller_ref_id", columnNames = "ref_seller_id")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TossSeller {

    /**
     * 토스 셀러 고유 식별자 (UUID)
     * 36자리 문자열로 저장되며, 시스템 전체에서 유일한 값을 보장합니다.
     */
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    /**
     * 연결된 가게 정보
     * 
     * OneToOne 관계로 설정되어 하나의 가게는 하나의 토스 셀러 정보만 가질 수 있습니다.
     * 지연 로딩(LAZY)을 사용하여 성능을 최적화하며,
     * unique=true 제약조건으로 중복 등록을 방지합니다.
     */
    @NotNull(message = "가게 정보는 필수입니다")
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false, unique = true, 
                foreignKey = @ForeignKey(name = "fk_toss_seller_store"))
    private Store store;

    /**
     * 우리 시스템의 셀러 참조 ID
     * 
     * 토스페이먼츠 API 호출 시 사용되는 우리 시스템의 고유 식별자입니다.
     * 일반적으로 가게 ID와 동일한 값을 사용하며,
     * 토스페이먼츠와의 데이터 매핑에 사용됩니다.
     */
    @NotNull(message = "참조 셀러 ID는 필수입니다")
    @Column(name = "ref_seller_id", nullable = false, length = 100, unique = true)
    private String refSellerId;

    /**
     * 토스페이먼츠에서 발급한 셀러 ID
     * 
     * 토스페이먼츠 셀러 등록 API 호출 후 반환되는 고유 식별자입니다.
     * 지급대행 요청 시 수취인 정보로 사용되며,
     * null인 경우 아직 토스페이먼츠에 등록되지 않은 상태입니다.
     */
    @Column(name = "toss_seller_id", length = 100)
    private String tossSellerId;

    /**
     * 사업자 타입
     * 
     * 개인사업자(INDIVIDUAL_BUSINESS)와 법인사업자(CORPORATE)를 구분합니다.
     * 사업자 타입에 따라 승인 프로세스와 한도가 다르게 적용됩니다.
     * 
     * - 개인사업자: 간단한 승인 프로세스, 월 1천만원 한도
     * - 법인사업자: KYC 심사 필요, 높은 한도
     */
    @NotNull(message = "사업자 타입은 필수입니다")
    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 20)
    private TossBusinessType businessType;

    /**
     * 토스 셀러 승인 상태
     * 
     * 토스페이먼츠 셀러 등록 및 승인 프로세스의 현재 상태를 나타냅니다.
     * 상태에 따라 지급대행 서비스 이용 가능 여부가 결정됩니다.
     * 
     * - APPROVAL_REQUIRED: 승인 필요 (초기 상태)
     * - PARTIALLY_APPROVED: 부분 승인 (월 1천만원 미만)
     * - KYC_REQUIRED: KYC 심사 필요 (법인사업자)
     * - APPROVED: 완전 승인 (모든 기능 이용 가능)
     */
    @NotNull(message = "셀러 상태는 필수입니다")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TossSellerStatus status;

    /**
     * 토스페이먼츠 셀러 등록 요청 시각
     * 
     * 토스페이먼츠 셀러 등록 API를 최초 호출한 시간을 기록합니다.
     * 승인 프로세스 소요 시간 분석 및 SLA 측정에 사용됩니다.
     */
    @NotNull(message = "등록 시각은 필수입니다")
    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    /**
     * 토스페이먼츠 셀러 승인 완료 시각
     * 
     * 토스페이먼츠로부터 최종 승인을 받은 시간을 기록합니다.
     * 승인 프로세스 완료 시에만 값이 설정되며,
     * 승인 소요 시간 분석에 사용됩니다.
     */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /**
     * 레코드 생성 시각
     * 
     * 엔티티가 데이터베이스에 최초 저장된 시간입니다.
     * 감사 로그 및 데이터 분석에 사용됩니다.
     */
    @NotNull(message = "생성 시각은 필수입니다")
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 레코드 최종 수정 시각
     * 
     * 엔티티가 마지막으로 수정된 시간입니다.
     * 상태 변경 추적 및 동시성 제어에 사용됩니다.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * JPA 엔티티 저장 전 실행되는 콜백 메서드
     * 
     * 엔티티가 데이터베이스에 저장되기 전에 자동으로 호출되어
     * 필수 필드들의 기본값을 설정합니다.
     * 
     * 설정되는 기본값:
     * - id: UUID 자동 생성
     * - createdAt: 현재 시각
     * - registeredAt: 현재 시각 (셀러 등록 요청 시각)
     * - status: APPROVAL_REQUIRED (승인 필요 상태)
     */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (registeredAt == null) {
            registeredAt = LocalDateTime.now();
        }
        if (status == null) {
            status = TossSellerStatus.APPROVAL_REQUIRED;
        }
    }

    /**
     * JPA 엔티티 수정 전 실행되는 콜백 메서드
     * 
     * 엔티티가 수정될 때마다 자동으로 호출되어
     * 최종 수정 시각을 현재 시간으로 업데이트합니다.
     * 
     * 상태 변경 이력 추적에 중요한 역할을 합니다.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 토스 셀러 엔티티를 생성하는 정적 팩토리 메서드
     * 
     * 가게가 토스페이먼츠 지급대행 서비스를 이용하기 위해 셀러로 등록할 때 호출됩니다.
     * 사업자 타입에 따라 적절한 초기 상태를 설정하여 TossSeller 객체를 생성합니다.
     * 
     * 생성 규칙:
     * - refSellerId는 가게 ID와 동일하게 설정
     * - 초기 상태는 APPROVAL_REQUIRED로 설정
     * - tossSellerId는 토스페이먼츠 API 호출 후 별도로 설정
     * 
     * @param store 토스 셀러로 등록할 가게 정보 (null 불가)
     * @param businessType 사업자 타입 (개인사업자 또는 법인사업자, null 불가)
     * @return 생성된 TossSeller 객체
     * @throws IllegalArgumentException 입력 파라미터가 유효하지 않은 경우
     */
    public static TossSeller createTossSeller(Store store, TossBusinessType businessType) {
        // 입력값 검증
        if (store == null) {
            throw new IllegalArgumentException("가게 정보는 필수입니다");
        }
        if (businessType == null) {
            throw new IllegalArgumentException("사업자 타입은 필수입니다");
        }
        if (store.getId() == null || store.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("가게 ID는 필수입니다");
        }

        TossSeller tossSeller = new TossSeller();
        tossSeller.setStore(store);
        tossSeller.setRefSellerId(store.getId()); // 가게 ID를 참조 셀러 ID로 사용
        tossSeller.setBusinessType(businessType);
        tossSeller.setStatus(TossSellerStatus.APPROVAL_REQUIRED);
        
        return tossSeller;
    }

    /**
     * 토스페이먼츠에서 발급한 셀러 ID 할당
     * 
     * 토스페이먼츠 셀러 등록 API 호출이 성공한 후,
     * 응답으로 받은 셀러 ID를 저장할 때 사용됩니다.
     * 
     * @param tossSellerId 토스페이먼츠에서 발급한 고유 셀러 식별자
     * @throws IllegalArgumentException tossSellerId가 null이거나 빈 문자열인 경우
     * @throws IllegalStateException 이미 토스 셀러 ID가 할당된 경우
     */
    public void assignTossId(String tossSellerId) {
        if (tossSellerId == null || tossSellerId.trim().isEmpty()) {
            throw new IllegalArgumentException("토스 셀러 ID는 필수입니다");
        }
        if (this.tossSellerId != null) {
            throw new IllegalStateException("이미 토스 셀러 ID가 할당되었습니다: " + this.tossSellerId);
        }
        this.tossSellerId = tossSellerId;
    }

    /**
     * 셀러 최종 승인 완료 처리
     * 
     * 토스페이먼츠로부터 셀러 승인이 완료되었을 때 호출됩니다.
     * 웹훅을 통해 승인 완료 알림을 받거나,
     * 셀러 상태 조회 API를 통해 승인을 확인했을 때 사용됩니다.
     * 
     * 수행 작업:
     * 1. 상태를 APPROVED로 변경
     * 2. 승인 완료 시각 기록
     * 
     * @throws IllegalStateException 승인 가능한 상태가 아닌 경우
     */
    public void approve() {
        if (this.status == TossSellerStatus.APPROVED) {
            return; // 이미 승인된 상태면 무시
        }
        
        this.status = TossSellerStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
    }

    /**
     * 셀러 상태 업데이트
     * 
     * 토스페이먼츠 웹훅이나 상태 조회 API를 통해
     * 셀러 상태가 변경되었을 때 호출됩니다.
     * 
     * 특별 처리:
     * - APPROVED 상태로 변경 시 승인 시각 자동 설정
     * - 상태 변경 이력은 updatedAt으로 추적
     * 
     * @param newStatus 변경할 새로운 상태
     * @throws IllegalArgumentException newStatus가 null인 경우
     */
    public void updateStatus(TossSellerStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("새로운 상태는 필수입니다");
        }
        
        // 상태가 실제로 변경되는 경우에만 처리
        if (this.status != newStatus) {
            this.status = newStatus;
            
            // APPROVED 상태로 변경 시 승인 시각 설정
            if (newStatus == TossSellerStatus.APPROVED && this.approvedAt == null) {
                this.approvedAt = LocalDateTime.now();
            }
        }
    }

    /**
     * 지급대행 서비스 이용 가능 여부 확인
     * 
     * 현재 셀러가 토스페이먼츠 지급대행 서비스를 이용할 수 있는지 판단합니다.
     * 정산 처리 시 사전 검증에 사용됩니다.
     * 
     * 이용 가능 조건:
     * 1. 토스 셀러 ID가 발급되어 있어야 함
     * 2. 상태가 APPROVED 또는 PARTIALLY_APPROVED여야 함
     * 
     * @return true: 지급대행 이용 가능, false: 이용 불가
     */
    public boolean canProcessPayout() {
        return tossSellerId != null && 
               !tossSellerId.trim().isEmpty() &&
               (status == TossSellerStatus.APPROVED || status == TossSellerStatus.PARTIALLY_APPROVED);
    }

    /**
     * 승인 대기 중인지 확인
     * 
     * 현재 셀러가 토스페이먼츠의 승인을 기다리고 있는 상태인지 확인합니다.
     * 관리자 대시보드나 알림 시스템에서 사용됩니다.
     * 
     * 승인 대기 상태:
     * - APPROVAL_REQUIRED: 초기 승인 필요
     * - KYC_REQUIRED: KYC 심사 필요 (법인사업자)
     * 
     * @return true: 승인 대기 중, false: 승인 완료 또는 기타 상태
     */
    public boolean isPendingApproval() {
        return status == TossSellerStatus.APPROVAL_REQUIRED || 
               status == TossSellerStatus.KYC_REQUIRED;
    }

    /**
     * 셀러 등록 완료 여부 확인
     * 
     * 토스페이먼츠에 셀러로 등록이 완료되었는지 확인합니다.
     * 
     * @return true: 등록 완료, false: 등록 미완료
     */
    public boolean isRegistered() {
        return tossSellerId != null && !tossSellerId.trim().isEmpty();
    }

    /**
     * 완전 승인 여부 확인
     * 
     * 모든 제한 없이 지급대행 서비스를 이용할 수 있는지 확인합니다.
     * 
     * @return true: 완전 승인, false: 부분 승인 또는 미승인
     */
    public boolean isFullyApproved() {
        return status == TossSellerStatus.APPROVED;
    }

    /**
     * 부분 승인 여부 확인
     * 
     * 월 한도 제한이 있는 부분 승인 상태인지 확인합니다.
     * 
     * @return true: 부분 승인, false: 완전 승인 또는 미승인
     */
    public boolean isPartiallyApproved() {
        return status == TossSellerStatus.PARTIALLY_APPROVED;
    }
}