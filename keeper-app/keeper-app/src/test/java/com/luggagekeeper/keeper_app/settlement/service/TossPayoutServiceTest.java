package com.luggagekeeper.keeper_app.settlement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luggagekeeper.keeper_app.settlement.domain.*;
import com.luggagekeeper.keeper_app.settlement.dto.TossSellerResponse;
import com.luggagekeeper.keeper_app.settlement.repository.TossSellerRepository;
import com.luggagekeeper.keeper_app.store.domain.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TossPayoutService 단위 테스트
 * 
 * 토스페이먼츠 API 연동 서비스의 핵심 기능을 테스트합니다.
 * Mock 객체를 사용하여 외부 의존성을 격리하고,
 * 다양한 시나리오에 대한 동작을 검증합니다.
 * 
 * <p>테스트 범위:</p>
 * <ul>
 *   <li>셀러 등록 API 연동</li>
 *   <li>잔액 조회 API 연동</li>
 *   <li>지급대행 요청 API 연동</li>
 *   <li>JWE 암호화/복호화 처리</li>
 *   <li>오류 상황별 예외 처리</li>
 * </ul>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TossPayoutService 단위 테스트")
class TossPayoutServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private JweEncryptionService jweEncryptionService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TossSellerRepository tossSellerRepository;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private TossPayoutService tossPayoutService;

    private Store testStore;
    private TossSeller testTossSeller;
    private Settlement testSettlement;

    /**
     * 테스트 데이터 초기화
     * 
     * 각 테스트 메서드 실행 전에 호출되어 공통으로 사용할 테스트 데이터를 준비합니다.
     * 실제 운영 환경과 유사한 데이터 구조를 생성하여 테스트의 신뢰성을 높입니다.
     */
    @BeforeEach
    void setUp() {
        // TossPayoutService 설정값 주입
        ReflectionTestUtils.setField(tossPayoutService, "baseUrl", "https://api.tosspayments.com");
        ReflectionTestUtils.setField(tossPayoutService, "secretKey", "test_sk_example_key");
        ReflectionTestUtils.setField(tossPayoutService, "clientId", "test_client_id");
        ReflectionTestUtils.setField(tossPayoutService, "connectionTimeout", 30000);
        ReflectionTestUtils.setField(tossPayoutService, "readTimeout", 30000);
        ReflectionTestUtils.setField(tossPayoutService, "maxRetryAttempts", 3);

        // 테스트용 Store 엔티티 생성
        testStore = new Store();
        testStore.setId("store-123");
        testStore.setName("테스트 편의점");
        testStore.setEmail("test@example.com");
        testStore.setPhoneNumber("010-1234-5678");
        testStore.setAddress("서울시 강남구 테스트로 123");

        // 테스트용 TossSeller 엔티티 생성
        testTossSeller = new TossSeller();
        testTossSeller.setId("tossseller-456");
        testTossSeller.setStore(testStore);
        testTossSeller.setRefSellerId("store-123");
        testTossSeller.setTossSellerId("toss-seller-789");
        testTossSeller.setBusinessType(TossBusinessType.INDIVIDUAL_BUSINESS);
        testTossSeller.setStatus(TossSellerStatus.APPROVED);
        testTossSeller.setRegisteredAt(LocalDateTime.now().minusDays(1));
        testTossSeller.setApprovedAt(LocalDateTime.now().minusHours(1));

        // 테스트용 Settlement 엔티티 생성
        testSettlement = new Settlement();
        testSettlement.setId("settlement-789");
        testSettlement.setStore(testStore);
        testSettlement.setOrderId("order-123");
        testSettlement.setOriginalAmount(new BigDecimal("10000"));
        testSettlement.setPlatformFeeRate(new BigDecimal("0.20"));
        testSettlement.setPlatformFee(new BigDecimal("2000"));
        testSettlement.setSettlementAmount(new BigDecimal("8000"));
        testSettlement.setStatus(SettlementStatus.PENDING);
        testSettlement.setTossSellerId("toss-seller-789");
    }

    @Test
    @DisplayName("셀러 등록 성공 - 새로운 셀러 등록")
    void registerSeller_ShouldSucceed_WhenNewSeller() throws Exception {
        // Given: 새로운 셀러 등록 시나리오
        when(tossSellerRepository.findByStoreId("store-123")).thenReturn(Optional.empty());
        when(tossSellerRepository.save(any(TossSeller.class))).thenReturn(testTossSeller);

        // JWE 암호화 Mock 설정
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("refSellerId", "store-123");
        requestData.put("businessType", "INDIVIDUAL_BUSINESS");
        requestData.put("storeName", "테스트 편의점");

        String encryptedRequest = "encrypted_jwe_token";
        when(jweEncryptionService.encrypt(any())).thenReturn(encryptedRequest);

        // API 응답 Mock 설정
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("sellerId", "toss-seller-789");
        responseData.put("status", "APPROVED");

        String apiResponse = "encrypted_response";
        when(jweEncryptionService.decrypt(eq(apiResponse), eq(Map.class))).thenReturn(responseData);

        // WebClient Mock 설정
        setupWebClientMocks(apiResponse);

        // When: 셀러 등록 실행
        TossSellerResponse result = tossPayoutService.registerSeller(testStore);

        // Then: 결과 검증
        assertThat(result).isNotNull();
        assertThat(result.getTossSellerId()).isEqualTo("toss-seller-789");
        assertThat(result.getStoreId()).isEqualTo("store-123");
        assertThat(result.getStatus()).isEqualTo(TossSellerStatus.APPROVED);

        // Mock 호출 검증
        verify(tossSellerRepository).findByStoreId("store-123");
        verify(tossSellerRepository, times(2)).save(any(TossSeller.class)); // 생성 시 1회, 업데이트 시 1회
        verify(jweEncryptionService).encrypt(any());
        verify(jweEncryptionService).decrypt(eq(apiResponse), eq(Map.class));
    }

    @Test
    @DisplayName("셀러 등록 성공 - 기존 셀러 반환")
    void registerSeller_ShouldReturnExisting_WhenSellerAlreadyExists() {
        // Given: 이미 등록된 셀러가 존재하는 시나리오
        when(tossSellerRepository.findByStoreId("store-123")).thenReturn(Optional.of(testTossSeller));

        // When: 셀러 등록 실행
        TossSellerResponse result = tossPayoutService.registerSeller(testStore);

        // Then: 기존 셀러 정보 반환 검증
        assertThat(result).isNotNull();
        assertThat(result.getTossSellerId()).isEqualTo("toss-seller-789");
        assertThat(result.getStoreId()).isEqualTo("store-123");

        // API 호출이 발생하지 않았는지 검증
        verify(jweEncryptionService, never()).encrypt(any());
        verify(tossSellerRepository, never()).save(any(TossSeller.class));
    }

    @Test
    @DisplayName("셀러 등록 실패 - null 가게 정보")
    void registerSeller_ShouldThrowException_WhenStoreIsNull() {
        // When & Then: null 가게 정보로 셀러 등록 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.registerSeller(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("가게 정보는 필수입니다");
    }

    @Test
    @DisplayName("셀러 등록 실패 - JWE 암호화 오류")
    void registerSeller_ShouldThrowException_WhenEncryptionFails() {
        // Given: JWE 암호화 실패 시나리오
        when(tossSellerRepository.findByStoreId("store-123")).thenReturn(Optional.empty());
        when(tossSellerRepository.save(any(TossSeller.class))).thenReturn(testTossSeller);
        when(jweEncryptionService.encrypt(any()))
                .thenThrow(new JweEncryptionService.EncryptionException("암호화 실패"));

        // When & Then: JWE 암호화 실패 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.registerSeller(testStore))
                .isInstanceOf(TossPayoutService.TossApiException.class)
                .hasMessage("JWE 암호화 처리 중 오류가 발생했습니다");
    }

    @Test
    @DisplayName("잔액 조회 성공")
    void getBalance_ShouldSucceed_WhenApiCallSucceeds() throws Exception {
        // Given: 잔액 조회 성공 시나리오
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("availableAmount", "50000");
        responseData.put("pendingAmount", "20000");

        String apiResponse = "{\"availableAmount\":\"50000\",\"pendingAmount\":\"20000\"}";
        when(objectMapper.readValue(eq(apiResponse), eq(Map.class))).thenReturn(responseData);

        // WebClient Mock 설정
        setupWebClientMocks(apiResponse);

        // When: 잔액 조회 실행
        TossPayoutService.TossBalanceResponse result = tossPayoutService.getBalance();

        // Then: 결과 검증
        assertThat(result).isNotNull();
        assertThat(result.getAvailableAmount()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(result.getPendingAmount()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("70000"));

        // Mock 호출 검증
        verify(objectMapper).readValue(eq(apiResponse), eq(Map.class));
    }

    @Test
    @DisplayName("잔액 조회 실패 - 필수 필드 누락")
    void getBalance_ShouldThrowException_WhenRequiredFieldsMissing() throws Exception {
        // Given: 필수 필드가 누락된 응답
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("availableAmount", "50000");
        // pendingAmount 필드 누락

        String apiResponse = "{\"availableAmount\":\"50000\"}";
        when(objectMapper.readValue(eq(apiResponse), eq(Map.class))).thenReturn(responseData);

        // WebClient Mock 설정
        setupWebClientMocks(apiResponse);

        // When & Then: 필수 필드 누락 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.getBalance())
                .isInstanceOf(TossPayoutService.TossApiException.class)
                .hasMessage("잔액 조회 응답에 필수 필드가 누락되었습니다");
    }

    @Test
    @DisplayName("지급대행 요청 성공")
    void requestPayout_ShouldSucceed_WhenValidSettlement() throws Exception {
        // Given: 지급대행 요청 성공 시나리오
        when(tossSellerRepository.findByStoreId("store-123")).thenReturn(Optional.of(testTossSeller));

        // JWE 암호화/복호화 Mock 설정
        String encryptedRequest = "encrypted_payout_request";
        when(jweEncryptionService.encrypt(any())).thenReturn(encryptedRequest);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("payoutId", "payout-123");
        responseData.put("status", "PROCESSING");
        responseData.put("amount", "8000");

        String apiResponse = "encrypted_payout_response";
        when(jweEncryptionService.decrypt(eq(apiResponse), eq(Map.class))).thenReturn(responseData);

        // WebClient Mock 설정
        setupWebClientMocks(apiResponse);

        // When: 지급대행 요청 실행
        TossPayoutService.TossPayoutResponse result = tossPayoutService.requestPayout(testSettlement);

        // Then: 결과 검증
        assertThat(result).isNotNull();
        assertThat(result.getPayoutId()).isEqualTo("payout-123");
        assertThat(result.getSettlementId()).isEqualTo("settlement-789");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat(result.getStatus()).isEqualTo("PROCESSING");

        // Mock 호출 검증
        verify(tossSellerRepository).findByStoreId("store-123");
        verify(jweEncryptionService).encrypt(any());
        verify(jweEncryptionService).decrypt(eq(apiResponse), eq(Map.class));
    }

    @Test
    @DisplayName("지급대행 요청 실패 - 셀러 정보 없음")
    void requestPayout_ShouldThrowException_WhenSellerNotFound() {
        // Given: 셀러 정보가 없는 시나리오
        when(tossSellerRepository.findByStoreId("store-123")).thenReturn(Optional.empty());

        // When & Then: 셀러 정보 없음 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
                .isInstanceOf(TossPayoutService.TossApiException.class)
                .hasMessage("토스 셀러 정보를 찾을 수 없습니다. 가게 ID: store-123");
    }

    @Test
    @DisplayName("지급대행 요청 실패 - 셀러 승인 미완료")
    void requestPayout_ShouldThrowException_WhenSellerNotApproved() {
        // Given: 승인되지 않은 셀러
        testTossSeller.setStatus(TossSellerStatus.APPROVAL_REQUIRED);
        testTossSeller.setTossSellerId(null); // 토스 셀러 ID 미할당
        when(tossSellerRepository.findByStoreId("store-123")).thenReturn(Optional.of(testTossSeller));

        // When & Then: 승인되지 않은 셀러 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
                .isInstanceOf(TossPayoutService.TossApiException.class)
                .hasMessageContaining("지급대행을 처리할 수 없는 셀러 상태입니다");
    }

    @Test
    @DisplayName("지급대행 요청 실패 - null 정산 정보")
    void requestPayout_ShouldThrowException_WhenSettlementIsNull() {
        // When & Then: null 정산 정보로 지급대행 요청 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("정산 정보는 필수입니다");
    }

    @Test
    @DisplayName("지급대행 요청 실패 - 잔액 부족")
    void requestPayout_ShouldThrowInsufficientBalanceException_WhenBalanceInsufficient() throws Exception {
        // Given: 잔액 부족 시나리오
        when(tossSellerRepository.findByStoreId("store-123")).thenReturn(Optional.of(testTossSeller));
        when(jweEncryptionService.encrypt(any())).thenReturn("encrypted_request");

        // 잔액 부족 오류를 포함한 예외 발생
        RuntimeException balanceException = new RuntimeException("INSUFFICIENT_BALANCE");
        when(webClient.method(any())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(balanceException));

        // When & Then: 잔액 부족 시 InsufficientBalanceException 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
                .isInstanceOf(TossPayoutService.InsufficientBalanceException.class)
                .hasMessage("지급 가능한 잔액이 부족합니다");
    }

    @Test
    @DisplayName("지급대행 취소 성공")
    void cancelPayout_ShouldSucceed_WhenValidPayoutId() {
        // Given: 지급대행 취소 성공 시나리오
        String tossPayoutId = "payout-123";
        String apiResponse = "{\"status\":\"CANCELLED\"}";

        // WebClient Mock 설정
        setupWebClientMocks(apiResponse);

        // When & Then: 지급대행 취소 실행 (예외 발생하지 않음)
        assertThatCode(() -> tossPayoutService.cancelPayout(tossPayoutId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("지급대행 취소 실패 - null 지급대행 ID")
    void cancelPayout_ShouldThrowException_WhenPayoutIdIsNull() {
        // When & Then: null 지급대행 ID로 취소 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.cancelPayout(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("토스 지급대행 ID는 필수입니다");
    }

    @Test
    @DisplayName("지급대행 취소 실패 - 빈 지급대행 ID")
    void cancelPayout_ShouldThrowException_WhenPayoutIdIsEmpty() {
        // When & Then: 빈 지급대행 ID로 취소 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.cancelPayout(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("토스 지급대행 ID는 필수입니다");
    }

    /**
     * WebClient Mock 설정 헬퍼 메서드
     * 
     * 반복적으로 사용되는 WebClient Mock 설정을 간소화합니다.
     * 모든 HTTP 메서드에 대해 동일한 응답을 반환하도록 설정합니다.
     * 
     * @param apiResponse Mock API 응답 문자열
     */
    private void setupWebClientMocks(String apiResponse) {
        when(webClient.method(any())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(apiResponse));
    }
}