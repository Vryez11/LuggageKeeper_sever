package com.luggagekeeper.keeper_app.settlement.exception;

/**
 * 정산 상태 관련 오류 예외 클래스
 * 
 * 정산의 현재 상태로 인해 요청한 작업을 수행할 수 없을 때 발생하는 예외입니다.
 * 정산 상태 전이 규칙을 위반하거나, 특정 상태에서만 가능한 작업을 다른 상태에서 시도할 때 발생합니다.
 * 
 * <p>발생 상황:</p>
 * <ul>
 *   <li>이미 처리 완료된 정산을 다시 처리하려고 할 때</li>
 *   <li>실패한 정산을 승인하려고 할 때</li>
 *   <li>대기 상태가 아닌 정산을 취소하려고 할 때</li>
 *   <li>잘못된 상태 전이를 시도할 때</li>
 * </ul>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
public class SettlementStatusException extends SettlementException {
    
    /**
     * 현재 정산 상태
     */
    private final String currentStatus;
    
    /**
     * 요청한 작업
     */
    private final String requestedAction;
    
    /**
     * 정산 ID
     */
    private final String settlementId;

    /**
     * 기본 생성자
     * 
     * @param message 오류 메시지
     */
    public SettlementStatusException(String message) {
        super(message, "SETTLEMENT_INVALID_STATUS");
        this.currentStatus = null;
        this.requestedAction = null;
        this.settlementId = null;
    }

    /**
     * 상태 정보를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param currentStatus 현재 정산 상태
     * @param requestedAction 요청한 작업
     */
    public SettlementStatusException(String message, String currentStatus, String requestedAction) {
        super(message, "SETTLEMENT_INVALID_STATUS", 
              String.format("currentStatus: %s, requestedAction: %s", currentStatus, requestedAction));
        this.currentStatus = currentStatus;
        this.requestedAction = requestedAction;
        this.settlementId = null;
    }

    /**
     * 모든 정보를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param settlementId 정산 ID
     * @param currentStatus 현재 정산 상태
     * @param requestedAction 요청한 작업
     */
    public SettlementStatusException(String message, String settlementId, String currentStatus, String requestedAction) {
        super(message, "SETTLEMENT_INVALID_STATUS", 
              String.format("settlementId: %s, currentStatus: %s, requestedAction: %s", 
                          settlementId, currentStatus, requestedAction));
        this.currentStatus = currentStatus;
        this.requestedAction = requestedAction;
        this.settlementId = settlementId;
    }

    /**
     * 원인 예외를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param settlementId 정산 ID
     * @param currentStatus 현재 정산 상태
     * @param requestedAction 요청한 작업
     * @param cause 원인 예외
     */
    public SettlementStatusException(String message, String settlementId, String currentStatus, 
                                   String requestedAction, Throwable cause) {
        super(message, "SETTLEMENT_INVALID_STATUS", 
              String.format("settlementId: %s, currentStatus: %s, requestedAction: %s", 
                          settlementId, currentStatus, requestedAction), cause);
        this.currentStatus = currentStatus;
        this.requestedAction = requestedAction;
        this.settlementId = settlementId;
    }

    /**
     * 현재 정산 상태 반환
     * 
     * @return 현재 정산 상태 (없으면 null)
     */
    public String getCurrentStatus() {
        return currentStatus;
    }

    /**
     * 요청한 작업 반환
     * 
     * @return 요청한 작업 (없으면 null)
     */
    public String getRequestedAction() {
        return requestedAction;
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
     * 사용자 친화적 오류 메시지 반환
     * 
     * @return 사용자에게 표시할 오류 메시지
     */
    @Override
    public String getUserFriendlyMessage() {
        if (currentStatus != null && requestedAction != null) {
            return String.format("현재 정산 상태(%s)에서는 요청하신 작업(%s)을 수행할 수 없습니다.", 
                               currentStatus, requestedAction);
        }
        return "정산 상태로 인해 요청하신 작업을 수행할 수 없습니다.";
    }
}
