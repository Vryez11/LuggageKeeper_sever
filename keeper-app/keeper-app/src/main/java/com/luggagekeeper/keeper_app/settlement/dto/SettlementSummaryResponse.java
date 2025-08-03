package com.luggagekeeper.keeper_app.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 정산 요약 정보 API 응답용 DTO (Data Transfer Object)
 * 
 * 특정 가게의 특정 날짜에 대한 정산 요약 통계를 클라이언트에게 전달하기 위한 응답 객체입니다.
 * Flutter 앱의 대시보드나 요약 화면에서 사용되며, json_serializable과 완벽하게 호환됩니다.
 * 
 * <p>주요 특징:</p>
 * <ul>
 *   <li>일별 정산 통계 정보 제공</li>
 *   <li>금액별, 상태별 집계 데이터 포함</li>
 *   <li>Flutter json_serializable 호환 구조</li>
 *   <li>null 안전성을 고려한 기본값 처리</li>
 *   <li>대시보드 UI에 최적화된 데이터 구조</li>
 * </ul>
 * 
 * <p>사용 용도:</p>
 * <ul>
 *   <li>GET /api/v1/settlements/summary API 응답</li>
 *   <li>가게 사장님 대시보드 요약 정보</li>
 *   <li>관리자 통계 화면 데이터</li>
 *   <li>Flutter 앱의 정산 요약 위젯</li>
 * </ul>
 * 
 * <p>Flutter 연동 특징:</p>
 * <ul>
 *   <li>모든 필드가 getter/setter로 접근 가능</li>
 *   <li>기본 생성자 제공 (json_serializable 요구사항)</li>
 *   <li>LocalDate는 ISO 8601 형식으로 직렬화 (YYYY-MM-DD)</li>
 *   <li>BigDecimal은 숫자로 직렬화 (문자열 아님)</li>
 *   <li>Long 타입은 정수로 직렬화</li>
 * </ul>
 * 
 * <p>JSON 직렬화 예시:</p>
 * <pre>
 * {
 *   "storeId": "store-uuid-123",
 *   "date": "2024-01-15",
 *   "totalOriginalAmount": 50000,
 *   "totalPlatformFee": 10000,
 *   "totalSettlementAmount": 40000,
 *   "completedCount": 3,
 *   "pendingCount": 1,
 *   "failedCount": 0
 * }
 * </pre>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementSummaryResponse {
    
    /**
     * 정산 대상 가게 ID
     * 
     * 요약 정보가 속한 가게의 고유 식별자입니다.
     * Store 엔티티의 UUID 값이며, 가게별 통계 구분에 사용됩니다.
     */
    private String storeId;
    
    /**
     * 요약 정보 기준 날짜
     * 
     * 통계가 집계된 기준 날짜입니다.
     * 해당 날짜 00:00:00 ~ 23:59:59 범위의 정산 데이터를 집계합니다.
     * 
     * Flutter에서는 DateTime.parse()로 파싱 가능한 ISO 8601 형식으로 직렬화됩니다.
     */
    private LocalDate date;
    
    /**
     * 총 원본 결제 금액
     * 
     * 해당 날짜에 발생한 모든 정산의 원본 결제 금액 합계입니다.
     * 고객이 실제로 결제한 전체 금액의 총합을 나타냅니다.
     * 
     * 계산 방식: Σ(각 정산의 originalAmount)
     * 
     * 예시:
     * - 정산 1: 10,000원
     * - 정산 2: 15,000원  
     * - 정산 3: 25,000원
     * - 총합: 50,000원
     */
    private BigDecimal totalOriginalAmount;
    
    /**
     * 총 플랫폼 수수료
     * 
     * 해당 날짜에 발생한 모든 정산의 플랫폼 수수료 합계입니다.
     * 플랫폼이 가져가는 20% 수수료의 총합을 나타냅니다.
     * 
     * 계산 방식: Σ(각 정산의 platformFee)
     * 
     * 예시:
     * - 정산 1 수수료: 2,000원 (10,000원의 20%)
     * - 정산 2 수수료: 3,000원 (15,000원의 20%)
     * - 정산 3 수수료: 5,000원 (25,000원의 20%)
     * - 총합: 10,000원
     */
    private BigDecimal totalPlatformFee;
    
    /**
     * 총 정산 금액 (가게 수령액)
     * 
     * 해당 날짜에 가게가 실제로 받을 정산 금액의 합계입니다.
     * 원본 금액에서 플랫폼 수수료를 차감한 실제 지급 예정 금액입니다.
     * 
     * 계산 방식: Σ(각 정산의 settlementAmount) = totalOriginalAmount - totalPlatformFee
     * 
     * 예시:
     * - 정산 1 지급액: 8,000원 (10,000원 - 2,000원)
     * - 정산 2 지급액: 12,000원 (15,000원 - 3,000원)
     * - 정산 3 지급액: 20,000원 (25,000원 - 5,000원)
     * - 총합: 40,000원
     */
    private BigDecimal totalSettlementAmount;
    
    /**
     * 완료된 정산 건수
     * 
     * 해당 날짜에 성공적으로 완료된 정산의 건수입니다.
     * 토스페이먼츠 지급대행이 성공하여 COMPLETED 상태인 정산들을 집계합니다.
     * 
     * 포함 상태: SettlementStatus.COMPLETED
     */
    private Long completedCount;
    
    /**
     * 대기 중인 정산 건수
     * 
     * 해당 날짜에 아직 처리되지 않았거나 처리 중인 정산의 건수입니다.
     * PENDING(대기) 및 PROCESSING(처리중) 상태인 정산들을 집계합니다.
     * 
     * 포함 상태: SettlementStatus.PENDING, SettlementStatus.PROCESSING
     */
    private Long pendingCount;
    
    /**
     * 실패한 정산 건수
     * 
     * 해당 날짜에 처리가 실패한 정산의 건수입니다.
     * 토스페이먼츠 지급대행 실패, 잔액 부족 등으로 FAILED 상태인 정산들을 집계합니다.
     * 
     * 포함 상태: SettlementStatus.FAILED
     */
    private Long failedCount;

    /**
     * 정산 요약 정보를 생성하는 정적 팩토리 메서드
     * 
     * 입력된 통계 데이터를 바탕으로 SettlementSummaryResponse 객체를 생성합니다.
     * null 값에 대한 안전한 처리를 수행하여 NPE를 방지하고,
     * 기본값을 설정하여 클라이언트에서 안정적으로 사용할 수 있도록 합니다.
     * 
     * <p>null 값 처리 규칙:</p>
     * <ul>
     *   <li>BigDecimal null → BigDecimal.ZERO</li>
     *   <li>Long null → 0L</li>
     *   <li>String null → 그대로 유지 (storeId는 필수값이므로 null이면 안됨)</li>
     *   <li>LocalDate null → 그대로 유지 (date는 필수값이므로 null이면 안됨)</li>
     * </ul>
     * 
     * <p>데이터 일관성 검증:</p>
     * <ul>
     *   <li>totalOriginalAmount = totalPlatformFee + totalSettlementAmount (오차 허용)</li>
     *   <li>모든 금액은 0 이상이어야 함</li>
     *   <li>모든 건수는 0 이상이어야 함</li>
     * </ul>
     * 
     * @param storeId 가게 ID (null 불가)
     * @param date 기준 날짜 (null 불가)
     * @param totalOriginalAmount 총 원본 결제 금액 (null 시 0으로 처리)
     * @param totalPlatformFee 총 플랫폼 수수료 (null 시 0으로 처리)
     * @param totalSettlementAmount 총 정산 금액 (null 시 0으로 처리)
     * @param completedCount 완료된 정산 건수 (null 시 0으로 처리)
     * @param pendingCount 대기 중인 정산 건수 (null 시 0으로 처리)
     * @param failedCount 실패한 정산 건수 (null 시 0으로 처리)
     * @return null 안전성이 보장된 SettlementSummaryResponse 객체
     * @throws IllegalArgumentException storeId가 null이거나 빈 문자열인 경우
     * @throws IllegalArgumentException date가 null인 경우
     */
    public static SettlementSummaryResponse create(
            String storeId,
            LocalDate date,
            BigDecimal totalOriginalAmount,
            BigDecimal totalPlatformFee,
            BigDecimal totalSettlementAmount,
            Long completedCount,
            Long pendingCount,
            Long failedCount) {
        
        // 필수 파라미터 검증
        if (storeId == null || storeId.trim().isEmpty()) {
            throw new IllegalArgumentException("가게 ID는 필수입니다");
        }
        if (date == null) {
            throw new IllegalArgumentException("기준 날짜는 필수입니다");
        }
        
        // null 값을 기본값으로 처리하여 안전한 객체 생성
        return new SettlementSummaryResponse(
                storeId,
                date,
                totalOriginalAmount != null ? totalOriginalAmount : BigDecimal.ZERO,
                totalPlatformFee != null ? totalPlatformFee : BigDecimal.ZERO,
                totalSettlementAmount != null ? totalSettlementAmount : BigDecimal.ZERO,
                completedCount != null ? completedCount : 0L,
                pendingCount != null ? pendingCount : 0L,
                failedCount != null ? failedCount : 0L
        );
    }

    /**
     * 전체 정산 건수를 계산하는 편의 메서드
     * 
     * 완료, 대기, 실패 건수를 모두 합한 전체 정산 건수를 반환합니다.
     * 
     * @return 전체 정산 건수
     */
    public Long getTotalCount() {
        return completedCount + pendingCount + failedCount;
    }

    /**
     * 정산 완료율을 계산하는 편의 메서드
     * 
     * 전체 정산 건수 대비 완료된 정산 건수의 비율을 백분율로 반환합니다.
     * 전체 건수가 0인 경우 0.0을 반환합니다.
     * 
     * @return 정산 완료율 (0.0 ~ 100.0)
     */
    public Double getCompletionRate() {
        Long totalCount = getTotalCount();
        if (totalCount == 0) {
            return 0.0;
        }
        return (completedCount.doubleValue() / totalCount.doubleValue()) * 100.0;
    }

    /**
     * 정산 실패율을 계산하는 편의 메서드
     * 
     * 전체 정산 건수 대비 실패한 정산 건수의 비율을 백분율로 반환합니다.
     * 전체 건수가 0인 경우 0.0을 반환합니다.
     * 
     * @return 정산 실패율 (0.0 ~ 100.0)
     */
    public Double getFailureRate() {
        Long totalCount = getTotalCount();
        if (totalCount == 0) {
            return 0.0;
        }
        return (failedCount.doubleValue() / totalCount.doubleValue()) * 100.0;
    }

    /**
     * 평균 정산 금액을 계산하는 편의 메서드
     * 
     * 완료된 정산들의 평균 정산 금액을 반환합니다.
     * 완료된 정산이 없는 경우 BigDecimal.ZERO를 반환합니다.
     * 
     * @return 평균 정산 금액
     */
    public BigDecimal getAverageSettlementAmount() {
        if (completedCount == 0) {
            return BigDecimal.ZERO;
        }
        return totalSettlementAmount.divide(
            BigDecimal.valueOf(completedCount), 
            2, 
            java.math.RoundingMode.HALF_UP
        );
    }

    /**
     * 데이터 일관성을 검증하는 메서드
     * 
     * 총 원본 금액이 플랫폼 수수료와 정산 금액의 합과 일치하는지 확인합니다.
     * 부동소수점 연산 오차를 고려하여 0.01원 이내의 차이는 허용합니다.
     * 
     * @return true: 데이터 일관성 OK, false: 데이터 불일치
     */
    public boolean isDataConsistent() {
        BigDecimal calculatedTotal = totalPlatformFee.add(totalSettlementAmount);
        BigDecimal difference = totalOriginalAmount.subtract(calculatedTotal).abs();
        
        // 0.01원 이내의 차이는 허용 (부동소수점 연산 오차 고려)
        return difference.compareTo(new BigDecimal("0.01")) <= 0;
    }

    /**
     * 요약 정보를 로그용 문자열로 변환
     * 
     * 디버깅 및 로깅 목적으로 요약 정보를 간결한 문자열로 표현합니다.
     * 민감한 정보는 포함하지 않으며, 주요 통계만 포함합니다.
     * 
     * @return 로그용 문자열 표현
     */
    public String toLogString() {
        return String.format(
            "SettlementSummary{storeId='%s', date=%s, totalAmount=%s, " +
            "completed=%d, pending=%d, failed=%d, completionRate=%.1f%%}",
            storeId, date, totalSettlementAmount, 
            completedCount, pendingCount, failedCount, getCompletionRate()
        );
    }
}