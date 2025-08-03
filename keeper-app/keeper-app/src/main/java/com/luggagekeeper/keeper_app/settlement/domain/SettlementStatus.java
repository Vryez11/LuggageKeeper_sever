package com.luggagekeeper.keeper_app.settlement.domain;

/**
 * 정산 처리 상태를 나타내는 열거형
 * 
 * 정산 요청부터 완료까지의 전체 생명주기를 추적하기 위한 상태값들을 정의합니다.
 * 각 상태는 정산 처리 프로세스의 특정 단계를 나타내며,
 * 상태 전이는 비즈니스 로직에 따라 엄격하게 관리됩니다.
 * 
 * 상태 전이 흐름:
 * PENDING → PROCESSING → COMPLETED (성공)
 * PENDING → PROCESSING → FAILED → PROCESSING (재시도)
 * PENDING/PROCESSING/FAILED → CANCELLED (취소)
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
public enum SettlementStatus {
    
    /**
     * 정산 대기 상태
     * 
     * 정산 요청이 생성되었지만 아직 토스페이먼츠 지급대행 처리가 시작되지 않은 상태입니다.
     * 이 상태에서는 정산 처리를 시작하거나 취소할 수 있습니다.
     * 
     * 가능한 다음 상태:
     * - PROCESSING: 정산 처리 시작
     * - CANCELLED: 정산 취소
     */
    PENDING,
    
    /**
     * 정산 처리 중 상태
     * 
     * 토스페이먼츠 지급대행 API 호출이 진행 중인 상태입니다.
     * 중복 처리를 방지하기 위해 이 상태에서는 추가 처리 요청을 차단합니다.
     * 
     * 가능한 다음 상태:
     * - COMPLETED: 지급대행 성공
     * - FAILED: 지급대행 실패
     * - CANCELLED: 처리 중 취소 (관리자 개입)
     */
    PROCESSING,
    
    /**
     * 정산 완료 상태
     * 
     * 토스페이먼츠 지급대행이 성공적으로 완료된 상태입니다.
     * 가게 계좌로 정산 금액이 송금되었으며, 더 이상 상태 변경이 불가능합니다.
     * 
     * 특징:
     * - 최종 상태 (더 이상 변경 불가)
     * - completedAt 필드에 완료 시각 기록
     * - tossPayoutId 필드에 토스 지급대행 ID 저장
     */
    COMPLETED,
    
    /**
     * 정산 실패 상태
     * 
     * 토스페이먼츠 지급대행 요청이 실패한 상태입니다.
     * 잔액 부족, 계좌 정보 오류, 네트워크 오류 등의 이유로 발생할 수 있습니다.
     * 
     * 가능한 다음 상태:
     * - PROCESSING: 재시도 (최대 3회)
     * - CANCELLED: 재시도 포기 후 취소
     * 
     * 특징:
     * - errorMessage 필드에 실패 원인 저장
     * - retryCount 필드로 재시도 횟수 추적
     * - 최대 3회까지 자동 재시도 가능
     */
    FAILED,
    
    /**
     * 정산 취소 상태
     * 
     * 관리자가 수동으로 정산을 취소하거나,
     * 시스템 오류로 인해 정산 처리를 중단한 상태입니다.
     * 
     * 특징:
     * - 최종 상태 (더 이상 변경 불가)
     * - 취소된 정산은 다시 처리할 수 없음
     * - 새로운 정산 요청을 생성해야 함
     * 
     * 취소 가능한 이전 상태:
     * - PENDING: 처리 시작 전 취소
     * - PROCESSING: 처리 중 강제 취소 (관리자 개입)
     * - FAILED: 재시도 포기 후 취소
     */
    CANCELLED;
    
    /**
     * 정산이 진행 중인 상태인지 확인
     * 
     * @return true: 진행 중 (PENDING, PROCESSING), false: 완료 또는 중단됨
     */
    public boolean isInProgress() {
        return this == PENDING || this == PROCESSING;
    }
    
    /**
     * 정산이 최종 완료된 상태인지 확인
     * 
     * @return true: 최종 상태 (COMPLETED, CANCELLED), false: 진행 중
     */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED;
    }
    
    /**
     * 정산 처리가 성공한 상태인지 확인
     * 
     * @return true: 성공 (COMPLETED), false: 실패 또는 진행 중
     */
    public boolean isSuccessful() {
        return this == COMPLETED;
    }
    
    /**
     * 재시도가 가능한 상태인지 확인
     * 
     * @return true: 재시도 가능 (FAILED), false: 재시도 불가
     */
    public boolean canRetry() {
        return this == FAILED;
    }
}