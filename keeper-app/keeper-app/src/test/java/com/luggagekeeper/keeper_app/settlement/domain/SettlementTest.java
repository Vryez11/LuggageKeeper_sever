package com.luggagekeeper.keeper_app.settlement.domain;

import com.luggagekeeper.keeper_app.settlement.SettlementTestConfig;
import com.luggagekeeper.keeper_app.store.domain.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Settlement 엔티티 단위 테스트
 */
@SpringBootTest
@Import(SettlementTestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SettlementTest {

    private Store testStore;

    @BeforeEach
    void setUp() {
        testStore = new Store();
        testStore.setId("test-store-id");
        testStore.setName("테스트 매장");
        testStore.setEmail("test@example.com");
    }

    @Test
    @DisplayName("정산 생성 - 20% 수수료 계산 확인")
    void createSettlement_ShouldCalculate20PercentFee() {
        // Given
        BigDecimal originalAmount = new BigDecimal("10000");
        String orderId = "order-123";

        // When
        Settlement settlement = Settlement.createSettlement(testStore, orderId, originalAmount);

        // Then
        assertThat(settlement.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(settlement.getPlatformFeeRate()).isEqualByComparingTo(new BigDecimal("0.20"));
        assertThat(settlement.getPlatformFee()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(settlement.getSettlementAmount()).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(settlement.getStore()).isEqualTo(testStore);
        assertThat(settlement.getOrderId()).isEqualTo(orderId);
        assertThat(settlement.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("정산 완료 처리")
    void completeSettlement_ShouldUpdateStatusAndTimestamp() {
        // Given
        Settlement settlement = Settlement.createSettlement(testStore, "order-123", new BigDecimal("10000"));
        String tossPayoutId = "payout-123";

        // When
        settlement.completeSettlement(tossPayoutId);

        // Then
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(settlement.getTossPayoutId()).isEqualTo(tossPayoutId);
        assertThat(settlement.getCompletedAt()).isNotNull();
        assertThat(settlement.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("정산 실패 처리")
    void failSettlement_ShouldUpdateStatusAndRetryCount() {
        // Given
        Settlement settlement = Settlement.createSettlement(testStore, "order-123", new BigDecimal("10000"));
        String errorMessage = "잔액 부족";

        // When
        settlement.failSettlement(errorMessage);

        // Then
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(settlement.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(settlement.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("정산 처리 중 상태 변경")
    void startProcessing_ShouldUpdateStatus() {
        // Given
        Settlement settlement = Settlement.createSettlement(testStore, "order-123", new BigDecimal("10000"));

        // When
        settlement.startProcessing();

        // Then
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PROCESSING);
    }

    @Test
    @DisplayName("재시도 가능 여부 확인 - 3회 미만 실패")
    void canRetry_ShouldReturnTrueWhenRetryCountLessThan3() {
        // Given
        Settlement settlement = Settlement.createSettlement(testStore, "order-123", new BigDecimal("10000"));
        settlement.failSettlement("첫 번째 실패");
        settlement.failSettlement("두 번째 실패");

        // When & Then
        assertThat(settlement.canRetry()).isTrue();
        assertThat(settlement.getRetryCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("재시도 불가능 - 3회 실패")
    void canRetry_ShouldReturnFalseWhenRetryCountEquals3() {
        // Given
        Settlement settlement = Settlement.createSettlement(testStore, "order-123", new BigDecimal("10000"));
        settlement.failSettlement("첫 번째 실패");
        settlement.failSettlement("두 번째 실패");
        settlement.failSettlement("세 번째 실패");

        // When & Then
        assertThat(settlement.canRetry()).isFalse();
        assertThat(settlement.getRetryCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("정산 취소 처리")
    void cancelSettlement_ShouldUpdateStatus() {
        // Given
        Settlement settlement = Settlement.createSettlement(testStore, "order-123", new BigDecimal("10000"));

        // When
        settlement.cancelSettlement();

        // Then
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CANCELLED);
    }

    @Test
    @DisplayName("PrePersist 콜백 동작 확인")
    void prePersist_ShouldSetDefaultValues() {
        // Given
        Settlement settlement = new Settlement();
        settlement.setStore(testStore);
        settlement.setOrderId("order-123");
        settlement.setOriginalAmount(new BigDecimal("10000"));

        // When
        settlement.onCreate(); // @PrePersist 메서드 직접 호출

        // Then
        assertThat(settlement.getId()).isNotNull();
        assertThat(settlement.getCreatedAt()).isNotNull();
        assertThat(settlement.getRequestedAt()).isNotNull();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(settlement.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("PreUpdate 콜백 동작 확인")
    void preUpdate_ShouldSetUpdatedAt() {
        // Given
        Settlement settlement = Settlement.createSettlement(testStore, "order-123", new BigDecimal("10000"));
        LocalDateTime beforeUpdate = LocalDateTime.now();

        // When
        settlement.onUpdate(); // @PreUpdate 메서드 직접 호출

        // Then
        assertThat(settlement.getUpdatedAt()).isNotNull();
        assertThat(settlement.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
    }

    @Test
    @DisplayName("소수점 계산 정확성 확인")
    void createSettlement_ShouldHandleDecimalCalculationCorrectly() {
        // Given
        BigDecimal originalAmount = new BigDecimal("12345.67");
        String orderId = "order-decimal-test";

        // When
        Settlement settlement = Settlement.createSettlement(testStore, orderId, originalAmount);

        // Then
        BigDecimal expectedFee = new BigDecimal("2469.134"); // 12345.67 * 0.20
        BigDecimal expectedSettlement = new BigDecimal("9876.536"); // 12345.67 - 2469.134
        
        assertThat(settlement.getPlatformFee()).isEqualByComparingTo(expectedFee);
        assertThat(settlement.getSettlementAmount()).isEqualByComparingTo(expectedSettlement);
    }
}