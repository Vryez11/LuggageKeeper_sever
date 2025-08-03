package com.luggagekeeper.keeper_app.settlement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luggagekeeper.keeper_app.settlement.domain.Settlement;
import com.luggagekeeper.keeper_app.settlement.domain.TossSeller;
import com.luggagekeeper.keeper_app.settlement.domain.TossSellerStatus;
import com.luggagekeeper.keeper_app.settlement.domain.TossBusinessType;
import com.luggagekeeper.keeper_app.settlement.service.TossPayoutService.TossApiException;
import com.luggagekeeper.keeper_app.settlement.exception.InsufficientBalanceException;
import com.luggagekeeper.keeper_app.settlement.repository.TossSellerRepository;
import com.luggagekeeper.keeper_app.store.domain.Store;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TossPayments API 연동 단위 테스트 클래스
 * 
 * <p><strong>테스트 대상:</strong> TossPayoutService의 외부 API 연동 기능</p>
 * <p><strong>테스트 범위:</strong> Mock 서버를 사용한 API 호출, 성공/실패 시나리오, 네트워크 오류 처리</p>
 * 
 * <h3>테스트 전략:</h3>
 * <ul>
 *   <li><strong>Mock 서버 사용:</strong> 실제 토스페이먼츠 API 없이 HTTP 통신 테스트</li>
 *   <li><strong>성공 시나리오:</strong> 정상적인 지급대행 요청 및 응답 처리</li>
 *   <li><strong>실패 시나리오:</strong> API 오류, 잔액 부족, 인증 실패 등</li>
 *   <li><strong>네트워크 오류:</strong> 타임아웃, 연결 실패, 서버 오류 등</li>
 * </ul>
 * 
 * <h3>Mock 서버 사용 이유:</h3>
 * <ul>
 *   <li><strong>외부 의존성 제거:</strong> 실제 토스페이먼츠 API 서버 없이 테스트 가능</li>
 *   <li><strong>오류 시나리오 시뮬레이션:</strong> 다양한 HTTP 상태 코드와 오류 응답 테스트</li>
 *   <li><strong>네트워크 조건 제어:</strong> 타임아웃, 지연, 연결 실패 등 시뮬레이션</li>
 *   <li><strong>테스트 안정성:</strong> 외부 서비스 상태에 영향받지 않는 일관된 테스트</li>
 * </ul>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
@ExtendWith(MockitoExtension.class)
class TossPaymentsMockServerTest {

    private MockWebServer mockWebServer;
    private TossPayoutService tossPayoutService;
    private Settlement testSettlement;
    private TossSeller testTossSeller;
    private Store testStore;
    
    @Mock
    private TossSellerRepository tossSellerRepository;
    
    @Mock
    private JweEncryptionService jweEncryptionService;

    @BeforeEach
    void setUp() throws IOException {
        // Mock 웹 서버 시작
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        // Mock 서버 URL 설정
        String mockServerUrl = mockWebServer.url("/").toString();
        
        // WebClient를 Mock 서버 URL과 타임아웃 설정으로 구성
        WebClient webClient = WebClient.builder()
            .baseUrl(mockServerUrl)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
            .build();
        
        // TossPayoutService 인스턴스 생성 및 설정
        tossPayoutService = new TossPayoutService(
            webClient,
            jweEncryptionService,
            new ObjectMapper(),
            tossSellerRepository
        );
        
        // 서비스 설정값 주입
        ReflectionTestUtils.setField(tossPayoutService, "baseUrl", mockServerUrl);
        ReflectionTestUtils.setField(tossPayoutService, "readTimeout", 5000);
        ReflectionTestUtils.setField(tossPayoutService, "connectionTimeout", 5000);
        
        testStore = new Store();
        testStore.setId("test-store-123");
        testStore.setName("테스트 짐보관소");
        
        testSettlement = Settlement.createSettlement(testStore, "test-order-456", new BigDecimal("10000"));
        testSettlement.setId("test-settlement-789");
        
        // TossSeller 생성 시 올바른 방법 사용
        testTossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        testTossSeller.setId("test-toss-seller-001");
        testTossSeller.setTossSellerId("toss-seller-12345");
        testTossSeller.setStatus(TossSellerStatus.APPROVED);
        testTossSeller.setRegisteredAt(LocalDateTime.now());
        testTossSeller.setCreatedAt(LocalDateTime.now());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Test
    @DisplayName("지급대행 요청 - 성공적인 API 호출 및 응답 처리")
    void requestPayout_ShouldReturnSuccessResponse_WhenApiCallSucceeds() throws InterruptedException {
        // Given: 성공적인 토스페이먼츠 API 응답 준비
        // 테스트 시나리오: 정상적인 지급대행 요청 플로우
        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("payoutId", "payout-success-12345");
        successResponse.put("status", "REQUESTED");
        successResponse.put("requestedAt", "2024-01-01T10:00:00");
        successResponse.put("amount", 8000);
        
        String encryptedResponse = "encrypted-success-response";
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(encryptedResponse));
        
        // Mock 설정 이유: 외부 의존성 격리 및 테스트 데이터 제어
        when(tossSellerRepository.findByStoreId(testStore.getId())).thenReturn(Optional.of(testTossSeller));
        when(jweEncryptionService.encrypt(any(Map.class))).thenReturn("encrypted-request");
        when(jweEncryptionService.decrypt(eq(encryptedResponse), eq(Map.class))).thenReturn(successResponse);
        
        // When: 지급대행 요청 실행
        TossPayoutService.TossPayoutResponse result = tossPayoutService.requestPayout(testSettlement);
        
        // Then: 응답 데이터 검증
        // 검증 목적: API 호출 성공 시 올바른 응답 데이터 매핑 확인
        assertThat(result).isNotNull();
        assertThat(result.getPayoutId()).isEqualTo("payout-success-12345");
        assertThat(result.getStatus()).isEqualTo("REQUESTED");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("8000"));
        
        // HTTP 요청 검증
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).isEqualTo("/v1/payouts");
        
        verify(jweEncryptionService).encrypt(any(Map.class));
        verify(jweEncryptionService).decrypt(encryptedResponse, Map.class);
    }

    @Test
    @DisplayName("지급대행 요청 - 잔액 부족 오류 응답 처리")
    void requestPayout_ShouldThrowInsufficientBalanceException_WhenInsufficientBalance() throws InterruptedException {
        // Given: 잔액 부족 오류 응답 준비
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("code", "INSUFFICIENT_BALANCE");
        errorResponse.put("message", "잔액이 부족합니다");
        errorResponse.put("requestedAmount", 10000);
        errorResponse.put("availableBalance", 5000);
        
        String encryptedErrorResponse = "encrypted-error-response";
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(422)
            .setHeader("Content-Type", "application/json")
            .setBody(encryptedErrorResponse));
        
        when(tossSellerRepository.findByStoreId(testStore.getId())).thenReturn(Optional.of(testTossSeller));
        when(jweEncryptionService.encrypt(any(Map.class))).thenReturn("encrypted-request");
        when(jweEncryptionService.decrypt(eq(encryptedErrorResponse), eq(Map.class))).thenReturn(errorResponse);
        
        // When & Then: 예외 발생 검증
        // 검증 목적: 잔액 부족 시 적절한 비즈니스 예외 발생 확인
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
            .isInstanceOf(InsufficientBalanceException.class)
            .hasMessageContaining("잔액이 부족합니다");
        
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    }

    @Test
    @DisplayName("지급대행 요청 - 네트워크 오류 처리")
    void requestPayout_ShouldThrowTossApiException_WhenNetworkError() {
        // Given: 네트워크 연결 실패 시나리오 (잘못된 서버 주소)
        // 잘못된 URL로 WebClient 재설정
        WebClient brokenWebClient = WebClient.builder()
            .baseUrl("http://invalid-host-that-does-not-exist:9999")
            .build();
        
        TossPayoutService brokenService = new TossPayoutService(
            brokenWebClient,
            jweEncryptionService,
            new ObjectMapper(),
            tossSellerRepository
        );
        
        // 설정값 주입
        ReflectionTestUtils.setField(brokenService, "baseUrl", "http://invalid-host-that-does-not-exist:9999");
        ReflectionTestUtils.setField(brokenService, "readTimeout", 1000);
        ReflectionTestUtils.setField(brokenService, "connectionTimeout", 1000);
        
        when(tossSellerRepository.findByStoreId(testStore.getId())).thenReturn(Optional.of(testTossSeller));
        when(jweEncryptionService.encrypt(any(Map.class))).thenReturn("encrypted-request");
        
        // When & Then: 네트워크 오류 예외 발생 검증
        // 네트워크 오류 시 TossApiException이 발생하면 성공
        assertThatThrownBy(() -> brokenService.requestPayout(testSettlement))
            .isInstanceOf(TossApiException.class)
            .hasMessageContaining("지급대행 요청에 실패했습니다");
    }
    
    @Test
    @DisplayName("지급대행 요청 - 인증 실패 오류 처리")
    void requestPayout_ShouldThrowTossApiException_WhenAuthenticationFails() throws InterruptedException {
        // Given: 인증 실패 응답 준비
        Map<String, Object> authErrorResponse = new HashMap<>();
        authErrorResponse.put("code", "UNAUTHORIZED");
        authErrorResponse.put("message", "인증에 실패했습니다");
        
        String encryptedAuthError = "encrypted-auth-error";
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(401)
            .setHeader("Content-Type", "application/json")
            .setBody(encryptedAuthError));
        
        when(tossSellerRepository.findByStoreId(testStore.getId())).thenReturn(Optional.of(testTossSeller));
        when(jweEncryptionService.encrypt(any(Map.class))).thenReturn("encrypted-request");
        when(jweEncryptionService.decrypt(eq(encryptedAuthError), eq(Map.class))).thenReturn(authErrorResponse);
        
        // When & Then: 인증 실패 예외 발생 검증
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
            .isInstanceOf(TossApiException.class)
            .hasMessageContaining("인증에 실패했습니다");
        
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    }
    
    @Test
    @DisplayName("지급대행 요청 - 서버 내부 오류 처리")
    void requestPayout_ShouldThrowTossApiException_WhenServerError() throws InterruptedException {
        // Given: 서버 내부 오류 응답 준비
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setHeader("Content-Type", "application/json")
            .setBody("Internal Server Error"));
        
        when(tossSellerRepository.findByStoreId(testStore.getId())).thenReturn(Optional.of(testTossSeller));
        when(jweEncryptionService.encrypt(any(Map.class))).thenReturn("encrypted-request");
        
        // When & Then: 서버 오류 예외 발생 검증
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
            .isInstanceOf(TossApiException.class)
            .hasMessageContaining("서버 오류");
        
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    }
    
    @Test
    @DisplayName("지급대행 요청 - JWE 암호화 실패 처리")
    void requestPayout_ShouldThrowTossApiException_WhenJweEncryptionFails() {
        // Given: JWE 암호화 실패 시나리오
        when(tossSellerRepository.findByStoreId(testStore.getId())).thenReturn(Optional.of(testTossSeller));
        when(jweEncryptionService.encrypt(any(Map.class)))
            .thenThrow(new JweEncryptionService.EncryptionException("JWE 암호화에 실패했습니다"));
        
        // When & Then: JWE 암호화 실패 예외 발생 검증
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
            .isInstanceOf(TossApiException.class)
            .hasMessageContaining("JWE 암호화에 실패했습니다");
        
        // HTTP 요청이 발생하지 않아야 함
        assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
    }
    
    @Test
    @DisplayName("지급대행 요청 - JWE 복호화 실패 처리")
    void requestPayout_ShouldThrowTossApiException_WhenJweDecryptionFails() throws InterruptedException {
        // Given: JWE 복호화 실패 시나리오
        String encryptedResponse = "encrypted-response";
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(encryptedResponse));
        
        when(tossSellerRepository.findByStoreId(testStore.getId())).thenReturn(Optional.of(testTossSeller));
        when(jweEncryptionService.encrypt(any(Map.class))).thenReturn("encrypted-request");
        when(jweEncryptionService.decrypt(eq(encryptedResponse), eq(Map.class)))
            .thenThrow(new JweEncryptionService.EncryptionException("JWE 복호화에 실패했습니다"));
        
        // When & Then: JWE 복호화 실패 예외 발생 검증
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
            .isInstanceOf(TossApiException.class)
            .hasMessageContaining("JWE 복호화에 실패했습니다");
        
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    }
    
    @Test
    @DisplayName("지급대행 요청 - 존재하지 않는 TossSeller로 예외 발생")
    void requestPayout_ShouldThrowTossApiException_WhenTossSellerNotFound() {
        // Given: TossSeller가 존재하지 않는 상황
        when(tossSellerRepository.findByStoreId(testStore.getId())).thenReturn(Optional.empty());
        
        // When & Then: TossApiException 발생 검증
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
            .isInstanceOf(TossApiException.class)
            .hasMessageContaining("토스 셀러 정보를 찾을 수 없습니다");
        
        // HTTP 요청이 발생하지 않아야 함
        assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
        verify(tossSellerRepository).findByStoreId(testStore.getId());
        verifyNoInteractions(jweEncryptionService);
    }
    
    @Test
    @DisplayName("지급대행 요청 - 비활성 TossSeller 상태로 예외 발생")
    void requestPayout_ShouldThrowTossApiException_WhenTossSellerInactive() {
        // Given: 비활성 상태의 TossSeller
        testTossSeller.setStatus(TossSellerStatus.APPROVAL_REQUIRED);
        when(tossSellerRepository.findByStoreId(testStore.getId())).thenReturn(Optional.of(testTossSeller));
        
        // When & Then: TossApiException 발생 검증
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
            .isInstanceOf(TossApiException.class)
            .hasMessageContaining("지급대행을 처리할 수 없는 셀러 상태입니다");
        
        // HTTP 요청이 발생하지 않아야 함
        assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
        verify(tossSellerRepository).findByStoreId(testStore.getId());
        verifyNoInteractions(jweEncryptionService);
    }
    
    @Test
    @DisplayName("지급대행 요청 - null Settlement로 예외 발생")
    void requestPayout_ShouldThrowException_WhenSettlementIsNull() {
        // When & Then: IllegalArgumentException 발생 검증
        assertThatThrownBy(() -> tossPayoutService.requestPayout(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("정산 정보는 필수입니다");
        
        // 외부 의존성 호출 없어야 함
        assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
        verifyNoInteractions(tossSellerRepository);
        verifyNoInteractions(jweEncryptionService);
    }
    
    @Test
    @DisplayName("지급대행 요청 - 경계값 테스트: 최소 금액 (1원)")
    void requestPayout_ShouldHandleMinimumAmount() throws InterruptedException {
        // Given: 최소 금액 정산 (1원)
        Settlement minAmountSettlement = Settlement.createSettlement(testStore, "min-amount-order", new BigDecimal("1"));
        minAmountSettlement.setId("min-amount-settlement");
        
        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("payoutId", "payout-min-amount");
        successResponse.put("status", "REQUESTED");
        successResponse.put("amount", 1);
        
        String encryptedResponse = "encrypted-min-response";
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(encryptedResponse));
        
        when(tossSellerRepository.findByStoreId(testStore.getId())).thenReturn(Optional.of(testTossSeller));
        when(jweEncryptionService.encrypt(any(Map.class))).thenReturn("encrypted-request");
        when(jweEncryptionService.decrypt(eq(encryptedResponse), eq(Map.class))).thenReturn(successResponse);
        
        // When: 최소 금액으로 지급대행 요청
        TossPayoutService.TossPayoutResponse result = tossPayoutService.requestPayout(minAmountSettlement);
        
        // Then: 성공적인 처리 확인
        assertThat(result).isNotNull();
        assertThat(result.getPayoutId()).isEqualTo("payout-min-amount");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("1"));
        
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    }
    
    @Test
    @DisplayName("지급대행 요청 - 경계값 테스트: 대용량 금액 (1억원)")
    void requestPayout_ShouldHandleLargeAmount() throws InterruptedException {
        // Given: 대용량 금액 정산 (1억원)
        Settlement largeAmountSettlement = Settlement.createSettlement(testStore, "large-amount-order", new BigDecimal("100000000"));
        largeAmountSettlement.setId("large-amount-settlement");
        
        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("payoutId", "payout-large-amount");
        successResponse.put("status", "REQUESTED");
        successResponse.put("amount", 100000000);
        
        String encryptedResponse = "encrypted-large-response";
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(encryptedResponse));
        
        when(tossSellerRepository.findByStoreId(testStore.getId())).thenReturn(Optional.of(testTossSeller));
        when(jweEncryptionService.encrypt(any(Map.class))).thenReturn("encrypted-request");
        when(jweEncryptionService.decrypt(eq(encryptedResponse), eq(Map.class))).thenReturn(successResponse);
        
        // When: 대용량 금액으로 지급대행 요청
        TossPayoutService.TossPayoutResponse result = tossPayoutService.requestPayout(largeAmountSettlement);
        
        // Then: 성공적인 처리 확인
        assertThat(result).isNotNull();
        assertThat(result.getPayoutId()).isEqualTo("payout-large-amount");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("100000000"));
        
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    }
}
