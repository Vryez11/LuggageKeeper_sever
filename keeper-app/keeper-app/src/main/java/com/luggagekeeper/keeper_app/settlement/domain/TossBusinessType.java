package com.luggagekeeper.keeper_app.settlement.domain;

/**
 * 토스페이먼츠 사업자 타입을 나타내는 열거형
 * 
 * 토스페이먼츠 지급대행 서비스에서 지원하는 사업자 타입을 정의합니다.
 * 사업자 타입에 따라 승인 프로세스, 한도, 수수료 등이 다르게 적용됩니다.
 * 
 * 각 타입별 특징:
 * - 개인사업자: 간단한 승인, 낮은 초기 한도, 빠른 처리
 * - 법인사업자: KYC 심사, 높은 한도, 추가 서류 필요
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
public enum TossBusinessType {
    
    /**
     * 개인사업자 타입
     * 
     * 개인이 운영하는 사업체로, 상대적으로 간단한 승인 프로세스를 거칩니다.
     * 소규모 가게나 개인 운영 매장에 적합합니다.
     * 
     * 특징:
     * - 빠른 승인 프로세스 (보통 1-2일)
     * - 초기 월 한도: 1천만원 미만 (부분 승인)
     * - 추가 서류 제출로 한도 확대 가능
     * - 간단한 사업자등록증만으로 등록 가능
     * 
     * 승인 프로세스:
     * 1. APPROVAL_REQUIRED (승인 필요)
     * 2. PARTIALLY_APPROVED (부분 승인, 월 1천만원 미만)
     * 3. APPROVED (완전 승인, 추가 서류 제출 후)
     * 
     * 필요 서류:
     * - 사업자등록증
     * - 대표자 신분증
     * - 통장 사본 (계좌 확인용)
     * 
     * 적용 대상:
     * - 편의점, 카페, 소규모 음식점
     * - 개인 운영 숙박업소
     * - 기타 개인사업자로 등록된 가게
     */
    INDIVIDUAL_BUSINESS,
    
    /**
     * 법인사업자 타입
     * 
     * 법인으로 등록된 사업체로, 엄격한 KYC(Know Your Customer) 심사를 거칩니다.
     * 대규모 체인점이나 법인 운영 매장에 적합합니다.
     * 
     * 특징:
     * - 엄격한 KYC 심사 프로세스 (보통 3-7일)
     * - 높은 지급 한도 (토스페이먼츠 정책 범위 내)
     * - 다양한 부가 서비스 이용 가능
     * - 법인 관련 서류 필수 제출
     * 
     * 승인 프로세스:
     * 1. APPROVAL_REQUIRED (승인 필요)
     * 2. KYC_REQUIRED (KYC 심사 필요)
     * 3. APPROVED (완전 승인, KYC 통과 후)
     * 
     * 필요 서류:
     * - 법인 등기부등본
     * - 사업자등록증
     * - 대표자 신분증
     * - 법인 통장 사본
     * - 기타 토스페이먼츠 요구 서류
     * 
     * 적용 대상:
     * - 대형 체인점 (편의점, 프랜차이즈)
     * - 법인 운영 호텔, 펜션
     * - 기타 법인사업자로 등록된 가게
     * 
     * 장점:
     * - 높은 지급 한도
     * - 우선 고객 지원
     * - 고급 리포팅 기능
     * - 대량 거래 지원
     */
    CORPORATE;
    
    /**
     * 개인사업자 타입인지 확인
     * 
     * @return true: 개인사업자, false: 법인사업자
     */
    public boolean isIndividual() {
        return this == INDIVIDUAL_BUSINESS;
    }
    
    /**
     * 법인사업자 타입인지 확인
     * 
     * @return true: 법인사업자, false: 개인사업자
     */
    public boolean isCorporate() {
        return this == CORPORATE;
    }
    
    /**
     * KYC 심사가 필요한 사업자 타입인지 확인
     * 
     * @return true: KYC 필요 (법인사업자), false: KYC 불필요 (개인사업자)
     */
    public boolean requiresKyc() {
        return this == CORPORATE;
    }
    
    /**
     * 부분 승인이 가능한 사업자 타입인지 확인
     * 
     * @return true: 부분 승인 가능 (개인사업자), false: 부분 승인 불가 (법인사업자)
     */
    public boolean supportsPartialApproval() {
        return this == INDIVIDUAL_BUSINESS;
    }
    
    /**
     * 사업자 타입의 한국어 표시명 반환
     * 
     * @return 사업자 타입의 한국어 명칭
     */
    public String getDisplayName() {
        return switch (this) {
            case INDIVIDUAL_BUSINESS -> "개인사업자";
            case CORPORATE -> "법인사업자";
        };
    }
    
    /**
     * 예상 승인 소요 시간 반환 (일 단위)
     * 
     * @return 승인 완료까지 예상 소요 일수
     */
    public int getExpectedApprovalDays() {
        return switch (this) {
            case INDIVIDUAL_BUSINESS -> 2; // 1-2일
            case CORPORATE -> 5; // 3-7일
        };
    }
}