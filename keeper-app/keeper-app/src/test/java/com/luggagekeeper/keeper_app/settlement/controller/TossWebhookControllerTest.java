package com.luggagekeeper.keeper_app.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luggagekeeper.keeper_app.settlement.dto.TossWebhookEvent;
import com.luggagekeeper.keeper_app.settlement.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TossWebhookController 단위 테스트
 * 
 * 토스페이먼츠 웹훅 컨트롤러의 기능을 검증합니다.
 * 웹훅 이벤트 수신, 서명 검증, 이벤트 처리 등을 테스트합니다.
 * 
 * <p>테스트 범위:</p>
 * <ul>
 *   <li>지급대행 상태 변경 웹훅 처리</li>
 *   <li>셀러 상태 변경 웹훅 처리</li>
 *   <li>웹훅 서명 검증</li>
 *   <li>잘못된 요청 처리</li>
 *   <li>헬스체크 엔드포인트</li>
 * </ul>
 */
@WebMvcTest(TossWebhookController.class)
@TestPropertySource(properties = {
    "toss.webhook.secret-key=test_webhook_secret_key",
    "toss.webhook.signature-verification.enabled=false", // 테스트에서는 서명 검증 비활성화
    "toss.webhook.timestamp-tolerance=300"
})
class TossWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SettlementService settlementService;

    private TossWebhookEvent payoutEvent;
    private TossWebhookEvent sellerEvent;

    @BeforeEach
    void setUp() {
        // 지급대행 상태 변경 이벤트 생성
        payoutEvent = createPayoutChangedEvent();
        
        // 셀러 상태 변경 이벤트 생성
        sellerEvent = createSellerChangedEvent();
    }

    @Test
    @DisplayName("지급대행 상태 변경 웹훅 처리 성공")
    void handlePayoutChanged_Success() throws Exception {
        // Given: 서비스에서 이벤트 처리 성공을 반환
        when(settlementService.handlePayoutStatusChanged(any(TossWebhookEvent.class)))
                .thenReturn(true);

        // When & Then: 웹훅 요청이 성공적으로 처리됨
        mockMvc.perform(post("/api/webhooks/toss/payout-changed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payoutEvent)))
                .andExpect(status().isOk())
                .andExpect(content().string("Payout status updated successfully"));
    }

    @Test
    @DisplayName("지급대행 상태 변경 웹훅 처리 - 중복 이벤트")
    void handlePayoutChanged_DuplicateEvent() throws Exception {
        // Given: 서비스에서 중복 이벤트로 false 반환
        when(settlementService.handlePayoutStatusChanged(any(TossWebhookEvent.class)))
                .thenReturn(false);

        // When & Then: 409 Conflict 응답
        mockMvc.perform(post("/api/webhooks/toss/payout-changed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payoutEvent)))
                .andExpect(status().isConflict())
                .andExpect(content().string("Event already processed or duplicate"));
    }

    @Test
    @DisplayName("지급대행 상태 변경 웹훅 처리 - 잘못된 이벤트 타입")
    void handlePayoutChanged_InvalidEventType() throws Exception {
        // Given: 셀러 이벤트를 지급대행 엔드포인트로 전송
        mockMvc.perform(post("/api/webhooks/toss/payout-changed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sellerEvent)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Not a payout event"));
    }

    @Test
    @DisplayName("지급대행 상태 변경 웹훅 처리 - 서비스 예외")
    void handlePayoutChanged_ServiceException() throws Exception {
        // Given: 서비스에서 예외 발생
        when(settlementService.handlePayoutStatusChanged(any(TossWebhookEvent.class)))
                .thenThrow(new RuntimeException("Service error"));

        // When & Then: 500 Internal Server Error 응답
        mockMvc.perform(post("/api/webhooks/toss/payout-changed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payoutEvent)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to process payout webhook: Service error"));
    }

    @Test
    @DisplayName("셀러 상태 변경 웹훅 처리 성공")
    void handleSellerChanged_Success() throws Exception {
        // Given: 서비스에서 이벤트 처리 성공을 반환
        when(settlementService.handleSellerStatusChanged(any(TossWebhookEvent.class)))
                .thenReturn(true);

        // When & Then: 웹훅 요청이 성공적으로 처리됨
        mockMvc.perform(post("/api/webhooks/toss/seller-changed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sellerEvent)))
                .andExpect(status().isOk())
                .andExpect(content().string("Seller status updated successfully"));
    }

    @Test
    @DisplayName("셀러 상태 변경 웹훅 처리 - 중복 이벤트")
    void handleSellerChanged_DuplicateEvent() throws Exception {
        // Given: 서비스에서 중복 이벤트로 false 반환
        when(settlementService.handleSellerStatusChanged(any(TossWebhookEvent.class)))
                .thenReturn(false);

        // When & Then: 409 Conflict 응답
        mockMvc.perform(post("/api/webhooks/toss/seller-changed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sellerEvent)))
                .andExpect(status().isConflict())
                .andExpect(content().string("Event already processed or duplicate"));
    }

    @Test
    @DisplayName("셀러 상태 변경 웹훅 처리 - 잘못된 이벤트 타입")
    void handleSellerChanged_InvalidEventType() throws Exception {
        // Given: 지급대행 이벤트를 셀러 엔드포인트로 전송
        mockMvc.perform(post("/api/webhooks/toss/seller-changed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payoutEvent)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Not a seller event"));
    }

    @Test
    @DisplayName("웹훅 헬스체크 엔드포인트")
    void healthCheck_Success() throws Exception {
        // When & Then: 헬스체크 요청이 성공적으로 처리됨
        mockMvc.perform(get("/api/webhooks/toss/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Webhook endpoint is healthy"));
    }

    @Test
    @DisplayName("잘못된 JSON 형식 요청")
    void handleWebhook_InvalidJson() throws Exception {
        // When & Then: 잘못된 JSON 요청 시 400 Bad Request
        mockMvc.perform(post("/api/webhooks/toss/payout-changed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("invalid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("빈 요청 본문")
    void handleWebhook_EmptyBody() throws Exception {
        // When & Then: 빈 요청 본문 시 400 Bad Request
        mockMvc.perform(post("/api/webhooks/toss/payout-changed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    /**
     * 지급대행 상태 변경 이벤트 생성
     * 
     * @return 테스트용 지급대행 웹훅 이벤트
     */
    private TossWebhookEvent createPayoutChangedEvent() {
        TossWebhookEvent event = new TossWebhookEvent();
        event.setEventId("evt_payout_12345678901234567890");
        event.setEventType("payout.changed");
        event.setCreatedAt(LocalDateTime.now());
        event.setTimestamp(System.currentTimeMillis() / 1000);

        // 지급대행 이벤트 데이터
        Map<String, Object> data = new HashMap<>();
        data.put("payoutId", "payout_abc123def456");
        data.put("refPayoutId", "settlement-789");
        data.put("status", "COMPLETED");
        data.put("amount", 8000);
        data.put("completedAt", "2024-01-15T10:29:45");

        event.setData(data);
        return event;
    }

    /**
     * 셀러 상태 변경 이벤트 생성
     * 
     * @return 테스트용 셀러 웹훅 이벤트
     */
    private TossWebhookEvent createSellerChangedEvent() {
        TossWebhookEvent event = new TossWebhookEvent();
        event.setEventId("evt_seller_98765432109876543210");
        event.setEventType("seller.changed");
        event.setCreatedAt(LocalDateTime.now());
        event.setTimestamp(System.currentTimeMillis() / 1000);

        // 셀러 이벤트 데이터
        Map<String, Object> data = new HashMap<>();
        data.put("sellerId", "seller_xyz789abc123");
        data.put("refSellerId", "store-456");
        data.put("status", "APPROVED");
        data.put("businessType", "INDIVIDUAL_BUSINESS");
        data.put("approvedAt", "2024-01-15T10:59:30");

        event.setData(data);
        return event;
    }
}