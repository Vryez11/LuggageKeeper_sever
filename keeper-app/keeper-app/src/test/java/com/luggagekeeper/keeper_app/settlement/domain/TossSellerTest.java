package com.luggagekeeper.keeper_app.settlement.domain;

import com.luggagekeeper.keeper_app.settlement.SettlementTestConfig;
import com.luggagekeeper.keeper_app.store.domain.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * TossSeller 엔티티 단위 테스트
 */
@SpringBootTest
@Import(SettlementTestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TossSellerTest {

    private Store testStore;

    @BeforeEach
    void setUp() {
        testStore = new Store();
        testStore.setId("test-store-id");
        testStore.setName("테스트 매장");
        testStore.setEmail("test@example.com");
    }

    @Test
    @DisplayName("토스 셀러 생성 - 개인사업자")
    void createTossSeller_IndividualBusiness() {
        // Given
        TossBusinessType businessType = TossBusinessType.INDIVIDUAL_BUSINESS;

        // When
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, businessType);

        // Then
        assertThat(tossSeller.getStore()).isEqualTo(testStore);
        assertThat(tossSeller.getRefSellerId()).isEqualTo(testStore.getId());
        assertThat(tossSeller.getBusinessType()).isEqualTo(TossBusinessType.INDIVIDUAL_BUSINESS);
        assertThat(tossSeller.getStatus()).isEqualTo(TossSellerStatus.APPROVAL_REQUIRED);
        assertThat(tossSeller.getTossSellerId()).isNull();
        assertThat(tossSeller.getApprovedAt()).isNull();
    }

    @Test
    @DisplayName("토스 셀러 생성 - 법인사업자")
    void createTossSeller_Corporate() {
        // Given
        TossBusinessType businessType = TossBusinessType.CORPORATE;

        // When
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, businessType);

        // Then
        assertThat(tossSeller.getStore()).isEqualTo(testStore);
        assertThat(tossSeller.getBusinessType()).isEqualTo(TossBusinessType.CORPORATE);
        assertThat(tossSeller.getStatus()).isEqualTo(TossSellerStatus.APPROVAL_REQUIRED);
    }

    @Test
    @DisplayName("토스 셀러 ID 할당")
    void assignTossId_ShouldSetTossId() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        String tossId = "toss-seller-123";

        // When
        tossSeller.assignTossId(tossId);

        // Then
        assertThat(tossSeller.getTossSellerId()).isEqualTo(tossId);
    }

    @Test
    @DisplayName("셀러 승인 처리")
    void approve_ShouldUpdateStatusAndTimestamp() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.assignTossId("toss-seller-123");

        // When
        tossSeller.approve();

        // Then
        assertThat(tossSeller.getStatus()).isEqualTo(TossSellerStatus.APPROVED);
        assertThat(tossSeller.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("셀러 상태 업데이트 - 승인으로 변경")
    void updateStatus_ToApproved_ShouldSetApprovedAt() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);

        // When
        tossSeller.updateStatus(TossSellerStatus.APPROVED);

        // Then
        assertThat(tossSeller.getStatus()).isEqualTo(TossSellerStatus.APPROVED);
        assertThat(tossSeller.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("셀러 상태 업데이트 - 부분 승인으로 변경")
    void updateStatus_ToPartiallyApproved_ShouldNotOverwriteApprovedAt() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.updateStatus(TossSellerStatus.APPROVED);
        LocalDateTime firstApprovedAt = tossSeller.getApprovedAt();

        // When
        tossSeller.updateStatus(TossSellerStatus.PARTIALLY_APPROVED);

        // Then
        assertThat(tossSeller.getStatus()).isEqualTo(TossSellerStatus.PARTIALLY_APPROVED);
        assertThat(tossSeller.getApprovedAt()).isEqualTo(firstApprovedAt); // 기존 시간 유지
    }

    @Test
    @DisplayName("지급대행 가능 여부 확인 - 승인된 셀러")
    void canProcessPayout_ApprovedSeller_ShouldReturnTrue() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.assignTossId("toss-seller-123");
        tossSeller.updateStatus(TossSellerStatus.APPROVED);

        // When & Then
        assertThat(tossSeller.canProcessPayout()).isTrue();
    }

    @Test
    @DisplayName("지급대행 가능 여부 확인 - 부분 승인된 셀러")
    void canProcessPayout_PartiallyApprovedSeller_ShouldReturnTrue() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.assignTossId("toss-seller-123");
        tossSeller.updateStatus(TossSellerStatus.PARTIALLY_APPROVED);

        // When & Then
        assertThat(tossSeller.canProcessPayout()).isTrue();
    }

    @Test
    @DisplayName("지급대행 불가능 - 토스 ID 없음")
    void canProcessPayout_NoTossId_ShouldReturnFalse() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.updateStatus(TossSellerStatus.APPROVED);
        // 토스 ID 할당하지 않음

        // When & Then
        assertThat(tossSeller.canProcessPayout()).isFalse();
    }

    @Test
    @DisplayName("지급대행 불가능 - 승인 대기 상태")
    void canProcessPayout_ApprovalRequired_ShouldReturnFalse() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.assignTossId("toss-seller-123");
        // 상태는 APPROVAL_REQUIRED 유지

        // When & Then
        assertThat(tossSeller.canProcessPayout()).isFalse();
    }

    @Test
    @DisplayName("승인 대기 여부 확인 - APPROVAL_REQUIRED")
    void isPendingApproval_ApprovalRequired_ShouldReturnTrue() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);

        // When & Then
        assertThat(tossSeller.isPendingApproval()).isTrue();
    }

    @Test
    @DisplayName("승인 대기 여부 확인 - KYC_REQUIRED")
    void isPendingApproval_KycRequired_ShouldReturnTrue() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.updateStatus(TossSellerStatus.KYC_REQUIRED);

        // When & Then
        assertThat(tossSeller.isPendingApproval()).isTrue();
    }

    @Test
    @DisplayName("승인 대기 아님 - APPROVED")
    void isPendingApproval_Approved_ShouldReturnFalse() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        tossSeller.updateStatus(TossSellerStatus.APPROVED);

        // When & Then
        assertThat(tossSeller.isPendingApproval()).isFalse();
    }

    @Test
    @DisplayName("PrePersist 콜백 동작 확인")
    void prePersist_ShouldSetDefaultValues() {
        // Given
        TossSeller tossSeller = new TossSeller();
        tossSeller.setStore(testStore);
        tossSeller.setRefSellerId("ref-seller-123");
        tossSeller.setBusinessType(TossBusinessType.INDIVIDUAL_BUSINESS);

        // When
        tossSeller.onCreate(); // @PrePersist 메서드 직접 호출

        // Then
        assertThat(tossSeller.getId()).isNotNull();
        assertThat(tossSeller.getCreatedAt()).isNotNull();
        assertThat(tossSeller.getRegisteredAt()).isNotNull();
        assertThat(tossSeller.getStatus()).isEqualTo(TossSellerStatus.APPROVAL_REQUIRED);
    }

    @Test
    @DisplayName("PreUpdate 콜백 동작 확인")
    void preUpdate_ShouldSetUpdatedAt() {
        // Given
        TossSeller tossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        LocalDateTime beforeUpdate = LocalDateTime.now();

        // When
        tossSeller.onUpdate(); // @PreUpdate 메서드 직접 호출

        // Then
        assertThat(tossSeller.getUpdatedAt()).isNotNull();
        assertThat(tossSeller.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
    }
}