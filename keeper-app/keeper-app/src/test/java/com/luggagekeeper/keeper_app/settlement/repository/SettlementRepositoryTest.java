package com.luggagekeeper.keeper_app.settlement.repository;

import com.luggagekeeper.keeper_app.settlement.domain.Settlement;
import com.luggagekeeper.keeper_app.settlement.domain.SettlementStatus;
import com.luggagekeeper.keeper_app.store.domain.BusinessType;
import com.luggagekeeper.keeper_app.store.domain.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * SettlementRepository 통합 테스트
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SettlementRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SettlementRepository settlementRepository;

    private Store testStore1;
    private Store testStore2;

    @BeforeEach
    void setUp() {
        // 테스트용 Store 생성
        testStore1 = createTestStore("store-1", "test1@example.com", "매장1");
        testStore2 = createTestStore("store-2", "test2@example.com", "매장2");
        
        entityManager.persistAndFlush(testStore1);
        entityManager.persistAndFlush(testStore2);
    }

    private Store createTestStore(String id, String email, String name) {
        Store store = new Store();
        store.setId(id);
        store.setEmail(email);
        store.setName(name);
        store.setBusinessType(BusinessType.INDIVIDUAL);
        store.setHasCompletedSetup(true);
        store.setCreatedAt(LocalDateTime.now());
        return store;
    }

    @Test
    @DisplayName("정산 저장 및 조회")
    void save_AndFindById_ShouldWork() {
        // Given
        Settlement settlement = Settlement.createSettlement(testStore1, "order-123", new BigDecimal("10000"));

        // When
        Settlement saved = settlementRepository.save(settlement);
        Optional<Settlement> found = settlementRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo("order-123");
        assertThat(found.get().getOriginalAmount()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(found.get().getStore().getId()).isEqualTo("store-1");
    }

    @Test
    @DisplayName("가게별 정산 내역 조회")
    void findByStoreIdOrderByCreatedAtDesc_ShouldReturnCorrectResults() {
        // Given
        Settlement settlement1 = Settlement.createSettlement(testStore1, "order-1", new BigDecimal("10000"));
        Settlement settlement2 = Settlement.createSettlement(testStore1, "order-2", new BigDecimal("20000"));
        Settlement settlement3 = Settlement.createSettlement(testStore2, "order-3", new BigDecimal("30000"));
        
        settlementRepository.saveAll(List.of(settlement1, settlement2, settlement3));
        entityManager.flush();

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Settlement> result = settlementRepository.findByStoreIdOrderByCreatedAtDesc("store-1", pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getOrderId()).isIn("order-1", "order-2");
        assertThat(result.getContent().get(1).getOrderId()).isIn("order-1", "order-2");
        
        // store-2의 정산은 포함되지 않아야 함
        assertThat(result.getContent()).noneMatch(s -> s.getOrderId().equals("order-3"));
    }

    @Test
    @DisplayName("가게별 특정 기간 정산 내역 조회")
    void findByStoreIdAndDateRange_ShouldReturnCorrectResults() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);
        LocalDateTime tomorrow = now.plusDays(1);

        Settlement settlement1 = Settlement.createSettlement(testStore1, "order-1", new BigDecimal("10000"));
        settlement1.setCreatedAt(yesterday);
        
        Settlement settlement2 = Settlement.createSettlement(testStore1, "order-2", new BigDecimal("20000"));
        settlement2.setCreatedAt(now);
        
        Settlement settlement3 = Settlement.createSettlement(testStore1, "order-3", new BigDecimal("30000"));
        settlement3.setCreatedAt(tomorrow);
        
        settlementRepository.saveAll(List.of(settlement1, settlement2, settlement3));
        entityManager.flush();

        Pageable pageable = PageRequest.of(0, 10);

        // When - 어제부터 오늘까지 조회
        Page<Settlement> result = settlementRepository.findByStoreIdAndDateRange(
                "store-1", yesterday.minusHours(1), now.plusHours(1), pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(Settlement::getOrderId)
                .containsExactlyInAnyOrder("order-1", "order-2");
    }

    @Test
    @DisplayName("상태별 정산 내역 조회")
    void findByStatusOrderByCreatedAtDesc_ShouldReturnCorrectResults() {
        // Given
        Settlement settlement1 = Settlement.createSettlement(testStore1, "order-1", new BigDecimal("10000"));
        settlement1.completeSettlement("payout-1");
        
        Settlement settlement2 = Settlement.createSettlement(testStore1, "order-2", new BigDecimal("20000"));
        settlement2.failSettlement("오류 발생");
        
        Settlement settlement3 = Settlement.createSettlement(testStore2, "order-3", new BigDecimal("30000"));
        // PENDING 상태 유지
        
        settlementRepository.saveAll(List.of(settlement1, settlement2, settlement3));
        entityManager.flush();

        // When
        List<Settlement> completedSettlements = settlementRepository.findByStatusOrderByCreatedAtDesc(SettlementStatus.COMPLETED);
        List<Settlement> failedSettlements = settlementRepository.findByStatusOrderByCreatedAtDesc(SettlementStatus.FAILED);
        List<Settlement> pendingSettlements = settlementRepository.findByStatusOrderByCreatedAtDesc(SettlementStatus.PENDING);

        // Then
        assertThat(completedSettlements).hasSize(1);
        assertThat(completedSettlements.get(0).getOrderId()).isEqualTo("order-1");
        
        assertThat(failedSettlements).hasSize(1);
        assertThat(failedSettlements.get(0).getOrderId()).isEqualTo("order-2");
        
        assertThat(pendingSettlements).hasSize(1);
        assertThat(pendingSettlements.get(0).getOrderId()).isEqualTo("order-3");
    }

    @Test
    @DisplayName("주문 ID로 정산 내역 조회")
    void findByOrderId_ShouldReturnCorrectResult() {
        // Given
        Settlement settlement = Settlement.createSettlement(testStore1, "unique-order-123", new BigDecimal("10000"));
        settlementRepository.save(settlement);
        entityManager.flush();

        // When
        Optional<Settlement> found = settlementRepository.findByOrderId("unique-order-123");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo("unique-order-123");
        assertThat(found.get().getStore().getId()).isEqualTo("store-1");
    }

    @Test
    @DisplayName("토스 지급대행 ID로 정산 내역 조회")
    void findByTossPayoutId_ShouldReturnCorrectResult() {
        // Given
        Settlement settlement = Settlement.createSettlement(testStore1, "order-123", new BigDecimal("10000"));
        settlement.completeSettlement("toss-payout-123");
        settlementRepository.save(settlement);
        entityManager.flush();

        // When
        Optional<Settlement> found = settlementRepository.findByTossPayoutId("toss-payout-123");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTossPayoutId()).isEqualTo("toss-payout-123");
        assertThat(found.get().getStatus()).isEqualTo(SettlementStatus.COMPLETED);
    }

    @Test
    @DisplayName("재시도 가능한 실패 정산 내역 조회")
    void findRetryableFailedSettlements_ShouldReturnCorrectResults() {
        // Given
        Settlement settlement1 = Settlement.createSettlement(testStore1, "order-1", new BigDecimal("10000"));
        settlement1.failSettlement("첫 번째 실패"); // retryCount = 1
        
        Settlement settlement2 = Settlement.createSettlement(testStore1, "order-2", new BigDecimal("20000"));
        settlement2.failSettlement("첫 번째 실패"); // retryCount = 1
        settlement2.failSettlement("두 번째 실패"); // retryCount = 2
        settlement2.failSettlement("세 번째 실패"); // retryCount = 3 (재시도 불가)
        
        Settlement settlement3 = Settlement.createSettlement(testStore1, "order-3", new BigDecimal("30000"));
        settlement3.failSettlement("첫 번째 실패"); // retryCount = 1
        settlement3.failSettlement("두 번째 실패"); // retryCount = 2
        
        settlementRepository.saveAll(List.of(settlement1, settlement2, settlement3));
        entityManager.flush();

        // When
        List<Settlement> retryableSettlements = settlementRepository.findRetryableFailedSettlements();

        // Then
        assertThat(retryableSettlements).hasSize(2);
        assertThat(retryableSettlements).extracting(Settlement::getOrderId)
                .containsExactlyInAnyOrder("order-1", "order-3");
        assertThat(retryableSettlements).allMatch(s -> s.getRetryCount() < 3);
        assertThat(retryableSettlements).allMatch(s -> s.getStatus() == SettlementStatus.FAILED);
    }

    @Test
    @DisplayName("가게별 상태별 정산 내역 조회")
    void findByStoreIdAndStatus_ShouldReturnCorrectResults() {
        // Given
        Settlement settlement1 = Settlement.createSettlement(testStore1, "order-1", new BigDecimal("10000"));
        settlement1.completeSettlement("payout-1");
        
        Settlement settlement2 = Settlement.createSettlement(testStore1, "order-2", new BigDecimal("20000"));
        settlement2.completeSettlement("payout-2");
        
        Settlement settlement3 = Settlement.createSettlement(testStore2, "order-3", new BigDecimal("30000"));
        settlement3.completeSettlement("payout-3");
        
        Settlement settlement4 = Settlement.createSettlement(testStore1, "order-4", new BigDecimal("40000"));
        // PENDING 상태 유지
        
        settlementRepository.saveAll(List.of(settlement1, settlement2, settlement3, settlement4));
        entityManager.flush();

        // When
        List<Settlement> store1Completed = settlementRepository.findByStoreIdAndStatus("store-1", SettlementStatus.COMPLETED);
        List<Settlement> store1Pending = settlementRepository.findByStoreIdAndStatus("store-1", SettlementStatus.PENDING);

        // Then
        assertThat(store1Completed).hasSize(2);
        assertThat(store1Completed).extracting(Settlement::getOrderId)
                .containsExactlyInAnyOrder("order-1", "order-2");
        
        assertThat(store1Pending).hasSize(1);
        assertThat(store1Pending.get(0).getOrderId()).isEqualTo("order-4");
    }

    @Test
    @DisplayName("JPA 매핑 및 연관관계 확인")
    void jpaMapping_ShouldWorkCorrectly() {
        // Given
        Settlement settlement = Settlement.createSettlement(testStore1, "order-123", new BigDecimal("10000"));
        settlement.setTossPayoutId("payout-123");
        settlement.setTossSellerId("seller-123");
        settlement.setMetadata("{\"key\": \"value\"}");

        // When
        Settlement saved = settlementRepository.save(settlement);
        entityManager.flush();
        entityManager.clear(); // 1차 캐시 클리어

        Optional<Settlement> found = settlementRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        Settlement foundSettlement = found.get();
        
        // 기본 필드 확인
        assertThat(foundSettlement.getId()).isNotNull();
        assertThat(foundSettlement.getOrderId()).isEqualTo("order-123");
        assertThat(foundSettlement.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(foundSettlement.getTossPayoutId()).isEqualTo("payout-123");
        assertThat(foundSettlement.getTossSellerId()).isEqualTo("seller-123");
        assertThat(foundSettlement.getMetadata()).isEqualTo("{\"key\": \"value\"}");
        
        // 연관관계 확인 (지연 로딩)
        assertThat(foundSettlement.getStore()).isNotNull();
        assertThat(foundSettlement.getStore().getId()).isEqualTo("store-1");
        assertThat(foundSettlement.getStore().getName()).isEqualTo("매장1");
        
        // 타임스탬프 확인
        assertThat(foundSettlement.getCreatedAt()).isNotNull();
        assertThat(foundSettlement.getRequestedAt()).isNotNull();
    }
}