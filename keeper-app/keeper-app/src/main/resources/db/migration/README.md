# 정산 시스템 데이터베이스 마이그레이션 가이드

## 📋 개요

이 디렉토리는 정산 시스템의 데이터베이스 스키마, 인덱스, 그리고 성능 최적화를 위한 마이그레이션 스크립트들을 포함합니다.

## 🗂️ 파일 구조

```
db/migration/
├── V1__Create_settlement_tables.sql      # 기본 스키마 생성
├── V2__Create_performance_indexes.sql    # 성능 최적화 인덱스
├── V3__Database_validation_tests.sql     # 검증 및 테스트 스크립트
├── run_migrations.sql                    # 마이그레이션 실행 스크립트
└── README.md                            # 이 파일
```

## 🚀 마이그레이션 실행 방법

### 1. 전체 마이그레이션 실행
```sql
-- MySQL 클라이언트에서 실행
SOURCE /path/to/db/migration/run_migrations.sql;
```

### 2. 개별 마이그레이션 실행
```sql
-- V1: 기본 스키마 생성
SOURCE V1__Create_settlement_tables.sql;

-- V2: 성능 인덱스 생성
SOURCE V2__Create_performance_indexes.sql;

-- V3: 검증 스크립트 실행
SOURCE V3__Database_validation_tests.sql;
```

## 📊 데이터베이스 스키마

### 주요 테이블

#### 1. `toss_sellers` - 토스페이먼츠 판매자 정보
```sql
- seller_id (PK): 토스페이먼츠 판매자 ID
- store_id: 내부 가게 ID
- store_name: 가게명
- toss_account_id: 토스페이먼츠 계정 ID
- settlement_cycle: 정산 주기 (DAILY/WEEKLY/MONTHLY)
- commission_rate: 수수료율
- bank_code, account_number: 계좌 정보
- status: 상태 (ACTIVE/INACTIVE/SUSPENDED)
```

#### 2. `settlements` - 정산 정보
```sql
- settlement_id (PK): 정산 고유 ID (UUID)
- store_id: 가게 ID
- order_id: 주문 ID
- seller_id (FK): 판매자 ID
- original_amount: 원본 결제 금액
- commission_amount: 수수료 금액
- settlement_amount: 실제 정산 금액
- status: 정산 상태 (PENDING/PROCESSING/COMPLETED/FAILED/CANCELLED)
- toss_payout_id: 토스페이먼츠 지급대행 ID
- requested_at, processed_at: 처리 시간
- error_code, error_message: 오류 정보
- retry_count: 재시도 횟수
```

#### 3. `settlement_history` - 정산 상태 변경 이력
```sql
- history_id (PK): 이력 ID
- settlement_id (FK): 정산 ID
- previous_status, current_status: 상태 변경 정보
- change_reason: 변경 사유
- created_at, created_by: 감사 정보
```

### 제약조건

#### 외래키 제약조건
- `settlements.seller_id` → `toss_sellers.seller_id`
- `settlement_history.settlement_id` → `settlements.settlement_id`

#### 체크 제약조건
- 정산 상태 유효성 검증
- 금액 계산 일관성 검증 (original_amount - commission_amount = settlement_amount)
- 재시도 횟수 제한 (0-10회)
- 처리 완료 시간 일관성 검증

#### 유니크 제약조건
- 주문별 중복 정산 방지 (`settlements.order_id`)
- 토스페이먼츠 지급대행 ID 중복 방지 (`settlements.toss_payout_id`)

## 🔍 성능 최적화 인덱스

### 단일 컬럼 인덱스
- `idx_settlements_store_id`: 가게별 조회
- `idx_settlements_status`: 상태별 필터링
- `idx_settlements_created_at`: 날짜 범위 검색
- `idx_settlements_requested_at`: 정산 요청 시간 검색
- `idx_settlements_processed_at`: 정산 완료 시간 검색

### 복합 인덱스
- `idx_settlements_store_created`: 가게별 + 생성일시 (메인 대시보드)
- `idx_settlements_store_status`: 가게별 + 상태 필터링
- `idx_settlements_status_created`: 상태별 + 생성일시 (관리자 모니터링)
- `idx_settlements_store_status_created`: 복합 필터링
- `idx_settlements_requested_status`: 배치 작업용

### 커버링 인덱스
- `idx_settlements_dashboard_covering`: 대시보드 요약 정보 최적화

### 함수 기반 인덱스
- `idx_settlements_date_aggregation`: 일별 집계 최적화
- `idx_settlements_month_aggregation`: 월별 집계 최적화

## 🧪 테스트 및 검증

### 성능 테스트 프로시저

#### 1. 대용량 테스트 데이터 생성
```sql
CALL sp_generate_settlement_test_data(
    10,    -- 가게 수
    1000,  -- 가게당 정산 건수
    90     -- 과거 며칠간 데이터
);
```

#### 2. 성능 테스트 실행
```sql
CALL sp_run_performance_tests();
```

#### 3. 동시성 테스트
```sql
CALL sp_test_concurrency();
```

### 데이터 무결성 검증

#### 무결성 체크 뷰
```sql
-- 데이터 무결성 위반 확인
SELECT * FROM v_data_integrity_check WHERE violation_count > 0;

-- 비즈니스 규칙 위반 확인
SELECT * FROM v_business_rule_violations WHERE violation_count > 0;
```

### 시스템 모니터링

#### 시스템 상태 모니터링
```sql
-- 시스템 건강 상태 확인
SELECT * FROM v_system_health_monitor;

-- 성능 지표 확인
SELECT * FROM v_performance_metrics;
```

#### 인덱스 사용 통계
```sql
-- 인덱스 효율성 모니터링
SELECT * FROM v_settlement_index_usage;
```

## 🛠️ 유지보수

### 정기 메인테넌스

#### 1. 테스트 데이터 정리
```sql
-- 30일 이전 테스트 데이터 삭제
CALL sp_cleanup_test_data(30);
```

#### 2. 인덱스 최적화
```sql
-- 인덱스 통계 업데이트
ANALYZE TABLE settlements, toss_sellers, settlement_history;

-- 인덱스 재구성 (필요시)
OPTIMIZE TABLE settlements, toss_sellers, settlement_history;
```

### 백업 및 복구

#### 백업 전 체크섬 생성
```sql
-- 데이터 체크섬 확인
SELECT * FROM v_data_checksum;
```

#### 백업 명령어 예시
```bash
# 전체 데이터베이스 백업
mysqldump -u username -p database_name > settlement_backup_$(date +%Y%m%d).sql

# 스키마만 백업
mysqldump -u username -p --no-data database_name > settlement_schema_$(date +%Y%m%d).sql

# 데이터만 백업
mysqldump -u username -p --no-create-info database_name > settlement_data_$(date +%Y%m%d).sql
```

## 📈 성능 최적화 가이드

### 쿼리 최적화 팁

#### 1. 인덱스 힌트 사용
```sql
-- 특정 인덱스 강제 사용
SELECT * FROM settlements USE INDEX (idx_settlements_store_status_created)
WHERE store_id = 'store_001' AND status = 'COMPLETED'
ORDER BY created_at DESC;
```

#### 2. 페이징 최적화
```sql
-- 효율적인 페이징 쿼리
SELECT * FROM settlements 
WHERE store_id = 'store_001' 
  AND created_at < '2024-01-01 00:00:00'  -- 커서 기반 페이징
ORDER BY created_at DESC 
LIMIT 20;
```

#### 3. 집계 쿼리 최적화
```sql
-- 인덱스를 활용한 집계
SELECT 
    DATE(created_at) as settlement_date,
    COUNT(*) as settlement_count,
    SUM(settlement_amount) as total_amount
FROM settlements USE INDEX (idx_settlements_date_aggregation)
WHERE store_id = 'store_001' 
  AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
GROUP BY DATE(created_at)
ORDER BY settlement_date DESC;
```

## 🚨 알림 및 모니터링

### 주요 모니터링 지표

1. **대기 중인 정산 건수** (임계값: 1,000건)
2. **24시간 내 실패한 정산 건수** (임계값: 100건)
3. **평균 정산 처리 시간** (임계값: 60분)
4. **일일 정산 처리량**
5. **시간당 정산 처리율**

### 알림 설정 예시

```sql
-- 위험 상황 감지 쿼리
SELECT 
    metric_name,
    metric_value,
    description,
    status
FROM v_system_health_monitor 
WHERE status IN ('WARNING', 'CRITICAL');
```

## 🔧 문제 해결

### 일반적인 문제와 해결방법

#### 1. 마이그레이션 실패
```sql
-- 마이그레이션 로그 확인
SELECT * FROM migration_log WHERE status = 'FAILED';

-- 롤백 후 재실행
-- (각 마이그레이션 스크립트는 멱등성을 보장함)
```

#### 2. 성능 저하
```sql
-- 느린 쿼리 확인
SHOW PROCESSLIST;

-- 인덱스 사용률 확인
SELECT * FROM v_settlement_index_usage;
```

#### 3. 데이터 무결성 위반
```sql
-- 무결성 위반 상세 확인
SELECT * FROM v_data_integrity_check WHERE violation_count > 0;
SELECT * FROM v_business_rule_violations WHERE violation_count > 0;
```

## 📞 지원

문제가 발생하거나 추가 정보가 필요한 경우:

1. **데이터베이스 로그 확인**: `migration_log` 테이블
2. **시스템 상태 확인**: `v_system_health_monitor` 뷰
3. **성능 지표 확인**: `v_performance_metrics` 뷰
4. **무결성 검증**: `v_data_integrity_check` 뷰

---

**마지막 업데이트**: 2024-01-01  
**버전**: 1.0  
**작성자**: Settlement System Team
