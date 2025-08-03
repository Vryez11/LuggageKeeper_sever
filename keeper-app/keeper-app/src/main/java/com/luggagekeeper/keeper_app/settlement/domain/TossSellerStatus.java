package com.luggagekeeper.keeper_app.settlement.domain;

/**
 * 토스페이먼츠 셀러 승인 상태를 나타내는 열거형
 * 
 * 토스페이먼츠 지급대행 서비스를 이용하기 위한 셀러 등록 및 승인 프로세스의
 * 각 단계를 추적하기 위한 상태값들을 정의합니다.
 * 
 * 사업자 타입별 승인 프로세스:
 * 
 * 개인사업자:
 * APPROVAL_REQUIRED → PARTIALLY_APPROVED → APPROVED
 * 
 * 법인사업자:
 * APPROVAL_REQUIRED → KYC_REQUIRED → APPROVED
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
public enum TossSellerStatus {
    
    /**
     * 승인 필요 상태 (초기 상태)
     * 
     * 토스페이먼츠에 셀러 등록 요청을 보낸 후 승인을 기다리는 상태입니다.
     * 모든 사업자 타입의 초기 상태이며, 토스페이먼츠의 검토를 기다립니다.
     * 
     * 특징:
     * - 셀러 등록 직후의 기본 상태
     * - 지급대행 서비스 이용 불가
     * - 토스페이먼츠의 승인 검토 대기 중
     * 
     * 가능한 다음 상태:
     * - PARTIALLY_APPROVED: 개인사업자 부분 승인
     * - KYC_REQUIRED: 법인사업자 KYC 심사 필요
     * - APPROVED: 즉시 완전 승인 (드문 경우)
     */
    APPROVAL_REQUIRED,
    
    /**
     * 부분 승인 상태 (개인사업자 전용)
     * 
     * 개인사업자가 기본적인 승인을 받아 제한된 범위에서 지급대행 서비스를
     * 이용할 수 있는 상태입니다. 월 1천만원 미만의 한도가 적용됩니다.
     * 
     * 특징:
     * - 개인사업자만 해당
     * - 월 1천만원 미만 지급대행 가능
     * - 추가 서류 제출로 완전 승인 가능
     * 
     * 제한사항:
     * - 월 지급 한도: 1천만원 미만
     * - 일부 고급 기능 제한
     * 
     * 가능한 다음 상태:
     * - APPROVED: 추가 서류 제출 후 완전 승인
     */
    PARTIALLY_APPROVED,
    
    /**
     * KYC 심사 필요 상태 (법인사업자 전용)
     * 
     * 법인사업자가 Know Your Customer(KYC) 심사를 받아야 하는 상태입니다.
     * 법인 등기부등본, 대표자 신분증 등 추가 서류 제출이 필요합니다.
     * 
     * 특징:
     * - 법인사업자만 해당
     * - 지급대행 서비스 이용 불가
     * - 추가 서류 및 심사 필요
     * 
     * 필요 서류:
     * - 법인 등기부등본
     * - 대표자 신분증
     * - 사업자등록증
     * - 기타 토스페이먼츠 요구 서류
     * 
     * 가능한 다음 상태:
     * - APPROVED: KYC 심사 통과 후 완전 승인
     */
    KYC_REQUIRED,
    
    /**
     * 완전 승인 상태 (최종 목표)
     * 
     * 모든 승인 프로세스를 완료하여 토스페이먼츠 지급대행 서비스를
     * 제한 없이 이용할 수 있는 상태입니다.
     * 
     * 특징:
     * - 모든 지급대행 기능 이용 가능
     * - 한도 제한 없음 (토스페이먼츠 정책 범위 내)
     * - 최종 승인 상태
     * 
     * 이용 가능 서비스:
     * - 무제한 지급대행 (정책 범위 내)
     * - 실시간 잔액 조회
     * - 지급 내역 조회
     * - 지급 취소 (조건부)
     * 
     * 특징:
     * - 최종 상태 (더 이상 변경 없음)
     * - approvedAt 필드에 승인 완료 시각 기록
     */
    APPROVED;
    
    /**
     * 지급대행 서비스 이용 가능한 상태인지 확인
     * 
     * @return true: 이용 가능 (PARTIALLY_APPROVED, APPROVED), false: 이용 불가
     */
    public boolean canProcessPayout() {
        return this == PARTIALLY_APPROVED || this == APPROVED;
    }
    
    /**
     * 승인 대기 중인 상태인지 확인
     * 
     * @return true: 승인 대기 중 (APPROVAL_REQUIRED, KYC_REQUIRED), false: 승인 완료
     */
    public boolean isPending() {
        return this == APPROVAL_REQUIRED || this == KYC_REQUIRED;
    }
    
    /**
     * 완전 승인된 상태인지 확인
     * 
     * @return true: 완전 승인 (APPROVED), false: 부분 승인 또는 미승인
     */
    public boolean isFullyApproved() {
        return this == APPROVED;
    }
    
    /**
     * 부분 승인된 상태인지 확인
     * 
     * @return true: 부분 승인 (PARTIALLY_APPROVED), false: 완전 승인 또는 미승인
     */
    public boolean isPartiallyApproved() {
        return this == PARTIALLY_APPROVED;
    }
    
    /**
     * 추가 서류나 심사가 필요한 상태인지 확인
     * 
     * @return true: 추가 절차 필요, false: 승인 완료
     */
    public boolean requiresAdditionalProcess() {
        return this == APPROVAL_REQUIRED || this == KYC_REQUIRED;
    }
}