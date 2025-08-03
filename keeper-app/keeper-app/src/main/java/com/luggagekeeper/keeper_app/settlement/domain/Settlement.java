package com.luggagekeeper.keeper_app.settlement.domain;

import com.luggagekeeper.keeper_app.store.domain.Store;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 정산 정보를 저장하는 JPA 엔티티
 * 
 * 짐 보관 플랫폼에서 발생하는 수익을 플랫폼과 가게 간에 정산하는 핵심 도메인 모델입니다.
 * 플랫폼은 20% 수수료(PG사 수수료 포함)를 가져가고, 나머지 80%는 토스 지급대행을 통해 가게에 송금됩니다.
 * 
 * 주요 기능:
 * - 자동 수수료 계산 (20% 플랫폼, 80% 가게)
 * - 토스페이먼츠 지급대행 연동
 * - 정산 상태 관리 및 추적
 * - 재시도 로직 지원
 * - 감사 로그 및 메타데이터 관리
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
@Entity
@Table(name = "settlements", indexes = {
    @Index(name = "idx_settlement_store_id", columnList = "store_id"),
    @Index(name = "idx_settlement_status", columnList = "status"),
    @Index(name = "idx_settlement_created_at", columnList = "created_at"),
    @Index(name = "idx_settlement_store_created", columnList = "store_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {

    /**
     * 정산 고유 식별자 (UUID)
     * 36자리 문자열로 저장되며, 시스템 전체에서 유일한 값을 보장합니다.
     */
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    /**
     * 정산 대상 가게 정보
     * 지연 로딩(LAZY)을 사용하여 성능을 최적화합니다.
     * 가게가 삭제되어도 정산 기록은 유지되어야 하므로 CASCADE 설정하지 않습니다.
     */
    @NotNull(message = "가게 정보는 필수입니다")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_settlement_store"))
    private Store store;

    /**
     * 주문 고유 식별자
     * 결제 시스템에서 생성된 주문 ID로, 결제와 정산을 연결하는 키입니다.
     * 중복 정산을 방지하기 위해 유니크 제약조건을 고려할 수 있습니다.
     */
    @NotNull(message = "주문 ID는 필수입니다")
    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;

    /**
     * 원본 결제 금액 (고객이 실제 결제한 금액)
     * precision=19, scale=2로 설정하여 최대 999,999,999,999,999.99까지 저장 가능합니다.
     * 이 금액에서 플랫폼 수수료를 차감하여 정산 금액을 계산합니다.
     */
    @NotNull(message = "원본 결제 금액은 필수입니다")
    @Positive(message = "결제 금액은 0보다 커야 합니다")
    @Column(name = "original_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal originalAmount;

    /**
     * 플랫폼 수수료율 (기본값: 0.20 = 20%)
     * PG사 수수료가 포함된 플랫폼의 총 수수료율입니다.
     * precision=5, scale=4로 설정하여 0.0001 단위까지 정확한 계산이 가능합니다.
     */
    @NotNull(message = "플랫폼 수수료율은 필수입니다")
    @Column(name = "platform_fee_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal platformFeeRate;

    /**
     * 계산된 플랫폼 수수료 금액
     * originalAmount * platformFeeRate로 계산됩니다.
     * 예: 10,000원 * 0.20 = 2,000원
     */
    @NotNull(message = "플랫폼 수수료 금액은 필수입니다")
    @Column(name = "platform_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal platformFee;

    /**
     * 가게에 지급될 정산 금액
     * originalAmount - platformFee로 계산됩니다.
     * 예: 10,000원 - 2,000원 = 8,000원
     * 이 금액이 토스페이먼츠 지급대행을 통해 가게 계좌로 송금됩니다.
     */
    @NotNull(message = "정산 금액은 필수입니다")
    @Column(name = "settlement_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal settlementAmount;

    /**
     * 정산 처리 상태
     * PENDING(대기) -> PROCESSING(처리중) -> COMPLETED(완료) 또는 FAILED(실패)
     * STRING 타입으로 저장하여 가독성을 높입니다.
     */
    @NotNull(message = "정산 상태는 필수입니다")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementStatus status;

    /**
     * 토스페이먼츠 지급대행 요청 ID
     * 토스페이먼츠 API 호출 시 반환되는 고유 식별자입니다.
     * 지급대행 상태 조회 및 취소 시 사용됩니다.
     */
    @Column(name = "toss_payout_id", length = 100)
    private String tossPayoutId;

    /**
     * 토스페이먼츠 셀러 ID
     * 가게가 토스페이먼츠에 등록된 셀러 ID입니다.
     * 지급대행 요청 시 수취인 정보로 사용됩니다.
     */
    @Column(name = "toss_seller_id", length = 100)
    private String tossSellerId;

    /**
     * 정산 요청 시각
     * 정산 처리가 최초 요청된 시간을 기록합니다.
     * 성능 분석 및 SLA 측정에 사용됩니다.
     */
    @NotNull(message = "정산 요청 시각은 필수입니다")
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    /**
     * 정산 완료 시각
     * 토스페이먼츠 지급대행이 성공적으로 완료된 시간을 기록합니다.
     * 정산 처리 시간 분석에 사용됩니다.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * 오류 메시지
     * 정산 처리 실패 시 상세한 오류 정보를 저장합니다.
     * TEXT 타입으로 설정하여 긴 오류 메시지도 저장 가능합니다.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 재시도 횟수
     * 정산 처리 실패 시 자동 재시도 횟수를 추적합니다.
     * 최대 3회까지 재시도하며, 초과 시 수동 처리가 필요합니다.
     */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /**
     * 추가 메타데이터 (JSON 형태)
     * 정산과 관련된 부가 정보를 JSON 문자열로 저장합니다.
     * 예: 결제 방법, 할인 정보, 특별 처리 사항 등
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * 레코드 생성 시각
     * 엔티티가 데이터베이스에 최초 저장된 시간입니다.
     * 감사 로그 및 데이터 분석에 사용됩니다.
     */
    @NotNull(message = "생성 시각은 필수입니다")
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 레코드 최종 수정 시각
     * 엔티티가 마지막으로 수정된 시간입니다.
     * 데이터 변경 추적 및 동시성 제어에 사용됩니다.
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
     * - requestedAt: 현재 시각 (정산 요청 시각)
     * - status: PENDING (정산 대기 상태)
     * - retryCount: 0 (재시도 횟수 초기화)
     */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = SettlementStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    /**
     * JPA 엔티티 수정 전 실행되는 콜백 메서드
     * 
     * 엔티티가 수정될 때마다 자동으로 호출되어
     * 최종 수정 시각을 현재 시간으로 업데이트합니다.
     * 
     * 이를 통해 데이터 변경 이력을 추적할 수 있습니다.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 정산 엔티티를 생성하는 정적 팩토리 메서드
     * 
     * 새로운 정산 요청이 발생했을 때 호출되며, 플랫폼 수수료를 자동으로 계산하여
     * 완전한 정산 정보를 가진 Settlement 객체를 생성합니다.
     * 
     * 계산 로직:
     * 1. 플랫폼 수수료율: 20% (PG사 수수료 포함)
     * 2. 플랫폼 수수료 = 원본 금액 × 0.20
     * 3. 정산 금액 = 원본 금액 - 플랫폼 수수료
     * 
     * 예시:
     * - 원본 금액: 10,000원
     * - 플랫폼 수수료: 2,000원 (20%)
     * - 정산 금액: 8,000원 (가게 수령액)
     * 
     * @param store 정산 대상 가게 정보 (null 불가)
     * @param orderId 결제 시스템의 주문 고유 식별자 (null 불가)
     * @param originalAmount 고객이 실제 결제한 원본 금액 (0보다 커야 함)
     * @return 계산된 정산 정보가 포함된 Settlement 객체
     * @throws IllegalArgumentException 입력 파라미터가 유효하지 않은 경우
     */
    public static Settlement createSettlement(Store store, String orderId, BigDecimal originalAmount) {
        // 입력값 검증
        if (store == null) {
            throw new IllegalArgumentException("가게 정보는 필수입니다");
        }
        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("주문 ID는 필수입니다");
        }
        if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다");
        }

        Settlement settlement = new Settlement();
        settlement.setStore(store);
        settlement.setOrderId(orderId);
        settlement.setOriginalAmount(originalAmount);
        
        // 플랫폼 수수료율 20% 설정 (PG사 수수료 포함)
        BigDecimal feeRate = new BigDecimal("0.20");
        settlement.setPlatformFeeRate(feeRate);
        
        // 플랫폼 수수료 계산 (소수점 이하 반올림)
        BigDecimal platformFee = originalAmount.multiply(feeRate)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        settlement.setPlatformFee(platformFee);
        
        // 정산 금액 계산 (원본 금액 - 플랫폼 수수료)
        BigDecimal settlementAmount = originalAmount.subtract(platformFee);
        settlement.setSettlementAmount(settlementAmount);
        
        // 초기 상태를 PENDING으로 설정
        settlement.setStatus(SettlementStatus.PENDING);
        
        return settlement;
    }

    /**
     * 정산 처리 완료 상태로 변경
     * 
     * 토스페이먼츠 지급대행이 성공적으로 완료되었을 때 호출됩니다.
     * 웹훅을 통해 토스페이먼츠로부터 완료 알림을 받거나,
     * 지급대행 상태 조회 API를 통해 완료를 확인했을 때 사용됩니다.
     * 
     * 수행 작업:
     * 1. 토스 지급대행 ID 저장
     * 2. 상태를 COMPLETED로 변경
     * 3. 완료 시각 기록
     * 4. 오류 메시지 초기화 (이전 실패 기록 제거)
     * 
     * @param tossPayoutId 토스페이먼츠에서 발급한 지급대행 고유 식별자
     * @throws IllegalArgumentException tossPayoutId가 null이거나 빈 문자열인 경우
     * @throws IllegalStateException 현재 상태가 PROCESSING이 아닌 경우
     */
    public void completeSettlement(String tossPayoutId) {
        if (tossPayoutId == null || tossPayoutId.trim().isEmpty()) {
            throw new IllegalArgumentException("토스 지급대행 ID는 필수입니다");
        }
        if (this.status != SettlementStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 정산만 완료할 수 있습니다. 현재 상태: " + this.status);
        }

        this.tossPayoutId = tossPayoutId;
        this.status = SettlementStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = null; // 이전 오류 메시지 초기화
    }

    /**
     * 정산 처리 실패 상태로 변경
     * 
     * 토스페이먼츠 지급대행 요청이 실패했을 때 호출됩니다.
     * API 호출 실패, 잔액 부족, 계좌 정보 오류 등의 상황에서 사용됩니다.
     * 
     * 수행 작업:
     * 1. 상태를 FAILED로 변경
     * 2. 상세한 오류 메시지 저장
     * 3. 재시도 횟수 증가
     * 4. 완료 시각 초기화 (실패이므로)
     * 
     * @param errorMessage 실패 원인에 대한 상세한 설명
     * @throws IllegalArgumentException errorMessage가 null이거나 빈 문자열인 경우
     */
    public void failSettlement(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("오류 메시지는 필수입니다");
        }

        this.status = SettlementStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
        this.completedAt = null; // 실패 시 완료 시각 초기화
    }

    /**
     * 정산 처리 시작 상태로 변경
     * 
     * 토스페이먼츠 지급대행 API 호출을 시작할 때 호출됩니다.
     * PENDING 상태에서 PROCESSING 상태로 변경하여
     * 중복 처리를 방지하고 현재 처리 중임을 표시합니다.
     * 
     * @throws IllegalStateException 현재 상태가 PENDING이 아닌 경우
     */
    public void startProcessing() {
        if (this.status != SettlementStatus.PENDING) {
            throw new IllegalStateException("대기 중인 정산만 처리를 시작할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = SettlementStatus.PROCESSING;
    }

    /**
     * 자동 재시도 가능 여부 확인
     * 
     * 정산 처리가 실패했을 때 자동으로 재시도할 수 있는지 판단합니다.
     * 시스템의 안정성을 위해 최대 3회까지만 재시도를 허용합니다.
     * 
     * 재시도 조건:
     * 1. 현재 상태가 FAILED여야 함
     * 2. 재시도 횟수가 3회 미만이어야 함
     * 
     * @return true: 재시도 가능, false: 재시도 불가 (수동 처리 필요)
     */
    public boolean canRetry() {
        return retryCount < 3 && status == SettlementStatus.FAILED;
    }

    /**
     * 정산 취소 처리
     * 
     * 관리자가 수동으로 정산을 취소하거나,
     * 시스템 오류로 인해 정산을 중단해야 할 때 호출됩니다.
     * 
     * 주의사항:
     * - 이미 완료된 정산은 취소할 수 없습니다
     * - 취소된 정산은 다시 처리할 수 없습니다
     * 
     * @throws IllegalStateException 이미 완료된 정산을 취소하려는 경우
     */
    public void cancelSettlement() {
        if (this.status == SettlementStatus.COMPLETED) {
            throw new IllegalStateException("완료된 정산은 취소할 수 없습니다");
        }
        this.status = SettlementStatus.CANCELLED;
        this.completedAt = null;
        this.tossPayoutId = null;
    }

    /**
     * 정산 처리 가능 여부 확인
     * 
     * 현재 정산이 토스페이먼츠 지급대행을 통해 처리될 수 있는 상태인지 확인합니다.
     * 
     * @return true: 처리 가능, false: 처리 불가
     */
    public boolean canProcess() {
        return this.status == SettlementStatus.PENDING && 
               this.store != null && 
               this.settlementAmount != null && 
               this.settlementAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 정산 완료 여부 확인
     * 
     * @return true: 정산 완료, false: 미완료
     */
    public boolean isCompleted() {
        return this.status == SettlementStatus.COMPLETED;
    }

    /**
     * 정산 실패 여부 확인
     * 
     * @return true: 정산 실패, false: 실패 아님
     */
    public boolean isFailed() {
        return this.status == SettlementStatus.FAILED;
    }
}