-- =============================================
-- 정산 시스템 데이터베이스 스키마 생성 스크립트
-- Version: 1.0
-- Author: Settlement System
-- Created: 2024-01-01
-- Description: settlements, toss_sellers 테이블 및 관련 제약조건 생성
-- =============================================

-- 1. 토스페이먼츠 판매자 정보 테이블 생성
-- 토스페이먼츠 지급대행 서비스를 사용하는 판매자(가게) 정보를 저장
CREATE TABLE toss_sellers (
    -- 기본 키: 토스페이먼츠 판매자 ID
    seller_id VARCHAR(100) NOT NULL COMMENT '토스페이먼츠 판매자 고유 ID',
    
    -- 가게 정보
    store_id VARCHAR(100) NOT NULL COMMENT '내부 가게 ID (외래키)',
    store_name VARCHAR(200) NOT NULL COMMENT '가게명',
    
    -- 토스페이먼츠 계정 정보
    toss_account_id VARCHAR(100) NOT NULL COMMENT '토스페이먼츠 계정 ID',
    toss_secret_key VARCHAR(500) NOT NULL COMMENT '토스페이먼츠 시크릿 키 (암호화)',
    
    -- 정산 설정
    settlement_cycle VARCHAR(20) NOT NULL DEFAULT 'DAILY' COMMENT '정산 주기 (DAILY, WEEKLY, MONTHLY)',
    settlement_day_of_week INT NULL COMMENT '정산 요일 (주간 정산 시, 1=월요일)',
    settlement_day_of_month INT NULL COMMENT '정산일 (월간 정산 시, 1-31)',
    
    -- 수수료 설정
    commission_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0300 COMMENT '수수료율 (기본 3%)',
    min_commission_amount DECIMAL(10,2) NOT NULL DEFAULT 100.00 COMMENT '최소 수수료 금액',
    
    -- 계좌 정보
    bank_code VARCHAR(10) NOT NULL COMMENT '은행 코드',
    account_number VARCHAR(50) NOT NULL COMMENT '계좌번호 (암호화)',
    account_holder VARCHAR(100) NOT NULL COMMENT '예금주명',
    
    -- 상태 관리
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태 (ACTIVE, INACTIVE, SUSPENDED)',
    
    -- 메타데이터
    metadata JSON NULL COMMENT '추가 메타데이터 (JSON 형태)',
    
    -- 감사 필드
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM' COMMENT '생성자',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM' COMMENT '수정자',
    
    -- 기본 키 제약조건
    PRIMARY KEY (seller_id),
    
    -- 유니크 제약조건
    UNIQUE KEY uk_toss_sellers_store_id (store_id),
    UNIQUE KEY uk_toss_sellers_account (toss_account_id),
    
    -- 체크 제약조건
    CONSTRAINT chk_toss_sellers_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    CONSTRAINT chk_toss_sellers_cycle CHECK (settlement_cycle IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    CONSTRAINT chk_toss_sellers_commission_rate CHECK (commission_rate >= 0 AND commission_rate <= 1),
    CONSTRAINT chk_toss_sellers_min_commission CHECK (min_commission_amount >= 0),
    CONSTRAINT chk_toss_sellers_day_of_week CHECK (settlement_day_of_week IS NULL OR (settlement_day_of_week >= 1 AND settlement_day_of_week <= 7)),
    CONSTRAINT chk_toss_sellers_day_of_month CHECK (settlement_day_of_month IS NULL OR (settlement_day_of_month >= 1 AND settlement_day_of_month <= 31))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='토스페이먼츠 판매자 정보';

-- 2. 정산 테이블 생성
-- 개별 정산 요청과 처리 상태를 관리하는 메인 테이블
CREATE TABLE settlements (
    -- 기본 키: 정산 ID (UUID)
    settlement_id VARCHAR(36) NOT NULL COMMENT '정산 고유 ID (UUID)',
    
    -- 관련 정보
    store_id VARCHAR(100) NOT NULL COMMENT '가게 ID',
    order_id VARCHAR(100) NOT NULL COMMENT '주문 ID',
    seller_id VARCHAR(100) NOT NULL COMMENT '토스페이먼츠 판매자 ID (외래키)',
    
    -- 금액 정보 (모든 금액은 원화 기준)
    original_amount DECIMAL(12,2) NOT NULL COMMENT '원본 결제 금액',
    commission_amount DECIMAL(12,2) NOT NULL COMMENT '수수료 금액',
    settlement_amount DECIMAL(12,2) NOT NULL COMMENT '실제 정산 금액 (원본 - 수수료)',
    
    -- 상태 관리
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '정산 상태',
    
    -- 토스페이먼츠 관련 정보
    toss_payout_id VARCHAR(100) NULL COMMENT '토스페이먼츠 지급대행 ID',
    toss_transaction_id VARCHAR(100) NULL COMMENT '토스페이먼츠 거래 ID',
    toss_status VARCHAR(50) NULL COMMENT '토스페이먼츠 처리 상태',
    
    -- 처리 시간 정보
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '정산 요청 시간',
    processed_at TIMESTAMP NULL COMMENT '정산 처리 완료 시간',
    
    -- 오류 정보
    error_code VARCHAR(50) NULL COMMENT '오류 코드',
    error_message TEXT NULL COMMENT '오류 메시지',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '재시도 횟수',
    
    -- 메타데이터
    metadata JSON NULL COMMENT '추가 메타데이터 (결제 방법, 특이사항 등)',
    
    -- 감사 필드
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM' COMMENT '생성자',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM' COMMENT '수정자',
    
    -- 기본 키 제약조건
    PRIMARY KEY (settlement_id),
    
    -- 외래키 제약조건
    CONSTRAINT fk_settlements_seller_id 
        FOREIGN KEY (seller_id) REFERENCES toss_sellers(seller_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    
    -- 유니크 제약조건 (동일 주문에 대한 중복 정산 방지)
    UNIQUE KEY uk_settlements_order_id (order_id),
    UNIQUE KEY uk_settlements_toss_payout_id (toss_payout_id),
    
    -- 체크 제약조건
    CONSTRAINT chk_settlements_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_settlements_amounts CHECK (
        original_amount > 0 AND 
        commission_amount >= 0 AND 
        settlement_amount >= 0 AND
        settlement_amount = original_amount - commission_amount
    ),
    CONSTRAINT chk_settlements_retry_count CHECK (retry_count >= 0 AND retry_count <= 10),
    CONSTRAINT chk_settlements_processed_at CHECK (
        (status IN ('COMPLETED', 'FAILED') AND processed_at IS NOT NULL) OR
        (status NOT IN ('COMPLETED', 'FAILED') AND processed_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='정산 정보';

-- 3. 정산 이력 테이블 생성 (선택사항 - 감사 로그용)
-- 정산 상태 변경 이력을 추적하기 위한 테이블
CREATE TABLE settlement_history (
    -- 기본 키
    history_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '이력 ID',
    
    -- 정산 정보
    settlement_id VARCHAR(36) NOT NULL COMMENT '정산 ID (외래키)',
    
    -- 상태 변경 정보
    previous_status VARCHAR(20) NULL COMMENT '이전 상태',
    current_status VARCHAR(20) NOT NULL COMMENT '현재 상태',
    
    -- 변경 사유
    change_reason VARCHAR(200) NULL COMMENT '상태 변경 사유',
    change_details TEXT NULL COMMENT '상태 변경 상세 내용',
    
    -- 감사 필드
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM' COMMENT '생성자',
    
    -- 기본 키 제약조건
    PRIMARY KEY (history_id),
    
    -- 외래키 제약조건
    CONSTRAINT fk_settlement_history_settlement_id 
        FOREIGN KEY (settlement_id) REFERENCES settlements(settlement_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    
    -- 인덱스 (조회 성능 최적화)
    INDEX idx_settlement_history_settlement_id (settlement_id),
    INDEX idx_settlement_history_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='정산 상태 변경 이력';

-- 4. 기본 데이터 삽입
-- 개발 및 테스트용 기본 데이터 생성

-- 샘플 토스페이먼츠 판매자 데이터 삽입
INSERT INTO toss_sellers (
    seller_id, store_id, store_name, toss_account_id, toss_secret_key,
    settlement_cycle, commission_rate, min_commission_amount,
    bank_code, account_number, account_holder, status,
    created_by, updated_by
) VALUES 
(
    'toss_seller_001', 'store_001', '강남역 짐보관소', 'toss_account_001', 
    'encrypted_secret_key_001', 'DAILY', 0.0300, 100.00,
    '004', 'encrypted_account_001', '김철수', 'ACTIVE',
    'MIGRATION_SCRIPT', 'MIGRATION_SCRIPT'
),
(
    'toss_seller_002', 'store_002', '홍대입구 짐보관소', 'toss_account_002', 
    'encrypted_secret_key_002', 'WEEKLY', 0.0250, 150.00,
    '011', 'encrypted_account_002', '이영희', 'ACTIVE',
    'MIGRATION_SCRIPT', 'MIGRATION_SCRIPT'
),
(
    'toss_seller_003', 'store_003', '명동 짐보관소', 'toss_account_003', 
    'encrypted_secret_key_003', 'MONTHLY', 0.0350, 200.00,
    '020', 'encrypted_account_003', '박민수', 'ACTIVE',
    'MIGRATION_SCRIPT', 'MIGRATION_SCRIPT'
);

-- 샘플 정산 데이터 삽입 (테스트용)
INSERT INTO settlements (
    settlement_id, store_id, order_id, seller_id,
    original_amount, commission_amount, settlement_amount,
    status, requested_at, created_by, updated_by
) VALUES 
(
    UUID(), 'store_001', 'order_001', 'toss_seller_001',
    10000.00, 300.00, 9700.00,
    'PENDING', NOW(), 'MIGRATION_SCRIPT', 'MIGRATION_SCRIPT'
),
(
    UUID(), 'store_002', 'order_002', 'toss_seller_002',
    25000.00, 625.00, 24375.00,
    'COMPLETED', DATE_SUB(NOW(), INTERVAL 1 DAY), 'MIGRATION_SCRIPT', 'MIGRATION_SCRIPT'
),
(
    UUID(), 'store_003', 'order_003', 'toss_seller_003',
    50000.00, 1750.00, 48250.00,
    'PROCESSING', DATE_SUB(NOW(), INTERVAL 2 HOUR), 'MIGRATION_SCRIPT', 'MIGRATION_SCRIPT'
);

-- 5. 트리거 생성 (정산 상태 변경 시 이력 자동 생성)
DELIMITER //

CREATE TRIGGER tr_settlements_status_change
    AFTER UPDATE ON settlements
    FOR EACH ROW
BEGIN
    -- 상태가 변경된 경우에만 이력 생성
    IF OLD.status != NEW.status THEN
        INSERT INTO settlement_history (
            settlement_id, previous_status, current_status,
            change_reason, created_by
        ) VALUES (
            NEW.settlement_id, OLD.status, NEW.status,
            CONCAT('Status changed from ', OLD.status, ' to ', NEW.status),
            NEW.updated_by
        );
    END IF;
END//

DELIMITER ;

-- 스크립트 실행 완료 로그
SELECT 'V1__Create_settlement_tables.sql 실행 완료' AS migration_status,
       NOW() AS completed_at,
       (SELECT COUNT(*) FROM toss_sellers) AS toss_sellers_count,
       (SELECT COUNT(*) FROM settlements) AS settlements_count;
