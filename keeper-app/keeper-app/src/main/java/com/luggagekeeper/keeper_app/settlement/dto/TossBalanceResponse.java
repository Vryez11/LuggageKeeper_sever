package com.luggagekeeper.keeper_app.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 토스페이먼츠 잔액 조회 API 응답용 DTO
 * 
 * 토스페이먼츠 지급대행 서비스의 잔액 정보를 담는 응답 객체입니다.
 * 현재 지급 가능한 금액과 대기 중인 금액 정보를 제공합니다.
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TossBalanceResponse {
    
    /**
     * 즉시 지급 가능한 금액
     */
    private BigDecimal availableAmount;
    
    /**
     * 정산 대기 중인 금액
     */
    private BigDecimal pendingAmount;
    
    /**
     * 전체 잔액 (available + pending)
     */
    private BigDecimal totalAmount;
    
    /**
     * 잔액 정보 최종 업데이트 시간
     */
    private LocalDateTime lastUpdatedAt;

    /**
     * 전체 잔액 계산
     * 
     * @return 지급 가능 금액 + 대기 중인 금액
     */
    public BigDecimal getTotalAmount() {
        if (availableAmount == null && pendingAmount == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal available = availableAmount != null ? availableAmount : BigDecimal.ZERO;
        BigDecimal pending = pendingAmount != null ? pendingAmount : BigDecimal.ZERO;
        
        return available.add(pending);
    }
}