package com.luggagekeeper.keeper_app.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 토스페이먼츠 지급대행 요청 API 응답용 DTO
 * 
 * 토스페이먼츠 지급대행 요청의 결과를 담는 응답 객체입니다.
 * 지급대행 ID, 상태, 처리 시간 등의 정보를 제공합니다.
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TossPayoutResponse {
    
    /**
     * 토스페이먼츠 지급대행 ID
     */
    private String payoutId;
    
    /**
     * 우리 시스템의 정산 ID (멱등키)
     */
    private String refPayoutId;
    
    /**
     * 지급 금액
     */
    private BigDecimal amount;
    
    /**
     * 지급대행 상태
     */
    private String status;
    
    /**
     * 수취인 정보 (토스 셀러 ID)
     */
    private String destination;
    
    /**
     * 지급 일정 타입
     */
    private String scheduleType;
    
    /**
     * 거래 설명
     */
    private String transactionDescription;
    
    /**
     * 지급대행 요청 시간
     */
    private LocalDateTime requestedAt;
    
    /**
     * 지급 완료 예정 시간
     */
    private LocalDateTime scheduledAt;
}