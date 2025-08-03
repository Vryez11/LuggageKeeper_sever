package com.luggagekeeper.keeper_app.settlement.service;

import com.luggagekeeper.keeper_app.settlement.domain.Settlement;
import com.luggagekeeper.keeper_app.settlement.domain.SettlementStatus;
import com.luggagekeeper.keeper_app.settlement.domain.TossSeller;
import com.luggagekeeper.keeper_app.settlement.domain.TossSellerStatus;
import com.luggagekeeper.keeper_app.settlement.domain.TossBusinessType;
import com.luggagekeeper.keeper_app.settlement.dto.TossWebhookEvent;
import com.luggagekeeper.keeper_app.settlement.repository.SettlementRepository;
import com.luggagekeeper.keeper_app.settlement.repository.TossSellerRepository;
import com.luggagekeeper.keeper_app.store.domain.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 웹훅 이벤트 처리 로직 단위 테스트
 * 
 * SettlementService의 웹훅 이벤트 처리 메서드들을 테스트합니다.
 * 지급대행 상태 변경과 셀러 상태 변경 이벤트 처리를 검증합니다.
 * 
 * <p>테스트 범위:</p>
 * <ul>
 *   <li>지급대행 완료/실패/취소 이벤트 처리</li>
 *   <li>셀러 승인/거부 이벤트 처리</li>
 *   <li>중복 이벤트 처리 방지</li>
 *   <li>잘못된 이벤트 데이터 처리</li>
 *   <li>예외 상황 처리</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class WebhookEventProcessingTest {

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private TossSellerRepository tossSellerRepository;

    @Mock
    private TossPayoutService tossPayoutService;

    @InjectMocks
    private SettlementService settlementService;

    private Settlement testSettlement;
    private TossSeller testTossSeller;
    private Store testStore;

    @BeforeEach
    void setUp() {
        // 테스트용 Store 생성
        testStore = new Store();
        testStore.setId("store-123");
        testStore.setName("테스트 가게");

        // 테스트용 Settlement 생성
        testSettlement = Settlement.createSettlement(testStore, "order-456", new BigDecimal("10000"));
        testSettlement.setId("settlement-789");
        testSettlement.setStatus(SettlementStatus.PROCESSING);

        // 테스트용 TossSeller 생성
        testTossSeller = TossSeller.createTossSeller(testStore, TossBusinessType.INDIVIDUAL_BUSINESS);
        testTossSeller.setId("tossseller-123");
        testTossSeller.assignTossId("seller_xyz789");
        testTossSeller.updateStatus(TossSellerStatus.APPROVED);
    }

    @Test
    @DisplayName("지급대행 완료 웹훅 이벤트 처리 성공")
    void handlePayoutStatusChanged_Completed_Success() {
        // Given: 지급대행 완료 이벤트
        TossWebhookEvent event = createPayoutEvent("COMPLETED", "payout_abc123", "settlement-789");
        
        when(settlementRepository.findById("settlement-789"))
                .thenReturn(Optional.of(testSettlement));
        when(settlementRepository.save(any(Settlement.class)))
                .thenReturn(testSettlement);

        // When: 웹훅 이벤트 처리
        boolean result = settlementService.handlePayoutStatusChanged(event);

        // Then: 처리 성공 및 정산 완료 상태 변경
        assertThat(result).isTrue();
        assertThat(testSettlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(testSettlement.getTossPayoutId()).isEqualTo("payout_abc123");
        assertThat(testSettlement.getCompletedAt()).isNotNull();

        verify(settlementRepository).save(testSettlement);
    }

    @Test
    @DisplayName("지급대행 실패 웹훅 이벤트 처리 성공")
    void handlePayoutStatusChanged_Failed_Success() {
        // Given: 지급대행 실패 이벤트
        TossWebhookEvent event = createPayoutEvent("FAILED", "payout_def456", "settlement-789");
        event.getData().put("failureReason", "잔액 부족");
        
        when(settlementRepository.findById("settlement-789"))
                .thenReturn(Optional.of(testSettlement));
        when(settlementRepository.save(any(Settlement.class)))
                .thenReturn(testSettlement);

        // When: 웹훅 이벤트 처리
        boolean result = settlementService.handlePayoutStatusChanged(event);

        // Then: 처리 성공 및 정산 실패 상태 변경
        assertThat(result).isTrue();
        assertThat(testSettlement.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(testSettlement.getTossPayoutId()).isEqualTo("payout_def456");
        assertThat(testSettlement.getErrorMessage()).contains("잔액 부족");
        assertThat(testSettlement.getRetryCount()).isEqualTo(1);

        verify(settlementRepository).save(testSettlement);
    }

    @Test
    @DisplayName("지급대행 취소 웹훅 이벤트 처리 성공")
    void handlePayoutStatusChanged_Cancelled_Success() {
        // Given: 지급대행 취소 이벤트
        TossWebhookEvent event = createPayoutEvent("CANCELLED", "payout_ghi789", "settlement-789");
        
        when(settlementRepository.findById("settlement-789"))
                .thenReturn(Optional.of(testSettlement));
        when(settlementRepository.save(any(Settlement.class)))
                .thenReturn(testSettlement);

        // When: 웹훅 이벤트 처리
        boolean result = settlementService.handlePayoutStatusChanged(event);

        // Then: 처리 성공 및 정산 취소 상태 변경
        assertThat(result).isTrue();
        assertThat(testSettlement.getStatus()).isEqualTo(SettlementStatus.CANCELLED);
        assertThat(testSettlement.getTossPayoutId()).isEqualTo("payout_ghi789");

        verify(settlementRepository).save(testSettlement);
    }

    @Test
    @DisplayName("지급대행 웹훅 이벤트 - 정산 정보 없음")
    void handlePayoutStatusChanged_SettlementNotFound() {
        // Given: 존재하지 않는 정산 ID
        TossWebhookEvent event = createPayoutEvent("COMPLETED", "payout_abc123", "nonexistent-settlement");
        
        when(settlementRepository.findById("nonexistent-settlement"))
                .thenReturn(Optional.empty());

        // When: 웹훅 이벤트 처리
        boolean result = settlementService.handlePayoutStatusChanged(event);

        // Then: 처리하지 않음 (false 반환)
        assertThat(result).isFalse();
        verify(settlementRepository, never()).save(any());
    }

    @Test
    @DisplayName("지급대행 웹훅 이벤트 - 중복 이벤트")
    void handlePayoutStatusChanged_DuplicateEvent() {
        // Given: 이미 완료된 정산
        testSettlement.completeSettlement("payout_abc123");
        TossWebhookEvent event = createPayoutEvent("COMPLETED", "payout_abc123", "settlement-789");
        
        when(settlementRepository.findById("settlement-789"))
                .thenReturn(Optional.of(testSettlement));

        // When: 웹훅 이벤트 처리
        boolean result = settlementService.handlePayoutStatusChanged(event);

        // Then: 중복 처리 방지 (false 반환)
        assertThat(result).isFalse();
        verify(settlementRepository, never()).save(any());
    }

    @Test
    @DisplayName("셀러 승인 완료 웹훅 이벤트 처리 성공")
    void handleSellerStatusChanged_Approved_Success() {
        // Given: 셀러 승인 완료 이벤트
        TossWebhookEvent event = createSellerEvent("APPROVED", "seller_xyz789", "store-123");
        testTossSeller.updateStatus(TossSellerStatus.APPROVAL_REQUIRED); // 초기 상태로 변경
        
        when(tossSellerRepository.findByRefSellerId("store-123"))
                .thenReturn(Optional.of(testTossSeller));
        when(tossSellerRepository.save(any(TossSeller.class)))
                .thenReturn(testTossSeller);

        // When: 웹훅 이벤트 처리
        boolean result = settlementService.handleSellerStatusChanged(event);

        // Then: 처리 성공 및 셀러 승인 상태 변경
        assertThat(result).isTrue();
        assertThat(testTossSeller.getStatus()).isEqualTo(TossSellerStatus.APPROVED);
        assertThat(testTossSeller.getApprovedAt()).isNotNull();

        verify(tossSellerRepository).save(testTossSeller);
    }

    @Test
    @DisplayName("셀러 부분 승인 웹훅 이벤트 처리 성공")
    void handleSellerStatusChanged_PartiallyApproved_Success() {
        // Given: 셀러 부분 승인 이벤트
        TossWebhookEvent event = createSellerEvent("PARTIALLY_APPROVED", "seller_xyz789", "store-123");
        testTossSeller.updateStatus(TossSellerStatus.APPROVAL_REQUIRED); // 초기 상태로 변경
        
        when(tossSellerRepository.findByRefSellerId("store-123"))
                .thenReturn(Optional.of(testTossSeller));
        when(tossSellerRepository.save(any(TossSeller.class)))
                .thenReturn(testTossSeller);

        // When: 웹훅 이벤트 처리
        boolean result = settlementService.handleSellerStatusChanged(event);

        // Then: 처리 성공 및 셀러 부분 승인 상태 변경
        assertThat(result).isTrue();
        assertThat(testTossSeller.getStatus()).isEqualTo(TossSellerStatus.PARTIALLY_APPROVED);

        verify(tossSellerRepository).save(testTossSeller);
    }

    @Test
    @DisplayName("셀러 웹훅 이벤트 - 셀러 정보 없음")
    void handleSellerStatusChanged_SellerNotFound() {
        // Given: 존재하지 않는 셀러 참조 ID
        TossWebhookEvent event = createSellerEvent("APPROVED", "seller_xyz789", "nonexistent-store");
        
        when(tossSellerRepository.findByRefSellerId("nonexistent-store"))
                .thenReturn(Optional.empty());

        // When: 웹훅 이벤트 처리
        boolean result = settlementService.handleSellerStatusChanged(event);

        // Then: 처리하지 않음 (false 반환)
        assertThat(result).isFalse();
        verify(tossSellerRepository, never()).save(any());
    }

    @Test
    @DisplayName("셀러 웹훅 이벤트 - 중복 이벤트")
    void handleSellerStatusChanged_DuplicateEvent() {
        // Given: 이미 승인된 셀러
        testTossSeller.updateStatus(TossSellerStatus.APPROVED);
        TossWebhookEvent event = createSellerEvent("APPROVED", "seller_xyz789", "store-123");
        
        when(tossSellerRepository.findByRefSellerId("store-123"))
                .thenReturn(Optional.of(testTossSeller));

        // When: 웹훅 이벤트 처리
        boolean result = settlementService.handleSellerStatusChanged(event);

        // Then: 중복 처리 방지 (false 반환)
        assertThat(result).isFalse();
        verify(tossSellerRepository, never()).save(any());
    }

    @Test
    @DisplayName("잘못된 웹훅 이벤트 - null 이벤트")
    void handleWebhookEvent_NullEvent() {
        // When & Then: null 이벤트 시 예외 발생
        assertThatThrownBy(() -> settlementService.handlePayoutStatusChanged(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("웹훅 이벤트는 필수입니다");

        assertThatThrownBy(() -> settlementService.handleSellerStatusChanged(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("웹훅 이벤트는 필수입니다");
    }

    @Test
    @DisplayName("잘못된 웹훅 이벤트 - 잘못된 이벤트 타입")
    void handleWebhookEvent_InvalidEventType() {
        // Given: 잘못된 이벤트 타입
        TossWebhookEvent payoutEvent = createPayoutEvent("COMPLETED", "payout_abc123", "settlement-789");
        TossWebhookEvent sellerEvent = createSellerEvent("APPROVED", "seller_xyz789", "store-123");

        // When & Then: 잘못된 이벤트 타입 시 예외 발생
        assertThatThrownBy(() -> settlementService.handlePayoutStatusChanged(sellerEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지급대행 이벤트가 아닙니다: seller.changed");

        assertThatThrownBy(() -> settlementService.handleSellerStatusChanged(payoutEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("셀러 이벤트가 아닙니다: payout.changed");
    }

    @Test
    @DisplayName("잘못된 웹훅 이벤트 - 필수 데이터 누락")
    void handleWebhookEvent_MissingRequiredData() {
        // Given: 필수 데이터가 누락된 이벤트
        TossWebhookEvent event = new TossWebhookEvent();
        event.setEventId("evt_test_123");
        event.setEventType("payout.changed");
        event.setCreatedAt(LocalDateTime.now());
        event.setTimestamp(System.currentTimeMillis() / 1000);
        
        // 빈 데이터 맵 (필수 필드 누락)
        event.setData(new HashMap<>());

        // When & Then: 필수 데이터 누락 시 예외 발생
        assertThatThrownBy(() -> settlementService.handlePayoutStatusChanged(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 웹훅 이벤트입니다");
    }

    /**
     * 지급대행 웹훅 이벤트 생성 헬퍼 메서드
     * 
     * @param status 지급대행 상태
     * @param payoutId 토스 지급대행 ID
     * @param refPayoutId 참조 정산 ID
     * @return 생성된 웹훅 이벤트
     */
    private TossWebhookEvent createPayoutEvent(String status, String payoutId, String refPayoutId) {
        TossWebhookEvent event = new TossWebhookEvent();
        event.setEventId("evt_payout_" + System.currentTimeMillis());
        event.setEventType("payout.changed");
        event.setCreatedAt(LocalDateTime.now());
        event.setTimestamp(System.currentTimeMillis() / 1000);

        Map<String, Object> data = new HashMap<>();
        data.put("payoutId", payoutId);
        data.put("refPayoutId", refPayoutId);
        data.put("status", status);
        data.put("amount", 8000);

        if ("COMPLETED".equals(status)) {
            data.put("completedAt", LocalDateTime.now().toString());
        }

        event.setData(data);
        return event;
    }

    /**
     * 셀러 웹훅 이벤트 생성 헬퍼 메서드
     * 
     * @param status 셀러 상태
     * @param sellerId 토스 셀러 ID
     * @param refSellerId 참조 셀러 ID
     * @return 생성된 웹훅 이벤트
     */
    private TossWebhookEvent createSellerEvent(String status, String sellerId, String refSellerId) {
        TossWebhookEvent event = new TossWebhookEvent();
        event.setEventId("evt_seller_" + System.currentTimeMillis());
        event.setEventType("seller.changed");
        event.setCreatedAt(LocalDateTime.now());
        event.setTimestamp(System.currentTimeMillis() / 1000);

        Map<String, Object> data = new HashMap<>();
        data.put("sellerId", sellerId);
        data.put("refSellerId", refSellerId);
        data.put("status", status);
        data.put("businessType", "INDIVIDUAL_BUSINESS");

        if ("APPROVED".equals(status) || "PARTIALLY_APPROVED".equals(status)) {
            data.put("approvedAt", LocalDateTime.now().toString());
        }

        event.setData(data);
        return event;
    }
}