package com.luggagekeeper.keeper_app.settlement.dto;

import com.luggagekeeper.keeper_app.settlement.domain.Settlement;
import com.luggagekeeper.keeper_app.settlement.domain.SettlementStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 정산 정보 API 응답용 DTO (Data Transfer Object)
 * 
 * Settlement 엔티티의 정보를 클라이언트(Flutter 앱)에게 전달하기 위한 응답 객체입니다.
 * Flutter의 json_serializable 패키지와 완벽하게 호환되도록 설계되었습니다.
 * 
 * 주요 특징:
 * - JPA 엔티티와 분리된 순수 데이터 전송 객체
 * - Flutter json_serializable 호환 구조
 * - 모든 필드에 getter/setter 제공
 * - 민감한 정보 제외 (보안 고려)
 * 
 * 사용 용도:
 * - GET /api/v1/settlements API 응답
 * - 정산 내역 조회 결과 전달
 * - Flutter 앱의 정산 화면 데이터 바인딩
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementResponse {
    
    /**
     * 정산 고유 식별자
     * Settlement 엔티티의 UUID 기반 ID입니다.
     */
    private String id;
    
    /**
     * 정산 대상 가게 ID
     * Store 엔티티의 ID로, 어느 가게의 정산인지 식별합니다.
     */
    private String storeId;
    
    /**
     * 주문 고유 식별자
     * 결제 시스템에서 생성된 주문 ID로, 결제와 정산을 연결합니다.
     */
    private String orderId;
    
    /**
     * 원본 결제 금액
     * 고객이 실제로 결제한 전체 금액입니다.
     * 이 금액에서 플랫폼 수수료를 차감하여 정산 금액을 계산합니다.
     */
    private BigDecimal originalAmount;
    
    /**
     * 플랫폼 수수료율
     * 소수점 형태로 표현된 수수료율입니다. (예: 0.20 = 20%)
     * PG사 수수료가 포함된 플랫폼의 총 수수료율입니다.
     */
    private BigDecimal platformFeeRate;
    
    /**
     * 계산된 플랫폼 수수료 금액
     * originalAmount × platformFeeRate로 계산된 실제 수수료 금액입니다.
     */
    private BigDecimal platformFee;
    
    /**
     * 가게에 지급될 정산 금액
     * originalAmount - platformFee로 계산된 가게 수령액입니다.
     * 이 금액이 토스페이먼츠를 통해 가게 계좌로 송금됩니다.
     */
    private BigDecimal settlementAmount;
    
    /**
     * 정산 처리 상태
     * PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED 중 하나입니다.
     * Flutter 앱에서 상태별 UI 표시에 사용됩니다.
     */
    private SettlementStatus status;
    
    /**
     * 토스페이먼츠 지급대행 ID
     * 토스페이먼츠 API 호출 시 반환되는 고유 식별자입니다.
     * 지급 완료 후에만 값이 설정됩니다.
     */
    private String tossPayoutId;
    
    /**
     * 토스페이먼츠 셀러 ID
     * 가게가 토스페이먼츠에 등록된 셀러 ID입니다.
     * 지급대행 요청 시 수취인 정보로 사용됩니다.
     */
    private String tossSellerId;
    
    /**
     * 정산 요청 시각
     * 정산 처리가 최초 요청된 시간입니다.
     * ISO 8601 형식으로 직렬화됩니다.
     */
    private LocalDateTime requestedAt;
    
    /**
     * 정산 완료 시각
     * 토스페이먼츠 지급대행이 성공적으로 완료된 시간입니다.
     * 완료되지 않은 정산의 경우 null입니다.
     */
    private LocalDateTime completedAt;
    
    /**
     * 오류 메시지
     * 정산 처리 실패 시 상세한 오류 정보입니다.
     * 성공한 정산의 경우 null입니다.
     */
    private String errorMessage;
    
    /**
     * 재시도 횟수
     * 정산 처리 실패 시 자동 재시도된 횟수입니다.
     * 최대 3회까지 재시도됩니다.
     */
    private Integer retryCount;
    
    /**
     * 레코드 생성 시각
     * 정산 요청이 시스템에 최초 생성된 시간입니다.
     */
    private LocalDateTime createdAt;
    
    /**
     * 레코드 최종 수정 시각
     * 정산 정보가 마지막으로 수정된 시간입니다.
     * 상태 변경 시마다 업데이트됩니다.
     */
    private LocalDateTime updatedAt;

    /**
     * Settlement 엔티티로부터 SettlementResponse DTO를 생성하는 정적 팩토리 메서드
     * 
     * JPA 엔티티를 클라이언트 응답용 DTO로 변환합니다.
     * 엔티티의 모든 필드를 DTO로 매핑하되, 민감한 정보는 제외하고
     * 클라이언트에서 필요한 정보만 포함합니다.
     * 
     * 변환 규칙:
     * - 모든 기본 필드는 그대로 복사
     * - Store 엔티티는 ID만 추출하여 storeId로 설정
     * - null 값은 그대로 유지 (Flutter null safety 고려)
     * - 민감한 내부 정보는 제외
     * 
     * @param settlement 변환할 Settlement 엔티티 (null 불가)
     * @return 변환된 SettlementResponse DTO
     * @throws IllegalArgumentException settlement가 null인 경우
     * @throws IllegalStateException settlement.store가 null이거나 ID가 없는 경우
     */
    public static SettlementResponse from(Settlement settlement) {
        // 입력값 검증
        if (settlement == null) {
            throw new IllegalArgumentException("Settlement 엔티티는 필수입니다");
        }
        if (settlement.getStore() == null) {
            throw new IllegalStateException("Settlement의 Store 정보가 없습니다");
        }
        if (settlement.getStore().getId() == null) {
            throw new IllegalStateException("Store의 ID가 없습니다");
        }

        SettlementResponse response = new SettlementResponse();
        
        // 기본 정보 매핑
        response.setId(settlement.getId());
        response.setStoreId(settlement.getStore().getId());
        response.setOrderId(settlement.getOrderId());
        
        // 금액 정보 매핑
        response.setOriginalAmount(settlement.getOriginalAmount());
        response.setPlatformFeeRate(settlement.getPlatformFeeRate());
        response.setPlatformFee(settlement.getPlatformFee());
        response.setSettlementAmount(settlement.getSettlementAmount());
        
        // 상태 및 처리 정보 매핑
        response.setStatus(settlement.getStatus());
        response.setTossPayoutId(settlement.getTossPayoutId());
        response.setTossSellerId(settlement.getTossSellerId());
        
        // 시간 정보 매핑
        response.setRequestedAt(settlement.getRequestedAt());
        response.setCompletedAt(settlement.getCompletedAt());
        response.setCreatedAt(settlement.getCreatedAt());
        response.setUpdatedAt(settlement.getUpdatedAt());
        
        // 오류 및 재시도 정보 매핑
        response.setErrorMessage(settlement.getErrorMessage());
        response.setRetryCount(settlement.getRetryCount());
        
        return response;
    }

    /**
     * 정산이 완료된 상태인지 확인하는 편의 메서드
     * 
     * @return true: 정산 완료, false: 미완료
     */
    public boolean isCompleted() {
        return status == SettlementStatus.COMPLETED;
    }

    /**
     * 정산이 실패한 상태인지 확인하는 편의 메서드
     * 
     * @return true: 정산 실패, false: 실패 아님
     */
    public boolean isFailed() {
        return status == SettlementStatus.FAILED;
    }

    /**
     * 정산이 진행 중인 상태인지 확인하는 편의 메서드
     * 
     * @return true: 진행 중 (PENDING, PROCESSING), false: 완료 또는 중단
     */
    public boolean isInProgress() {
        return status == SettlementStatus.PENDING || status == SettlementStatus.PROCESSING;
    }

    /**
     * 재시도 가능한 상태인지 확인하는 편의 메서드
     * 
     * @return true: 재시도 가능, false: 재시도 불가
     */
    public boolean canRetry() {
        return status == SettlementStatus.FAILED && retryCount != null && retryCount < 3;
    }
}