package com.luggagekeeper.keeper_app.settlement.exception;

/**
 * 정산 시스템 기본 예외 클래스
 * 
 * 정산 시스템에서 발생하는 모든 비즈니스 로직 관련 예외의 기본 클래스입니다.
 * 이 클래스를 상속받아 구체적인 예외 상황을 나타내는 예외 클래스들을 정의합니다.
 * 
 * <p>예외 계층 구조:</p>
 * <ul>
 *   <li>SettlementException (기본 클래스)</li>
 *   <li>├── SettlementNotFoundException (정산 정보 없음)</li>
 *   <li>├── SettlementStatusException (정산 상태 오류)</li>
 *   <li>├── SettlementValidationException (정산 데이터 검증 오류)</li>
 *   <li>├── TossApiException (토스 API 관련 오류)</li>
 *   <li>│   └── InsufficientBalanceException (잔액 부족)</li>
 *   <li>└── SettlementProcessingException (정산 처리 오류)</li>
 * </ul>
 * 
 * <p>오류 코드 체계:</p>
 * <ul>
 *   <li>SETTLEMENT_XXX: 정산 관련 오류</li>
 *   <li>TOSS_XXX: 토스페이먼츠 관련 오류</li>
 *   <li>VALIDATION_XXX: 데이터 검증 오류</li>
 *   <li>PROCESSING_XXX: 처리 과정 오류</li>
 * </ul>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
public class SettlementException extends RuntimeException {
    
    /**
     * 오류 코드
     * 시스템에서 정의한 구체적인 오류 코드입니다.
     */
    private final String errorCode;
    
    /**
     * 오류 세부 정보
     * 디버깅이나 로깅에 사용할 추가 정보입니다.
     */
    private final String errorDetail;

    /**
     * 기본 생성자
     * 
     * @param message 오류 메시지
     */
    public SettlementException(String message) {
        super(message);
        this.errorCode = null;
        this.errorDetail = null;
    }

    /**
     * 원인 예외를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param cause 원인 예외
     */
    public SettlementException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.errorDetail = null;
    }

    /**
     * 오류 코드를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param errorCode 오류 코드
     */
    public SettlementException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.errorDetail = null;
    }

    /**
     * 오류 코드와 세부 정보를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param errorCode 오류 코드
     * @param errorDetail 오류 세부 정보
     */
    public SettlementException(String message, String errorCode, String errorDetail) {
        super(message);
        this.errorCode = errorCode;
        this.errorDetail = errorDetail;
    }

    /**
     * 모든 정보를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param errorCode 오류 코드
     * @param errorDetail 오류 세부 정보
     * @param cause 원인 예외
     */
    public SettlementException(String message, String errorCode, String errorDetail, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorDetail = errorDetail;
    }

    /**
     * 오류 코드 반환
     * 
     * @return 오류 코드 (없으면 null)
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 오류 세부 정보 반환
     * 
     * @return 오류 세부 정보 (없으면 null)
     */
    public String getErrorDetail() {
        return errorDetail;
    }

    /**
     * 재시도 가능한 오류인지 판단
     * 기본적으로 비즈니스 로직 오류는 재시도하지 않습니다.
     * 
     * @return false (재시도 불가)
     */
    public boolean isRetryable() {
        return false;
    }

    /**
     * 사용자에게 표시할 친화적인 오류 메시지 반환
     * 
     * @return 사용자 친화적 오류 메시지
     */
    public String getUserFriendlyMessage() {
        return getMessage();
    }
}
