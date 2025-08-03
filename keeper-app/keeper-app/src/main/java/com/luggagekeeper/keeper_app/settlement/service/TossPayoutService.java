package com.luggagekeeper.keeper_app.settlement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luggagekeeper.keeper_app.settlement.domain.Settlement;
import com.luggagekeeper.keeper_app.settlement.domain.TossBusinessType;
import com.luggagekeeper.keeper_app.settlement.domain.TossSeller;
import com.luggagekeeper.keeper_app.settlement.domain.TossSellerStatus;
import com.luggagekeeper.keeper_app.settlement.dto.TossSellerRequest;
import com.luggagekeeper.keeper_app.settlement.dto.TossSellerResponse;
import com.luggagekeeper.keeper_app.settlement.repository.TossSellerRepository;
import com.luggagekeeper.keeper_app.store.domain.Store;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 토스페이먼츠 지급대행 API 연동 서비스
 * 
 * 토스페이먼츠의 지급대행 서비스와 연동하여 다음 기능을 제공합니다:
 * - 셀러 등록 및 관리
 * - 정산 잔액 조회
 * - 지급대행 요청 및 처리
 * - 지급대행 상태 조회 및 취소
 * 
 * <p>주요 특징:</p>
 * <ul>
 *   <li>JWE 암호화를 통한 보안 통신 (ENCRYPTION 모드)</li>
 *   <li>WebClient를 사용한 비동기 HTTP 통신</li>
 *   <li>자동 재시도 및 오류 처리</li>
 *   <li>멱등키를 통한 중복 요청 방지</li>
 *   <li>상세한 로깅 및 모니터링</li>
 * </ul>
 * 
 * <p>보안 고려사항:</p>
 * <ul>
 *   <li>모든 요청 데이터는 JWE로 암호화</li>
 *   <li>API 키는 환경변수로 관리</li>
 *   <li>민감한 정보는 로그에 출력하지 않음</li>
 *   <li>HTTPS 통신 강제</li>
 * </ul>
 * 
 * <p>사용 예시:</p>
 * <pre>
 * // 셀러 등록
 * TossSellerResponse seller = tossPayoutService.registerSeller(store);
 * 
 * // 잔액 조회
 * TossBalanceResponse balance = tossPayoutService.getBalance();
 * 
 * // 지급대행 요청
 * TossPayoutResponse payout = tossPayoutService.requestPayout(settlement);
 * </pre>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 * @see JweEncryptionService
 * @see Settlement
 * @see TossSeller
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TossPayoutService {

    private final WebClient webClient;
    private final JweEncryptionService jweEncryptionService;
    private final ObjectMapper objectMapper;
    private final TossSellerRepository tossSellerRepository;

    /**
     * 토스페이먼츠 API 기본 URL
     * 운영 환경에서는 https://api.tosspayments.com 사용
     */
    @Value("${toss.api.base-url}")
    private String baseUrl;

    /**
     * 토스페이먼츠 시크릿 키
     * Basic 인증에 사용되며, Base64 인코딩하여 Authorization 헤더에 포함
     */
    @Value("${toss.api.secret-key}")
    private String secretKey;

    /**
     * 토스페이먼츠 클라이언트 ID
     * API 호출 시 클라이언트 식별에 사용
     */
    @Value("${toss.api.client-id}")
    private String clientId;

    /**
     * HTTP 연결 타임아웃 (밀리초)
     * 기본값: 30초
     */
    @Value("${toss.api.connection-timeout:30000}")
    private int connectionTimeout;

    /**
     * HTTP 읽기 타임아웃 (밀리초)
     * 기본값: 30초
     */
    @Value("${toss.api.read-timeout:30000}")
    private int readTimeout;

    /**
     * 최대 재시도 횟수
     * 네트워크 오류나 일시적 서버 오류 시 자동 재시도
     * 기본값: 3회
     */
    @Value("${toss.api.max-retry-attempts:3}")
    private int maxRetryAttempts;

    /**
     * 토스페이먼츠 셀러 등록 API 연동
     * 
     * 가게를 토스페이먼츠 지급대행 서비스의 셀러로 등록합니다.
     * JWE 암호화를 적용하여 보안 통신을 수행하며,
     * 등록 성공 시 토스 셀러 ID를 반환받아 데이터베이스에 저장합니다.
     * 
     * <p>API 호출 과정:</p>
     * <ol>
     *   <li>기존 셀러 등록 여부 확인</li>
     *   <li>TossSeller 엔티티 생성 및 저장</li>
     *   <li>셀러 등록 요청 데이터 생성</li>
     *   <li>JWE 암호화 수행</li>
     *   <li>토스페이먼츠 API 호출 (POST /v1/payouts/sellers)</li>
     *   <li>응답 복호화 및 검증</li>
     *   <li>TossSeller 엔티티 업데이트 (토스 셀러 ID 할당)</li>
     * </ol>
     * 
     * <p>중복 등록 방지:</p>
     * <ul>
     *   <li>동일한 가게가 이미 등록된 경우 기존 정보 반환</li>
     *   <li>등록 실패 시 생성된 엔티티는 롤백 처리</li>
     * </ul>
     * 
     * <p>오류 처리:</p>
     * <ul>
     *   <li>네트워크 오류: 자동 재시도 (최대 3회)</li>
     *   <li>인증 오류: TossApiException 발생</li>
     *   <li>비즈니스 오류: 상세 오류 코드와 함께 예외 발생</li>
     * </ul>
     * 
     * @param store 셀러로 등록할 가게 정보 (null 불가)
     * @return 등록된 셀러 정보가 포함된 응답 DTO
     * @throws IllegalArgumentException store가 null이거나 필수 정보가 누락된 경우
     * @throws TossApiException 토스페이먼츠 API 호출 실패 시
     * @throws JweEncryptionService.EncryptionException JWE 암호화/복호화 실패 시
     */
    @Transactional
    public TossSellerResponse registerSeller(Store store) {
        if (store == null) {
            throw new IllegalArgumentException("가게 정보는 필수입니다");
        }
        if (store.getId() == null || store.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("가게 ID는 필수입니다");
        }

        log.info("토스 셀러 등록 시작 - 가게 ID: {}, 가게명: {}", store.getId(), store.getName());

        try {
            // 1. 기존 셀러 등록 여부 확인
            Optional<TossSeller> existingSeller = tossSellerRepository.findByStoreId(store.getId());
            if (existingSeller.isPresent()) {
                TossSeller seller = existingSeller.get();
                log.info("이미 등록된 셀러 발견 - 가게 ID: {}, 토스 셀러 ID: {}, 상태: {}", 
                        store.getId(), seller.getTossSellerId(), seller.getStatus());
                return TossSellerResponse.from(seller);
            }

            // 2. 새로운 TossSeller 엔티티 생성 및 저장
            // 기본값으로 개인사업자 타입 설정 (실제로는 가게 정보에서 가져와야 함)
            TossBusinessType businessType = TossBusinessType.INDIVIDUAL_BUSINESS;
            TossSeller tossSeller = TossSeller.createTossSeller(store, businessType);
            tossSeller = tossSellerRepository.save(tossSeller);
            
            log.debug("TossSeller 엔티티 생성 완료 - ID: {}, 참조 셀러 ID: {}", 
                     tossSeller.getId(), tossSeller.getRefSellerId());

            // 3. 셀러 등록 요청 데이터 생성
            Map<String, Object> requestData = createSellerRegistrationRequest(store, tossSeller);
            log.debug("셀러 등록 요청 데이터 생성 완료 - 가게 ID: {}", store.getId());

            // 4. JWE 암호화 수행
            String encryptedRequest = jweEncryptionService.encrypt(requestData);
            log.debug("JWE 암호화 완료 - 가게 ID: {}, 암호화된 데이터 길이: {} characters", 
                     store.getId(), encryptedRequest.length());

            // 5. 토스페이먼츠 API 호출
            String response = callTossApi("/v1/payouts/sellers", "POST", encryptedRequest)
                    .block(Duration.ofSeconds(readTimeout / 1000));

            // 6. 응답 복호화
            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = jweEncryptionService.decrypt(response, Map.class);
            log.debug("응답 복호화 완료 - 가게 ID: {}", store.getId());

            // 7. 토스 셀러 ID 할당 및 상태 업데이트
            String tossSellerId = (String) responseData.get("sellerId");
            if (tossSellerId == null || tossSellerId.trim().isEmpty()) {
                throw new TossApiException("토스페이먼츠 응답에서 셀러 ID를 찾을 수 없습니다");
            }

            tossSeller.assignTossId(tossSellerId);
            
            // 응답에서 상태 정보가 있다면 업데이트
            String statusStr = (String) responseData.get("status");
            if (statusStr != null) {
                try {
                    TossSellerStatus status = TossSellerStatus.valueOf(statusStr);
                    tossSeller.updateStatus(status);
                } catch (IllegalArgumentException e) {
                    log.warn("알 수 없는 셀러 상태: {} - 기본값 유지", statusStr);
                }
            }

            // 8. 업데이트된 엔티티 저장
            tossSeller = tossSellerRepository.save(tossSeller);
            
            log.info("토스 셀러 등록 완료 - 가게 ID: {}, 토스 셀러 ID: {}, 상태: {}", 
                    store.getId(), tossSeller.getTossSellerId(), tossSeller.getStatus());

            return TossSellerResponse.from(tossSeller);

        } catch (Exception e) {
            log.error("토스 셀러 등록 실패 - 가게 ID: {}, 오류: {}", 
                     store.getId(), e.getMessage(), e);
            
            // 특정 예외 타입별 처리
            if (e instanceof JweEncryptionService.EncryptionException) {
                throw new TossApiException("JWE 암호화 처리 중 오류가 발생했습니다", e);
            } else if (e instanceof TossApiException) {
                throw e; // 이미 TossApiException인 경우 그대로 전파
            } else {
                throw new TossApiException("셀러 등록에 실패했습니다", e);
            }
        }
    }

    /**
     * 토스페이먼츠 정산 잔액 조회 API 연동
     * 
     * 현재 지급 가능한 잔액과 대기 중인 금액을 조회합니다.
     * 정산 처리 전 잔액 확인이나 대시보드 표시에 사용됩니다.
     * 
     * <p>API 호출 과정:</p>
     * <ol>
     *   <li>토스페이먼츠 잔액 조회 API 호출 (GET /v1/payouts/balance)</li>
     *   <li>응답 데이터 파싱 및 검증</li>
     *   <li>TossBalanceResponse DTO로 변환</li>
     *   <li>총 잔액 계산 (available + pending)</li>
     * </ol>
     * 
     * <p>조회되는 정보:</p>
     * <ul>
     *   <li>availableAmount: 즉시 지급 가능한 금액</li>
     *   <li>pendingAmount: 정산 대기 중인 금액</li>
     *   <li>totalAmount: 전체 잔액 (available + pending)</li>
     *   <li>lastUpdatedAt: 잔액 정보 최종 업데이트 시간</li>
     * </ul>
     * 
     * <p>캐싱 고려사항:</p>
     * <ul>
     *   <li>잔액 정보는 실시간성이 중요하므로 캐싱하지 않음</li>
     *   <li>빈번한 조회 시 토스페이먼츠 API 제한에 주의</li>
     * </ul>
     * 
     * @return 잔액 정보가 포함된 응답 DTO
     * @throws TossApiException 토스페이먼츠 API 호출 실패 시
     */
    public TossBalanceResponse getBalance() {
        log.info("토스 잔액 조회 시작");

        try {
            // 1. 토스페이먼츠 잔액 조회 API 호출
            // 잔액 조회는 GET 요청이므로 요청 본문 없음
            String response = callTossApi("/v1/payouts/balance", "GET", null)
                    .block(Duration.ofSeconds(readTimeout / 1000));

            // 2. 응답 데이터 파싱
            // 잔액 조회 API는 일반적으로 암호화되지 않은 JSON 응답을 반환
            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = objectMapper.readValue(response, Map.class);
            log.debug("잔액 조회 응답 수신 완료 - 응답 필드 수: {}", responseData.size());

            // 3. 응답 데이터 검증
            if (!responseData.containsKey("availableAmount") || !responseData.containsKey("pendingAmount")) {
                throw new TossApiException("잔액 조회 응답에 필수 필드가 누락되었습니다");
            }

            // 4. 응답 데이터를 DTO로 변환
            TossBalanceResponse balanceResponse = mapToBalanceResponse(responseData);
            
            log.info("토스 잔액 조회 완료 - 지급가능: {}, 대기중: {}, 총액: {}", 
                    balanceResponse.getAvailableAmount(), 
                    balanceResponse.getPendingAmount(),
                    balanceResponse.getTotalAmount());

            return balanceResponse;

        } catch (Exception e) {
            log.error("토스 잔액 조회 실패 - 오류: {}", e.getMessage(), e);
            
            // 특정 예외 타입별 처리
            if (e instanceof TossApiException) {
                throw (TossApiException) e; // 이미 TossApiException인 경우 그대로 전파
            } else if (e.getClass().getSimpleName().equals("JsonProcessingException")) {
                throw new TossApiException("잔액 조회 응답 파싱에 실패했습니다", e);
            } else {
                throw new TossApiException("잔액 조회에 실패했습니다", e);
            }
        }
    }

    /**
     * 토스페이먼츠 지급대행 요청 API 연동
     * 
     * 정산 금액을 가게 계좌로 송금하는 지급대행을 요청합니다.
     * JWE 암호화를 적용하여 보안 통신을 수행하며,
     * 멱등키를 사용하여 중복 요청을 방지합니다.
     * 
     * <p>지급대행 요청 과정:</p>
     * <ol>
     *   <li>정산 정보 및 토스 셀러 정보 검증</li>
     *   <li>지급대행 요청 데이터 생성 (멱등키 포함)</li>
     *   <li>JWE 암호화 수행</li>
     *   <li>토스페이먼츠 API 호출 (POST /v1/payouts)</li>
     *   <li>응답 복호화 및 검증</li>
     *   <li>TossPayoutResponse DTO 생성 및 반환</li>
     * </ol>
     * 
     * <p>멱등키 처리:</p>
     * <ul>
     *   <li>정산 ID를 멱등키로 사용하여 중복 요청 방지</li>
     *   <li>동일한 정산 ID로 중복 요청 시 기존 결과 반환</li>
     *   <li>네트워크 오류로 인한 재시도 시 중복 처리 방지</li>
     * </ul>
     * 
     * <p>오류 처리:</p>
     * <ul>
     *   <li>잔액 부족: InsufficientBalanceException 발생</li>
     *   <li>셀러 정보 없음: TossApiException 발생</li>
     *   <li>네트워크 오류: 자동 재시도 후 예외 발생</li>
     * </ul>
     * 
     * @param settlement 지급대행을 요청할 정산 정보 (null 불가)
     * @return 지급대행 요청 결과가 포함된 응답 DTO
     * @throws IllegalArgumentException settlement가 null이거나 필수 정보가 누락된 경우
     * @throws TossApiException 토스페이먼츠 API 호출 실패 시
     * @throws InsufficientBalanceException 잔액 부족 시
     */
    public TossPayoutResponse requestPayout(Settlement settlement) {
        if (settlement == null) {
            throw new IllegalArgumentException("정산 정보는 필수입니다");
        }
        if (settlement.getId() == null || settlement.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("정산 ID는 필수입니다");
        }
        if (settlement.getSettlementAmount() == null || 
            settlement.getSettlementAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("정산 금액은 0보다 커야 합니다");
        }
        if (settlement.getStore() == null) {
            throw new IllegalArgumentException("가게 정보는 필수입니다");
        }

        log.info("토스 지급대행 요청 시작 - 정산 ID: {}, 가게 ID: {}, 금액: {}", 
                settlement.getId(), settlement.getStore().getId(), settlement.getSettlementAmount());

        try {
            // 1. 토스 셀러 정보 조회 및 검증
            Optional<TossSeller> tossSellerOpt = tossSellerRepository.findByStoreId(settlement.getStore().getId());
            if (tossSellerOpt.isEmpty()) {
                throw new TossApiException("토스 셀러 정보를 찾을 수 없습니다. 가게 ID: " + settlement.getStore().getId());
            }

            TossSeller tossSeller = tossSellerOpt.get();
            if (!tossSeller.canProcessPayout()) {
                throw new TossApiException(String.format(
                    "지급대행을 처리할 수 없는 셀러 상태입니다. 셀러 ID: %s, 상태: %s", 
                    tossSeller.getId(), tossSeller.getStatus()));
            }

            log.debug("토스 셀러 검증 완료 - 셀러 ID: {}, 토스 셀러 ID: {}, 상태: {}", 
                     tossSeller.getId(), tossSeller.getTossSellerId(), tossSeller.getStatus());

            // 2. 지급대행 요청 데이터 생성
            Map<String, Object> requestData = createPayoutRequest(settlement, tossSeller);
            log.debug("지급대행 요청 데이터 생성 완료 - 정산 ID: {}", settlement.getId());

            // 3. JWE 암호화 수행
            String encryptedRequest = jweEncryptionService.encrypt(requestData);
            log.debug("JWE 암호화 완료 - 정산 ID: {}, 암호화된 데이터 길이: {} characters", 
                     settlement.getId(), encryptedRequest.length());

            // 4. 토스페이먼츠 API 호출
            String response = callTossApi("/v1/payouts", "POST", encryptedRequest)
                    .block(Duration.ofSeconds(readTimeout / 1000));

            // 5. 응답 복호화
            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = jweEncryptionService.decrypt(response, Map.class);
            log.debug("응답 복호화 완료 - 정산 ID: {}", settlement.getId());

            // 6. 응답 데이터 검증
            if (!responseData.containsKey("payoutId")) {
                throw new TossApiException("지급대행 응답에서 payoutId를 찾을 수 없습니다");
            }

            // 7. 응답 데이터를 DTO로 변환
            TossPayoutResponse payoutResponse = mapToPayoutResponse(responseData, settlement);
            
            log.info("토스 지급대행 요청 완료 - 정산 ID: {}, 지급대행 ID: {}, 상태: {}", 
                    settlement.getId(), payoutResponse.getPayoutId(), payoutResponse.getStatus());

            return payoutResponse;

        } catch (Exception e) {
            log.error("토스 지급대행 요청 실패 - 정산 ID: {}, 오류: {}", 
                     settlement.getId(), e.getMessage(), e);
            
            // 특정 오류 타입별 처리
            if (e instanceof JweEncryptionService.EncryptionException) {
                throw new TossApiException("JWE 암호화 처리 중 오류가 발생했습니다", e);
            } else if (e instanceof InsufficientBalanceException || e instanceof TossApiException) {
                throw e; // 이미 적절한 예외 타입인 경우 그대로 전파
            } else if (e.getMessage() != null && e.getMessage().contains("INSUFFICIENT_BALANCE")) {
                throw new InsufficientBalanceException("지급 가능한 잔액이 부족합니다", e);
            } else {
                throw new TossApiException("지급대행 요청에 실패했습니다", e);
            }
        }
    }

    /**
     * 토스페이먼츠 지급대행 취소 API 연동
     * 
     * 처리 중인 지급대행을 취소합니다.
     * 완료된 지급대행은 취소할 수 없으며,
     * 처리 중인 상태에서만 취소가 가능합니다.
     * 
     * @param tossPayoutId 취소할 토스 지급대행 ID (null 불가)
     * @throws IllegalArgumentException tossPayoutId가 null이거나 빈 문자열인 경우
     * @throws TossApiException 토스페이먼츠 API 호출 실패 시
     */
    public void cancelPayout(String tossPayoutId) {
        if (tossPayoutId == null || tossPayoutId.trim().isEmpty()) {
            throw new IllegalArgumentException("토스 지급대행 ID는 필수입니다");
        }

        log.info("토스 지급대행 취소 시작 - 지급대행 ID: {}", tossPayoutId);

        try {
            // 1. 지급대행 취소 API 호출
            String response = callTossApi("/v1/payouts/" + tossPayoutId + "/cancel", "POST", null)
                    .block(Duration.ofSeconds(readTimeout / 1000));

            log.info("토스 지급대행 취소 완료 - 지급대행 ID: {}", tossPayoutId);

        } catch (Exception e) {
            log.error("토스 지급대행 취소 실패 - 지급대행 ID: {}, 오류: {}", 
                     tossPayoutId, e.getMessage(), e);
            throw new TossApiException("지급대행 취소에 실패했습니다", e);
        }
    }

    /**
     * 토스페이먼츠 API 공통 호출 메서드
     * 
     * 모든 토스페이먼츠 API 호출에 공통으로 사용되는 메서드입니다.
     * 인증 헤더 설정, 재시도 로직, 오류 처리를 담당합니다.
     * 
     * <p>공통 처리 사항:</p>
     * <ul>
     *   <li>Basic 인증 헤더 자동 설정</li>
     *   <li>Content-Type 및 Accept 헤더 설정</li>
     *   <li>네트워크 오류 시 자동 재시도</li>
     *   <li>HTTP 상태 코드별 오류 처리</li>
     * </ul>
     * 
     * @param endpoint API 엔드포인트 경로 (예: "/v1/payouts/sellers")
     * @param method HTTP 메서드 ("GET", "POST", "PUT", "DELETE")
     * @param requestBody 요청 본문 (GET 요청 시 null 가능)
     * @return API 응답 본문을 포함한 Mono
     * @throws TossApiException API 호출 실패 시
     */
    private Mono<String> callTossApi(String endpoint, String method, String requestBody) {
        log.debug("토스 API 호출 시작 - 엔드포인트: {}, 메서드: {}", endpoint, method);

        // Basic 인증 헤더 생성
        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes());

        // WebClient 요청 빌더 생성
        WebClient.RequestBodySpec requestSpec = webClient
                .method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(baseUrl + endpoint)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header("TossPayments-Test-Code", "NORMAL") // 테스트 환경용 헤더
                .header("User-Agent", "LuggageKeeper-Settlement/1.0");

        // 요청 본문이 있는 경우 설정
        Mono<String> responseMono;
        if (requestBody != null && !requestBody.trim().isEmpty()) {
            responseMono = requestSpec
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class);
        } else {
            responseMono = requestSpec
                    .retrieve()
                    .bodyToMono(String.class);
        }

        // 재시도 로직 적용
        return responseMono
                .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofSeconds(1))
                        .filter(this::isRetryableException)
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                            log.error("토스 API 호출 재시도 한도 초과 - 엔드포인트: {}, 시도 횟수: {}", 
                                     endpoint, retrySignal.totalRetries());
                            return new TossApiException("API 호출 재시도 한도를 초과했습니다");
                        }))
                .doOnSuccess(response -> log.debug("토스 API 호출 성공 - 엔드포인트: {}", endpoint))
                .doOnError(error -> log.error("토스 API 호출 실패 - 엔드포인트: {}, 오류: {}", 
                                             endpoint, error.getMessage()));
    }

    /**
     * 재시도 가능한 예외인지 판단
     * 
     * 네트워크 오류나 일시적 서버 오류는 재시도하지만,
     * 인증 오류나 비즈니스 로직 오류는 재시도하지 않습니다.
     * 
     * @param throwable 발생한 예외
     * @return true: 재시도 가능, false: 재시도 불가
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException ex = (WebClientResponseException) throwable;
            int statusCode = ex.getStatusCode().value();
            
            // 5xx 서버 오류는 재시도, 4xx 클라이언트 오류는 재시도하지 않음
            return statusCode >= 500;
        }
        
        // 네트워크 연결 오류 등은 재시도
        return throwable instanceof java.net.ConnectException ||
               throwable instanceof java.net.SocketTimeoutException ||
               throwable instanceof java.io.IOException;
    }

    /**
     * 셀러 등록 요청 데이터 생성
     * 
     * 토스페이먼츠 셀러 등록 API에 전송할 요청 데이터를 생성합니다.
     * JWE 암호화 전에 호출되며, 토스페이먼츠 API 스펙에 맞는 형식으로 데이터를 구성합니다.
     * 
     * <p>포함되는 데이터:</p>
     * <ul>
     *   <li>refSellerId: 우리 시스템의 셀러 참조 ID</li>
     *   <li>businessType: 사업자 타입 (개인사업자/법인사업자)</li>
     *   <li>storeName: 가게명</li>
     *   <li>storeAddress: 가게 주소 (선택사항)</li>
     *   <li>contactInfo: 연락처 정보 (선택사항)</li>
     * </ul>
     * 
     * @param store 등록할 가게 정보 (null 불가)
     * @param tossSeller 생성된 TossSeller 엔티티 (null 불가)
     * @return 토스페이먼츠 API 요청 형식의 데이터 맵
     * @throws IllegalArgumentException 필수 파라미터가 null인 경우
     */
    private Map<String, Object> createSellerRegistrationRequest(Store store, TossSeller tossSeller) {
        if (store == null) {
            throw new IllegalArgumentException("가게 정보는 필수입니다");
        }
        if (tossSeller == null) {
            throw new IllegalArgumentException("토스 셀러 정보는 필수입니다");
        }

        Map<String, Object> request = new HashMap<>();
        
        // 1. 필수 필드 설정
        request.put("refSellerId", tossSeller.getRefSellerId());
        request.put("businessType", tossSeller.getBusinessType().name());
        request.put("storeName", store.getName());
        
        // 2. 선택적 필드 설정 (가게 정보에서 추출)
        if (store.getAddress() != null && !store.getAddress().trim().isEmpty()) {
            request.put("storeAddress", store.getAddress());
        }
        
        // 3. 연락처 정보 (가게 정보에서 추출 가능한 경우)
        Map<String, Object> contactInfo = new HashMap<>();
        if (store.getPhoneNumber() != null && !store.getPhoneNumber().trim().isEmpty()) {
            contactInfo.put("phoneNumber", store.getPhoneNumber());
        }
        if (store.getEmail() != null && !store.getEmail().trim().isEmpty()) {
            contactInfo.put("email", store.getEmail());
        }
        if (!contactInfo.isEmpty()) {
            request.put("contactInfo", contactInfo);
        }
        
        // 4. 메타데이터 (추적 및 디버깅용)
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("systemVersion", "1.0");
        metadata.put("registrationSource", "LuggageKeeper-Backend");
        metadata.put("timestamp", java.time.Instant.now().toString());
        request.put("metadata", metadata);
        
        log.debug("셀러 등록 요청 데이터 생성 - 참조 ID: {}, 사업자 타입: {}, 가게명: {}", 
                 tossSeller.getRefSellerId(), tossSeller.getBusinessType(), store.getName());
        
        return request;
    }

    /**
     * 지급대행 요청 데이터 생성
     * 
     * 토스페이먼츠 지급대행 API에 전송할 요청 데이터를 생성합니다.
     * JWE 암호화 전에 호출되며, 토스페이먼츠 API 스펙에 맞는 형식으로 데이터를 구성합니다.
     * 
     * <p>포함되는 데이터:</p>
     * <ul>
     *   <li>refPayoutId: 멱등키로 사용되는 우리 시스템의 정산 ID</li>
     *   <li>destination: 수취인 정보 (토스 셀러 ID)</li>
     *   <li>amount: 지급할 금액</li>
     *   <li>scheduleType: 지급 일정 (EXPRESS: 즉시 지급)</li>
     *   <li>transactionDescription: 거래 설명</li>
     *   <li>metadata: 추가 메타데이터 (추적용)</li>
     * </ul>
     * 
     * <p>멱등키 처리:</p>
     * <ul>
     *   <li>refPayoutId를 멱등키로 사용하여 중복 요청 방지</li>
     *   <li>동일한 정산 ID로 재요청 시 기존 결과 반환</li>
     * </ul>
     * 
     * @param settlement 정산 정보 (null 불가)
     * @param tossSeller 토스 셀러 정보 (null 불가)
     * @return 토스페이먼츠 API 요청 형식의 데이터 맵
     * @throws IllegalArgumentException 필수 파라미터가 null인 경우
     */
    private Map<String, Object> createPayoutRequest(Settlement settlement, TossSeller tossSeller) {
        if (settlement == null) {
            throw new IllegalArgumentException("정산 정보는 필수입니다");
        }
        if (tossSeller == null) {
            throw new IllegalArgumentException("토스 셀러 정보는 필수입니다");
        }

        Map<String, Object> request = new HashMap<>();
        
        // 1. 필수 필드 설정
        request.put("refPayoutId", settlement.getId()); // 멱등키로 사용
        request.put("destination", tossSeller.getTossSellerId()); // 수취인 (토스 셀러 ID)
        request.put("amount", settlement.getSettlementAmount()); // 지급 금액
        request.put("scheduleType", "EXPRESS"); // 즉시 지급
        
        // 2. 거래 설명 생성
        String description = String.format("짐보관 서비스 정산 - 주문 ID: %s", 
                                         settlement.getOrderId() != null ? settlement.getOrderId() : "N/A");
        request.put("transactionDescription", description);
        
        // 3. 추가 정보 설정
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("storeId", settlement.getStore().getId());
        additionalInfo.put("storeName", settlement.getStore().getName());
        additionalInfo.put("originalAmount", settlement.getOriginalAmount());
        additionalInfo.put("platformFee", settlement.getPlatformFee());
        request.put("additionalInfo", additionalInfo);
        
        // 4. 메타데이터 (추적 및 디버깅용)
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("systemVersion", "1.0");
        metadata.put("payoutSource", "LuggageKeeper-Settlement");
        metadata.put("timestamp", java.time.Instant.now().toString());
        metadata.put("settlementId", settlement.getId());
        metadata.put("businessType", tossSeller.getBusinessType().name());
        request.put("metadata", metadata);
        
        // 5. 알림 설정 (선택사항)
        Map<String, Object> notification = new HashMap<>();
        notification.put("webhookUrl", "/api/webhooks/toss/payout-changed"); // 웹훅 URL
        notification.put("notifyOnComplete", true);
        notification.put("notifyOnFailed", true);
        request.put("notification", notification);
        
        log.debug("지급대행 요청 데이터 생성 - 멱등키: {}, 수취인: {}, 금액: {}, 일정: {}", 
                 settlement.getId(), tossSeller.getTossSellerId(), 
                 settlement.getSettlementAmount(), "EXPRESS");
        
        return request;
    }

    /**
     * 셀러 등록 응답을 DTO로 변환
     * 
     * 토스페이먼츠 API 응답 데이터를 우리 시스템의 TossSellerResponse DTO로 변환합니다.
     * 이 메서드는 registerSeller 메서드에서 사용되지 않으며,
     * 향후 다른 API 호출에서 사용할 수 있도록 유지됩니다.
     * 
     * @param responseData 토스페이먼츠 API 응답 데이터
     * @param store 가게 정보
     * @return 변환된 TossSellerResponse DTO
     * @deprecated 현재 registerSeller에서는 TossSellerResponse.from() 사용
     */
    @Deprecated
    private TossSellerResponse mapToSellerResponse(Map<String, Object> responseData, Store store) {
        // 실제 토스페이먼츠 API 응답 구조에 따라 구현
        TossSellerResponse response = new TossSellerResponse();
        response.setTossSellerId((String) responseData.get("sellerId"));
        response.setStoreId(store.getId());
        
        // 추가 응답 필드 매핑
        if (responseData.containsKey("status")) {
            String statusStr = (String) responseData.get("status");
            try {
                TossSellerStatus status = TossSellerStatus.valueOf(statusStr);
                response.setStatus(status);
            } catch (IllegalArgumentException e) {
                log.warn("알 수 없는 셀러 상태: {}", statusStr);
                response.setStatus(TossSellerStatus.APPROVAL_REQUIRED);
            }
        }
        
        if (responseData.containsKey("businessType")) {
            String businessTypeStr = (String) responseData.get("businessType");
            try {
                TossBusinessType businessType = TossBusinessType.valueOf(businessTypeStr);
                response.setBusinessType(businessType);
            } catch (IllegalArgumentException e) {
                log.warn("알 수 없는 사업자 타입: {}", businessTypeStr);
                response.setBusinessType(TossBusinessType.INDIVIDUAL_BUSINESS);
            }
        }
        
        return response;
    }

    /**
     * 잔액 조회 응답을 DTO로 변환
     * 
     * 토스페이먼츠 API 응답 데이터를 우리 시스템의 TossBalanceResponse DTO로 변환합니다.
     * 금액 데이터의 정확성을 위해 BigDecimal을 사용하며, null 값에 대한 안전한 처리를 수행합니다.
     * 
     * <p>변환 과정:</p>
     * <ol>
     *   <li>availableAmount 추출 및 BigDecimal 변환</li>
     *   <li>pendingAmount 추출 및 BigDecimal 변환</li>
     *   <li>totalAmount 계산 (available + pending)</li>
     *   <li>추가 메타데이터 설정 (업데이트 시간 등)</li>
     * </ol>
     * 
     * <p>오류 처리:</p>
     * <ul>
     *   <li>null 값은 BigDecimal.ZERO로 처리</li>
     *   <li>숫자 변환 실패 시 예외 발생</li>
     *   <li>음수 값 검증 및 경고 로그</li>
     * </ul>
     * 
     * @param responseData 토스페이먼츠 API 응답 데이터 (null 불가)
     * @return 변환된 TossBalanceResponse DTO
     * @throws IllegalArgumentException responseData가 null이거나 필수 필드가 누락된 경우
     * @throws NumberFormatException 금액 데이터 변환 실패 시
     */
    private TossBalanceResponse mapToBalanceResponse(Map<String, Object> responseData) {
        if (responseData == null) {
            throw new IllegalArgumentException("응답 데이터가 null입니다");
        }

        TossBalanceResponse response = new TossBalanceResponse();

        try {
            // 1. 지급 가능한 금액 추출 및 변환
            Object availableAmountObj = responseData.get("availableAmount");
            BigDecimal availableAmount = convertToBigDecimal(availableAmountObj, "availableAmount");
            response.setAvailableAmount(availableAmount);

            // 2. 대기 중인 금액 추출 및 변환
            Object pendingAmountObj = responseData.get("pendingAmount");
            BigDecimal pendingAmount = convertToBigDecimal(pendingAmountObj, "pendingAmount");
            response.setPendingAmount(pendingAmount);

            // 3. 총 잔액 계산
            BigDecimal totalAmount = availableAmount.add(pendingAmount);
            response.setTotalAmount(totalAmount);

            // 4. 음수 값 검증 및 경고
            if (availableAmount.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("지급 가능한 금액이 음수입니다: {}", availableAmount);
            }
            if (pendingAmount.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("대기 중인 금액이 음수입니다: {}", pendingAmount);
            }

            log.debug("잔액 응답 변환 완료 - 지급가능: {}, 대기중: {}, 총액: {}", 
                     availableAmount, pendingAmount, totalAmount);

            return response;

        } catch (NumberFormatException e) {
            log.error("잔액 데이터 변환 실패 - 응답 데이터: {}", responseData, e);
            throw new TossApiException("잔액 데이터 형식이 올바르지 않습니다", e);
        }
    }

    /**
     * 객체를 BigDecimal로 안전하게 변환
     * 
     * null 값이나 빈 문자열을 BigDecimal.ZERO로 처리하고,
     * 다양한 숫자 형식을 BigDecimal로 변환합니다.
     * 
     * @param value 변환할 값 (Number, String, null 가능)
     * @param fieldName 필드명 (오류 메시지용)
     * @return 변환된 BigDecimal 값
     * @throws NumberFormatException 변환 실패 시
     */
    private BigDecimal convertToBigDecimal(Object value, String fieldName) {
        if (value == null) {
            log.debug("{} 필드가 null입니다. BigDecimal.ZERO로 설정합니다.", fieldName);
            return BigDecimal.ZERO;
        }

        if (value instanceof Number) {
            // Number 타입인 경우 직접 변환
            return new BigDecimal(value.toString());
        } else if (value instanceof String) {
            String strValue = (String) value;
            if (strValue.trim().isEmpty()) {
                log.debug("{} 필드가 빈 문자열입니다. BigDecimal.ZERO로 설정합니다.", fieldName);
                return BigDecimal.ZERO;
            }
            return new BigDecimal(strValue);
        } else {
            // 기타 타입인 경우 toString()으로 변환 시도
            return new BigDecimal(value.toString());
        }
    }

    /**
     * 지급대행 요청 응답을 DTO로 변환
     * 
     * 토스페이먼츠 API 응답 데이터를 우리 시스템의 TossPayoutResponse DTO로 변환합니다.
     * 응답 데이터의 무결성을 검증하고, null 값에 대한 안전한 처리를 수행합니다.
     * 
     * <p>변환 과정:</p>
     * <ol>
     *   <li>payoutId 추출 및 검증</li>
     *   <li>status 추출 및 기본값 설정</li>
     *   <li>amount 검증 (요청 금액과 일치 여부)</li>
     *   <li>추가 메타데이터 설정</li>
     * </ol>
     * 
     * <p>검증 항목:</p>
     * <ul>
     *   <li>payoutId 필수 필드 존재 여부</li>
     *   <li>응답 금액과 요청 금액 일치 여부</li>
     *   <li>상태 값의 유효성</li>
     * </ul>
     * 
     * @param responseData 토스페이먼츠 API 응답 데이터 (null 불가)
     * @param settlement 원본 정산 정보 (null 불가)
     * @return 변환된 TossPayoutResponse DTO
     * @throws IllegalArgumentException 필수 파라미터가 null인 경우
     * @throws TossApiException 응답 데이터가 유효하지 않은 경우
     */
    private TossPayoutResponse mapToPayoutResponse(Map<String, Object> responseData, Settlement settlement) {
        if (responseData == null) {
            throw new IllegalArgumentException("응답 데이터가 null입니다");
        }
        if (settlement == null) {
            throw new IllegalArgumentException("정산 정보가 null입니다");
        }

        TossPayoutResponse response = new TossPayoutResponse();

        try {
            // 1. 지급대행 ID 추출 및 검증 (필수 필드)
            String payoutId = (String) responseData.get("payoutId");
            if (payoutId == null || payoutId.trim().isEmpty()) {
                throw new TossApiException("응답에서 payoutId를 찾을 수 없습니다");
            }
            response.setPayoutId(payoutId);

            // 2. 정산 ID 설정
            response.setSettlementId(settlement.getId());

            // 3. 금액 정보 설정 및 검증
            response.setAmount(settlement.getSettlementAmount());
            
            // 응답에 금액 정보가 있다면 검증
            if (responseData.containsKey("amount")) {
                Object responseAmountObj = responseData.get("amount");
                BigDecimal responseAmount = convertToBigDecimal(responseAmountObj, "amount");
                
                // 요청 금액과 응답 금액 일치 여부 확인
                if (settlement.getSettlementAmount().compareTo(responseAmount) != 0) {
                    log.warn("요청 금액과 응답 금액이 일치하지 않습니다 - 요청: {}, 응답: {}", 
                            settlement.getSettlementAmount(), responseAmount);
                }
            }

            // 4. 상태 정보 설정
            String status = (String) responseData.get("status");
            if (status != null && !status.trim().isEmpty()) {
                response.setStatus(status);
            } else {
                // 기본값: 처리 중 상태
                response.setStatus("PROCESSING");
            }

            // 5. 추가 정보 설정 (선택사항)
            if (responseData.containsKey("estimatedCompletionTime")) {
                // 예상 완료 시간이 있다면 로그에 기록
                String estimatedTime = (String) responseData.get("estimatedCompletionTime");
                log.info("지급대행 예상 완료 시간 - 지급대행 ID: {}, 예상 시간: {}", 
                        payoutId, estimatedTime);
            }

            if (responseData.containsKey("fee")) {
                // 지급대행 수수료 정보가 있다면 로그에 기록
                Object feeObj = responseData.get("fee");
                BigDecimal fee = convertToBigDecimal(feeObj, "fee");
                log.info("지급대행 수수료 - 지급대행 ID: {}, 수수료: {}", payoutId, fee);
            }

            log.debug("지급대행 응답 변환 완료 - 지급대행 ID: {}, 정산 ID: {}, 금액: {}, 상태: {}", 
                     response.getPayoutId(), response.getSettlementId(), 
                     response.getAmount(), response.getStatus());

            return response;

        } catch (Exception e) {
            log.error("지급대행 응답 변환 실패 - 정산 ID: {}, 응답 데이터: {}", 
                     settlement.getId(), responseData, e);
            
            if (e instanceof TossApiException) {
                throw e;
            } else {
                throw new TossApiException("지급대행 응답 데이터 변환에 실패했습니다", e);
            }
        }
    }

    /**
     * 토스페이먼츠 API 관련 커스텀 예외
     */
    public static class TossApiException extends RuntimeException {
        public TossApiException(String message) {
            super(message);
        }
        
        public TossApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 잔액 부족 관련 커스텀 예외
     */
    public static class InsufficientBalanceException extends TossApiException {
        public InsufficientBalanceException(String message) {
            super(message);
        }
        
        public InsufficientBalanceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 토스 잔액 조회 응답 DTO
     */
    public static class TossBalanceResponse {
        private BigDecimal availableAmount;
        private BigDecimal pendingAmount;
        private BigDecimal totalAmount;

        // Getters and Setters
        public BigDecimal getAvailableAmount() { return availableAmount; }
        public void setAvailableAmount(BigDecimal availableAmount) { this.availableAmount = availableAmount; }
        
        public BigDecimal getPendingAmount() { return pendingAmount; }
        public void setPendingAmount(BigDecimal pendingAmount) { this.pendingAmount = pendingAmount; }
        
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    }

    /**
     * 토스 지급대행 응답 DTO
     */
    public static class TossPayoutResponse {
        private String payoutId;
        private String settlementId;
        private BigDecimal amount;
        private String status;

        // Getters and Setters
        public String getPayoutId() { return payoutId; }
        public void setPayoutId(String payoutId) { this.payoutId = payoutId; }
        
        public String getSettlementId() { return settlementId; }
        public void setSettlementId(String settlementId) { this.settlementId = settlementId; }
        
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}