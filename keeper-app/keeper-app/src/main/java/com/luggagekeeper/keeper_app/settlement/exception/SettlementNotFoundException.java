package com.luggagekeeper.keeper_app.settlement.exception;

/**
 * 정산 정보를 찾을 수 없는 경우 발생하는 예외
 * 
 * 요청한 정산 ID에 해당하는 정산 정보가 데이터베이스에 존재하지 않을 때 발생합니다.
 * 이는 잘못된 정산 ID가 전달되었거나, 정산이 삭제되었을 가능성을 나타냅니다.
 * 
 * <p>발생 상황:</p>
 * <ul>
 *   <li>존재하지 않는 정산 ID로 조회 시도</li>
 *   <li>삭제된 정산에 대한 접근 시도</li>
 *   <li>권한이 없는 정산에 대한 접근 시도</li>
 *   <li>잘못된 형식의 정산 ID 전달</li>
 * </ul>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
public class SettlementNotFoundException extends SettlementException {
    
    /**
     * 찾을 수 없는 정산 ID
     */
    private final String settlementId;

    /**
     * 기본 생성자
     * 
     * @param message 오류 메시지
     */
    public SettlementNotFoundException(String message) {
        super(message, "SETTLEMENT_NOT_FOUND");
        this.settlementId = null;
    }

    /**
     * 정산 ID를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param settlementId 찾을 수 없는 정산 ID
     */
    public SettlementNotFoundException(String message, String settlementId) {
        super(message, "SETTLEMENT_NOT_FOUND", "settlementId: " + settlementId);
        this.settlementId = settlementId;
    }

    /**
     * 원인 예외를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param settlementId 찾을 수 없는 정산 ID
     * @param cause 원인 예외
     */
    public SettlementNotFoundException(String message, String settlementId, Throwable cause) {
        super(message, "SETTLEMENT_NOT_FOUND", "settlementId: " + settlementId, cause);
        this.settlementId = settlementId;
    }

    /**
     * 찾을 수 없는 정산 ID 반환
     * 
     * @return 정산 ID (없으면 null)
     */
    public String getSettlementId() {
        return settlementId;
    }

    /**
     * 사용자 친화적 오류 메시지 반환
     * 
     * @return 사용자에게 표시할 오류 메시지
     */
    @Override
    public String getUserFriendlyMessage() {
        if (settlementId != null) {
            return String.format("요청하신 정산 정보를 찾을 수 없습니다. (정산 ID: %s)", settlementId);
        }
        return "요청하신 정산 정보를 찾을 수 없습니다.";
    }
}
