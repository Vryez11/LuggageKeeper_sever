package com.luggagekeeper.keeper_app.settlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.luggagekeeper.keeper_app.settlement.domain.*;
import com.luggagekeeper.keeper_app.settlement.dto.*;
import com.luggagekeeper.keeper_app.settlement.repository.SettlementRepository;
import com.luggagekeeper.keeper_app.settlement.repository.TossSellerRepository;
import com.luggagekeeper.keeper_app.store.domain.BusinessType;
import com.luggagekeeper.keeper_app.store.domain.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * 정산 시스템 기본 CRUD 동작 검증 테스트
 * Task 2.4: Settlement, TossSeller 엔티티의 기본 CRUD 동작 테스트
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CrudVerificationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private TossSellerRepository tossSellerRepository;

    private ObjectMapper objectMapper;
    private Store testStore;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        // 테스트용 Store 생성
        testStore = new Store();
        testStore.setId("test-store-id");
        testStore.setName("테스트 매장");
        testStore.setEmail("test@example.com");
        testStore.setBusinessType(BusinessType.INDIVIDUAL);
        testStore.setHasCompletedSetup(true);
        testStore.setCreatedAt(LocalDateTime.now());
        
        entityManager.persistAndFlush(testStore);
    }

    @Test
    @DisplayName("Settlement 엔티티 CRUD 동작 확인")
    void settlement_CrudOperations() {
        // Create
        Settlement settlement = Settlement.createSettlement(testStore, "order-123", new BigDecimal("10000"));
        Settlement saved = settlementRepository.save(settlement);
        entityManager.flush();
        
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrderId()).isEqualTo("order-123");
        assertThat(saved.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(saved.getPlatformFee()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(saved.getSettlementAmount()).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.PENDING);
        
        // Read
        Optional<Settlement> found = settlementRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo("order-123");
        
        // Update
        found.get().startProcessing();
        Settlement updated = settlementRepository.save(found.get());
        entityManager.flush();
        
        assertThat(updated.getStatus()).isEqualTo(SettlementStatus.PROCESSING);
        
        // Delete
        settlementRepository.delete(updated);
        entityManager.flush();
        
        Optional<Settlement> deleted = settlementRepository.findById(saved.getId());
        assertThat(deleted).isEmpty();
    }

    @Test
    @DisplayName("TossSeller 엔티티 CRUD 동작 확인")
    void tossSeller_CrudOperations() {
        // Create
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        TossSeller saved = tossSellerRepository.save(tossSeller);
        entityManager.flush();
        
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRefSellerId()).isEqualTo("test-store-id");
        assertThat(saved.getBusinessType()).isEqualTo(TossBusinessType.INDIVIDUAL_BUSINESS);
        assertThat(saved.getStatus()).isEqualTo(TossSellerStatus.APPROVAL_REQUIRED);
        
        // Read
        Optional<TossSeller> found = tossSellerRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getRefSellerId()).isEqualTo("test-store-id");
        
        // Update
        found.get().assignTossId("toss-seller-123");
        found.get().approve();
        TossSeller updated = tossSellerRepository.save(found.get());
        entityManager.flush();
        
        assertThat(updated.getTossSellerId()).isEqualTo("toss-seller-123");
        assertThat(updated.getStatus()).isEqualTo(TossSellerStatus.APPROVED);
        assertThat(updated.getApprovedAt()).isNotNull();
        
        // Delete
        tossSellerRepository.delete(updated);
        entityManager.flush();
        
        Optional<TossSeller> deleted = tossSellerRepository.findById(saved.getId());
        assertThat(deleted).isEmpty();
    }

    @Test
    @DisplayName("Settlement DTO 변환 확인")
    void settlement_DtoConversion() {
        // Given
        Settlement settlement = Settlement.createSettlement(testStore, "order-123", new BigDecimal("10000"));
        settlement = settlementRepository.save(settlement);
        entityManager.flush();
        
        // When - Entity to DTO
        SettlementResponse response = SettlementResponse.from(settlement);
        
        // Then
        assertThat(response.getId()).isEqualTo(settlement.getId());
        assertThat(response.getStoreId()).isEqualTo("test-store-id");
        assertThat(response.getOrderId()).isEqualTo("order-123");
        assertThat(response.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(response.getPlatformFee()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(response.getSettlementAmount()).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat(response.getStatus()).isEqualTo(SettlementStatus.PENDING);
    }

    @Test
    @DisplayName("TossSeller DTO 변환 확인")
    void tossSeller_DtoConversion() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.assignTossId("toss-seller-123");
        tossSeller.approve();
        tossSeller = tossSellerRepository.save(tossSeller);
        entityManager.flush();
        
        // When - Entity to DTO
        TossSellerResponse response = TossSellerResponse.from(tossSeller);
        
        // Then
        assertThat(response.getId()).isEqualTo(tossSeller.getId());
        assertThat(response.getStoreId()).isEqualTo("test-store-id");
        assertThat(response.getRefSellerId()).isEqualTo("test-store-id");
        assertThat(response.getTossSellerId()).isEqualTo("toss-seller-123");
        assertThat(response.getBusinessType()).isEqualTo(TossBusinessType.INDIVIDUAL_BUSINESS);
        assertThat(response.getStatus()).isEqualTo(TossSellerStatus.APPROVED);
        assertThat(response.isCanProcessPayout()).isTrue();
        assertThat(response.isPendingApproval()).isFalse();
    }

    @Test
    @DisplayName("JSON 직렬화/역직렬화 확인 - Flutter 호환성")
    void json_SerializationDeserialization() throws Exception {
        // Settlement JSON 테스트
        Settlement settlement = Settlement.createSettlement(testStore, "order-123", new BigDecimal("10000"));
        settlement = settlementRepository.save(settlement);
        entityManager.flush();
        
        SettlementResponse settlementResponse = SettlementResponse.from(settlement);
        
        // JSON 직렬화
        String settlementJson = objectMapper.writeValueAsString(settlementResponse);
        assertThat(settlementJson).contains("\"storeId\":\"test-store-id\"");
        assertThat(settlementJson).contains("\"orderId\":\"order-123\"");
        assertThat(settlementJson).contains("\"originalAmount\":10000");
        assertThat(settlementJson).contains("\"status\":\"PENDING\"");
        
        // JSON 역직렬화
        SettlementResponse deserializedSettlement = objectMapper.readValue(settlementJson, SettlementResponse.class);
        assertThat(deserializedSettlement.getStoreId()).isEqualTo("test-store-id");
        assertThat(deserializedSettlement.getOrderId()).isEqualTo("order-123");
        assertThat(deserializedSettlement.getStatus()).isEqualTo(SettlementStatus.PENDING);
        
        // TossSeller JSON 테스트
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.assignTossId("toss-seller-123");
        tossSeller.approve();
        tossSeller = tossSellerRepository.save(tossSeller);
        entityManager.flush();
        
        TossSellerResponse tossSellerResponse = TossSellerResponse.from(tossSeller);
        
        // JSON 직렬화
        String tossSellerJson = objectMapper.writeValueAsString(tossSellerResponse);
        assertThat(tossSellerJson).contains("\"storeId\":\"test-store-id\"");
        assertThat(tossSellerJson).contains("\"tossSellerId\":\"toss-seller-123\"");
        assertThat(tossSellerJson).contains("\"businessType\":\"INDIVIDUAL_BUSINESS\"");
        assertThat(tossSellerJson).contains("\"status\":\"APPROVED\"");
        assertThat(tossSellerJson).contains("\"canProcessPayout\":true");
        assertThat(tossSellerJson).contains("\"pendingApproval\":false");
        
        // JSON 역직렬화
        TossSellerResponse deserializedTossSeller = objectMapper.readValue(tossSellerJson, TossSellerResponse.class);
        assertThat(deserializedTossSeller.getStoreId()).isEqualTo("test-store-id");
        assertThat(deserializedTossSeller.getTossSellerId()).isEqualTo("toss-seller-123");
        assertThat(deserializedTossSeller.getStatus()).isEqualTo(TossSellerStatus.APPROVED);
        assertThat(deserializedTossSeller.isCanProcessPayout()).isTrue();
        assertThat(deserializedTossSeller.isPendingApproval()).isFalse();
    }

    @Test
    @DisplayName("Request DTO 검증 및 JSON 처리")
    void requestDto_ValidationAndJson() throws Exception {
        // SettlementRequest 테스트
        SettlementRequest settlementRequest = new SettlementRequest(
                "store-123",
                "order-456",
                new BigDecimal("15000"),
                "{\"metadata\": \"test\"}"
        );
        
        String settlementRequestJson = objectMapper.writeValueAsString(settlementRequest);
        assertThat(settlementRequestJson).contains("\"storeId\":\"store-123\"");
        assertThat(settlementRequestJson).contains("\"orderId\":\"order-456\"");
        assertThat(settlementRequestJson).contains("\"originalAmount\":15000");
        
        SettlementRequest deserializedRequest = objectMapper.readValue(settlementRequestJson, SettlementRequest.class);
        assertThat(deserializedRequest.getStoreId()).isEqualTo("store-123");
        assertThat(deserializedRequest.getOrderId()).isEqualTo("order-456");
        assertThat(deserializedRequest.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("15000"));
        
        // TossSellerRequest 테스트
        TossSellerRequest tossSellerRequest = new TossSellerRequest(
                "store-456",
                TossBusinessType.CORPORATE
        );
        
        String tossSellerRequestJson = objectMapper.writeValueAsString(tossSellerRequest);
        assertThat(tossSellerRequestJson).contains("\"storeId\":\"store-456\"");
        assertThat(tossSellerRequestJson).contains("\"businessType\":\"CORPORATE\"");
        
        TossSellerRequest deserializedTossRequest = objectMapper.readValue(tossSellerRequestJson, TossSellerRequest.class);
        assertThat(deserializedTossRequest.getStoreId()).isEqualTo("store-456");
        assertThat(deserializedTossRequest.getBusinessType()).isEqualTo(TossBusinessType.CORPORATE);
    }

    @Test
    @DisplayName("데이터베이스 연결 및 JPA 매핑 확인")
    void database_ConnectionAndJpaMapping() {
        // Settlement 매핑 확인
        Settlement settlement = Settlement.createSettlement(testStore, "order-123", new BigDecimal("10000"));
        settlement.setMetadata("{\"test\": \"data\"}");
        settlement.setTossPayoutId("payout-123");
        settlement.setTossSellerId("seller-123");
        
        Settlement saved = settlementRepository.save(settlement);
        entityManager.flush();
        entityManager.clear(); // 1차 캐시 클리어
        
        Optional<Settlement> found = settlementRepository.findById(saved.getId());
        assertThat(found).isPresent();
        
        Settlement foundSettlement = found.get();
        assertThat(foundSettlement.getStore().getId()).isEqualTo("test-store-id");
        assertThat(foundSettlement.getMetadata()).isEqualTo("{\"test\": \"data\"}");
        assertThat(foundSettlement.getTossPayoutId()).isEqualTo("payout-123");
        assertThat(foundSettlement.getTossSellerId()).isEqualTo("seller-123");
        
        // TossSeller 매핑 확인
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.assignTossId("toss-seller-123");
        
        TossSeller savedSeller = tossSellerRepository.save(tossSeller);
        entityManager.flush();
        entityManager.clear(); // 1차 캐시 클리어
        
        Optional<TossSeller> foundSeller = tossSellerRepository.findById(savedSeller.getId());
        assertThat(foundSeller).isPresent();
        
        TossSeller foundTossSeller = foundSeller.get();
        assertThat(foundTossSeller.getStore().getId()).isEqualTo("test-store-id");
        assertThat(foundTossSeller.getTossSellerId()).isEqualTo("toss-seller-123");
        assertThat(foundTossSeller.getBusinessType()).isEqualTo(TossBusinessType.INDIVIDUAL_BUSINESS);
    }
}