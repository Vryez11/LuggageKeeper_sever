# Implementation Plan

## 코드 작성 가이드라인

### 주석 작성 규칙

모든 작업을 수행할 때 다음 주석 작성 규칙을 준수해야 합니다:

#### 1. 클래스 레벨 주석 (JavaDoc)

```java
/**
 * [클래스의 목적과 책임을 한 줄로 요약]
 * [상세한 설명 - 클래스가 하는 일, 사용 방법 등]
 *
 * <p>주요 기능:</p>
 * <ul>
 *   <li>기능 1 설명</li>
 *   <li>기능 2 설명</li>
 * </ul>
 *
 * <p>사용 예시:</p>
 * <pre>
 * // 코드 예시
 * </pre>
 *
 * @author 개발자명
 * @since 버전
 * @see 관련클래스1
 * @see 관련클래스2
 */
```

#### 2. 메서드 레벨 주석 (JavaDoc)

```java
/**
 * [메서드의 목적을 한 줄로 요약]
 *
 * <p>[상세한 동작 설명]</p>
 *
 * <p>처리 과정:</p>
 * <ol>
 *   <li>단계 1 설명</li>
 *   <li>단계 2 설명</li>
 *   <li>단계 3 설명</li>
 * </ol>
 *
 * @param param1 파라미터1 설명 (제약사항 포함)
 * @param param2 파라미터2 설명 (제약사항 포함)
 * @return 반환값 설명 (null 가능성 포함)
 * @throws ExceptionType1 예외 발생 조건1
 * @throws ExceptionType2 예외 발생 조건2
 * @since 버전
 */
```

#### 3. 필드 레벨 주석

```java
/**
 * [필드의 용도 설명]
 * [추가 설명 - 제약사항, 기본값, 관련 정보 등]
 *
 * <p>제약사항:</p>
 * <ul>
 *   <li>null 불가 (@NotNull)</li>
 *   <li>양수여야 함 (@Positive)</li>
 *   <li>최대 길이: 255자</li>
 * </ul>
 *
 * <p>예시 값:</p>
 * <ul>
 *   <li>"PENDING" - 대기 상태</li>
 *   <li>"COMPLETED" - 완료 상태</li>
 * </ul>
 */
@NotNull
@Column(name = "field_name")
private String fieldName;
```

#### 4. 복잡한 로직 인라인 주석

```java
public void complexMethod() {
    // 1. 전처리: 입력값 검증 및 초기화
    if (input == null) {
        throw new IllegalArgumentException("입력값이 null입니다");
    }

    // 2. 비즈니스 로직: 20% 수수료 계산
    // 공식: 수수료 = 원본금액 × 0.20
    BigDecimal fee = originalAmount.multiply(new BigDecimal("0.20"));

    // 3. 데이터 저장: 트랜잭션 내에서 안전하게 저장
    try {
        repository.save(entity);
    } catch (DataAccessException e) {
        // 4. 오류 처리: 데이터베이스 오류 시 롤백
        log.error("데이터 저장 실패: {}", e.getMessage());
        throw new ServiceException("정산 데이터 저장에 실패했습니다", e);
    }
}
```

#### 5. DTO 클래스 특별 주석 (Flutter 호환성)

```java
/**
 * [DTO 목적] API 응답용 DTO
 * Flutter json_serializable과 호환되는 구조로 설계
 *
 * <p>Flutter 연동 특징:</p>
 * <ul>
 *   <li>모든 필드가 getter/setter로 접근 가능</li>
 *   <li>기본 생성자 제공 (json_serializable 요구사항)</li>
 *   <li>LocalDateTime은 ISO 8601 형식으로 직렬화</li>
 *   <li>BigDecimal은 숫자로 직렬화 (문자열 아님)</li>
 * </ul>
 *
 * <p>JSON 예시:</p>
 * <pre>
 * {
 *   "id": "settlement-123",
 *   "amount": 10000,
 *   "status": "COMPLETED",
 *   "createdAt": "2024-01-15T10:30:00"
 * }
 * </pre>
 */
```

#### 6. Repository 인터페이스 주석

```java
/**
 * [엔티티명] 엔티티를 위한 Repository 인터페이스
 * [주요 기능 요약]
 *
 * <p>제공하는 쿼리:</p>
 * <ul>
 *   <li>기본 CRUD 작업 (JpaRepository 상속)</li>
 *   <li>커스텀 쿼리 1 설명</li>
 *   <li>커스텀 쿼리 2 설명</li>
 * </ul>
 *
 * <p>성능 고려사항:</p>
 * <ul>
 *   <li>인덱스 활용: store_id, created_at</li>
 *   <li>페이징 처리로 대용량 데이터 최적화</li>
 * </ul>
 */
@Repository
public interface EntityRepository extends JpaRepository<Entity, String> {

    /**
     * [쿼리 목적 설명]
     *
     * <p>사용 사례:</p>
     * <ul>
     *   <li>사례 1</li>
     *   <li>사례 2</li>
     * </ul>
     *
     * @param param1 파라미터 설명
     * @param pageable 페이징 정보
     * @return 쿼리 결과 설명
     */
    Page<Entity> findByCustomQuery(String param1, Pageable pageable);
}
```

#### 7. 테스트 코드 주석

```java
/**
 * [테스트 대상 클래스] 단위 테스트
 * [테스트 목적 및 범위 설명]
 */
@SpringBootTest
class EntityTest {

    @Test
    @DisplayName("[테스트 시나리오 한글 설명]")
    void testMethodName_ShouldExpectedBehavior_WhenCondition() {
        // Given: 테스트 데이터 준비
        // [준비하는 데이터와 그 이유 설명]
        Entity entity = new Entity();
        entity.setField("test-value");

        // When: 테스트 대상 메서드 실행
        // [실행하는 동작과 기대하는 결과 설명]
        Result result = service.processEntity(entity);

        // Then: 결과 검증
        // [검증하는 내용과 그 중요성 설명]
        assertThat(result.getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(result.getValue()).isEqualTo(expectedValue);
    }
}
```

### 주석 작성 체크리스트

각 작업 완료 시 다음 항목들을 확인해주세요:

#### 필수 주석

- [ ] 모든 public 클래스에 JavaDoc 주석 작성
- [ ] 모든 public 메서드에 파라미터, 반환값, 예외 설명
- [ ] 복잡한 비즈니스 로직에 단계별 인라인 주석
- [ ] 중요한 필드에 용도 및 제약사항 설명
- [ ] DTO 클래스에 Flutter 호환성 정보 포함

#### 권장 주석

- [ ] 알고리즘의 시간/공간 복잡도 명시
- [ ] 성능 고려사항 및 최적화 포인트 설명
- [ ] 외부 시스템 연동 시 주의사항 기록
- [ ] 향후 확장 가능성 및 제약사항 언급
- [ ] 테스트 시나리오 및 검증 포인트 명시

- [-] 1. 프로젝트 의존성 및 설정 추가

  - build.gradle에 토스페이먼츠 연동을 위한 JWE 암호화 라이브러리 추가
  - application.properties에 토스페이먼츠 API 설정 추가
  - _Requirements: 3.3, 7.1, 7.2_

- [ ] 2. 정산 도메인 모델 구현
- [x] 2.1 Settlement 엔티티 생성

  - Settlement 엔티티 클래스 작성 (정산 정보 저장)
  - SettlementStatus enum 정의
  - JPA 어노테이션 및 연관관계 설정
  - **주석 요구사항:**
    - 클래스 레벨: 정산 시스템의 핵심 역할, 20% 수수료 계산 로직 설명
    - 필드 레벨: 각 금액 필드의 용도와 제약사항 (precision, scale 포함)
    - 메서드 레벨: createSettlement() 팩토리 메서드의 계산 과정 단계별 설명
    - 비즈니스 로직: 수수료 계산 공식과 상태 변경 규칙 상세 주석
  - _Requirements: 1.1, 1.3_

- [x] 2.2 TossSeller 엔티티 생성

  - TossSeller 엔티티 클래스 작성 (토스 셀러 정보 저장)
  - TossBusinessType, TossSellerStatus enum 정의
  - Store와의 OneToOne 관계 설정
  - _Requirements: 3.1_

- [x] 2.3 Flutter 호환 DTO 클래스 생성

  - SettlementRequest, SettlementResponse DTO 클래스 작성
  - TossSellerRequest, TossSellerResponse DTO 클래스 작성
  - SettlementSummaryResponse DTO 클래스 작성
  - json_serializable 호환을 위한 getter/setter 및 static factory 메서드 구현
  - **주석 요구사항:**
    - 클래스 레벨: Flutter json_serializable 호환성 상세 설명
    - JSON 예시: 실제 직렬화되는 JSON 구조 예시 포함
    - 필드 레벨: Flutter에서의 타입 매핑 정보 (BigDecimal → double 등)
    - 메서드 레벨: from() 팩토리 메서드의 null 안전성 처리 설명
    - 특별 주의사항: LocalDateTime ISO 형식, boolean 필드 네이밍 규칙
  - _Requirements: 5.4, 5.5, 5.6_

- [x] 2.4 **[검증] 기본 CRUD 동작 확인**

  - Settlement, TossSeller 엔티티의 기본 CRUD 동작 테스트
  - DTO 변환 로직 검증 (엔티티 ↔ DTO)
  - 데이터베이스 연결 및 JPA 매핑 확인
  - JSON 직렬화/역직렬화 테스트 (Flutter 호환성)
  - _Requirements: 5.5, 5.6_

- [x] 6. Repository 계층 구현
- [x] 6.1 SettlementRepository 인터페이스 생성

  - JpaRepository 상속한 SettlementRepository 인터페이스 생성
  - 가게별, 날짜별 조회를 위한 커스텀 쿼리 메서드 정의
  - **주석 요구사항:**
    - 인터페이스 레벨: Repository의 역할과 제공하는 쿼리 기능 요약
    - 메서드 레벨: 각 쿼리 메서드의 목적, 파라미터, 반환값 상세 설명
    - 성능 고려사항: 인덱스 활용 방법과 페이징 처리 이유
    - 사용 사례: 각 메서드가 사용되는 실제 비즈니스 시나리오
    - JPQL 쿼리: 복잡한 쿼리의 경우 쿼리 로직과 조인 관계 설명
  - _Requirements: 2.1, 2.2_

- [x] 6.2 TossSellerRepository 인터페이스 생성

  - JpaRepository 상속한 TossSellerRepository 인터페이스 생성
  - Store와의 연관관계 조회 메서드 정의
  - _Requirements: 3.1_

- [x] 6.3 **[검증] Repository 계층 동작 확인**

  - 커스텀 쿼리 메서드 동작 테스트
  - 페이징 및 정렬 기능 검증
  - 연관관계 매핑 및 지연 로딩 확인
  - 데이터베이스 인덱스 성능 테스트
  - _Requirements: 2.1, 2.2, 3.1_

- [ ] 3. JWE 암호화 서비스 구현
- [x] 3.1 JweEncryptionService 기본 구조 생성

  - JWE 암호화/복호화를 위한 서비스 클래스 생성
  - 토스페이먼츠 보안 키 설정 및 관리
  - _Requirements: 7.1, 7.2, 7.3_

- [x] 3.2 JWE 암호화 로직 구현

  - A256GCM 암호화 알고리즘을 사용한 encrypt 메서드 구현
  - iat, nonce 헤더를 포함한 JWE 헤더 생성
  - 단위 테스트 작성
  - **주석 요구사항:**
    - 클래스 레벨: JWE 암호화의 목적과 토스페이먼츠 보안 요구사항 설명
    - 메서드 레벨: 암호화 과정의 각 단계 상세 설명 (키 변환, 헤더 생성, 페이로드 암호화)
    - 보안 주의사항: 보안 키 처리 방법과 메모리 관리 주의점
    - 알고리즘 설명: A256GCM과 dir 알고리즘 선택 이유
    - 헤더 필드: iat, nonce 필드의 역할과 생성 규칙
  - _Requirements: 7.2, 7.3_

- [x] 3.3 JWE 복호화 로직 구현

  - 토스페이먼츠 응답 복호화를 위한 decrypt 메서드 구현
  - 복호화 실패 시 예외 처리
  - 단위 테스트 작성
  - _Requirements: 7.2_

- [x] 4. 토스페이먼츠 API 연동 서비스 구현
- [x] 4.1 TossPayoutService 기본 구조 생성

  - 토스페이먼츠 API 호출을 위한 서비스 클래스 생성
  - RestTemplate 또는 WebClient 설정
  - API 기본 헤더 및 인증 설정
  - _Requirements: 3.2, 3.3_

- [x] 4.2 셀러 등록 API 연동 구현

  - registerSeller 메서드 구현 (JWE 암호화 적용)
  - 토스페이먼츠 셀러 등록 API 호출
  - 응답 복호화 및 TossSeller 엔티티 저장
  - _Requirements: 3.1, 3.3_

- [x] 4.3 정산 잔액 조회 API 연동 구현

  - getBalance 메서드 구현
  - 토스페이먼츠 잔액 조회 API 호출
  - availableAmount, pendingAmount 정보 반환
  - _Requirements: 2.4_

- [x] 4.4 지급대행 요청 API 연동 구현

  - requestPayout 메서드 구현 (JWE 암호화 적용)
  - 토스페이먼츠 지급대행 요청 API 호출
  - 멱등키 처리 및 오류 처리
  - _Requirements: 3.2, 3.4_

- [x] 4.5 **[검증] 토스 API 연동 테스트**

  - 토스페이먼츠 테스트 환경 연동 확인
  - JWE 암호화/복호화 동작 검증
  - 셀러 등록 API 호출 테스트
  - 잔액 조회 및 지급대행 요청 API 테스트
  - 오류 상황별 예외 처리 검증
  - _Requirements: 3.1, 3.2, 3.3, 7.2_

- [x] 5. 정산 비즈니스 로직 구현
- [x] 5.1 SettlementService 기본 구조 생성

  - 정산 관련 비즈니스 로직을 처리하는 서비스 클래스 생성
  - Repository 의존성 주입 설정
  - 트랜잭션 처리 설정
  - _Requirements: 1.1, 1.2_

- [x] 5.2 정산 계산 로직 구현

  - createSettlement 메서드 구현
  - 20% 플랫폼 수수료 자동 계산 로직
  - 정산 금액(80%) 계산 및 Settlement 엔티티 생성
  - **주석 요구사항:**
    - 메서드 레벨: 정산 계산의 전체 과정과 비즈니스 규칙 설명
    - 계산 로직: 각 계산 단계별 상세 주석 (수수료율, 수수료 금액, 정산 금액)
    - 수식 설명: 실제 계산 공식과 예시 (10,000원 → 2,000원 수수료, 8,000원 정산)
    - 예외 처리: 음수 금액, null 값 등 예외 상황 처리 방법
    - 정밀도 고려: BigDecimal 사용 이유와 반올림 정책
  - _Requirements: 1.1, 1.2_

- [x] 5.3 정산 처리 로직 구현

  - processSettlement 메서드 구현
  - 토스페이먼츠 지급대행 요청 호출
  - 정산 상태 업데이트 및 오류 처리
  - _Requirements: 3.2, 3.4, 3.5_

- [x] 5.4 정산 내역 조회 로직 구현

  - getSettlements 메서드 구현
  - 가게별, 날짜별 필터링 기능
  - 페이징 처리 및 정렬 기능
  - _Requirements: 2.1, 2.2, 2.3_

- [x] 7. 웹훅 처리 구현
- [x] 7.1 TossWebhookController 생성

  - 토스페이먼츠 웹훅을 받는 컨트롤러 생성
  - payout.changed, seller.changed 이벤트 처리 엔드포인트
  - 웹훅 검증 및 보안 처리
  - _Requirements: 3.6_

- [x] 7.2 웹훅 이벤트 처리 로직 구현

  - 지급대행 상태 변경 처리 (COMPLETED, FAILED)
  - 셀러 상태 변경 처리 (APPROVED, KYC_REQUIRED)
  - 정산 상태 자동 업데이트
  - _Requirements: 3.5, 3.6_

- [x] 8. REST API 컨트롤러 구현
- [x] 8.1 SettlementController 기본 구조 생성

  - REST API 엔드포인트를 제공하는 컨트롤러 생성
  - Flutter 호환 DTO 클래스를 사용한 요청/응답 처리
  - 기본적인 CRUD 엔드포인트 구조 설정
  - _Requirements: 5.1, 5.3, 5.4_

- [x] 8.2 정산 생성 API 구현

  - POST /api/v1/settlements 엔드포인트 구현
  - SettlementRequest DTO 검증 및 처리
  - SettlementResponse DTO 반환 (Flutter json_serializable 호환)
  - **주석 요구사항:**
    - 컨트롤러 레벨: API의 목적과 Flutter 앱과의 연동 방식 설명
    - 메서드 레벨: HTTP 메서드, 경로, 요청/응답 형식 상세 설명
    - 파라미터 검증: @Valid 어노테이션과 검증 규칙 설명
    - 응답 처리: HTTP 상태 코드별 응답 시나리오 (201, 400, 500)
    - 예외 처리: 각 예외 상황과 클라이언트 처리 방법 가이드
    - API 사용 예시: curl 명령어나 실제 요청/응답 JSON 예시
  - _Requirements: 5.1, 5.5, 5.6_

- [x] 8.3 정산 처리 API 구현

  - POST /api/v1/settlements/{id}/process 엔드포인트 구현
  - 정산 처리 요청 및 상태 업데이트
  - 비동기 처리 고려
  - _Requirements: 5.1_

- [x] 8.4 정산 내역 조회 API 구현

  - GET /api/v1/settlements 엔드포인트 구현
  - 쿼리 파라미터를 통한 필터링 (storeId, startDate, endDate)
  - 페이징 처리 및 SettlementResponse DTO 리스트 반환
  - _Requirements: 5.1, 5.3, 5.5_

- [x] 8.5 정산 잔액 조회 API 구현

  - GET /api/v1/settlements/balance 엔드포인트 구현
  - 토스페이먼츠 잔액 조회 API 호출
  - Flutter 호환 BalanceResponse DTO 반환
  - _Requirements: 5.1, 5.4_

- [x] 8.6 정산 요약 정보 조회 API 구현

  - GET /api/v1/settlements/summary 엔드포인트 구현
  - 가게별 일별/월별 정산 요약 정보 제공
  - SettlementSummaryResponse DTO 반환 (Flutter 대시보드용)
  - _Requirements: 5.1, 5.4_

- [x] 8.7 **[검증] Flutter와 통신 테스트**

  - REST API 엔드포인트 전체 동작 확인
  - Flutter 호환 JSON 응답 구조 검증
  - Postman/Insomnia를 통한 API 테스트
  - 페이징, 필터링, 정렬 기능 검증
  - 오류 응답 형식 및 HTTP 상태 코드 확인
  - _Requirements: 5.1, 5.3, 5.4, 5.5_

- [x] 9. 예외 처리 및 오류 관리 구현
- [x] 9.1 커스텀 예외 클래스 생성

  - SettlementException, TossApiException 등 예외 클래스 정의
  - 예외 계층 구조 설계
  - 오류 코드 및 메시지 정의
  - _Requirements: 6.1, 6.2_

- [x] 9.2 글로벌 예외 처리기 구현

  - @RestControllerAdvice를 사용한 전역 예외 처리
  - 일관된 오류 응답 형식 정의
  - 로깅 및 모니터링 연동
  - _Requirements: 6.1, 6.3_

- [x] 9.3 재시도 로직 구현

  - 지급대행 실패 시 자동 재시도 메커니즘
  - 최대 재시도 횟수 제한 (3회)
  - 지수 백오프 알고리즘 적용
  - _Requirements: 6.2_

- [x] 10. 데이터베이스 마이그레이션 및 인덱스 설정
- [x] 10.1 데이터베이스 스키마 생성

  - settlements, toss_sellers 테이블 생성 스크립트
  - 외래키 제약조건 설정
  - 기본 데이터 삽입 스크립트
  - _Requirements: 4.2_

- [x] 10.2 성능 최적화를 위한 인덱스 생성

  - store_id, status, created_at 컬럼에 인덱스 생성
  - 복합 인덱스 설정 (store_id + created_at)
  - 쿼리 성능 테스트 및 최적화
  - _Requirements: 4.2_

- [x] 10.3 **[검증] 데이터베이스 성능 및 안정성 확인**

  - 대용량 데이터 처리 성능 테스트
  - 동시성 처리 및 트랜잭션 격리 수준 검증
  - 데이터 무결성 제약조건 테스트
  - 백업 및 복구 시나리오 검증
  - _Requirements: 4.1, 4.2_

- [ ] 11. 단위 테스트 작성
- [ ] 11.1 Service 계층 단위 테스트

  - SettlementService 주요 메서드 테스트
  - Mock을 사용한 의존성 격리
  - 비즈니스 로직 검증 테스트
  - **주석 요구사항:**
    - 테스트 클래스 레벨: 테스트 대상과 테스트 범위 명시
    - 테스트 메서드 레벨: Given-When-Then 패턴으로 테스트 시나리오 설명
    - Mock 설정: Mock 객체 사용 이유와 설정 방법 설명
    - 검증 로직: 각 assertion의 목적과 검증하는 비즈니스 규칙 설명
    - 테스트 데이터: 테스트에 사용되는 데이터의 의미와 선택 이유
    - 경계값 테스트: 정상/비정상 케이스 구분과 테스트 의도
  - _Requirements: 1.1, 1.2, 1.3_

- [ ] 11.2 JWE 암호화 서비스 테스트

  - 암호화/복호화 기능 테스트
  - 토스페이먼츠 호환성 테스트
  - 오류 상황 처리 테스트
  - _Requirements: 7.2, 7.3_

- [ ] 11.3 토스페이먼츠 API 연동 테스트

  - Mock 서버를 사용한 API 호출 테스트
  - 성공/실패 시나리오 테스트
  - 네트워크 오류 처리 테스트
  - _Requirements: 3.1, 3.2, 3.4_

- [ ] 12. 통합 테스트 작성
- [ ] 12.1 API 엔드포인트 통합 테스트

  - @SpringBootTest를 사용한 전체 플로우 테스트
  - 데이터베이스 트랜잭션 테스트
  - 웹훅 처리 통합 테스트
  - _Requirements: 5.1, 5.3_

- [ ] 12.2 데이터베이스 연동 테스트

  - JPA 엔티티 매핑 테스트
  - Repository 쿼리 메서드 테스트
  - 트랜잭션 롤백 테스트
  - _Requirements: 4.2_

- [ ] 13. 보안 및 설정 강화
- [ ] 13.1 API 보안 설정

  - JWT 토큰 기반 인증 적용
  - CORS 설정 및 보안 헤더 추가
  - Rate Limiting 구현
  - _Requirements: 5.2, 7.1_

- [ ] 13.2 민감 정보 보호

  - 토스페이먼츠 API 키 환경변수 관리
  - 개인정보 마스킹 처리
  - 로그에서 민감 정보 제외
  - _Requirements: 7.1_

- [ ] 14. 모니터링 및 로깅 구현
- [ ] 14.1 감사 로그 시스템 구현

  - 정산 관련 모든 작업 로깅
  - 상태 변경 이력 추적
  - 오류 발생 시 상세 로그 기록
  - _Requirements: 4.1, 4.2_

- [ ] 14.2 메트릭 및 알림 설정

  - 정산 처리 성공/실패 메트릭 수집
  - 지급대행 실패 시 알림 발송
  - 시스템 상태 모니터링 대시보드
  - _Requirements: 6.1_

- [ ] 15. API 문서화 및 최종 통합
- [ ] 15.1 API 문서 작성

  - Swagger/OpenAPI 문서 생성
  - 요청/응답 예시 추가
  - 오류 코드 및 처리 방법 문서화
  - _Requirements: 5.3_

- [ ] 15.2 **[최종 검증] 전체 시스템 통합 테스트**
  - 실제 토스페이먼츠 테스트 환경 연동 테스트
  - Flutter 앱과의 API 통신 테스트
  - 전체 정산 플로우 End-to-End 테스트
  - 부하 테스트 및 성능 벤치마크
  - 보안 취약점 스캔 및 검증
  - 운영 환경 배포 준비 상태 확인
  - **주석 품질 최종 검증:**
    - 모든 public 클래스/메서드에 JavaDoc 주석 완성도 확인
    - 복잡한 비즈니스 로직의 인라인 주석 적절성 검토
    - DTO 클래스의 Flutter 호환성 정보 완전성 확인
    - 테스트 코드의 시나리오 설명 명확성 검증
    - 코드 리뷰를 통한 주석 품질 최종 점검
  - _Requirements: 5.1, 5.3, 6.1, 7.1, 8.1-8.7_

## 주석 품질 검증 체크리스트

### 각 작업 완료 시 확인사항

#### 📝 필수 주석 항목

- [ ] **클래스 레벨 JavaDoc**: 목적, 주요 기능, 사용 예시 포함
- [ ] **Public 메서드 JavaDoc**: 파라미터, 반환값, 예외, 처리 과정 설명
- [ ] **중요 필드 주석**: 용도, 제약사항, 예시값 포함
- [ ] **복잡한 로직 인라인 주석**: 단계별 처리 과정 설명
- [ ] **DTO 클래스 특별 주석**: Flutter 호환성 정보 포함

#### 🎯 품질 기준

- [ ] **명확성**: 코드를 처음 보는 개발자도 이해할 수 있는 수준
- [ ] **완전성**: 모든 중요한 정보가 누락 없이 포함
- [ ] **정확성**: 코드와 주석 내용이 일치하고 최신 상태 유지
- [ ] **일관성**: 프로젝트 전체에서 동일한 주석 스타일 적용
- [ ] **실용성**: 실제 개발과 유지보수에 도움이 되는 정보 제공

#### 🔍 특별 검증 항목

- [ ] **Flutter 연동 정보**: JSON 직렬화 형식, 타입 매핑 정보 정확성
- [ ] **토스페이먼츠 연동**: JWE 암호화, API 호출 과정 상세 설명
- [ ] **비즈니스 로직**: 20% 수수료 계산, 정산 상태 변경 규칙 명확성
- [ ] **보안 고려사항**: 민감 정보 처리, 암호화 과정 주의사항
- [ ] **성능 최적화**: 인덱스 활용, 페이징 처리 이유 설명

#### ✅ 최종 승인 기준

각 작업은 다음 조건을 모두 만족해야 완료로 간주됩니다:

1. **기능 구현 완료**: 요구사항에 명시된 모든 기능 구현
2. **테스트 통과**: 단위 테스트 및 통합 테스트 모두 통과
3. **주석 품질 검증**: 위 체크리스트 항목 모두 충족
4. **코드 리뷰 승인**: 팀원의 코드 리뷰 및 주석 품질 검토 완료
