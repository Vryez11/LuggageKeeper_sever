package com.luggagekeeper.keeper_app.settlement.repository;

import com.luggagekeeper.keeper_app.settlement.domain.TossBusinessType;
import com.luggagekeeper.keeper_app.settlement.domain.TossSeller;
import com.luggagekeeper.keeper_app.settlement.domain.TossSellerStatus;
import com.luggagekeeper.keeper_app.store.domain.BusinessType;
import com.luggagekeeper.keeper_app.store.domain.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * TossSellerRepository 통합 테스트
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TossSellerRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TossSellerRepository tossSellerRepository;

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
    @DisplayName("토스 셀러 저장 및 조회")
    void save_AndFindById_ShouldWork() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore1, TossBusinessType.INDIVIDUAL_BUSINESS);

        // When
        TossSeller saved = tossSellerRepository.save(tossSeller);
        Optional<TossSeller> found = tossSellerRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getRefSellerId()).isEqualTo("store-1");
        assertThat(found.get().getBusinessType()).isEqualTo(TossBusinessType.INDIVIDUAL_BUSINESS);
        assertThat(found.get().getStore().getId()).isEqualTo("store-1");
    }

    @Test
    @DisplayName("가게 ID로 토스 셀러 조회")
    void findByStoreId_ShouldReturnCorrectResult() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore1, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSellerRepository.save(tossSeller);
        entityManager.flush();

        // When
        Optional<TossSeller> found = tossSellerRepository.findByStoreId("store-1");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getStore().getId()).isEqualTo("store-1");
        assertThat(found.get().getRefSellerId()).isEqualTo("store-1");
    }

    @Test
    @DisplayName("토스 셀러 ID로 조회")
    void findByTossSellerId_ShouldReturnCorrectResult() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore1, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.assignTossId("toss-seller-123");
        tossSellerRepository.save(tossSeller);
        entityManager.flush();

        // When
        Optional<TossSeller> found = tossSellerRepository.findByTossSellerId("toss-seller-123");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTossSellerId()).isEqualTo("toss-seller-123");
        assertThat(found.get().getStore().getId()).isEqualTo("store-1");
    }

    @Test
    @DisplayName("참조 셀러 ID로 조회")
    void findByRefSellerId_ShouldReturnCorrectResult() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore1, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSellerRepository.save(tossSeller);
        entityManager.flush();

        // When
        Optional<TossSeller> found = tossSellerRepository.findByRefSellerId("store-1");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getRefSellerId()).isEqualTo("store-1");
    }

    @Test
    @DisplayName("상태별 토스 셀러 조회")
    void findByStatusOrderByCreatedAtDesc_ShouldReturnCorrectResults() {
        // Given
        TossSeller tossSeller1 = TossSeller.createTossSeller(testStore1, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller1.updateStatus(TossSellerStatus.APPROVED);
        
        TossSeller tossSeller2 = TossSeller.createTossSeller(testStore2, TossBusinessType.CORPORATE);
        // APPROVAL_REQUIRED 상태 유지
        
        tossSellerRepository.saveAll(List.of(tossSeller1, tossSeller2));
        entityManager.flush();

        // When
        List<TossSeller> approvedSellers = tossSellerRepository.findByStatusOrderByCreatedAtDesc(TossSellerStatus.APPROVED);
        List<TossSeller> pendingSellers = tossSellerRepository.findByStatusOrderByCreatedAtDesc(TossSellerStatus.APPROVAL_REQUIRED);

        // Then
        assertThat(approvedSellers).hasSize(1);
        assertThat(approvedSellers.get(0).getStore().getId()).isEqualTo("store-1");
        
        assertThat(pendingSellers).hasSize(1);
        assertThat(pendingSellers.get(0).getStore().getId()).isEqualTo("store-2");
    }

    @Test
    @DisplayName("지급대행 가능한 셀러 조회")
    void findPayoutEligibleSellers_ShouldReturnCorrectResults() {
        // Given
        TossSeller tossSeller1 = TossSeller.createTossSeller(testStore1, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller1.assignTossId("toss-seller-1");
        tossSeller1.updateStatus(TossSellerStatus.APPROVED);
        
        TossSeller tossSeller2 = TossSeller.createTossSeller(testStore2, TossBusinessType.CORPORATE);
        tossSeller2.assignTossId("toss-seller-2");
        tossSeller2.updateStatus(TossSellerStatus.PARTIALLY_APPROVED);
        
        // 토스 ID가 없는 셀러 (지급대행 불가)
        Store testStore3 = createTestStore("store-3", "test3@example.com", "매장3");
        entityManager.persistAndFlush(testStore3);
        TossSeller tossSeller3 = TossSeller.createTossSeller(testStore3, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller3.updateStatus(TossSellerStatus.APPROVED);
        // 토스 ID 할당하지 않음
        
        // 승인되지 않은 셀러 (지급대행 불가)
        Store testStore4 = createTestStore("store-4", "test4@example.com", "매장4");
        entityManager.persistAndFlush(testStore4);
        TossSeller tossSeller4 = TossSeller.createTossSeller(testStore4, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller4.assignTossId("toss-seller-4");
        // APPROVAL_REQUIRED 상태 유지
        
        tossSellerRepository.saveAll(List.of(tossSeller1, tossSeller2, tossSeller3, tossSeller4));
        entityManager.flush();

        // When
        List<TossSeller> eligibleSellers = tossSellerRepository.findPayoutEligibleSellers();

        // Then
        assertThat(eligibleSellers).hasSize(2);
        assertThat(eligibleSellers).extracting(ts -> ts.getStore().getId())
                .containsExactlyInAnyOrder("store-1", "store-2");
        assertThat(eligibleSellers).allMatch(ts -> ts.getTossSellerId() != null);
        assertThat(eligibleSellers).allMatch(ts -> 
                ts.getStatus() == TossSellerStatus.APPROVED || 
                ts.getStatus() == TossSellerStatus.PARTIALLY_APPROVED);
    }

    @Test
    @DisplayName("승인 대기 중인 셀러 조회")
    void findPendingApprovalSellers_ShouldReturnCorrectResults() {
        // Given
        TossSeller tossSeller1 = TossSeller.createTossSeller(testStore1, TossBusinessType.INDIVIDUAL_BUSINESS);
        // APPROVAL_REQUIRED 상태 유지
        
        TossSeller tossSeller2 = TossSeller.createTossSeller(testStore2, TossBusinessType.CORPORATE);
        tossSeller2.updateStatus(TossSellerStatus.KYC_REQUIRED);
        
        Store testStore3 = createTestStore("store-3", "test3@example.com", "매장3");
        entityManager.persistAndFlush(testStore3);
        TossSeller tossSeller3 = TossSeller.createTossSeller(testStore3, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller3.updateStatus(TossSellerStatus.APPROVED);
        
        tossSellerRepository.saveAll(List.of(tossSeller1, tossSeller2, tossSeller3));
        entityManager.flush();

        // When
        List<TossSeller> pendingSellers = tossSellerRepository.findPendingApprovalSellers();

        // Then
        assertThat(pendingSellers).hasSize(2);
        assertThat(pendingSellers).extracting(ts -> ts.getStore().getId())
                .containsExactlyInAnyOrder("store-1", "store-2");
        assertThat(pendingSellers).extracting(TossSeller::getStatus)
                .containsExactlyInAnyOrder(TossSellerStatus.APPROVAL_REQUIRED, TossSellerStatus.KYC_REQUIRED);
    }

    @Test
    @DisplayName("가게 존재 여부 확인")
    void existsByStoreId_ShouldReturnCorrectResult() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore1, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSellerRepository.save(tossSeller);
        entityManager.flush();

        // When & Then
        assertThat(tossSellerRepository.existsByStoreId("store-1")).isTrue();
        assertThat(tossSellerRepository.existsByStoreId("store-2")).isFalse();
        assertThat(tossSellerRepository.existsByStoreId("non-existent")).isFalse();
    }

    @Test
    @DisplayName("OneToOne 관계 매핑 확인")
    void oneToOneMapping_ShouldWorkCorrectly() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore1, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.assignTossId("toss-seller-123");
        tossSeller.updateStatus(TossSellerStatus.APPROVED);

        // When
        TossSeller saved = tossSellerRepository.save(tossSeller);
        entityManager.flush();
        entityManager.clear(); // 1차 캐시 클리어

        Optional<TossSeller> found = tossSellerRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        TossSeller foundSeller = found.get();
        
        // 기본 필드 확인
        assertThat(foundSeller.getId()).isNotNull();
        assertThat(foundSeller.getRefSellerId()).isEqualTo("store-1");
        assertThat(foundSeller.getTossSellerId()).isEqualTo("toss-seller-123");
        assertThat(foundSeller.getBusinessType()).isEqualTo(TossBusinessType.INDIVIDUAL_BUSINESS);
        assertThat(foundSeller.getStatus()).isEqualTo(TossSellerStatus.APPROVED);
        
        // OneToOne 연관관계 확인 (지연 로딩)
        assertThat(foundSeller.getStore()).isNotNull();
        assertThat(foundSeller.getStore().getId()).isEqualTo("store-1");
        assertThat(foundSeller.getStore().getName()).isEqualTo("매장1");
        
        // 타임스탬프 확인
        assertThat(foundSeller.getCreatedAt()).isNotNull();
        assertThat(foundSeller.getRegisteredAt()).isNotNull();
        assertThat(foundSeller.getApprovedAt()).isNotNull(); // APPROVED 상태이므로
        
        // 비즈니스 로직 메서드 확인
        assertThat(foundSeller.canProcessPayout()).isTrue();
        assertThat(foundSeller.isPendingApproval()).isFalse();
    }

    @Test
    @DisplayName("유니크 제약조건 확인 - 하나의 Store당 하나의 TossSeller")
    void uniqueConstraint_OneStoreOneTossSeller() {
        // Given
        TossSeller tossSeller1 = TossSeller.createTossSeller(testStore1, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSellerRepository.save(tossSeller1);
        entityManager.flush();

        // When & Then - 같은 Store에 대해 두 번째 TossSeller 생성 시도
        TossSeller tossSeller2 = TossSeller.createTossSeller(testStore1, TossBusinessType.CORPORATE);
        
        // 데이터베이스 제약조건에 의해 예외가 발생해야 함
        assertThatThrownBy(() -> {
            tossSellerRepository.save(tossSeller2);
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }
}