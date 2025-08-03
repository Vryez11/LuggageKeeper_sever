-- =============================================
-- 정산 시스템 데이터베이스 성능 및 안정성 검증 스크립트
-- Version: 3.0
-- Author: Settlement System
-- Created: 2024-01-01
-- Description: 대용량 데이터 처리, 동시성, 무결성, 백업/복구 검증
-- =============================================

-- 1. 대용량 테스트 데이터 생성 프로시저
-- 성능 테스트를 위한 대량의 샘플 데이터 생성

DELIMITER //

-- 1.1 대용량 정산 데이터 생성 프로시저
CREATE PROCEDURE sp_generate_settlement_test_data(
    IN p_store_count INT DEFAULT 10,           -- 생성할 가게 수
    IN p_settlements_per_store INT DEFAULT 1000, -- 가게당 정산 건수
    IN p_days_back INT DEFAULT 90              -- 과거 며칠간의 데이터 생성
)
BEGIN
    DECLARE v_counter INT DEFAULT 0;
    DECLARE v_store_counter INT DEFAULT 0;
    DECLARE v_current_store_id VARCHAR(100);
    DECLARE v_current_seller_id VARCHAR(100);
    DECLARE v_random_date TIMESTAMP;
    DECLARE v_random_amount DECIMAL(12,2);
    DECLARE v_commission DECIMAL(12,2);
    DECLARE v_settlement DECIMAL(12,2);
    DECLARE v_random_status VARCHAR(20);
    DECLARE v_status_array TEXT DEFAULT 'PENDING,PROCESSING,COMPLETED,FAILED';
    DECLARE v_status_count INT DEFAULT 4;
    
    -- 임시 테이블 생성 (성능 최적화)
    CREATE TEMPORARY TABLE temp_bulk_settlements (
        settlement_id VARCHAR(36),
        store_id VARCHAR(100),
        order_id VARCHAR(100),
        seller_id VARCHAR(100),
        original_amount DECIMAL(12,2),
        commission_amount DECIMAL(12,2),
        settlement_amount DECIMAL(12,2),
        status VARCHAR(20),
        requested_at TIMESTAMP,
        processed_at TIMESTAMP,
        created_at TIMESTAMP,
        updated_at TIMESTAMP
    );
    
    -- 가게별 데이터 생성
    WHILE v_store_counter < p_store_count DO
        SET v_current_store_id = CONCAT('test_store_', LPAD(v_store_counter + 1, 3, '0'));
        SET v_current_seller_id = CONCAT('test_seller_', LPAD(v_store_counter + 1, 3, '0'));
        
        -- 테스트용 판매자 데이터 생성 (존재하지 않는 경우)
        INSERT IGNORE INTO toss_sellers (
            seller_id, store_id, store_name, toss_account_id, toss_secret_key,
            settlement_cycle, commission_rate, min_commission_amount,
            bank_code, account_number, account_holder, status
        ) VALUES (
            v_current_seller_id, v_current_store_id, 
            CONCAT('테스트 가게 ', v_store_counter + 1),
            CONCAT('test_account_', v_store_counter + 1),
            'encrypted_test_key', 'DAILY', 0.0300, 100.00,
            '004', 'encrypted_test_account', '테스트 사용자', 'ACTIVE'
        );
        
        -- 가게별 정산 데이터 생성
        SET v_counter = 0;
        WHILE v_counter < p_settlements_per_store DO
            -- 랜덤 값 생성
            SET v_random_date = DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * p_days_back) DAY);
            SET v_random_date = DATE_ADD(v_random_date, INTERVAL FLOOR(RAND() * 24) HOUR);
            SET v_random_date = DATE_ADD(v_random_date, INTERVAL FLOOR(RAND() * 60) MINUTE);
            
            SET v_random_amount = ROUND(1000 + (RAND() * 99000), 2); -- 1,000 ~ 100,000원
            SET v_commission = ROUND(v_random_amount * 0.03, 2);
            SET v_settlement = v_random_amount - v_commission;
            
            -- 상태 랜덤 선택 (80% COMPLETED, 15% PENDING, 4% PROCESSING, 1% FAILED)
            CASE 
                WHEN RAND() < 0.80 THEN SET v_random_status = 'COMPLETED';
                WHEN RAND() < 0.95 THEN SET v_random_status = 'PENDING';
                WHEN RAND() < 0.99 THEN SET v_random_status = 'PROCESSING';
                ELSE SET v_random_status = 'FAILED';
            END CASE;
            
            -- 임시 테이블에 데이터 삽입
            INSERT INTO temp_bulk_settlements VALUES (
                UUID(),
                v_current_store_id,
                CONCAT('test_order_', v_store_counter, '_', v_counter),
                v_current_seller_id,
                v_random_amount,
                v_commission,
                v_settlement,
                v_random_status,
                v_random_date,
                CASE WHEN v_random_status IN ('COMPLETED', 'FAILED') 
                     THEN DATE_ADD(v_random_date, INTERVAL FLOOR(RAND() * 60) MINUTE)
                     ELSE NULL END,
                v_random_date,
                v_random_date
            );
            
            SET v_counter = v_counter + 1;
        END WHILE;
        
        SET v_store_counter = v_store_counter + 1;
    END WHILE;
    
    -- 벌크 인서트 실행
    INSERT INTO settlements (
        settlement_id, store_id, order_id, seller_id,
        original_amount, commission_amount, settlement_amount,
        status, requested_at, processed_at, created_at, updated_at
    )
    SELECT 
        settlement_id, store_id, order_id, seller_id,
        original_amount, commission_amount, settlement_amount,
        status, requested_at, processed_at, created_at, updated_at
    FROM temp_bulk_settlements;
    
    -- 임시 테이블 삭제
    DROP TEMPORARY TABLE temp_bulk_settlements;
    
    -- 결과 출력
    SELECT 
        CONCAT('테스트 데이터 생성 완료: ', p_store_count, '개 가게, ', 
               p_store_count * p_settlements_per_store, '건 정산') AS result,
        NOW() AS completed_at;
        
END//

-- 1.2 성능 테스트 실행 프로시저
CREATE PROCEDURE sp_run_performance_tests()
BEGIN
    DECLARE v_start_time TIMESTAMP;
    DECLARE v_end_time TIMESTAMP;
    DECLARE v_duration_ms INT;
    
    -- 성능 테스트 결과 저장 테이블 생성
    CREATE TEMPORARY TABLE temp_performance_results (
        test_name VARCHAR(100),
        query_description TEXT,
        execution_time_ms INT,
        rows_affected INT,
        executed_at TIMESTAMP
    );
    
    -- 테스트 1: 가게별 최신 정산 내역 조회 (페이징)
    SET v_start_time = NOW(6);
    SELECT COUNT(*) INTO @row_count FROM settlements 
    WHERE store_id = 'test_store_001' 
    ORDER BY created_at DESC 
    LIMIT 20;
    SET v_end_time = NOW(6);
    SET v_duration_ms = TIMESTAMPDIFF(MICROSECOND, v_start_time, v_end_time) / 1000;
    
    INSERT INTO temp_performance_results VALUES (
        'pagination_query', '가게별 최신 정산 내역 페이징 조회', 
        v_duration_ms, @row_count, NOW()
    );
    
    -- 테스트 2: 상태별 정산 통계 조회
    SET v_start_time = NOW(6);
    SELECT COUNT(*) INTO @row_count FROM (
        SELECT status, COUNT(*), SUM(settlement_amount) 
        FROM settlements 
        WHERE created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
        GROUP BY status
    ) t;
    SET v_end_time = NOW(6);
    SET v_duration_ms = TIMESTAMPDIFF(MICROSECOND, v_start_time, v_end_time) / 1000;
    
    INSERT INTO temp_performance_results VALUES (
        'status_aggregation', '상태별 정산 통계 집계', 
        v_duration_ms, @row_count, NOW()
    );
    
    -- 테스트 3: 가게별 일별 정산 집계
    SET v_start_time = NOW(6);
    SELECT COUNT(*) INTO @row_count FROM (
        SELECT 
            store_id,
            DATE(created_at) as settlement_date,
            COUNT(*) as settlement_count,
            SUM(settlement_amount) as total_amount
        FROM settlements 
        WHERE store_id = 'test_store_001' 
          AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
        GROUP BY store_id, DATE(created_at)
    ) t;
    SET v_end_time = NOW(6);
    SET v_duration_ms = TIMESTAMPDIFF(MICROSECOND, v_start_time, v_end_time) / 1000;
    
    INSERT INTO temp_performance_results VALUES (
        'daily_aggregation', '가게별 일별 정산 집계', 
        v_duration_ms, @row_count, NOW()
    );
    
    -- 테스트 4: 복잡한 조인 쿼리
    SET v_start_time = NOW(6);
    SELECT COUNT(*) INTO @row_count FROM settlements s
    INNER JOIN toss_sellers ts ON s.seller_id = ts.seller_id
    WHERE s.status = 'COMPLETED' 
      AND ts.status = 'ACTIVE'
      AND s.created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY);
    SET v_end_time = NOW(6);
    SET v_duration_ms = TIMESTAMPDIFF(MICROSECOND, v_start_time, v_end_time) / 1000;
    
    INSERT INTO temp_performance_results VALUES (
        'complex_join', '정산-판매자 조인 쿼리', 
        v_duration_ms, @row_count, NOW()
    );
    
    -- 결과 출력
    SELECT * FROM temp_performance_results ORDER BY execution_time_ms DESC;
    
    DROP TEMPORARY TABLE temp_performance_results;
    
END//

-- 1.3 동시성 테스트 프로시저
CREATE PROCEDURE sp_test_concurrency()
BEGIN
    DECLARE v_test_settlement_id VARCHAR(36);
    DECLARE v_original_status VARCHAR(20);
    DECLARE v_final_status VARCHAR(20);
    
    -- 테스트용 정산 데이터 생성
    SET v_test_settlement_id = UUID();
    INSERT INTO settlements (
        settlement_id, store_id, order_id, seller_id,
        original_amount, commission_amount, settlement_amount,
        status
    ) VALUES (
        v_test_settlement_id, 'test_store_001', CONCAT('concurrency_test_', UNIX_TIMESTAMP()),
        'test_seller_001', 10000.00, 300.00, 9700.00, 'PENDING'
    );
    
    -- 동시성 테스트 시뮬레이션
    START TRANSACTION;
    
    -- 1. 정산 상태 조회 (FOR UPDATE 락)
    SELECT status INTO v_original_status 
    FROM settlements 
    WHERE settlement_id = v_test_settlement_id 
    FOR UPDATE;
    
    -- 2. 비즈니스 로직 시뮬레이션 (1초 대기)
    SELECT SLEEP(1);
    
    -- 3. 상태 업데이트
    UPDATE settlements 
    SET status = 'PROCESSING', 
        updated_at = NOW() 
    WHERE settlement_id = v_test_settlement_id;
    
    COMMIT;
    
    -- 결과 확인
    SELECT status INTO v_final_status 
    FROM settlements 
    WHERE settlement_id = v_test_settlement_id;
    
    SELECT 
        '동시성 테스트 완료' AS test_result,
        v_test_settlement_id AS settlement_id,
        v_original_status AS original_status,
        v_final_status AS final_status,
        NOW() AS completed_at;
        
END//

DELIMITER ;

-- 2. 데이터 무결성 검증 스크립트

-- 2.1 외래키 제약조건 검증
CREATE OR REPLACE VIEW v_data_integrity_check AS
SELECT 
    'settlements_seller_fk' AS check_name,
    COUNT(*) AS violation_count,
    'settlements 테이블의 seller_id가 toss_sellers에 존재하지 않음' AS description
FROM settlements s
LEFT JOIN toss_sellers ts ON s.seller_id = ts.seller_id
WHERE ts.seller_id IS NULL

UNION ALL

SELECT 
    'settlement_amounts_consistency' AS check_name,
    COUNT(*) AS violation_count,
    '정산 금액 계산 오류 (original_amount - commission_amount != settlement_amount)' AS description
FROM settlements 
WHERE ABS(original_amount - commission_amount - settlement_amount) > 0.01

UNION ALL

SELECT 
    'settlement_status_processed_at' AS check_name,
    COUNT(*) AS violation_count,
    '완료/실패 상태인데 processed_at이 NULL인 경우' AS description
FROM settlements 
WHERE status IN ('COMPLETED', 'FAILED') AND processed_at IS NULL

UNION ALL

SELECT 
    'settlement_duplicate_orders' AS check_name,
    COUNT(*) - COUNT(DISTINCT order_id) AS violation_count,
    '중복된 order_id 존재' AS description
FROM settlements;

-- 2.2 비즈니스 규칙 검증
CREATE OR REPLACE VIEW v_business_rule_violations AS
SELECT 
    'negative_amounts' AS rule_name,
    COUNT(*) AS violation_count,
    '음수 금액 존재' AS description
FROM settlements 
WHERE original_amount < 0 OR commission_amount < 0 OR settlement_amount < 0

UNION ALL

SELECT 
    'excessive_retry_count' AS rule_name,
    COUNT(*) AS violation_count,
    '재시도 횟수 초과 (10회 이상)' AS description
FROM settlements 
WHERE retry_count > 10

UNION ALL

SELECT 
    'invalid_commission_rate' AS rule_name,
    COUNT(*) AS violation_count,
    '비정상적인 수수료율 (0% 미만 또는 100% 초과)' AS description
FROM toss_sellers 
WHERE commission_rate < 0 OR commission_rate > 1;

-- 3. 백업 및 복구 검증 스크립트

-- 3.1 백업 전 데이터 체크섬 생성
CREATE OR REPLACE VIEW v_data_checksum AS
SELECT 
    'settlements' AS table_name,
    COUNT(*) AS row_count,
    COALESCE(SUM(CRC32(CONCAT(settlement_id, store_id, original_amount, status))), 0) AS checksum,
    NOW() AS calculated_at
FROM settlements

UNION ALL

SELECT 
    'toss_sellers' AS table_name,
    COUNT(*) AS row_count,
    COALESCE(SUM(CRC32(CONCAT(seller_id, store_id, commission_rate, status))), 0) AS checksum,
    NOW() AS calculated_at
FROM toss_sellers

UNION ALL

SELECT 
    'settlement_history' AS table_name,
    COUNT(*) AS row_count,
    COALESCE(SUM(CRC32(CONCAT(history_id, settlement_id, current_status))), 0) AS checksum,
    NOW() AS calculated_at
FROM settlement_history;

-- 4. 모니터링 및 알림을 위한 뷰

-- 4.1 시스템 상태 모니터링 뷰
CREATE OR REPLACE VIEW v_system_health_monitor AS
SELECT 
    'pending_settlements' AS metric_name,
    COUNT(*) AS metric_value,
    '대기 중인 정산 건수' AS description,
    CASE WHEN COUNT(*) > 1000 THEN 'WARNING' ELSE 'OK' END AS status
FROM settlements WHERE status = 'PENDING'

UNION ALL

SELECT 
    'failed_settlements_24h' AS metric_name,
    COUNT(*) AS metric_value,
    '24시간 내 실패한 정산 건수' AS description,
    CASE WHEN COUNT(*) > 100 THEN 'CRITICAL' ELSE 'OK' END AS status
FROM settlements 
WHERE status = 'FAILED' AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)

UNION ALL

SELECT 
    'processing_time_avg_minutes' AS metric_name,
    COALESCE(AVG(TIMESTAMPDIFF(MINUTE, requested_at, processed_at)), 0) AS metric_value,
    '평균 정산 처리 시간 (분)' AS description,
    CASE WHEN AVG(TIMESTAMPDIFF(MINUTE, requested_at, processed_at)) > 60 THEN 'WARNING' ELSE 'OK' END AS status
FROM settlements 
WHERE status = 'COMPLETED' AND processed_at IS NOT NULL
  AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR);

-- 4.2 성능 지표 모니터링 뷰
CREATE OR REPLACE VIEW v_performance_metrics AS
SELECT 
    'daily_settlement_volume' AS metric_name,
    COUNT(*) AS metric_value,
    SUM(settlement_amount) AS total_amount,
    '일일 정산 처리량' AS description
FROM settlements 
WHERE DATE(created_at) = CURDATE()

UNION ALL

SELECT 
    'hourly_settlement_rate' AS metric_name,
    COUNT(*) AS metric_value,
    SUM(settlement_amount) AS total_amount,
    '시간당 정산 처리율' AS description
FROM settlements 
WHERE created_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR);

-- 5. 정기 메인테넌스 스크립트

-- 5.1 오래된 테스트 데이터 정리 프로시저
DELIMITER //

CREATE PROCEDURE sp_cleanup_test_data(
    IN p_days_to_keep INT DEFAULT 30
)
BEGIN
    DECLARE v_deleted_settlements INT DEFAULT 0;
    DECLARE v_deleted_sellers INT DEFAULT 0;
    
    -- 오래된 테스트 정산 데이터 삭제
    DELETE FROM settlements 
    WHERE store_id LIKE 'test_store_%' 
      AND created_at < DATE_SUB(NOW(), INTERVAL p_days_to_keep DAY);
    
    SET v_deleted_settlements = ROW_COUNT();
    
    -- 관련 없는 테스트 판매자 데이터 삭제
    DELETE ts FROM toss_sellers ts
    LEFT JOIN settlements s ON ts.seller_id = s.seller_id
    WHERE ts.seller_id LIKE 'test_seller_%' 
      AND s.seller_id IS NULL;
    
    SET v_deleted_sellers = ROW_COUNT();
    
    SELECT 
        '테스트 데이터 정리 완료' AS result,
        v_deleted_settlements AS deleted_settlements,
        v_deleted_sellers AS deleted_sellers,
        NOW() AS completed_at;
        
END//

DELIMITER ;

-- 스크립트 실행 완료 로그
SELECT 'V3__Database_validation_tests.sql 실행 완료' AS migration_status,
       NOW() AS completed_at,
       '성능 테스트, 무결성 검증, 모니터링 뷰 생성 완료' AS details;
