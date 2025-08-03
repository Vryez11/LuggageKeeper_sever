package com.luggagekeeper.keeper_app.settlement.service;

import com.luggagekeeper.keeper_app.settlement.domain.*;
import com.luggagekeeper.keeper_app.settlement.dto.TossSellerResponse;
import com.luggagekeeper.keeper_app.settlement.repository.TossSellerRepository;
import com.luggagekeeper.keeper_app.store.domain.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TossPayoutService 통합 테스트
 * 
 * 실제 Spring 컨텍스트를 사용하여 TossPayoutService의 통합 동작을 테스트합니다.
 * 외부 API 호출은 Mock으로 처리하고, 내부 비즈니스 로직과 데이터베이스 연동을 검증합니다.
 * 
 * <p>테스트 범위:</p>
 * <ul>
 *   <li>Spring Bean 주입 및 설정 검증</li>
 *   <li>데이터베이스 트랜잭션 처리</li>
 *   <li>JWE 암호화 서비스 연동</li>
 *   <li>비즈니스 로직 검증</li>
 * </ul>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
@SpringBootTest
@TestPropertySource(properties = {
    "toss.api.base-url=https://api.tosspayments.com",
    "toss.api.secret-key=test_sk_example_key",
    "toss.api.security-key=test_security_key_32_bytes_long_hex",
    "toss.api.client-id=test_client_id"
})
@Transactional
@DisplayName("TossPayoutService 통합 테스트")
class TossPayoutServiceIntegrationTest {

    @Autowired
    private TossPayoutService tossPayoutService;

    @Autowired
    private TossSellerRepository tossSellerRepository;

    @MockBean
    private WebClient webClient;

    @MockBean
    private JweEncryptionService jweEncryptionService;

    private Store testStore;
    private TossSeller testTossSeller;
    private Settlement testSettlement;

    /**
     * 테스트 데이터 초기화
     * 
     * 각 테스트 메서드 실행 전에 호출되어 공통으로 사용할 테스트 데이터를 준비합니다.
     * 실제 데이터베이스에 저장되는 엔티티를 생성하여 통합 테스트의 신뢰성을 높입니다.
     */
    @BeforeEach
    void setUp() {
        // 테스트용 Store 엔티티 생성
        testStore = new Store();
        testStore.setId("integration-store-123");
        testStore.setName("통합테스트 편의점");
        testStore.setEmail("integration@example.com");
        testStore.setPhoneNumber("010-9999-8888");
        testStore.setAddress("서울시 강남구 통합테스트로 456");

        // 테스트용 TossSeller 엔티티 생성
        testTossSeller = new TossSeller();
        testTossSeller.setId("integration-tossseller-456");
        testTossSeller.setStore(testStore);
        testTossSeller.setRefSellerId("integration-store-123");
        testTossSeller.setTossSellerId("integration-toss-seller-789");
        testTossSeller.setBusinessType(TossBusinessType.INDIVIDUAL_BUSINESS);
        testTossSeller.setStatus(TossSellerStatus.APPROVED);
        testTossSeller.setRegisteredAt(LocalDateTime.now().minusDays(1));
        testTossSeller.setApprovedAt(LocalDateTime.now().minusHours(1));

        // 테스트용 Settlement 엔티티 생성
        testSettlement = new Settlement();
        testSettlement.setId("integration-settlement-789");
        testSettlement.setStore(testStore);
        testSettlement.setOrderId("integration-order-123");
        testSettlement.setOriginalAmount(new BigDecimal("15000"));
        testSettlement.setPlatformFeeRate(new BigDecimal("0.20"));
        testSettlement.setPlatformFee(new BigDecimal("3000"));
        testSettlement.setSettlementAmount(new BigDecimal("12000"));
        testSettlement.setStatus(SettlementStatus.PENDING);
        testSettlement.setTossSellerId("integration-toss-seller-789");
    }

    @Test
    @DisplayName("서비스 빈 주입 검증")
    void contextLoads() {
        // Given & When & Then: Spring 컨텍스트가 정상적으로 로드되고 빈이 주입되는지 검증
        assertThat(tossPayoutService).isNotNull();
        assertThat(tossSellerRepository).isNotNull();
    }

    @Test
    @DisplayName("셀러 등록 - 기존 셀러 반환 (데이터베이스 연동)")
    void registerSeller_ShouldReturnExisting_WhenSellerExistsInDatabase() {
        // Given: 데이터베이스에 기존 셀러 저장
        TossSeller savedSeller = tossSellerRepository.save(testTossSeller);
        
        // When: 셀러 등록 요청
        TossSellerResponse result = tossPayoutService.registerSeller(testStore);

        // Then: 기존 셀러 정보 반환 검증
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(savedSeller.getId());
        assertThat(result.getTossSellerId()).isEqualTo("integration-toss-seller-789");
        assertThat(result.getStoreId()).isEqualTo("integration-store-123");
        assertThat(result.getStatus()).isEqualTo(TossSellerStatus.APPROVED);
        assertThat(result.isCanProcessPayout()).isTrue();
        assertThat(result.isPendingApproval()).isFalse();

        // 외부 API 호출이 발생하지 않았는지 검증
        verifyNoInteractions(jweEncryptionService);
        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("지급대행 요청 - 셀러 정보 없음 예외")
    void requestPayout_ShouldThrowException_WhenSellerNotFoundInDatabase() {
        // Given: 데이터베이스에 셀러 정보가 없는 상태
        // (setUp에서 생성한 testTossSeller를 저장하지 않음)

        // When & Then: 셀러 정보 없음 예외 발생 검증
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
                .isInstanceOf(TossPayoutService.TossApiException.class)
                .hasMessage("토스 셀러 정보를 찾을 수 없습니다. 가게 ID: integration-store-123");

        // 외부 API 호출이 발생하지 않았는지 검증
        verifyNoInteractions(jweEncryptionService);
        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("지급대행 요청 - 셀러 승인 미완료 예외")
    void requestPayout_ShouldThrowException_WhenSellerNotApproved() {
        // Given: 승인되지 않은 셀러를 데이터베이스에 저장
        testTossSeller.setStatus(TossSellerStatus.APPROVAL_REQUIRED);
        testTossSeller.setTossSellerId(null); // 토스 셀러 ID 미할당
        tossSellerRepository.save(testTossSeller);

        // When & Then: 셀러 승인 미완료 예외 발생 검증
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
                .isInstanceOf(TossPayoutService.TossApiException.class)
                .hasMessageContaining("지급대행을 처리할 수 없는 셀러 상태입니다");

        // 외부 API 호출이 발생하지 않았는지 검증
        verifyNoInteractions(jweEncryptionService);
        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("입력값 검증 - null 가게 정보")
    void registerSeller_ShouldThrowException_WhenStoreIsNull() {
        // When & Then: null 가게 정보로 셀러 등록 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.registerSeller(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("가게 정보는 필수입니다");
    }

    @Test
    @DisplayName("입력값 검증 - 가게 ID 없음")
    void registerSeller_ShouldThrowException_WhenStoreIdIsNull() {
        // Given: 가게 ID가 없는 Store
        testStore.setId(null);

        // When & Then: 가게 ID 없음 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.registerSeller(testStore))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("가게 ID는 필수입니다");
    }

    @Test
    @DisplayName("입력값 검증 - null 정산 정보")
    void requestPayout_ShouldThrowException_WhenSettlementIsNull() {
        // When & Then: null 정산 정보로 지급대행 요청 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("정산 정보는 필수입니다");
    }

    @Test
    @DisplayName("입력값 검증 - 정산 ID 없음")
    void requestPayout_ShouldThrowException_WhenSettlementIdIsNull() {
        // Given: 정산 ID가 없는 Settlement
        testSettlement.setId(null);

        // When & Then: 정산 ID 없음 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("정산 ID는 필수입니다");
    }

    @Test
    @DisplayName("입력값 검증 - 정산 금액 0 이하")
    void requestPayout_ShouldThrowException_WhenSettlementAmountIsZeroOrNegative() {
        // Given: 정산 금액이 0인 Settlement
        testSettlement.setSettlementAmount(BigDecimal.ZERO);

        // When & Then: 정산 금액 0 이하 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("정산 금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("입력값 검증 - 가게 정보 없음")
    void requestPayout_ShouldThrowException_WhenStoreIsNull() {
        // Given: 가게 정보가 없는 Settlement
        testSettlement.setStore(null);

        // When & Then: 가게 정보 없음 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("가게 정보는 필수입니다");
    }

    @Test
    @DisplayName("입력값 검증 - null 지급대행 ID")
    void cancelPayout_ShouldThrowException_WhenPayoutIdIsNull() {
        // When & Then: null 지급대행 ID로 취소 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.cancelPayout(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("토스 지급대행 ID는 필수입니다");
    }

    @Test
    @DisplayName("입력값 검증 - 빈 지급대행 ID")
    void cancelPayout_ShouldThrowException_WhenPayoutIdIsEmpty() {
        // When & Then: 빈 지급대행 ID로 취소 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.cancelPayout(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("토스 지급대행 ID는 필수입니다");

        assertThatThrownBy(() -> tossPayoutService.cancelPayout("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("토스 지급대행 ID는 필수입니다");
    }

    @Test
    @DisplayName("데이터베이스 트랜잭션 검증")
    void databaseTransactionTest() {
        // Given: 트랜잭션 내에서 셀러 저장
        TossSeller savedSeller = tossSellerRepository.save(testTossSeller);

        // When: 저장된 셀러 조회
        Optional<TossSeller> foundSeller = tossSellerRepository.findByStoreId("integration-store-123");

        // Then: 트랜잭션 내에서 정상적으로 조회되는지 검증
        assertThat(foundSeller).isPresent();
        assertThat(foundSeller.get().getId()).isEqualTo(savedSeller.getId());
        assertThat(foundSeller.get().getTossSellerId()).isEqualTo("integration-toss-seller-789");
        assertThat(foundSeller.get().canProcessPayout()).isTrue();
    }
}