-- =============================================
-- 정산 시스템 성능 최적화 인덱스 생성 스크립트
-- Version: 2.0
-- Author: Settlement System
-- Created: 2024-01-01
-- Description: 정산 시스템 주요 쿼리 성능 최적화를 위한 인덱스 생성
-- =============================================

-- 1. settlements 테이블 성능 최적화 인덱스
-- 가장 빈번하게 사용되는 쿼리 패턴에 따른 인덱스 설계

-- 1.1 단일 컬럼 인덱스
-- store_id 기반 조회 (가게별 정산 내역 조회)
CREATE INDEX idx_settlements_store_id 
ON settlements (store_id)
COMMENT '가게별 정산 내역 조회 최적화';

-- status 기반 조회 (상태별 정산 필터링)
CREATE INDEX idx_settlements_status 
ON settlements (status)
COMMENT '정산 상태별 필터링 최적화';

-- created_at 기간 조회 (날짜 범위 검색)
CREATE INDEX idx_settlements_created_at 
ON settlements (created_at)
COMMENT '생성일시 기간 검색 최적화';

-- requested_at 기간 조회 (정산 요청 시간 기준 검색)
CREATE INDEX idx_settlements_requested_at 
ON settlements (requested_at)
COMMENT '정산 요청 시간 기준 검색 최적화';

-- processed_at 기간 조회 (정산 완료 시간 기준 검색)
CREATE INDEX idx_settlements_processed_at 
ON settlements (processed_at)
COMMENT '정산 완료 시간 기준 검색 최적화';

-- 1.2 복합 인덱스 (가장 중요한 쿼리 패턴)
-- 가게별 + 생성일시 복합 인덱스 (가장 빈번한 조회 패턴)
CREATE INDEX idx_settlements_store_created 
ON settlements (store_id, created_at DESC)
COMMENT '가게별 최신 정산 내역 조회 최적화 (메인 대시보드)';

-- 가게별 + 상태 복합 인덱스 (가게별 상태 필터링)
CREATE INDEX idx_settlements_store_status 
ON settlements (store_id, status)
COMMENT '가게별 상태 필터링 최적화';

-- 상태별 + 생성일시 복합 인덱스 (관리자 모니터링)
CREATE INDEX idx_settlements_status_created 
ON settlements (status, created_at DESC)
COMMENT '상태별 최신 정산 모니터링 최적화';

-- 가게별 + 상태 + 생성일시 복합 인덱스 (상세 필터링)
CREATE INDEX idx_settlements_store_status_created 
ON settlements (store_id, status, created_at DESC)
COMMENT '가게별 상태 및 날짜 복합 필터링 최적화';

-- 정산 요청 시간 기준 복합 인덱스 (정산 처리 배치 작업용)
CREATE INDEX idx_settlements_requested_status 
ON settlements (requested_at, status)
COMMENT '정산 처리 배치 작업 최적화';

-- 재시도 관련 인덱스 (실패한 정산 재처리용)
CREATE INDEX idx_settlements_retry_status 
ON settlements (status, retry_count, updated_at)
COMMENT '실패 정산 재처리 최적화';

-- 1.3 커버링 인덱스 (자주 사용되는 SELECT 컬럼 포함)
-- 대시보드 요약 정보용 커버링 인덱스
CREATE INDEX idx_settlements_dashboard_covering 
ON settlements (store_id, status, created_at DESC, settlement_amount, original_amount)
COMMENT '대시보드 요약 정보 조회 커버링 인덱스';

-- 2. toss_sellers 테이블 성능 최적화 인덱스

-- store_id 기반 조회 (이미 UNIQUE 제약조건으로 인덱스 존재하지만 명시적 생성)
-- UNIQUE 제약조건이 있으므로 별도 인덱스 불필요

-- status 기반 조회 (활성 판매자 조회)
CREATE INDEX idx_toss_sellers_status 
ON toss_sellers (status)
COMMENT '판매자 상태별 조회 최적화';

-- 정산 주기별 조회 (배치 작업용)
CREATE INDEX idx_toss_sellers_settlement_cycle 
ON toss_sellers (settlement_cycle, status)
COMMENT '정산 주기별 배치 작업 최적화';

-- 3. settlement_history 테이블 성능 최적화 인덱스

-- settlement_id 기반 조회 (이미 생성됨)
-- created_at 기반 조회 (이미 생성됨)

-- 상태 변경 분석용 복합 인덱스
CREATE INDEX idx_settlement_history_status_analysis 
ON settlement_history (current_status, created_at DESC)
COMMENT '상태 변경 분석 및 모니터링 최적화';

-- 4. 함수 기반 인덱스 (MySQL 8.0+에서 지원)
-- 날짜별 집계 쿼리 최적화를 위한 함수 인덱스

-- 일별 집계용 함수 인덱스
CREATE INDEX idx_settlements_date_aggregation 
ON settlements ((DATE(created_at)), store_id, status)
COMMENT '일별 정산 집계 쿼리 최적화';

-- 월별 집계용 함수 인덱스  
CREATE INDEX idx_settlements_month_aggregation 
ON settlements ((YEAR(created_at)), (MONTH(created_at)), store_id)
COMMENT '월별 정산 집계 쿼리 최적화';

-- 5. 파티셔닝 준비 (대용량 데이터 처리 대비)
-- 실제 파티셔닝은 데이터 증가에 따라 별도 마이그레이션에서 수행

-- 파티셔닝을 위한 created_at 기준 인덱스 (이미 존재)
-- 향후 RANGE 파티셔닝 적용 시 사용

-- 6. 인덱스 사용 통계 및 모니터링을 위한 뷰 생성
-- 인덱스 효율성 모니터링을 위한 정보 뷰

CREATE OR REPLACE VIEW v_settlement_index_usage AS
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    COLUMN_NAME,
    CARDINALITY,
    SUB_PART,
    NULLABLE,
    INDEX_TYPE,
    COMMENT
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME IN ('settlements', 'toss_sellers', 'settlement_history')
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- 7. 쿼리 성능 테스트를 위한 샘플 쿼리들
-- 실제 애플리케이션에서 사용될 주요 쿼리 패턴들

-- 7.1 가게별 최신 정산 내역 조회 (페이징)
-- EXPLAIN SELECT * FROM settlements 
-- WHERE store_id = 'store_001' 
-- ORDER BY created_at DESC 
-- LIMIT 20 OFFSET 0;

-- 7.2 상태별 정산 통계 조회
-- EXPLAIN SELECT status, COUNT(*), SUM(settlement_amount) 
-- FROM settlements 
-- WHERE created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
-- GROUP BY status;

-- 7.3 가게별 일별 정산 집계
-- EXPLAIN SELECT 
--     store_id,
--     DATE(created_at) as settlement_date,
--     COUNT(*) as settlement_count,
--     SUM(settlement_amount) as total_amount
-- FROM settlements 
-- WHERE store_id = 'store_001' 
--   AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
-- GROUP BY store_id, DATE(created_at)
-- ORDER BY settlement_date DESC;

-- 7.4 실패한 정산 재처리 대상 조회
-- EXPLAIN SELECT settlement_id, retry_count, error_message
-- FROM settlements 
-- WHERE status = 'FAILED' 
--   AND retry_count < 3 
--   AND updated_at <= DATE_SUB(NOW(), INTERVAL 1 HOUR)
-- ORDER BY updated_at ASC
-- LIMIT 100;

-- 8. 인덱스 힌트 사용 예시 (필요시 애플리케이션에서 활용)
-- 복잡한 쿼리에서 옵티마이저가 잘못된 인덱스를 선택할 경우 사용

-- USE INDEX 힌트 예시:
-- SELECT * FROM settlements USE INDEX (idx_settlements_store_status_created)
-- WHERE store_id = 'store_001' AND status = 'COMPLETED'
-- ORDER BY created_at DESC;

-- FORCE INDEX 힌트 예시:
-- SELECT COUNT(*) FROM settlements FORCE INDEX (idx_settlements_status)
-- WHERE status IN ('PENDING', 'PROCESSING');

-- 9. 인덱스 메인테넌스 스크립트
-- 정기적인 인덱스 최적화를 위한 명령어들

-- 인덱스 통계 업데이트 (정기적으로 실행 권장)
-- ANALYZE TABLE settlements, toss_sellers, settlement_history;

-- 인덱스 재구성 (필요시 실행)
-- OPTIMIZE TABLE settlements, toss_sellers, settlement_history;

-- 스크립트 실행 완료 로그
SELECT 'V2__Create_performance_indexes.sql 실행 완료' AS migration_status,
       NOW() AS completed_at,
       (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
        WHERE TABLE_SCHEMA = DATABASE() 
          AND TABLE_NAME = 'settlements' 
          AND INDEX_NAME != 'PRIMARY') AS settlements_indexes_count,
       (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
        WHERE TABLE_SCHEMA = DATABASE() 
          AND TABLE_NAME = 'toss_sellers' 
          AND INDEX_NAME != 'PRIMARY') AS toss_sellers_indexes_count;
