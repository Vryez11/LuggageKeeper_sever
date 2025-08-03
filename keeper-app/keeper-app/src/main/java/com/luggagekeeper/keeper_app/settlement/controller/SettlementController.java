package com.luggagekeeper.keeper_app.settlement.controller;

import com.luggagekeeper.keeper_app.settlement.domain.Settlement;
import com.luggagekeeper.keeper_app.settlement.dto.SettlementRequest;
import com.luggagekeeper.keeper_app.settlement.dto.SettlementResponse;
import com.luggagekeeper.keeper_app.settlement.dto.SettlementSummaryResponse;
import com.luggagekeeper.keeper_app.settlement.dto.TossBalanceResponse;
import com.luggagekeeper.keeper_app.settlement.repository.SettlementRepository;
import com.luggagekeeper.keeper_app.settlement.service.SettlementService;
import com.luggagekeeper.keeper_app.settlement.service.TossPayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 정산 시스템 REST API 컨트롤러
 * 
 * 짐 보관 플랫폼의 정산 관련 모든 REST API 엔드포인트를 제공하는 컨트롤러입니다.
 * Flutter 앱과의 완벽한 호환성을 위해 설계되었으며, json_serializable 패키지와
 * 호환되는 JSON 구조로 요청과 응답을 처리합니다.
 * 
 * <p>주요 기능:</p>
 * <ul>
 *   <li>정산 생성 API - 새로운 정산 요청 처리</li>
 *   <li>정산 처리 API - 토스페이먼츠 지급대행 실행</li>
 *   <li>정산 내역 조회 API - 필터링 및 페이징 지원</li>
 *   <li>정산 잔액 조회 API - 토스페이먼츠 잔액 확인</li>
 *   <li>정산 요약 정보 API - 대시보드용 통계 데이터</li>
 * </ul>
 * 
 * <p>Flutter 연동 특징:</p>
 * <ul>
 *   <li>json_serializable 호환 DTO 구조 사용</li>
 *   <li>camelCase 필드명 (Jackson 자동 변환)</li>
 *   <li>null safety 고려한 응답 구조</li>
 *   <li>일관된 오류 응답 형식</li>
 *   <li>HTTP 상태 코드 표준 준수</li>
 * </ul>
 * 
 * <p>API 경로 구조:</p>
 * <ul>
 *   <li>POST /api/v1/settlements - 정산 생성</li>
 *   <li>POST /api/v1/settlements/{id}/process - 정산 처리</li>
 *   <li>GET /api/v1/settlements - 정산 내역 조회</li>
 *   <li>GET /api/v1/settlements/balance - 잔액 조회</li>
 *   <li>GET /api/v1/settlements/summary - 요약 정보 조회</li>
 * </ul>
 * 
 * <p>공통 응답 형식:</p>
 * <ul>
 *   <li>성공: HTTP 200/201 + 데이터</li>
 *   <li>클라이언트 오류: HTTP 400 + 오류 메시지</li>
 *   <li>서버 오류: HTTP 500 + 오류 메시지</li>
 *   <li>리소스 없음: HTTP 404 + 오류 메시지</li>
 * </ul>
 * 
 * <p>보안 고려사항:</p>
 * <ul>
 *   <li>입력값 자동 검증 (@Valid 어노테이션)</li>
 *   <li>민감한 정보 로깅 제외</li>
 *   <li>SQL 인젝션 방지 (JPA 사용)</li>
 *   <li>CORS 설정 적용</li>
 * </ul>
 * 
 * <p>사용 예시 (Flutter HTTP 클라이언트):</p>
 * <pre>
 * // 정산 생성
 * final response = await http.post(
 *   Uri.parse('$baseUrl/api/v1/settlements'),
 *   headers: {'Content-Type': 'application/json'},
 *   body: jsonEncode({
 *     'storeId': 'store-123',
 *     'orderId': 'order-456',
 *     'originalAmount': 10000
 *   })
 * );
 * 
 * // 정산 내역 조회
 * final response = await http.get(
 *   Uri.parse('$baseUrl/api/v1/settlements?storeId=store-123&page=0&size=20')
 * );
 * </pre>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 * @see SettlementService
 * @see SettlementRequest
 * @see SettlementResponse
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final TossPayoutService tossPayoutService;
    private final SettlementRepository settlementRepository;

    /**
     * 새로운 정산을 생성하는 API 엔드포인트
     * 
     * 클라이언트(Flutter 앱)로부터 정산 요청을 받아 새로운 정산을 생성합니다.
     * 플랫폼 수수료(20%)를 자동으로 계산하고, 정산 상태를 PENDING으로 설정합니다.
     * 
     * <p>처리 과정:</p>
     * <ol>
     *   <li>요청 데이터 검증 (@Valid 어노테이션 자동 실행)</li>
     *   <li>중복 주문 ID 확인</li>
     *   <li>가게 존재 여부 확인</li>
     *   <li>플랫폼 수수료 계산 (originalAmount × 0.20)</li>
     *   <li>정산 금액 계산 (originalAmount - platformFee)</li>
     *   <li>정산 엔티티 생성 및 저장</li>
     *   <li>SettlementResponse DTO 반환</li>
     * </ol>
     * 
     * <p>요청 형식:</p>
     * <pre>
     * POST /api/v1/settlements
     * Content-Type: application/json
     * 
     * {
     *   "storeId": "store-uuid-123",
     *   "orderId": "order-456",
     *   "originalAmount": 10000,
     *   "metadata": "{\"paymentMethod\":\"card\"}"
     * }
     * </pre>
     * 
     * <p>응답 형식 (성공 - HTTP 201):</p>
     * <pre>
     * {
     *   "id": "settlement-uuid-789",
     *   "storeId": "store-uuid-123",
     *   "orderId": "order-456",
     *   "originalAmount": 10000,
     *   "platformFeeRate": 0.20,
     *   "platformFee": 2000,
     *   "settlementAmount": 8000,
     *   "status": "PENDING",
     *   "requestedAt": "2024-01-01T10:00:00",
     *   "createdAt": "2024-01-01T10:00:00",
     *   "updatedAt": "2024-01-01T10:00:00"
     * }
     * </pre>
     * 
     * <p>오류 응답:</p>
     * <ul>
     *   <li>HTTP 400: 요청 데이터 검증 실패 (필수 필드 누락, 잘못된 형식 등)</li>
     *   <li>HTTP 400: 중복된 주문 ID</li>
     *   <li>HTTP 404: 존재하지 않는 가게 ID</li>
     *   <li>HTTP 500: 서버 내부 오류 (데이터베이스 오류 등)</li>
     * </ul>
     * 
     * <p>Flutter 사용 예시:</p>
     * <pre>
     * final settlementRequest = SettlementRequest(
     *   storeId: 'store-123',
     *   orderId: 'order-456',
     *   originalAmount: 10000,
     * );
     * 
     * final response = await http.post(
     *   Uri.parse('$baseUrl/api/v1/settlements'),
     *   headers: {'Content-Type': 'application/json'},
     *   body: jsonEncode(settlementRequest.toJson()),
     * );
     * 
     * if (response.statusCode == 201) {
     *   final settlement = SettlementResponse.fromJson(jsonDecode(response.body));
     *   print('정산 생성 완료: ${settlement.id}');
     * }
     * </pre>
     * 
     * @param request 정산 생성 요청 데이터 (자동 검증됨)
     * @return ResponseEntity<SettlementResponse> 생성된 정산 정보
     * @throws IllegalArgumentException 요청 데이터가 유효하지 않은 경우
     * @throws IllegalStateException 중복된 주문 ID인 경우
     * @throws RuntimeException 가게를 찾을 수 없거나 서버 오류인 경우
     */
    @PostMapping
    public ResponseEntity<SettlementResponse> createSettlement(@Valid @RequestBody SettlementRequest request) {
        log.info("정산 생성 요청 수신 - {}", request.toLogString());
        
        try {
            // 요청 데이터 추가 검증
            request.validate();
            
            // 정산 생성 서비스 호출
            SettlementResponse settlement = settlementService.createSettlement(request);
            
            log.info("정산 생성 완료 - 정산 ID: {}, 가게 ID: {}, 주문 ID: {}, 정산 금액: {}", 
                    settlement.getId(), settlement.getStoreId(), settlement.getOrderId(), settlement.getSettlementAmount());
            
            // HTTP 201 Created 응답
            return ResponseEntity.status(HttpStatus.CREATED).body(settlement);
            
        } catch (IllegalArgumentException e) {
            log.warn("정산 생성 실패 - 잘못된 요청 데이터: {}", e.getMessage());
            throw e; // @ControllerAdvice에서 HTTP 400으로 처리
            
        } catch (IllegalStateException e) {
            log.warn("정산 생성 실패 - 비즈니스 규칙 위반: {}", e.getMessage());
            throw e; // @ControllerAdvice에서 HTTP 400으로 처리
            
        } catch (RuntimeException e) {
            log.error("정산 생성 실패 - 서버 오류: {}", e.getMessage(), e);
            throw e; // @ControllerAdvice에서 HTTP 500으로 처리
        }
    }

    /**
     * 정산을 처리하여 토스페이먼츠 지급대행을 실행하는 API 엔드포인트
     * 
     * PENDING 상태의 정산을 토스페이먼츠 지급대행 API를 통해 실제로 처리합니다.
     * 비동기 처리를 고려하여 즉시 응답하며, 실제 처리 결과는 웹훅으로 수신합니다.
     * 
     * <p>처리 과정:</p>
     * <ol>
     *   <li>정산 ID로 정산 정보 조회</li>
     *   <li>정산 상태가 PENDING인지 확인</li>
     *   <li>가게의 토스 셀러 정보 확인</li>
     *   <li>정산 상태를 PROCESSING으로 변경</li>
     *   <li>토스페이먼츠 지급대행 API 호출</li>
     *   <li>처리 결과에 따라 상태 업데이트</li>
     * </ol>
     * 
     * <p>요청 형식:</p>
     * <pre>
     * POST /api/v1/settlements/{settlementId}/process
     * </pre>
     * 
     * <p>응답 형식 (성공 - HTTP 200):</p>
     * <pre>
     * {
     *   "id": "settlement-uuid-789",
     *   "status": "PROCESSING",
     *   "tossPayoutId": "payout-123",
     *   "updatedAt": "2024-01-01T10:05:00"
     * }
     * </pre>
     * 
     * <p>오류 응답:</p>
     * <ul>
     *   <li>HTTP 404: 존재하지 않는 정산 ID</li>
     *   <li>HTTP 400: 처리할 수 없는 상태 (이미 처리됨, 실패함 등)</li>
     *   <li>HTTP 400: 토스 셀러 정보 없음</li>
     *   <li>HTTP 500: 토스페이먼츠 API 호출 실패</li>
     * </ul>
     * 
     * @param settlementId 처리할 정산의 고유 식별자
     * @return ResponseEntity<SettlementResponse> 처리 중인 정산 정보
     * @throws IllegalArgumentException 정산 ID가 유효하지 않은 경우
     * @throws IllegalStateException 처리할 수 없는 상태인 경우
     * @throws RuntimeException 토스페이먼츠 API 호출 실패 시
     */
    @PostMapping("/{settlementId}/process")
    public ResponseEntity<SettlementResponse> processSettlement(@PathVariable String settlementId) {
        log.info("정산 처리 요청 수신 - 정산 ID: {}", settlementId);
        
        try {
            // 정산 처리 서비스 호출 (void 메서드)
            settlementService.processSettlement(settlementId);
            
            // 처리 후 업데이트된 정산 정보 조회
            Settlement updatedSettlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new IllegalStateException("정산 처리 후 정산을 찾을 수 없습니다: " + settlementId));
            
            SettlementResponse settlement = SettlementResponse.from(updatedSettlement);
            
            log.info("정산 처리 시작 완료 - 정산 ID: {}, 상태: {}, 토스 지급대행 ID: {}", 
                    settlement.getId(), settlement.getStatus(), settlement.getTossPayoutId());
            
            return ResponseEntity.ok(settlement);
            
        } catch (IllegalArgumentException e) {
            log.warn("정산 처리 실패 - 잘못된 정산 ID: {}", e.getMessage());
            throw e; // @ControllerAdvice에서 HTTP 404로 처리
            
        } catch (IllegalStateException e) {
            log.warn("정산 처리 실패 - 처리 불가능한 상태: {}", e.getMessage());
            throw e; // @ControllerAdvice에서 HTTP 400으로 처리
            
        } catch (RuntimeException e) {
            log.error("정산 처리 실패 - 서버 오류: {}", e.getMessage(), e);
            throw e; // @ControllerAdvice에서 HTTP 500으로 처리
        }
    }

    /**
     * 정산 내역을 조회하는 API 엔드포인트 (필터링 및 페이징 지원)
     * 
     * 가게별, 날짜별 필터링과 페이징을 지원하는 정산 내역 조회 API입니다.
     * Flutter 앱의 정산 내역 화면에서 사용되며, 무한 스크롤이나 페이지네이션을 지원합니다.
     * 
     * <p>지원하는 필터링:</p>
     * <ul>
     *   <li>storeId: 특정 가게의 정산만 조회</li>
     *   <li>startDate: 시작 날짜 이후의 정산만 조회</li>
     *   <li>endDate: 종료 날짜 이전의 정산만 조회</li>
     * </ul>
     * 
     * <p>페이징 파라미터:</p>
     * <ul>
     *   <li>page: 페이지 번호 (0부터 시작, 기본값: 0)</li>
     *   <li>size: 페이지 크기 (기본값: 20, 최대값: 100)</li>
     *   <li>sort: 정렬 기준 (기본값: createdAt,desc)</li>
     * </ul>
     * 
     * <p>요청 형식:</p>
     * <pre>
     * GET /api/v1/settlements?storeId=store-123&startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59&page=0&size=20
     * </pre>
     * 
     * <p>응답 형식 (성공 - HTTP 200):</p>
     * <pre>
     * {
     *   "content": [
     *     {
     *       "id": "settlement-1",
     *       "storeId": "store-123",
     *       "orderId": "order-456",
     *       "originalAmount": 10000,
     *       "settlementAmount": 8000,
     *       "status": "COMPLETED"
     *     }
     *   ],
     *   "pageable": {
     *     "pageNumber": 0,
     *     "pageSize": 20
     *   },
     *   "totalElements": 150,
     *   "totalPages": 8,
     *   "last": false,
     *   "first": true
     * }
     * </pre>
     * 
     * @param storeId 필터링할 가게 ID (선택사항)
     * @param startDate 조회 시작 날짜 (선택사항)
     * @param endDate 조회 종료 날짜 (선택사항)
     * @param pageable 페이징 정보 (page, size, sort)
     * @return ResponseEntity<Page<SettlementResponse>> 페이징된 정산 내역
     */
    @GetMapping
    public ResponseEntity<Page<SettlementResponse>> getSettlements(
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        
        log.info("정산 내역 조회 요청 - 가게 ID: {}, 시작일: {}, 종료일: {}, 페이지: {}", 
                storeId, startDate, endDate, pageable);
        
        try {
            // 정산 내역 조회 서비스 호출
            Page<SettlementResponse> settlements = settlementService.getSettlements(storeId, startDate, endDate, pageable);
            
            log.info("정산 내역 조회 완료 - 총 건수: {}, 현재 페이지: {}, 페이지 크기: {}", 
                    settlements.getTotalElements(), settlements.getNumber(), settlements.getSize());
            
            return ResponseEntity.ok(settlements);
            
        } catch (Exception e) {
            log.error("정산 내역 조회 실패 - 오류: {}", e.getMessage(), e);
            throw new RuntimeException("정산 내역 조회 중 오류가 발생했습니다", e);
        }
    }

    /**
     * 토스페이먼츠 잔액을 조회하는 API 엔드포인트
     * 
     * 토스페이먼츠 계정의 현재 잔액을 조회하여 Flutter 앱에 제공합니다.
     * 정산 가능한 금액을 확인하거나 대시보드에서 잔액 정보를 표시할 때 사용됩니다.
     * 
     * <p>조회되는 정보:</p>
     * <ul>
     *   <li>사용 가능한 잔액</li>
     *   <li>출금 대기 중인 금액</li>
     *   <li>총 잔액</li>
     *   <li>마지막 업데이트 시간</li>
     * </ul>
     * 
     * <p>요청 형식:</p>
     * <pre>
     * GET /api/v1/settlements/balance
     * </pre>
     * 
     * <p>응답 형식 (성공 - HTTP 200):</p>
     * <pre>
     * {
     *   "availableAmount": 1000000,
     *   "pendingAmount": 50000,
     *   "totalAmount": 1050000,
     *   "currency": "KRW",
     *   "updatedAt": "2024-01-01T10:00:00"
     * }
     * </pre>
     * 
     * @return ResponseEntity<TossBalanceResponse> 토스페이먼츠 잔액 정보
     * @throws RuntimeException 토스페이먼츠 API 호출 실패 시
     */
    @GetMapping("/balance")
    public ResponseEntity<TossBalanceResponse> getBalance() {
        log.info("토스페이먼츠 잔액 조회 요청");
        
        try {
            // 토스페이먼츠 잔액 조회 서비스 호출 (내부 클래스 반환)
            TossPayoutService.TossBalanceResponse serviceBalance = tossPayoutService.getBalance();
            
            // DTO로 변환
            TossBalanceResponse balance = new TossBalanceResponse(
                serviceBalance.getAvailableAmount(),
                serviceBalance.getPendingAmount(),
                serviceBalance.getTotalAmount(),
                LocalDateTime.now() // 현재 시간을 업데이트 시간으로 사용
            );
            
            log.info("토스페이먼츠 잔액 조회 완료 - 사용 가능 금액: {}, 총 금액: {}", 
                    balance.getAvailableAmount(), balance.getTotalAmount());
            
            return ResponseEntity.ok(balance);
            
        } catch (Exception e) {
            log.error("토스페이먼츠 잔액 조회 실패 - 오류: {}", e.getMessage(), e);
            throw new RuntimeException("잔액 조회 중 오류가 발생했습니다", e);
        }
    }

    /**
     * 정산 요약 정보를 조회하는 API 엔드포인트 (대시보드용)
     * 
     * 특정 가게의 특정 날짜에 대한 정산 요약 정보를 제공합니다.
     * Flutter 앱의 대시보드 화면에서 일별/월별 정산 현황을 표시할 때 사용됩니다.
     * 
     * <p>포함되는 정보:</p>
     * <ul>
     *   <li>총 정산 건수</li>
     *   <li>총 정산 금액</li>
     *   <li>상태별 통계 (완료, 대기, 실패)</li>
     *   <li>평균 정산 금액</li>
     *   <li>수수료 총액</li>
     * </ul>
     * 
     * <p>요청 형식:</p>
     * <pre>
     * GET /api/v1/settlements/summary?storeId=store-123&date=2024-01-01
     * </pre>
     * 
     * <p>응답 형식 (성공 - HTTP 200):</p>
     * <pre>
     * {
     *   "storeId": "store-123",
     *   "date": "2024-01-01",
     *   "totalCount": 25,
     *   "totalAmount": 500000,
     *   "completedCount": 20,
     *   "pendingCount": 3,
     *   "failedCount": 2,
     *   "averageAmount": 20000,
     *   "totalFee": 100000
     * }
     * </pre>
     * 
     * @param storeId 조회할 가게 ID (필수)
     * @param date 조회할 날짜 (필수)
     * @return ResponseEntity<SettlementSummaryResponse> 정산 요약 정보
     * @throws IllegalArgumentException 필수 파라미터가 누락된 경우
     * @throws RuntimeException 서버 오류 시
     */
    @GetMapping("/summary")
    public ResponseEntity<SettlementSummaryResponse> getSettlementSummary(
            @RequestParam String storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        log.info("정산 요약 정보 조회 요청 - 가게 ID: {}, 날짜: {}", storeId, date);
        
        try {
            // 입력값 검증
            if (storeId == null || storeId.trim().isEmpty()) {
                throw new IllegalArgumentException("가게 ID는 필수입니다");
            }
            if (date == null) {
                throw new IllegalArgumentException("조회 날짜는 필수입니다");
            }
            
            // 정산 요약 정보 조회 서비스 호출
            SettlementSummaryResponse summary = settlementService.getSettlementSummary(storeId, date);
            
            log.info("정산 요약 정보 조회 완료 - 가게 ID: {}, 날짜: {}, 총 건수: {}, 총 금액: {}", 
                    storeId, date, summary.getTotalCount(), summary.getTotalSettlementAmount());
            
            return ResponseEntity.ok(summary);
            
        } catch (IllegalArgumentException e) {
            log.warn("정산 요약 정보 조회 실패 - 잘못된 요청: {}", e.getMessage());
            throw e; // @ControllerAdvice에서 HTTP 400으로 처리
            
        } catch (Exception e) {
            log.error("정산 요약 정보 조회 실패 - 오류: {}", e.getMessage(), e);
            throw new RuntimeException("정산 요약 정보 조회 중 오류가 발생했습니다", e);
        }
    }
}
