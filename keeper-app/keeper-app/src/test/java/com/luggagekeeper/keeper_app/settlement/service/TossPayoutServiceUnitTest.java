package com.luggagekeeper.keeper_app.settlement.service;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TossPayoutService 단위 테스트 (간소화 버전)
 * 
 * 외부 의존성을 최소화하고 핵심 비즈니스 로직에 집중한 단위 테스트입니다.
 * WebClient나 JWE 암호화 등의 복잡한 외부 연동은 Mock으로 처리하고,
 * 입력값 검증과 기본적인 비즈니스 로직 동작을 검증합니다.
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TossPayoutService 단위 테스트 (간소화)")
class TossPayoutServiceUnitTest {

    @Mock
    private TossSellerRepository tossSellerRepository;

    @InjectMocks
    private TossPayoutService tossPayoutService;

    private Store testStore;
    private TossSeller testTossSeller;
    private Settlement testSettlement;

    /**
     * 테스트 데이터 초기화
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
    @DisplayName("셀러 등록 - 기존 셀러 반환")
    void registerSeller_ShouldReturnExisting_WhenSellerAlreadyExists() {
        // Given: 이미 등록된 셀러가 존재하는 시나리오
        when(tossSellerRepository.findByStoreId("store-123")).thenReturn(Optional.of(testTossSeller));

        // When: 셀러 등록 실행
        TossSellerResponse result = tossPayoutService.registerSeller(testStore);

        // Then: 기존 셀러 정보 반환 검증
        assertThat(result).isNotNull();
        assertThat(result.getTossSellerId()).isEqualTo("toss-seller-789");
        assertThat(result.getStoreId()).isEqualTo("store-123");
        assertThat(result.getStatus()).isEqualTo(TossSellerStatus.APPROVED);
        assertThat(result.isCanProcessPayout()).isTrue();
        assertThat(result.isPendingApproval()).isFalse();

        // Repository 조회만 발생하고 저장은 발생하지 않았는지 검증
        verify(tossSellerRepository).findByStoreId("store-123");
        verify(tossSellerRepository, never()).save(any(TossSeller.class));
    }

    @Test
    @DisplayName("지급대행 요청 - 셀러 정보 없음 예외")
    void requestPayout_ShouldThrowException_WhenSellerNotFound() {
        // Given: 셀러 정보가 없는 시나리오
        when(tossSellerRepository.findByStoreId("store-123")).thenReturn(Optional.empty());

        // When & Then: 셀러 정보 없음 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
                .isInstanceOf(TossPayoutService.TossApiException.class)
                .hasMessage("토스 셀러 정보를 찾을 수 없습니다. 가게 ID: store-123");

        verify(tossSellerRepository).findByStoreId("store-123");
    }

    @Test
    @DisplayName("지급대행 요청 - 셀러 승인 미완료 예외")
    void requestPayout_ShouldThrowException_WhenSellerNotApproved() {
        // Given: 승인되지 않은 셀러
        testTossSeller.setStatus(TossSellerStatus.APPROVAL_REQUIRED);
        testTossSeller.setTossSellerId(null); // 토스 셀러 ID 미할당
        when(tossSellerRepository.findByStoreId("store-123")).thenReturn(Optional.of(testTossSeller));

        // When & Then: 승인되지 않은 셀러 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
                .isInstanceOf(TossPayoutService.TossApiException.class)
                .hasMessageContaining("지급대행을 처리할 수 없는 셀러 상태입니다");

        verify(tossSellerRepository).findByStoreId("store-123");
    }

    @Test
    @DisplayName("입력값 검증 - null 가게 정보")
    void registerSeller_ShouldThrowException_WhenStoreIsNull() {
        // When & Then: null 가게 정보로 셀러 등록 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.registerSeller(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("가게 정보는 필수입니다");

        // Repository 호출이 발생하지 않았는지 검증
        verifyNoInteractions(tossSellerRepository);
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

        verifyNoInteractions(tossSellerRepository);
    }

    @Test
    @DisplayName("입력값 검증 - 빈 가게 ID")
    void registerSeller_ShouldThrowException_WhenStoreIdIsEmpty() {
        // Given: 빈 가게 ID
        testStore.setId("");

        // When & Then: 빈 가게 ID 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.registerSeller(testStore))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("가게 ID는 필수입니다");

        verifyNoInteractions(tossSellerRepository);
    }

    @Test
    @DisplayName("입력값 검증 - 공백 가게 ID")
    void registerSeller_ShouldThrowException_WhenStoreIdIsBlank() {
        // Given: 공백 가게 ID
        testStore.setId("   ");

        // When & Then: 공백 가게 ID 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.registerSeller(testStore))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("가게 ID는 필수입니다");

        verifyNoInteractions(tossSellerRepository);
    }

    @Test
    @DisplayName("입력값 검증 - null 정산 정보")
    void requestPayout_ShouldThrowException_WhenSettlementIsNull() {
        // When & Then: null 정산 정보로 지급대행 요청 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("정산 정보는 필수입니다");

        verifyNoInteractions(tossSellerRepository);
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

        verifyNoInteractions(tossSellerRepository);
    }

    @Test
    @DisplayName("입력값 검증 - 빈 정산 ID")
    void requestPayout_ShouldThrowException_WhenSettlementIdIsEmpty() {
        // Given: 빈 정산 ID
        testSettlement.setId("");

        // When & Then: 빈 정산 ID 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("정산 ID는 필수입니다");

        verifyNoInteractions(tossSellerRepository);
    }

    @Test
    @DisplayName("입력값 검증 - 정산 금액 null")
    void requestPayout_ShouldThrowException_WhenSettlementAmountIsNull() {
        // Given: 정산 금액이 null인 Settlement
        testSettlement.setSettlementAmount(null);

        // When & Then: 정산 금액 null 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("정산 금액은 0보다 커야 합니다");

        verifyNoInteractions(tossSellerRepository);
    }

    @Test
    @DisplayName("입력값 검증 - 정산 금액 0")
    void requestPayout_ShouldThrowException_WhenSettlementAmountIsZero() {
        // Given: 정산 금액이 0인 Settlement
        testSettlement.setSettlementAmount(BigDecimal.ZERO);

        // When & Then: 정산 금액 0 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("정산 금액은 0보다 커야 합니다");

        verifyNoInteractions(tossSellerRepository);
    }

    @Test
    @DisplayName("입력값 검증 - 정산 금액 음수")
    void requestPayout_ShouldThrowException_WhenSettlementAmountIsNegative() {
        // Given: 정산 금액이 음수인 Settlement
        testSettlement.setSettlementAmount(new BigDecimal("-1000"));

        // When & Then: 정산 금액 음수 시 예외 발생
        assertThatThrownBy(() -> tossPayoutService.requestPayout(testSettlement))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("정산 금액은 0보다 커야 합니다");

        verifyNoInteractions(tossSellerRepository);
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

        verifyNoInteractions(tossSellerRepository);
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
    @DisplayName("TossSeller 도메인 로직 검증 - 지급대행 가능 여부")
    void tossSeller_CanProcessPayout_ShouldReturnCorrectValue() {
        // Given & When & Then: 승인된 셀러는 지급대행 가능
        assertThat(testTossSeller.canProcessPayout()).isTrue();

        // Given: 토스 셀러 ID가 없는 경우
        testTossSeller.setTossSellerId(null);
        // When & Then: 지급대행 불가능
        assertThat(testTossSeller.canProcessPayout()).isFalse();

        // Given: 승인되지 않은 상태
        testTossSeller.setTossSellerId("toss-seller-789");
        testTossSeller.setStatus(TossSellerStatus.APPROVAL_REQUIRED);
        // When & Then: 지급대행 불가능
        assertThat(testTossSeller.canProcessPayout()).isFalse();

        // Given: 부분 승인 상태
        testTossSeller.setStatus(TossSellerStatus.PARTIALLY_APPROVED);
        // When & Then: 지급대행 가능
        assertThat(testTossSeller.canProcessPayout()).isTrue();
    }

    @Test
    @DisplayName("TossSeller 도메인 로직 검증 - 승인 대기 여부")
    void tossSeller_IsPendingApproval_ShouldReturnCorrectValue() {
        // Given: 승인 필요 상태
        testTossSeller.setStatus(TossSellerStatus.APPROVAL_REQUIRED);
        // When & Then: 승인 대기 중
        assertThat(testTossSeller.isPendingApproval()).isTrue();

        // Given: KYC 필요 상태
        testTossSeller.setStatus(TossSellerStatus.KYC_REQUIRED);
        // When & Then: 승인 대기 중
        assertThat(testTossSeller.isPendingApproval()).isTrue();

        // Given: 승인 완료 상태
        testTossSeller.setStatus(TossSellerStatus.APPROVED);
        // When & Then: 승인 대기 아님
        assertThat(testTossSeller.isPendingApproval()).isFalse();

        // Given: 부분 승인 상태
        testTossSeller.setStatus(TossSellerStatus.PARTIALLY_APPROVED);
        // When & Then: 승인 대기 아님
        assertThat(testTossSeller.isPendingApproval()).isFalse();
    }
}