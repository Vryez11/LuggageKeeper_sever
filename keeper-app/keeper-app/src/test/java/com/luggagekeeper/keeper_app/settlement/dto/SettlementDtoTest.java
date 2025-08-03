package com.luggagekeeper.keeper_app.settlement.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.luggagekeeper.keeper_app.settlement.domain.Settlement;
import com.luggagekeeper.keeper_app.settlement.domain.SettlementStatus;
import com.luggagekeeper.keeper_app.store.domain.BusinessType;
import com.luggagekeeper.keeper_app.store.domain.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Settlement DTO 변환 및 JSON 직렬화/역직렬화 테스트
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SettlementDtoTest {

    private ObjectMapper objectMapper;
    private Store testStore;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        testStore = new Store();
        testStore.setId("test-store-id");
        testStore.setName("테스트 매장");
        testStore.setEmail("test@example.com");
        testStore.setBusinessType(BusinessType.INDIVIDUAL);
        testStore.setHasCompletedSetup(true);
        testStore.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("SettlementRequest DTO 검증 및 JSON 역직렬화")
    void settlementRequest_ValidationAndDeserialization() throws Exception {
        // Given
        String json = """
            {
                "storeId": "store-123",
                "orderId": "order-456",
                "originalAmount": 15000.50,
                "metadata": "{\\"customerInfo\\": \\"test\\"}"
            }
            """;

        // When
        SettlementRequest request = objectMapper.readValue(json, SettlementRequest.class);

        // Then
        assertThat(request.getStoreId()).isEqualTo("store-123");
        assertThat(request.getOrderId()).isEqualTo("order-456");
        assertThat(request.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("15000.50"));
        assertThat(request.getMetadata()).isEqualTo("{\"customerInfo\": \"test\"}");
    }

    @Test
    @DisplayName("SettlementRequest JSON 직렬화")
    void settlementRequest_Serialization() throws Exception {
        // Given
        SettlementRequest request = new SettlementRequest(
                "store-123",
                "order-456",
                new BigDecimal("15000.50"),
                "{\"customerInfo\": \"test\"}"
        );

        // When
        String json = objectMapper.writeValueAsString(request);

        // Then
        assertThat(json).contains("\"storeId\":\"store-123\"");
        assertThat(json).contains("\"orderId\":\"order-456\"");
        assertThat(json).contains("\"originalAmount\":15000.5");
        assertThat(json).contains("\"metadata\":\"{\\\"customerInfo\\\": \\\"test\\\"}\"");
    }

    @Test
    @DisplayName("Settlement 엔티티에서 SettlementResponse DTO 변환")
    void settlementResponse_FromEntity() {
        // Given
        Settlement settlement = Settlement.createSettlement(testStore, "order-123", new BigDecimal("10000"));
        settlement.setTossPayoutId("payout-123");
        settlement.setTossSellerId("seller-123");
        settlement.completeSettlement("payout-123");
        settlement.setMetadata("{\"test\": \"data\"}");

        // When
        SettlementResponse response = SettlementResponse.from(settlement);

        // Then
        assertThat(response.getId()).isEqualTo(settlement.getId());
        assertThat(response.getStoreId()).isEqualTo("test-store-id");
        assertThat(response.getOrderId()).isEqualTo("order-123");
        assertThat(response.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(response.getPlatformFeeRate()).isEqualByComparingTo(new BigDecimal("0.20"));
        assertThat(response.getPlatformFee()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(response.getSettlementAmount()).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat(response.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(response.getTossPayoutId()).isEqualTo("payout-123");
        assertThat(response.getTossSellerId()).isEqualTo("seller-123");
        assertThat(response.getRequestedAt()).isEqualTo(settlement.getRequestedAt());
        assertThat(response.getCompletedAt()).isEqualTo(settlement.getCompletedAt());
        assertThat(response.getRetryCount()).isEqualTo(0);
        assertThat(response.getCreatedAt()).isEqualTo(settlement.getCreatedAt());
    }

    @Test
    @DisplayName("SettlementResponse JSON 직렬화 - Flutter 호환성")
    void settlementResponse_JsonSerialization_FlutterCompatible() throws Exception {
        // Given
        Settlement settlement = Settlement.createSettlement(testStore, "order-123", new BigDecimal("10000"));
        // @PrePersist 메서드는 JPA에 의해 자동 호출되므로 수동으로 기본값 설정
        settlement.setTossPayoutId("payout-123");
        settlement.setTossSellerId("seller-123");
        settlement.completeSettlement("payout-123");
        
        SettlementResponse response = SettlementResponse.from(settlement);

        // When
        String json = objectMapper.writeValueAsString(response);

        // Then - Flutter json_serializable 호환 구조 확인
        assertThat(json).contains("\"id\":");
        assertThat(json).contains("\"storeId\":\"test-store-id\"");
        assertThat(json).contains("\"orderId\":\"order-123\"");
        assertThat(json).contains("\"originalAmount\":10000");
        assertThat(json).contains("\"platformFeeRate\":0.2");
        assertThat(json).contains("\"platformFee\":2000");
        assertThat(json).contains("\"settlementAmount\":8000");
        assertThat(json).contains("\"status\":\"COMPLETED\"");
        assertThat(json).contains("\"tossPayoutId\":\"payout-123\"");
        assertThat(json).contains("\"tossSellerId\":\"seller-123\"");
        assertThat(json).contains("\"retryCount\":0");
        
        // 날짜 필드가 ISO 형식으로 직렬화되는지 확인
        assertThat(json).containsPattern("\"requestedAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        assertThat(json).containsPattern("\"completedAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        assertThat(json).containsPattern("\"createdAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    @DisplayName("SettlementResponse JSON 역직렬화")
    void settlementResponse_JsonDeserialization() throws Exception {
        // Given
        String json = """
            {
                "id": "settlement-123",
                "storeId": "store-456",
                "orderId": "order-789",
                "originalAmount": 25000,
                "platformFeeRate": 0.20,
                "platformFee": 5000,
                "settlementAmount": 20000,
                "status": "COMPLETED",
                "tossPayoutId": "payout-123",
                "tossSellerId": "seller-456",
                "requestedAt": "2024-01-15T10:30:00",
                "completedAt": "2024-01-15T11:00:00",
                "errorMessage": null,
                "retryCount": 0,
                "createdAt": "2024-01-15T10:30:00",
                "updatedAt": "2024-01-15T11:00:00"
            }
            """;

        // When
        SettlementResponse response = objectMapper.readValue(json, SettlementResponse.class);

        // Then
        assertThat(response.getId()).isEqualTo("settlement-123");
        assertThat(response.getStoreId()).isEqualTo("store-456");
        assertThat(response.getOrderId()).isEqualTo("order-789");
        assertThat(response.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("25000"));
        assertThat(response.getPlatformFeeRate()).isEqualByComparingTo(new BigDecimal("0.20"));
        assertThat(response.getPlatformFee()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(response.getSettlementAmount()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(response.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(response.getTossPayoutId()).isEqualTo("payout-123");
        assertThat(response.getTossSellerId()).isEqualTo("seller-456");
        assertThat(response.getRetryCount()).isEqualTo(0);
        assertThat(response.getRequestedAt()).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        assertThat(response.getCompletedAt()).isEqualTo(LocalDateTime.of(2024, 1, 15, 11, 0, 0));
        assertThat(response.getCreatedAt()).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        assertThat(response.getUpdatedAt()).isEqualTo(LocalDateTime.of(2024, 1, 15, 11, 0, 0));
    }

    @Test
    @DisplayName("SettlementSummaryResponse 생성 및 JSON 직렬화")
    void settlementSummaryResponse_CreationAndSerialization() throws Exception {
        // Given
        SettlementSummaryResponse summary = SettlementSummaryResponse.create(
                "store-123",
                LocalDateTime.of(2024, 1, 15, 0, 0).toLocalDate(),
                new BigDecimal("100000"),
                new BigDecimal("20000"),
                new BigDecimal("80000"),
                5L,
                2L,
                1L
        );

        // When
        String json = objectMapper.writeValueAsString(summary);

        // Then
        assertThat(json).contains("\"storeId\":\"store-123\"");
        assertThat(json).contains("\"date\":\"2024-01-15\"");
        assertThat(json).contains("\"totalOriginalAmount\":100000");
        assertThat(json).contains("\"totalPlatformFee\":20000");
        assertThat(json).contains("\"totalSettlementAmount\":80000");
        assertThat(json).contains("\"completedCount\":5");
        assertThat(json).contains("\"pendingCount\":2");
        assertThat(json).contains("\"failedCount\":1");
    }

    @Test
    @DisplayName("SettlementSummaryResponse null 값 처리")
    void settlementSummaryResponse_NullValueHandling() {
        // Given & When
        SettlementSummaryResponse summary = SettlementSummaryResponse.create(
                "store-123",
                LocalDateTime.of(2024, 1, 15, 0, 0).toLocalDate(),
                null, // null 값들
                null,
                null,
                null,
                null,
                null
        );

        // Then - null 값들이 기본값으로 처리되는지 확인
        assertThat(summary.getTotalOriginalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getTotalPlatformFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getTotalSettlementAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getCompletedCount()).isEqualTo(0L);
        assertThat(summary.getPendingCount()).isEqualTo(0L);
        assertThat(summary.getFailedCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("모든 DTO 클래스의 기본 생성자 및 getter/setter 동작 확인")
    void allDtos_DefaultConstructorAndGetterSetter() {
        // SettlementRequest 테스트
        SettlementRequest request = new SettlementRequest();
        request.setStoreId("store-123");
        request.setOrderId("order-456");
        request.setOriginalAmount(new BigDecimal("10000"));
        request.setMetadata("{}");
        
        assertThat(request.getStoreId()).isEqualTo("store-123");
        assertThat(request.getOrderId()).isEqualTo("order-456");
        assertThat(request.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(request.getMetadata()).isEqualTo("{}");

        // SettlementResponse 테스트
        SettlementResponse response = new SettlementResponse();
        response.setId("settlement-123");
        response.setStoreId("store-456");
        response.setStatus(SettlementStatus.PENDING);
        
        assertThat(response.getId()).isEqualTo("settlement-123");
        assertThat(response.getStoreId()).isEqualTo("store-456");
        assertThat(response.getStatus()).isEqualTo(SettlementStatus.PENDING);

        // SettlementSummaryResponse 테스트
        SettlementSummaryResponse summary = new SettlementSummaryResponse();
        summary.setStoreId("store-789");
        summary.setCompletedCount(10L);
        
        assertThat(summary.getStoreId()).isEqualTo("store-789");
        assertThat(summary.getCompletedCount()).isEqualTo(10L);
    }
}