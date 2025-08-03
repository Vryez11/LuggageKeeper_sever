package com.luggagekeeper.keeper_app.settlement.exception;

import java.math.BigDecimal;

/**
 * 잔액 부족 예외 클래스
 * 
 * 토스페이먼츠 지급대행 요청 시 잔액이 부족하여 처리할 수 없는 경우 발생하는 예외입니다.
 * 이 예외가 발생한 정산은 재시도하지 않고 수동 처리가 필요합니다.
 * 
 * <p>발생 상황:</p>
 * <ul>
 *   <li>토스페이먼츠 계정의 지급 가능 잔액 부족</li>
 *   <li>일일 지급 한도 초과</li>
 *   <li>월간 지급 한도 초과</li>
 *   <li>계정 상태 이상으로 인한 지급 제한</li>
 * </ul>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
public class InsufficientBalanceException extends TossApiException {
    
    /**
     * 요청한 지급 금액
     */
    private final BigDecimal requestedAmount;
    
    /**
     * 현재 사용 가능한 잔액
     */
    private final BigDecimal availableBalance;

    /**
     * 기본 생성자
     * 
     * @param message 오류 메시지
     */
    public InsufficientBalanceException(String message) {
        super(message);
        this.requestedAmount = null;
        this.availableBalance = null;
    }

    /**
     * 원인 예외를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param cause 원인 예외
     */
    public InsufficientBalanceException(String message, Throwable cause) {
        super(message, cause);
        this.requestedAmount = null;
        this.availableBalance = null;
    }

    /**
     * 금액 정보를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param requestedAmount 요청한 지급 금액
     * @param availableBalance 현재 사용 가능한 잔액
     */
    public InsufficientBalanceException(String message, BigDecimal requestedAmount, BigDecimal availableBalance) {
        super(message);
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
    }

    /**
     * 모든 정보를 포함하는 생성자
     * 
     * @param message 오류 메시지
     * @param requestedAmount 요청한 지급 금액
     * @param availableBalance 현재 사용 가능한 잔액
     * @param cause 원인 예외
     */
    public InsufficientBalanceException(String message, BigDecimal requestedAmount, 
                                      BigDecimal availableBalance, Throwable cause) {
        super(message, cause);
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
    }

    /**
     * 요청한 지급 금액 반환
     * 
     * @return 요청한 지급 금액 (없으면 null)
     */
    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    /**
     * 현재 사용 가능한 잔액 반환
     * 
     * @return 현재 사용 가능한 잔액 (없으면 null)
     */
    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    /**
     * 부족한 금액 계산
     * 
     * @return 부족한 금액 (요청 금액 - 사용 가능 잔액)
     */
    public BigDecimal getShortfallAmount() {
        if (requestedAmount == null || availableBalance == null) {
            return null;
        }
        return requestedAmount.subtract(availableBalance);
    }

    /**
     * 잔액 부족 예외는 재시도하지 않음
     * 
     * @return 항상 false (재시도 불가)
     */
    @Override
    public boolean isRetryable() {
        return false;
    }
}