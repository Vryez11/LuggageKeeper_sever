package com.luggagekeeper.keeper_app.settlement.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.luggagekeeper.keeper_app.settlement.domain.TossBusinessType;
import com.luggagekeeper.keeper_app.settlement.domain.TossSeller;
import com.luggagekeeper.keeper_app.settlement.domain.TossSellerStatus;
import com.luggagekeeper.keeper_app.store.domain.BusinessType;
import com.luggagekeeper.keeper_app.store.domain.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * TossSeller DTO 변환 및 JSON 직렬화/역직렬화 테스트
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TossSellerDtoTest {

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
    @DisplayName("TossSellerRequest DTO 검증 및 JSON 역직렬화")
    void tossSellerRequest_ValidationAndDeserialization() throws Exception {
        // Given
        String json = """
            {
                "storeId": "store-123",
                "businessType": "INDIVIDUAL_BUSINESS"
            }
            """;

        // When
        TossSellerRequest request = objectMapper.readValue(json, TossSellerRequest.class);

        // Then
        assertThat(request.getStoreId()).isEqualTo("store-123");
        assertThat(request.getBusinessType()).isEqualTo(TossBusinessType.INDIVIDUAL_BUSINESS);
    }

    @Test
    @DisplayName("TossSellerRequest JSON 직렬화")
    void tossSellerRequest_Serialization() throws Exception {
        // Given
        TossSellerRequest request = new TossSellerRequest(
                "store-456",
                TossBusinessType.CORPORATE
        );

        // When
        String json = objectMapper.writeValueAsString(request);

        // Then
        assertThat(json).contains("\"storeId\":\"store-456\"");
        assertThat(json).contains("\"businessType\":\"CORPORATE\"");
    }

    @Test
    @DisplayName("TossSeller 엔티티에서 TossSellerResponse DTO 변환")
    void tossSellerResponse_FromEntity() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.assignTossId("toss-seller-123");
        tossSeller.updateStatus(TossSellerStatus.APPROVED);

        // When
        TossSellerResponse response = TossSellerResponse.from(tossSeller);

        // Then
        assertThat(response.getId()).isEqualTo(tossSeller.getId());
        assertThat(response.getStoreId()).isEqualTo("test-store-id");
        assertThat(response.getRefSellerId()).isEqualTo("test-store-id");
        assertThat(response.getTossSellerId()).isEqualTo("toss-seller-123");
        assertThat(response.getBusinessType()).isEqualTo(TossBusinessType.INDIVIDUAL_BUSINESS);
        assertThat(response.getStatus()).isEqualTo(TossSellerStatus.APPROVED);
        assertThat(response.getRegisteredAt()).isEqualTo(tossSeller.getRegisteredAt());
        assertThat(response.getApprovedAt()).isEqualTo(tossSeller.getApprovedAt());
        assertThat(response.getCreatedAt()).isEqualTo(tossSeller.getCreatedAt());
        assertThat(response.isCanProcessPayout()).isTrue();
        assertThat(response.isPendingApproval()).isFalse();
    }

    @Test
    @DisplayName("TossSellerResponse JSON 직렬화 - Flutter 호환성")
    void tossSellerResponse_JsonSerialization_FlutterCompatible() throws Exception {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.CORPORATE);
        // @PrePersist 메서드는 JPA에 의해 자동 호출되므로 수동으로 기본값 설정
        tossSeller.assignTossId("toss-seller-456");
        tossSeller.updateStatus(TossSellerStatus.PARTIALLY_APPROVED);
        
        TossSellerResponse response = TossSellerResponse.from(tossSeller);

        // When
        String json = objectMapper.writeValueAsString(response);

        // Then - Flutter json_serializable 호환 구조 확인
        assertThat(json).contains("\"id\":");
        assertThat(json).contains("\"storeId\":\"test-store-id\"");
        assertThat(json).contains("\"refSellerId\":\"test-store-id\"");
        assertThat(json).contains("\"tossSellerId\":\"toss-seller-456\"");
        assertThat(json).contains("\"businessType\":\"CORPORATE\"");
        assertThat(json).contains("\"status\":\"PARTIALLY_APPROVED\"");
        assertThat(json).contains("\"canProcessPayout\":true");
        assertThat(json).contains("\"pendingApproval\":false");
        
        // 날짜 필드가 ISO 형식으로 직렬화되는지 확인
        assertThat(json).containsPattern("\"registeredAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        assertThat(json).containsPattern("\"createdAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    @DisplayName("TossSellerResponse JSON 역직렬화")
    void tossSellerResponse_JsonDeserialization() throws Exception {
        // Given
        String json = """
            {
                "id": "tossseller-123",
                "storeId": "store-456",
                "refSellerId": "ref-seller-789",
                "tossSellerId": "toss-seller-101",
                "businessType": "INDIVIDUAL_BUSINESS",
                "status": "APPROVED",
                "registeredAt": "2024-01-15T10:30:00",
                "approvedAt": "2024-01-15T11:00:00",
                "createdAt": "2024-01-15T10:30:00",
                "updatedAt": "2024-01-15T11:00:00",
                "canProcessPayout": true,
                "pendingApproval": false
            }
            """;

        // When
        TossSellerResponse response = objectMapper.readValue(json, TossSellerResponse.class);

        // Then
        assertThat(response.getId()).isEqualTo("tossseller-123");
        assertThat(response.getStoreId()).isEqualTo("store-456");
        assertThat(response.getRefSellerId()).isEqualTo("ref-seller-789");
        assertThat(response.getTossSellerId()).isEqualTo("toss-seller-101");
        assertThat(response.getBusinessType()).isEqualTo(TossBusinessType.INDIVIDUAL_BUSINESS);
        assertThat(response.getStatus()).isEqualTo(TossSellerStatus.APPROVED);
        assertThat(response.getRegisteredAt()).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        assertThat(response.getApprovedAt()).isEqualTo(LocalDateTime.of(2024, 1, 15, 11, 0, 0));
        assertThat(response.getCreatedAt()).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        assertThat(response.getUpdatedAt()).isEqualTo(LocalDateTime.of(2024, 1, 15, 11, 0, 0));
        assertThat(response.isCanProcessPayout()).isTrue();
        assertThat(response.isPendingApproval()).isFalse();
    }

    @Test
    @DisplayName("TossSellerResponse 비즈니스 로직 필드 확인 - 승인 대기 상태")
    void tossSellerResponse_BusinessLogicFields_PendingApproval() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.updateStatus(TossSellerStatus.KYC_REQUIRED);
        // 토스 ID 할당하지 않음

        // When
        TossSellerResponse response = TossSellerResponse.from(tossSeller);

        // Then
        assertThat(response.isCanProcessPayout()).isFalse(); // 토스 ID 없고 승인되지 않음
        assertThat(response.isPendingApproval()).isTrue(); // KYC_REQUIRED 상태
    }

    @Test
    @DisplayName("TossSellerResponse 비즈니스 로직 필드 확인 - 지급대행 가능")
    void tossSellerResponse_BusinessLogicFields_PayoutEligible() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.CORPORATE);
        tossSeller.assignTossId("toss-seller-123");
        tossSeller.updateStatus(TossSellerStatus.APPROVED);

        // When
        TossSellerResponse response = TossSellerResponse.from(tossSeller);

        // Then
        assertThat(response.isCanProcessPayout()).isTrue(); // 토스 ID 있고 승인됨
        assertThat(response.isPendingApproval()).isFalse(); // APPROVED 상태
    }

    @Test
    @DisplayName("Enum 값 직렬화/역직렬화 확인")
    void enumSerialization_ShouldWorkCorrectly() throws Exception {
        // TossBusinessType 테스트
        TossSellerRequest request1 = new TossSellerRequest("store-1", TossBusinessType.INDIVIDUAL_BUSINESS);
        String json1 = objectMapper.writeValueAsString(request1);
        assertThat(json1).contains("\"businessType\":\"INDIVIDUAL_BUSINESS\"");
        
        TossSellerRequest deserialized1 = objectMapper.readValue(json1, TossSellerRequest.class);
        assertThat(deserialized1.getBusinessType()).isEqualTo(TossBusinessType.INDIVIDUAL_BUSINESS);

        TossSellerRequest request2 = new TossSellerRequest("store-2", TossBusinessType.CORPORATE);
        String json2 = objectMapper.writeValueAsString(request2);
        assertThat(json2).contains("\"businessType\":\"CORPORATE\"");
        
        TossSellerRequest deserialized2 = objectMapper.readValue(json2, TossSellerRequest.class);
        assertThat(deserialized2.getBusinessType()).isEqualTo(TossBusinessType.CORPORATE);

        // TossSellerStatus 테스트
        TossSellerResponse response = new TossSellerResponse();
        response.setStatus(TossSellerStatus.PARTIALLY_APPROVED);
        
        String responseJson = objectMapper.writeValueAsString(response);
        assertThat(responseJson).contains("\"status\":\"PARTIALLY_APPROVED\"");
        
        TossSellerResponse deserializedResponse = objectMapper.readValue(responseJson, TossSellerResponse.class);
        assertThat(deserializedResponse.getStatus()).isEqualTo(TossSellerStatus.PARTIALLY_APPROVED);
    }

    @Test
    @DisplayName("모든 TossSeller DTO 클래스의 기본 생성자 및 getter/setter 동작 확인")
    void allTossSellerDtos_DefaultConstructorAndGetterSetter() {
        // TossSellerRequest 테스트
        TossSellerRequest request = new TossSellerRequest();
        request.setStoreId("store-123");
        request.setBusinessType(TossBusinessType.CORPORATE);
        
        assertThat(request.getStoreId()).isEqualTo("store-123");
        assertThat(request.getBusinessType()).isEqualTo(TossBusinessType.CORPORATE);

        // TossSellerResponse 테스트
        TossSellerResponse response = new TossSellerResponse();
        response.setId("tossseller-456");
        response.setStoreId("store-789");
        response.setTossSellerId("toss-seller-101");
        response.setStatus(TossSellerStatus.APPROVED);
        response.setCanProcessPayout(true);
        response.setPendingApproval(false);
        
        assertThat(response.getId()).isEqualTo("tossseller-456");
        assertThat(response.getStoreId()).isEqualTo("store-789");
        assertThat(response.getTossSellerId()).isEqualTo("toss-seller-101");
        assertThat(response.getStatus()).isEqualTo(TossSellerStatus.APPROVED);
        assertThat(response.isCanProcessPayout()).isTrue();
        assertThat(response.isPendingApproval()).isFalse();
    }

    @Test
    @DisplayName("null 값 처리 확인")
    void nullValueHandling() throws Exception {
        // Given - null 값이 포함된 JSON
        String json = """
            {
                "id": "tossseller-123",
                "storeId": "store-456",
                "refSellerId": "ref-seller-789",
                "tossSellerId": null,
                "businessType": "INDIVIDUAL_BUSINESS",
                "status": "APPROVAL_REQUIRED",
                "registeredAt": "2024-01-15T10:30:00",
                "approvedAt": null,
                "createdAt": "2024-01-15T10:30:00",
                "updatedAt": null,
                "canProcessPayout": false,
                "pendingApproval": true
            }
            """;

        // When
        TossSellerResponse response = objectMapper.readValue(json, TossSellerResponse.class);

        // Then - null 값들이 올바르게 처리되는지 확인
        assertThat(response.getTossSellerId()).isNull();
        assertThat(response.getApprovedAt()).isNull();
        assertThat(response.getUpdatedAt()).isNull();
        assertThat(response.isCanProcessPayout()).isFalse();
        assertThat(response.isPendingApproval()).isTrue();
    }
}