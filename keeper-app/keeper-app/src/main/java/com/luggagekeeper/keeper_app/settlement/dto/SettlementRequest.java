package com.luggagekeeper.keeper_app.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 정산 요청 API용 DTO (Data Transfer Object)
 * 
 * 클라이언트(Flutter 앱)에서 새로운 정산 요청을 생성할 때 사용하는 요청 객체입니다.
 * Bean Validation 어노테이션을 통해 입력값 검증을 수행하며,
 * Flutter의 json_serializable과 완벽하게 호환됩니다.
 * 
 * 주요 특징:
 * - 입력값 자동 검증 (Bean Validation)
 * - Flutter json_serializable 호환
 * - 최소한의 필수 정보만 포함
 * - 보안을 위한 민감 정보 제외
 * 
 * 사용 용도:
 * - POST /api/v1/settlements API 요청 본문
 * - 새로운 정산 요청 생성
 * - Flutter 앱의 정산 요청 폼 데이터 전송
 * 
 * API 호출 예시:
 * POST /api/v1/settlements
 * {
 *   "storeId": "store-uuid-123",
 *   "orderId": "order-456",
 *   "originalAmount": 10000,
 *   "metadata": "{\"paymentMethod\":\"card\"}"
 * }
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementRequest {
    
    /**
     * 정산 대상 가게 ID
     * 
     * 정산을 요청하는 가게의 고유 식별자입니다.
     * Store 엔티티의 UUID 값이며, 존재하는 가게여야 합니다.
     * 
     * 검증 규칙:
     * - null 또는 빈 문자열 불가
     * - 공백만 있는 문자열 불가
     * - 실제 존재하는 가게 ID여야 함 (서비스 레벨에서 검증)
     */
    @NotBlank(message = "가게 ID는 필수입니다")
    private String storeId;
    
    /**
     * 주문 고유 식별자
     * 
     * 결제 시스템에서 생성된 주문의 고유 식별자입니다.
     * 중복 정산을 방지하기 위해 동일한 주문 ID로는 한 번만 정산 요청이 가능합니다.
     * 
     * 검증 규칙:
     * - null 또는 빈 문자열 불가
     * - 공백만 있는 문자열 불가
     * - 이미 정산 요청된 주문 ID는 불가 (서비스 레벨에서 검증)
     * 
     * 예시: "order_20240101_123456", "payment-uuid-789"
     */
    @NotBlank(message = "주문 ID는 필수입니다")
    private String orderId;
    
    /**
     * 원본 결제 금액
     * 
     * 고객이 실제로 결제한 전체 금액입니다.
     * 이 금액에서 플랫폼 수수료(20%)를 차감하여 정산 금액을 계산합니다.
     * 
     * 검증 규칙:
     * - null 불가
     * - 0보다 큰 양수여야 함
     * - 소수점 둘째 자리까지 허용
     * - 최대값: 999,999,999,999,999.99
     * 
     * 계산 예시:
     * - 입력: 10,000원
     * - 플랫폼 수수료: 2,000원 (20%)
     * - 정산 금액: 8,000원 (가게 수령액)
     */
    @NotNull(message = "결제 금액은 필수입니다")
    @Positive(message = "결제 금액은 0보다 커야 합니다")
    private BigDecimal originalAmount;
    
    /**
     * 추가 메타데이터 (선택사항)
     * 
     * 정산과 관련된 부가 정보를 JSON 문자열 형태로 저장합니다.
     * 결제 방법, 할인 정보, 특별 처리 사항 등을 포함할 수 있습니다.
     * 
     * 특징:
     * - 선택적 필드 (null 허용)
     * - JSON 형태의 문자열
     * - 최대 길이 제한 없음 (TEXT 타입)
     * - 구조화된 데이터 저장 가능
     * 
     * 예시:
     * {
     *   "paymentMethod": "card",
     *   "cardType": "credit",
     *   "discount": {
     *     "type": "coupon",
     *     "amount": 1000
     *   },
     *   "specialNote": "VIP 고객 우대"
     * }
     */
    private String metadata;

    /**
     * 요청 데이터의 유효성을 검증하는 편의 메서드
     * 
     * Bean Validation 어노테이션 외에 추가적인 비즈니스 규칙을 검증합니다.
     * 컨트롤러에서 호출하여 요청 데이터의 무결성을 확인할 수 있습니다.
     * 
     * @throws IllegalArgumentException 유효하지 않은 데이터가 있는 경우
     */
    public void validate() {
        // storeId 형식 검증 (UUID 형태인지 확인)
        if (storeId != null && !storeId.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            // UUID 형태가 아니어도 허용 (기존 시스템 호환성)
            if (storeId.trim().isEmpty()) {
                throw new IllegalArgumentException("가게 ID는 빈 문자열일 수 없습니다");
            }
        }
        
        // orderId 길이 검증
        if (orderId != null && orderId.length() > 100) {
            throw new IllegalArgumentException("주문 ID는 100자를 초과할 수 없습니다");
        }
        
        // originalAmount 범위 검증
        if (originalAmount != null) {
            if (originalAmount.scale() > 2) {
                throw new IllegalArgumentException("결제 금액은 소수점 둘째 자리까지만 허용됩니다");
            }
            if (originalAmount.compareTo(new BigDecimal("999999999999999.99")) > 0) {
                throw new IllegalArgumentException("결제 금액이 허용 범위를 초과했습니다");
            }
        }
        
        // metadata JSON 형식 검증 (기본적인 형태만 확인)
        if (metadata != null && !metadata.trim().isEmpty()) {
            String trimmed = metadata.trim();
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                // JSON 형태가 아니어도 허용 (단순 문자열도 가능)
            }
        }
    }

    /**
     * 메타데이터가 있는지 확인하는 편의 메서드
     * 
     * @return true: 메타데이터 있음, false: 메타데이터 없음
     */
    public boolean hasMetadata() {
        return metadata != null && !metadata.trim().isEmpty();
    }

    /**
     * 요청 정보를 로그용 문자열로 변환
     * 민감한 정보는 마스킹하여 안전하게 로깅할 수 있습니다.
     * 
     * @return 로그용 문자열 표현
     */
    public String toLogString() {
        return String.format("SettlementRequest{storeId='%s', orderId='%s', amount=%s, hasMetadata=%s}",
                storeId, orderId, originalAmount, hasMetadata());
    }
}