package com.luggagekeeper.keeper_app.settlement.exception;

import java.util.List;
import java.util.Map;

/**
 * 정산 데이터 검증 오류 예외 클래스
 * 
 * 정산 생성이나 수정 시 입력 데이터의 유효성 검증에 실패했을 때 발생하는 예외입니다.
 * Bean Validation 오류나 비즈니스 규칙 위반 등을 포함합니다.
 * 
 * <p>발생 상황:</p>
 * <ul>
 *   <li>필수 필드 누락 (@NotNull, @NotBlank 위반)</li>
 *   <li>데이터 형식 오류 (@Pattern, @Email 위반)</li>
 *   <li>범위 초과 (@Min, @Max, @Size 위반)</li>
 *   <li>비즈니스 규칙 위반 (중복 주문, 금액 한도 초과 등)</li>
 * </ul>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
public class SettlementValidationException extends SettlementException {
    
    /**
     * 검증 실패한 필드별 오류 메시지
     */
    private final Map<String, List<String>> fieldErrors;
    
    /**
     * 전역 검증 오류 메시지
     */
    private final List<String> globalErrors;

    /**
     * 기본 생성자
     * 
     * @param message 오류 메시지
     */
    public SettlementValidationException(String message) {
        super(message, "SETTLEMENT_VALIDATION_ERROR");
        this.fieldErrors = null;
        this.globalErrors = null;
    }

    /**
     * 필드 오류를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param fieldErrors 필드별 오류 메시지
     */
    public SettlementValidationException(String message, Map<String, List<String>> fieldErrors) {
        super(message, "SETTLEMENT_VALIDATION_ERROR", "fieldErrors: " + fieldErrors);
        this.fieldErrors = fieldErrors;
        this.globalErrors = null;
    }

    /**
     * 전역 오류를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param globalErrors 전역 오류 메시지 목록
     */
    public SettlementValidationException(String message, List<String> globalErrors) {
        super(message, "SETTLEMENT_VALIDATION_ERROR", "globalErrors: " + globalErrors);
        this.fieldErrors = null;
        this.globalErrors = globalErrors;
    }

    /**
     * 모든 오류 정보를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param fieldErrors 필드별 오류 메시지
     * @param globalErrors 전역 오류 메시지 목록
     */
    public SettlementValidationException(String message, Map<String, List<String>> fieldErrors, 
                                       List<String> globalErrors) {
        super(message, "SETTLEMENT_VALIDATION_ERROR", 
              String.format("fieldErrors: %s, globalErrors: %s", fieldErrors, globalErrors));
        this.fieldErrors = fieldErrors;
        this.globalErrors = globalErrors;
    }

    /**
     * 원인 예외를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param fieldErrors 필드별 오류 메시지
     * @param globalErrors 전역 오류 메시지 목록
     * @param cause 원인 예외
     */
    public SettlementValidationException(String message, Map<String, List<String>> fieldErrors, 
                                       List<String> globalErrors, Throwable cause) {
        super(message, "SETTLEMENT_VALIDATION_ERROR", 
              String.format("fieldErrors: %s, globalErrors: %s", fieldErrors, globalErrors), cause);
        this.fieldErrors = fieldErrors;
        this.globalErrors = globalErrors;
    }

    /**
     * 필드별 오류 메시지 반환
     * 
     * @return 필드별 오류 메시지 (없으면 null)
     */
    public Map<String, List<String>> getFieldErrors() {
        return fieldErrors;
    }

    /**
     * 전역 오류 메시지 반환
     * 
     * @return 전역 오류 메시지 목록 (없으면 null)
     */
    public List<String> getGlobalErrors() {
        return globalErrors;
    }

    /**
     * 검증 오류가 있는지 확인
     * 
     * @return true: 검증 오류 있음, false: 검증 오류 없음
     */
    public boolean hasErrors() {
        return (fieldErrors != null && !fieldErrors.isEmpty()) || 
               (globalErrors != null && !globalErrors.isEmpty());
    }

    /**
     * 특정 필드에 오류가 있는지 확인
     * 
     * @param fieldName 확인할 필드명
     * @return true: 해당 필드에 오류 있음, false: 오류 없음
     */
    public boolean hasFieldError(String fieldName) {
        return fieldErrors != null && fieldErrors.containsKey(fieldName);
    }

    /**
     * 사용자 친화적 오류 메시지 반환
     * 
     * @return 사용자에게 표시할 오류 메시지
     */
    @Override
    public String getUserFriendlyMessage() {
        if (hasErrors()) {
            StringBuilder sb = new StringBuilder("입력 정보를 확인해 주세요:");
            
            if (fieldErrors != null) {
                fieldErrors.forEach((field, errors) -> {
                    sb.append(String.format("\n- %s: %s", field, String.join(", ", errors)));
                });
            }
            
            if (globalErrors != null) {
                globalErrors.forEach(error -> sb.append("\n- ").append(error));
            }
            
            return sb.toString();
        }
        return "입력 정보에 오류가 있습니다. 다시 확인해 주세요.";
    }
}
