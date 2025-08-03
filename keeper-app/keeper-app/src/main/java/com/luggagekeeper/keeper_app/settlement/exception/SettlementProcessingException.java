package com.luggagekeeper.keeper_app.settlement.exception;

/**
 * 정산 처리 과정에서 발생하는 예외 클래스
 * 
 * 정산 생성, 수정, 삭제 등의 처리 과정에서 예상치 못한 오류가 발생했을 때 사용하는 예외입니다.
 * 데이터베이스 오류, 외부 시스템 연동 실패, 동시성 문제 등을 포함합니다.
 * 
 * <p>발생 상황:</p>
 * <ul>
 *   <li>데이터베이스 연결 실패 또는 쿼리 오류</li>
 *   <li>외부 API 호출 실패 (토스페이먼츠 제외)</li>
 *   <li>파일 시스템 접근 오류</li>
 *   <li>동시성 제어 실패 (낙관적 락 등)</li>
 *   <li>시스템 리소스 부족</li>
 * </ul>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
public class SettlementProcessingException extends SettlementException {
    
    /**
     * 처리 단계
     */
    private final String processingStage;
    
    /**
     * 정산 ID
     */
    private final String settlementId;

    /**
     * 기본 생성자
     * 
     * @param message 오류 메시지
     */
    public SettlementProcessingException(String message) {
        super(message, "SETTLEMENT_PROCESSING_ERROR");
        this.processingStage = null;
        this.settlementId = null;
    }

    /**
     * 원인 예외를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param cause 원인 예외
     */
    public SettlementProcessingException(String message, Throwable cause) {
        super(message, "SETTLEMENT_PROCESSING_ERROR", null, cause);
        this.processingStage = null;
        this.settlementId = null;
    }

    /**
     * 처리 단계를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param processingStage 처리 단계
     */
    public SettlementProcessingException(String message, String processingStage) {
        super(message, "SETTLEMENT_PROCESSING_ERROR", "processingStage: " + processingStage);
        this.processingStage = processingStage;
        this.settlementId = null;
    }

    /**
     * 정산 ID와 처리 단계를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param settlementId 정산 ID
     * @param processingStage 처리 단계
     */
    public SettlementProcessingException(String message, String settlementId, String processingStage) {
        super(message, "SETTLEMENT_PROCESSING_ERROR", 
              String.format("settlementId: %s, processingStage: %s", settlementId, processingStage));
        this.processingStage = processingStage;
        this.settlementId = settlementId;
    }

    /**
     * 모든 정보를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param settlementId 정산 ID
     * @param processingStage 처리 단계
     * @param cause 원인 예외
     */
    public SettlementProcessingException(String message, String settlementId, String processingStage, Throwable cause) {
        super(message, "SETTLEMENT_PROCESSING_ERROR", 
              String.format("settlementId: %s, processingStage: %s", settlementId, processingStage), cause);
        this.processingStage = processingStage;
        this.settlementId = settlementId;
    }

    /**
     * 처리 단계 반환
     * 
     * @return 처리 단계 (없으면 null)
     */
    public String getProcessingStage() {
        return processingStage;
    }

    /**
     * 정산 ID 반환
     * 
     * @return 정산 ID (없으면 null)
     */
    public String getSettlementId() {
        return settlementId;
    }

    /**
     * 처리 오류는 상황에 따라 재시도 가능
     * 
     * @return true: 재시도 가능, false: 재시도 불가
     */
    @Override
    public boolean isRetryable() {
        // 원인 예외가 있는 경우 원인에 따라 판단
        if (getCause() != null) {
            String causeName = getCause().getClass().getSimpleName();
            // 일시적인 오류는 재시도 가능
            return causeName.contains("Timeout") || 
                   causeName.contains("Connection") || 
                   causeName.contains("Network") ||
                   causeName.contains("Temporary");
        }
        
        // 처리 단계에 따른 재시도 가능성 판단
        if (processingStage != null) {
            return switch (processingStage.toLowerCase()) {
                case "database_save", "external_api_call", "file_operation" -> true;
                case "validation", "business_logic" -> false;
                default -> false;
            };
        }
        
        return false;
    }

    /**
     * 사용자 친화적 오류 메시지 반환
     * 
     * @return 사용자에게 표시할 오류 메시지
     */
    @Override
    public String getUserFriendlyMessage() {
        if (processingStage != null) {
            return String.format("정산 처리 중 오류가 발생했습니다. (%s 단계)", processingStage);
        }
        return "정산 처리 중 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
    }
}
