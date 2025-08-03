package com.luggagekeeper.keeper_app.settlement.exception;

/**
 * 토스페이먼츠 API 호출 관련 예외 클래스
 * 
 * 토스페이먼츠 API 연동 중 발생하는 모든 오류를 나타내는 예외입니다.
 * 네트워크 오류, 인증 실패, 비즈니스 로직 오류 등을 포함합니다.
 * 
 * <p>주요 사용 사례:</p>
 * <ul>
 *   <li>토스페이먼츠 API 호출 실패</li>
 *   <li>JWE 암호화/복호화 실패</li>
 *   <li>인증 토큰 오류</li>
 *   <li>잔액 부족 등 비즈니스 오류</li>
 *   <li>네트워크 연결 실패</li>
 * </ul>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
public class TossApiException extends SettlementException {
    
    /**
     * 토스페이먼츠에서 반환한 오류 코드
     * API 응답에 포함된 구체적인 오류 코드입니다.
     */
    private final String tossErrorCode;
    
    /**
     * HTTP 상태 코드
     * API 호출 시 반환된 HTTP 상태 코드입니다.
     */
    private final Integer httpStatusCode;

    /**
     * 기본 생성자
     * 
     * @param message 오류 메시지
     */
    public TossApiException(String message) {
        super(message, "TOSS_API_ERROR");
        this.tossErrorCode = null;
        this.httpStatusCode = null;
    }

    /**
     * 원인 예외를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param cause 원인 예외
     */
    public TossApiException(String message, Throwable cause) {
        super(message, "TOSS_API_ERROR", null, cause);
        this.tossErrorCode = null;
        this.httpStatusCode = null;
    }

    /**
     * 토스 오류 코드를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param tossErrorCode 토스페이먼츠 오류 코드
     */
    public TossApiException(String message, String tossErrorCode) {
        super(message, "TOSS_API_ERROR", "tossErrorCode: " + tossErrorCode);
        this.tossErrorCode = tossErrorCode;
        this.httpStatusCode = null;
    }

    /**
     * 토스 오류 코드와 HTTP 상태 코드를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param tossErrorCode 토스페이먼츠 오류 코드
     * @param httpStatusCode HTTP 상태 코드
     */
    public TossApiException(String message, String tossErrorCode, Integer httpStatusCode) {
        super(message, "TOSS_API_ERROR", 
              String.format("tossErrorCode: %s, httpStatusCode: %d", tossErrorCode, httpStatusCode));
        this.tossErrorCode = tossErrorCode;
        this.httpStatusCode = httpStatusCode;
    }

    /**
     * 모든 정보를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param tossErrorCode 토스페이먼츠 오류 코드
     * @param httpStatusCode HTTP 상태 코드
     * @param cause 원인 예외
     */
    public TossApiException(String message, String tossErrorCode, Integer httpStatusCode, Throwable cause) {
        super(message, "TOSS_API_ERROR", 
              String.format("tossErrorCode: %s, httpStatusCode: %d", tossErrorCode, httpStatusCode), cause);
        this.tossErrorCode = tossErrorCode;
        this.httpStatusCode = httpStatusCode;
    }

    /**
     * 토스페이먼츠 오류 코드 반환
     * 
     * @return 토스페이먼츠 오류 코드 (없으면 null)
     */
    public String getTossErrorCode() {
        return tossErrorCode;
    }

    /**
     * HTTP 상태 코드 반환
     * 
     * @return HTTP 상태 코드 (없으면 null)
     */
    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    /**
     * 재시도 가능한 오류인지 판단
     * 
     * @return true: 재시도 가능, false: 재시도 불가
     */
    public boolean isRetryable() {
        // HTTP 5xx 오류는 재시도 가능
        if (httpStatusCode != null && httpStatusCode >= 500) {
            return true;
        }
        
        // 특정 토스 오류 코드는 재시도 가능
        if (tossErrorCode != null) {
            return switch (tossErrorCode) {
                case "NETWORK_ERROR", "TIMEOUT", "TEMPORARY_UNAVAILABLE" -> true;
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
        if (tossErrorCode != null) {
            return switch (tossErrorCode) {
                case "INVALID_REQUEST" -> "요청 정보가 올바르지 않습니다. 다시 확인해 주세요.";
                case "UNAUTHORIZED" -> "인증에 실패했습니다. 관리자에게 문의해 주세요.";
                case "FORBIDDEN" -> "해당 작업을 수행할 권한이 없습니다.";
                case "NOT_FOUND" -> "요청한 정보를 찾을 수 없습니다.";
                case "NETWORK_ERROR", "TIMEOUT" -> "일시적인 네트워크 오류입니다. 잠시 후 다시 시도해 주세요.";
                case "TEMPORARY_UNAVAILABLE" -> "서비스가 일시적으로 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.";
                default -> "결제 서비스 처리 중 오류가 발생했습니다. 관리자에게 문의해 주세요.";
            };
        }
        return "결제 서비스 연동 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
    }
}