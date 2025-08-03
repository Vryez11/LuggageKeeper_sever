# Requirements Document

## Introduction

짐 보관 플랫폼의 정산 시스템은 여행객이 주변 가게(편의점, 술집, 밥집 등)에 케리어와 같은 짐을 맡기는 서비스에서 발생하는 수익을 플랫폼과 가게 간에 정산하는 핵심 기능입니다. 플랫폼은 20% 수수료(PG사 수수료 포함)를 가져가고, 나머지 80%는 토스 지급대행을 통해 가게에 송금됩니다.

## Requirements

### Requirement 1

**User Story:** 플랫폼 관리자로서, 짐 보관 서비스 이용료에서 플랫폼 수수료(20%, PG사 수수료 포함)를 자동으로 계산하여 정산 금액을 산출하고 싶습니다.

#### Acceptance Criteria

1. WHEN 짐 보관 서비스 결제가 완료되면 THEN 시스템은 총 결제 금액에서 20% 플랫폼 수수료를 차감한 정산 금액을 계산해야 합니다.
2. WHEN 정산 금액을 계산할 때 THEN 시스템은 PG사 수수료가 이미 20% 수수료에 포함되어 있음을 고려해야 합니다.
3. WHEN 정산 계산이 완료되면 THEN 시스템은 플랫폼 수익과 가게 정산 금액을 각각 기록해야 합니다.

#### 구현 코드 예제

```java
/**
 * 정산 정보를 저장하는 엔티티
 * 짐 보관 서비스 이용료에서 플랫폼 수수료(20%)를 차감한 정산 금액을 관리
 */
@Entity
@Table(name = "settlements")
public class Settlement {
    
    // 정산 금액을 계산하는 정적 팩토리 메서드
    public static Settlement createSettlement(Store store, String orderId, BigDecimal originalAmount) {
        Settlement settlement = new Settlement();
        
        // 1. 기본 정보 설정
        settlement.setStore(store);           // 정산 대상 가게 설정
        settlement.setOrderId(orderId);       // 주문 ID 설정 (추적용)
        settlement.setOriginalAmount(originalAmount); // 원본 결제 금액 저장
        
        // 2. 플랫폼 수수료율 20% 설정 (PG사 수수료 포함)
        BigDecimal feeRate = new BigDecimal("0.20");  // 20% = 0.20
        settlement.setPlatformFeeRate(feeRate);
        
        // 3. 플랫폼 수수료 금액 계산
        // 예: 10,000원 * 0.20 = 2,000원
        BigDecimal platformFee = originalAmount.multiply(feeRate);
        settlement.setPlatformFee(platformFee);
        
        // 4. 실제 정산 금액 계산 (가게가 받을 금액)
        // 예: 10,000원 - 2,000원 = 8,000원
        BigDecimal settlementAmount = originalAmount.subtract(platformFee);
        settlement.setSettlementAmount(settlementAmount);
        
        // 5. 초기 상태를 PENDING으로 설정
        settlement.setStatus(SettlementStatus.PENDING);
        
        return settlement;
    }
}
```

### Requirement 2

**User Story:** 가게 사장으로서, 내 가게에서 발생한 짐 보관 서비스 수익의 정산 내역을 조회하고 싶습니다.

#### Acceptance Criteria

1. WHEN 가게 사장이 정산 내역을 요청하면 THEN 시스템은 해당 가게의 모든 정산 기록을 반환해야 합니다.
2. WHEN 정산 내역을 조회할 때 THEN 시스템은 날짜별, 기간별 필터링 기능을 제공해야 합니다.
3. WHEN 정산 내역을 표시할 때 THEN 시스템은 원본 결제 금액, 플랫폼 수수료, 실제 정산 금액을 명확히 구분하여 보여줘야 합니다.
4. WHEN 정산 잔액을 확인할 때 THEN 시스템은 토스페이먼츠 잔액 조회 API를 통해 지급 가능한 금액(availableAmount)과 대기 중인 금액(pendingAmount)을 조회해야 합니다.

#### 구현 코드 예제

```java
/**
 * Settlement 엔티티를 위한 Repository 인터페이스
 * 정산 데이터의 CRUD 및 커스텀 쿼리 제공
 */
@Repository
public interface SettlementRepository extends JpaRepository<Settlement, String> {

    /**
     * 가게별 정산 내역 조회 (최신순 정렬)
     * @param storeId 조회할 가게 ID
     * @param pageable 페이징 정보 (페이지 번호, 크기 등)
     * @return 해당 가게의 정산 내역 페이지
     */
    Page<Settlement> findByStoreIdOrderByCreatedAtDesc(String storeId, Pageable pageable);

    /**
     * 가게별 특정 기간 정산 내역 조회
     * @param storeId 조회할 가게 ID
     * @param startDate 조회 시작 날짜
     * @param endDate 조회 종료 날짜
     * @param pageable 페이징 정보
     * @return 지정된 기간의 정산 내역
     */
    @Query("SELECT s FROM Settlement s WHERE s.store.id = :storeId " +
           "AND s.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY s.createdAt DESC")
    Page<Settlement> findByStoreIdAndDateRange(
            @Param("storeId") String storeId,      // 가게 ID 파라미터
            @Param("startDate") LocalDateTime startDate,  // 시작 날짜 파라미터
            @Param("endDate") LocalDateTime endDate,      // 종료 날짜 파라미터
            Pageable pageable);

    /**
     * 가게별 정산 통계 조회 (완료된 정산만)
     * @param storeId 조회할 가게 ID
     * @return [정산 건수, 총 정산 금액] 배열
     */
    @Query("SELECT COUNT(s), SUM(s.settlementAmount) FROM Settlement s " +
           "WHERE s.store.id = :storeId AND s.status = 'COMPLETED'")
    Object[] getSettlementStatsByStoreId(@Param("storeId") String storeId);
}

/**
 * 정산 정보 API 응답용 DTO
 * Flutter json_serializable과 호환되는 구조
 */
@Getter
@Setter
public class SettlementResponse {
    
    private String id;                    // 정산 ID
    private String storeId;               // 가게 ID
    private String orderId;               // 주문 ID
    private BigDecimal originalAmount;    // 원본 결제 금액 (예: 10,000원)
    private BigDecimal platformFeeRate;   // 플랫폼 수수료율 (0.20 = 20%)
    private BigDecimal platformFee;       // 플랫폼 수수료 금액 (예: 2,000원)
    private BigDecimal settlementAmount;  // 실제 정산 금액 (예: 8,000원)
    private SettlementStatus status;      // 정산 상태 (PENDING, COMPLETED 등)
    private LocalDateTime requestedAt;    // 정산 요청 시간
    private LocalDateTime completedAt;    // 정산 완료 시간
    private Integer retryCount;           // 재시도 횟수
    
    /**
     * Settlement 엔티티로부터 DTO 생성
     * @param settlement 변환할 Settlement 엔티티
     * @return 생성된 SettlementResponse DTO
     */
    public static SettlementResponse from(Settlement settlement) {
        SettlementResponse response = new SettlementResponse();
        
        // 기본 정보 매핑
        response.setId(settlement.getId());
        response.setStoreId(settlement.getStore().getId());  // 연관 엔티티에서 ID 추출
        response.setOrderId(settlement.getOrderId());
        
        // 금액 정보 매핑 (BigDecimal 타입 유지)
        response.setOriginalAmount(settlement.getOriginalAmount());
        response.setPlatformFeeRate(settlement.getPlatformFeeRate());
        response.setPlatformFee(settlement.getPlatformFee());
        response.setSettlementAmount(settlement.getSettlementAmount());
        
        // 상태 및 시간 정보 매핑
        response.setStatus(settlement.getStatus());
        response.setRequestedAt(settlement.getRequestedAt());
        response.setCompletedAt(settlement.getCompletedAt());
        response.setRetryCount(settlement.getRetryCount());
        
        return response;
    }
}
```

### Requirement 3

**User Story:** 플랫폼 관리자로서, 토스페이먼츠 지급대행 API를 통해 가게(셀러)에 정산 금액을 자동으로 송금하고 싶습니다.

#### Acceptance Criteria

1. WHEN 가게가 시스템에 등록되면 THEN 시스템은 토스페이먼츠 셀러 등록 API를 통해 가게 정보를 등록하고 JWE 암호화를 적용해야 합니다.
2. WHEN 정산 처리가 요청되면 THEN 시스템은 토스페이먼츠 지급대행 요청 API를 호출하여 가게 계좌로 정산 금액을 송금해야 합니다.
3. WHEN 지급대행 API 요청 시 THEN 시스템은 JWE 암호화를 사용하여 Request Body를 암호화하고 ENCRYPTION 보안 모드를 적용해야 합니다.
4. WHEN 송금 처리 중 오류가 발생하면 THEN 시스템은 오류를 기록하고 재시도 로직을 실행해야 합니다.
5. WHEN 송금이 성공하면 THEN 시스템은 정산 상태를 '완료'로 업데이트하고 송금 완료 시간을 기록해야 합니다.
6. WHEN 지급대행 상태 변경이 발생하면 THEN 시스템은 payout.changed 웹훅을 통해 상태 변경을 수신하고 처리해야 합니다.

#### 구현 코드 예제

```java
/**
 * 토스페이먼츠 셀러 정보를 저장하는 엔티티
 * 가게와 토스페이먼츠 지급대행 서비스 간의 연결 정보를 관리
 */
@Entity
@Table(name = "toss_sellers")
public class TossSeller {
    
    /**
     * 토스 셀러 생성을 위한 정적 팩토리 메서드
     * @param store 연결할 가게 엔티티
     * @param businessType 사업자 타입 (개인사업자/법인사업자)
     * @return 생성된 TossSeller 객체
     */
    public static TossSeller createTossSeller(Store store, TossBusinessType businessType) {
        TossSeller tossSeller = new TossSeller();
        
        // 1. 기본 정보 설정
        tossSeller.setStore(store);                    // 연결할 가게 설정
        tossSeller.setRefSellerId(store.getId());      // 우리 시스템의 셀러 ID (가게 ID 사용)
        tossSeller.setBusinessType(businessType);      // 사업자 타입 설정
        
        // 2. 초기 상태 설정 (승인 대기)
        tossSeller.setStatus(TossSellerStatus.APPROVAL_REQUIRED);
        
        return tossSeller;
    }

    /**
     * 토스페이먼츠에서 셀러 ID 발급 완료 처리
     * @param tossSellerId 토스에서 발급한 셀러 ID
     */
    public void assignTossId(String tossSellerId) {
        this.tossSellerId = tossSellerId;  // 토스 시스템에서 발급받은 고유 ID 저장
    }

    /**
     * 셀러 승인 완료 처리
     * 본인인증 또는 KYC 심사 완료 후 호출
     */
    public void approve() {
        this.status = TossSellerStatus.APPROVED;      // 상태를 승인됨으로 변경
        this.approvedAt = LocalDateTime.now();        // 승인 시간 기록
    }

    /**
     * 지급대행 가능 여부 확인
     * @return 지급대행 가능 여부 (true: 가능, false: 불가능)
     */
    public boolean canProcessPayout() {
        // 1. 토스 셀러 ID가 발급되어 있어야 함
        boolean hasTossId = (tossSellerId != null);
        
        // 2. 상태가 승인됨 또는 부분승인됨이어야 함
        boolean isApproved = (status == TossSellerStatus.APPROVED || 
                             status == TossSellerStatus.PARTIALLY_APPROVED);
        
        return hasTossId && isApproved;
    }
}

/**
 * Settlement 엔티티의 정산 처리 관련 메서드들
 */
public class Settlement {
    
    /**
     * 정산 처리 완료 처리
     * 토스페이먼츠 지급대행 성공 시 호출
     * @param tossPayoutId 토스 지급대행 ID
     */
    public void completeSettlement(String tossPayoutId) {
        this.tossPayoutId = tossPayoutId;              // 토스 지급대행 ID 저장
        this.status = SettlementStatus.COMPLETED;      // 상태를 완료로 변경
        this.completedAt = LocalDateTime.now();        // 완료 시간 기록
        this.errorMessage = null;                      // 에러 메시지 초기화
    }

    /**
     * 정산 처리 실패 처리
     * 토스페이먼츠 지급대행 실패 시 호출
     * @param errorMessage 실패 사유
     */
    public void failSettlement(String errorMessage) {
        this.status = SettlementStatus.FAILED;         // 상태를 실패로 변경
        this.errorMessage = errorMessage;              // 실패 사유 저장
        this.retryCount++;                             // 재시도 횟수 증가
    }

    /**
     * 정산 처리 중 상태로 변경
     * 토스페이먼츠 API 호출 시작 시 호출
     */
    public void startProcessing() {
        this.status = SettlementStatus.PROCESSING;     // 상태를 처리중으로 변경
    }

    /**
     * 재시도 가능 여부 확인 (최대 3회)
     * @return 재시도 가능 여부
     */
    public boolean canRetry() {
        // 재시도 횟수가 3회 미만이고 상태가 실패인 경우에만 재시도 가능
        return retryCount < 3 && status == SettlementStatus.FAILED;
    }
}

/**
 * 토스페이먼츠 지급대행 서비스 클래스 (예시)
 */
@Service
public class TossPayoutService {
    
    /**
     * JWE 암호화를 사용한 지급대행 요청
     * @param settlement 정산 정보
     * @param tossSeller 토스 셀러 정보
     */
    public void requestPayout(Settlement settlement, TossSeller tossSeller) {
        try {
            // 1. 정산 상태를 처리중으로 변경
            settlement.startProcessing();
            
            // 2. 지급대행 요청 데이터 생성
            PayoutRequest request = PayoutRequest.builder()
                .refPayoutId(settlement.getId())                    // 우리 시스템의 정산 ID
                .destination(tossSeller.getTossSellerId())          // 토스 셀러 ID
                .scheduleType("EXPRESS")                            // 즉시 지급
                .amount(settlement.getSettlementAmount())           // 정산 금액
                .transactionDescription("짐보관 서비스 정산")        // 거래 설명
                .build();
            
            // 3. JWE 암호화 수행
            String encryptedRequest = encryptWithJWE(request);
            
            // 4. 토스페이먼츠 API 호출
            String response = callTossPayoutAPI(encryptedRequest);
            
            // 5. 응답 복호화 및 처리
            PayoutResponse payoutResponse = decryptJWEResponse(response);
            
            // 6. 성공 시 정산 완료 처리
            settlement.completeSettlement(payoutResponse.getId());
            
        } catch (Exception e) {
            // 7. 실패 시 에러 처리
            settlement.failSettlement(e.getMessage());
            
            // 8. 재시도 가능한 경우 재시도 스케줄링
            if (settlement.canRetry()) {
                scheduleRetry(settlement);
            }
        }
    }
    
    /**
     * JWE 암호화 수행
     * @param request 암호화할 요청 객체
     * @return JWE로 암호화된 문자열
     */
    private String encryptWithJWE(PayoutRequest request) {
        // 1. 보안 키를 바이트 배열로 변환
        byte[] key = Hex.decode(securityKey);

        // 2. JWE 헤더 생성 (토스페이먼츠 요구사항에 따라)
        JWEHeader jweHeader = new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM)
            .customParam("iat", OffsetDateTime.now(ZoneId.of("Asia/Seoul")).toString())  // 발급 시간
            .customParam("nonce", UUID.randomUUID().toString())                         // 고유 식별자
            .build();

        // 3. Request Body를 JSON으로 변환 후 암호화
        String payload = objectMapper.writeValueAsString(request);
        JWEObject jweObject = new JWEObject(jweHeader, new Payload(payload));
        jweObject.encrypt(new DirectEncrypter(key));
        
        return jweObject.serialize();  // 암호화된 JWE 문자열 반환
    }
}
```

### Requirement 4

**User Story:** 플랫폼 관리자로서, 정산 시스템의 모든 거래 내역을 추적하고 감사할 수 있는 기능이 필요합니다.

#### Acceptance Criteria

1. WHEN 정산 관련 모든 작업이 수행되면 THEN 시스템은 상세한 로그를 기록해야 합니다.
2. WHEN 정산 상태가 변경되면 THEN 시스템은 변경 이력을 추적 가능한 형태로 저장해야 합니다.
3. WHEN 관리자가 감사 보고서를 요청하면 THEN 시스템은 지정된 기간의 모든 정산 내역과 상태를 포함한 보고서를 생성해야 합니다.

### Requirement 5

**User Story:** 백엔드 시스템으로서, 외부 클라이언트(Flutter 앱 등)가 정산 관련 데이터를 조회하고 처리할 수 있는 REST API를 제공해야 합니다.

#### Acceptance Criteria

1. WHEN 외부 클라이언트에서 정산 내역 조회 API를 호출하면 THEN 백엔드는 JSON 형태로 정산 데이터를 반환해야 합니다.
2. WHEN API 요청에 인증 토큰이 포함되면 THEN 백엔드는 토큰을 검증하고 권한에 따라 접근을 제어해야 합니다.
3. WHEN API 응답을 생성할 때 THEN 백엔드는 일관된 응답 형식과 적절한 HTTP 상태 코드를 사용해야 합니다.
4. WHEN Flutter 앱에서 json_serializable을 사용할 때 THEN 백엔드는 Flutter와 호환되는 JSON 구조의 DTO 클래스를 제공해야 합니다.
5. WHEN API 응답 DTO를 생성할 때 THEN 시스템은 JPA 엔티티와 분리된 별도의 응답용 DTO 클래스를 사용해야 합니다.
6. WHEN API 요청 DTO를 처리할 때 THEN 시스템은 입력 검증과 타입 안전성을 보장하는 요청용 DTO 클래스를 사용해야 합니다.

#### 구현 코드 예제

```java
/**
 * 정산 요청 API용 DTO
 * Flutter에서 정산 요청 시 사용
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementRequest {
    
    @NotBlank(message = "가게 ID는 필수입니다")
    private String storeId;        // 정산 요청할 가게 ID
    
    @NotBlank(message = "주문 ID는 필수입니다")
    private String orderId;        // 정산 대상 주문 ID
    
    @NotNull(message = "결제 금액은 필수입니다")
    @Positive(message = "결제 금액은 0보다 커야 합니다")
    private BigDecimal originalAmount;  // 원본 결제 금액
    
    private String metadata;       // 추가 정보 (JSON 문자열)
}

/**
 * 정산 요약 정보 API 응답용 DTO
 * Flutter에서 대시보드나 요약 화면에 사용
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementSummaryResponse {
    
    private String storeId;                    // 가게 ID
    private LocalDate date;                    // 정산 날짜
    private BigDecimal totalOriginalAmount;    // 총 결제 금액
    private BigDecimal totalPlatformFee;       // 총 플랫폼 수수료
    private BigDecimal totalSettlementAmount;  // 총 정산 금액
    private Long completedCount;               // 완료된 정산 건수
    private Long pendingCount;                 // 대기 중인 정산 건수
    private Long failedCount;                  // 실패한 정산 건수
    
    /**
     * 정산 요약 정보 생성을 위한 정적 팩토리 메서드
     * null 값 처리를 통해 안전한 객체 생성
     */
    public static SettlementSummaryResponse create(
            String storeId,
            LocalDate date,
            BigDecimal totalOriginalAmount,
            BigDecimal totalPlatformFee,
            BigDecimal totalSettlementAmount,
            Long completedCount,
            Long pendingCount,
            Long failedCount) {
        
        return new SettlementSummaryResponse(
                storeId,
                date,
                // null 값을 기본값으로 처리하여 NPE 방지
                totalOriginalAmount != null ? totalOriginalAmount : BigDecimal.ZERO,
                totalPlatformFee != null ? totalPlatformFee : BigDecimal.ZERO,
                totalSettlementAmount != null ? totalSettlementAmount : BigDecimal.ZERO,
                completedCount != null ? completedCount : 0L,
                pendingCount != null ? pendingCount : 0L,
                failedCount != null ? failedCount : 0L
        );
    }
}

/**
 * 정산 관련 REST API 컨트롤러
 */
@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {
    
    private final SettlementService settlementService;
    
    /**
     * 가게별 정산 내역 조회 API
     * @param storeId 조회할 가게 ID
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @param startDate 조회 시작 날짜 (선택사항)
     * @param endDate 조회 종료 날짜 (선택사항)
     * @return 정산 내역 페이지 응답
     */
    @GetMapping("/store/{storeId}")
    public ResponseEntity<Page<SettlementResponse>> getSettlementsByStore(
            @PathVariable String storeId,                    // URL 경로에서 가게 ID 추출
            @RequestParam(defaultValue = "0") int page,      // 기본값 0으로 설정
            @RequestParam(defaultValue = "20") int size,     // 기본값 20으로 설정
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startDate,                         // 선택적 시작 날짜
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endDate) {                         // 선택적 종료 날짜
        
        try {
            // 1. 페이징 정보 생성
            Pageable pageable = PageRequest.of(page, size);
            
            // 2. 서비스 레이어에서 데이터 조회
            Page<SettlementResponse> settlements;
            if (startDate != null && endDate != null) {
                // 날짜 범위가 지정된 경우
                settlements = settlementService.getSettlementsByStoreAndDateRange(
                    storeId, startDate, endDate, pageable);
            } else {
                // 전체 조회
                settlements = settlementService.getSettlementsByStore(storeId, pageable);
            }
            
            // 3. 성공 응답 반환 (HTTP 200)
            return ResponseEntity.ok(settlements);
            
        } catch (IllegalArgumentException e) {
            // 4. 잘못된 요청 파라미터 (HTTP 400)
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            // 5. 서버 내부 오류 (HTTP 500)
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 정산 요청 API
     * @param request 정산 요청 DTO
     * @return 생성된 정산 정보
     */
    @PostMapping
    public ResponseEntity<SettlementResponse> createSettlement(
            @Valid @RequestBody SettlementRequest request) {  // @Valid로 입력 검증 수행
        
        try {
            // 1. 서비스 레이어에서 정산 생성
            SettlementResponse settlement = settlementService.createSettlement(request);
            
            // 2. 생성 성공 응답 (HTTP 201)
            return ResponseEntity.status(HttpStatus.CREATED).body(settlement);
            
        } catch (IllegalArgumentException e) {
            // 3. 잘못된 요청 데이터 (HTTP 400)
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            // 4. 서버 내부 오류 (HTTP 500)
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 정산 요약 정보 조회 API
     * @param storeId 조회할 가게 ID
     * @param date 조회할 날짜
     * @return 해당 날짜의 정산 요약 정보
     */
    @GetMapping("/store/{storeId}/summary")
    public ResponseEntity<SettlementSummaryResponse> getSettlementSummary(
            @PathVariable String storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        try {
            // 1. 서비스에서 요약 정보 조회
            SettlementSummaryResponse summary = settlementService.getSettlementSummary(storeId, date);
            
            // 2. 성공 응답 반환
            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            // 3. 오류 시 500 응답
            return ResponseEntity.internalServerError().build();
        }
    }
}

/**
 * JSON 직렬화/역직렬화 테스트 예제
 * Flutter json_serializable과의 호환성 확인
 */
@Test
public void testJsonCompatibility() throws Exception {
    // 1. ObjectMapper 설정 (Jackson)
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());  // LocalDateTime 지원
    
    // 2. SettlementResponse 객체 생성
    SettlementResponse response = new SettlementResponse();
    response.setId("settlement-123");
    response.setStoreId("store-456");
    response.setOriginalAmount(new BigDecimal("10000"));
    response.setPlatformFee(new BigDecimal("2000"));
    response.setSettlementAmount(new BigDecimal("8000"));
    response.setStatus(SettlementStatus.COMPLETED);
    
    // 3. JSON 직렬화 (Java → JSON)
    String json = objectMapper.writeValueAsString(response);
    
    // 4. Flutter 호환 JSON 구조 확인
    assertThat(json).contains("\"id\":\"settlement-123\"");           // 문자열 필드
    assertThat(json).contains("\"originalAmount\":10000");           // 숫자 필드
    assertThat(json).contains("\"status\":\"COMPLETED\"");           // Enum 필드
    
    // 5. JSON 역직렬화 (JSON → Java)
    SettlementResponse deserialized = objectMapper.readValue(json, SettlementResponse.class);
    
    // 6. 데이터 무결성 확인
    assertThat(deserialized.getId()).isEqualTo("settlement-123");
    assertThat(deserialized.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("10000"));
    assertThat(deserialized.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
}
```

### Requirement 6

**User Story:** 시스템 운영자로서, 정산 처리 실패나 오류 상황에 대한 알림과 복구 기능이 필요합니다.

#### Acceptance Criteria

1. WHEN 정산 처리가 실패하면 THEN 시스템은 관리자에게 알림을 발송해야 합니다.
2. WHEN 송금 실패가 발생하면 THEN 시스템은 자동 재시도를 수행하고 최대 재시도 횟수를 제한해야 합니다.
3. WHEN 수동 개입이 필요한 상황이 발생하면 THEN 시스템은 해당 정산 건을 별도로 표시하고 관리자 처리를 대기해야 합니다.

### Requirement 7

**User Story:** 백엔드 시스템으로서, 토스페이먼츠 지급대행 서비스의 보안 요구사항을 준수해야 합니다.

#### Acceptance Criteria

1. WHEN 토스페이먼츠 API 키를 관리할 때 THEN 시스템은 시크릿 키와 보안 키를 안전하게 저장하고 외부 노출을 방지해야 합니다.
2. WHEN JWE 암호화를 수행할 때 THEN 시스템은 A256GCM 암호화 알고리즘과 dir 키 암호화 알고리즘을 사용해야 합니다.
3. WHEN API 요청을 보낼 때 THEN 시스템은 iat(발급 시간)과 nonce(고유 식별자)를 포함한 JWE 헤더를 생성해야 합니다.

### Requirement 8

**User Story:** 개발자로서, 정산 시스템의 모든 코드가 이해하기 쉽고 유지보수가 가능하도록 상세한 주석이 포함되어야 합니다.

#### Acceptance Criteria

1. WHEN 새로운 클래스나 메서드를 작성할 때 THEN 개발자는 해당 코드의 목적과 동작 방식을 설명하는 JavaDoc 주석을 작성해야 합니다.
2. WHEN 복잡한 비즈니스 로직을 구현할 때 THEN 개발자는 각 단계별로 인라인 주석을 추가하여 코드의 의도를 명확히 해야 합니다.
3. WHEN 엔티티 클래스를 작성할 때 THEN 개발자는 각 필드의 용도와 제약사항을 주석으로 설명해야 합니다.
4. WHEN DTO 클래스를 작성할 때 THEN 개발자는 Flutter와의 호환성 및 JSON 직렬화 관련 정보를 주석에 포함해야 합니다.
5. WHEN Repository 인터페이스를 작성할 때 THEN 개발자는 각 쿼리 메서드의 목적과 반환값을 주석으로 설명해야 합니다.
6. WHEN 서비스 클래스의 메서드를 작성할 때 THEN 개발자는 파라미터, 반환값, 예외 상황을 포함한 상세한 주석을 작성해야 합니다.
7. WHEN 테스트 코드를 작성할 때 THEN 개발자는 테스트의 목적과 검증하는 내용을 주석으로 명시해야 합니다.