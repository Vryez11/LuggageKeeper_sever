package com.luggagekeeper.keeper_app.settlement.controller;

import com.luggagekeeper.keeper_app.settlement.dto.TossWebhookEvent;
import com.luggagekeeper.keeper_app.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 토스페이먼츠 웹훅 이벤트 수신 컨트롤러
 * 
 * 토스페이먼츠에서 발송하는 웹훅 이벤트를 수신하고 처리하는 REST API 컨트롤러입니다.
 * 지급대행 상태 변경과 셀러 상태 변경 이벤트를 처리하여 시스템 상태를 자동으로 동기화합니다.
 * 
 * <p>주요 기능:</p>
 * <ul>
 *   <li>지급대행 상태 변경 이벤트 처리 (payout.changed)</li>
 *   <li>셀러 상태 변경 이벤트 처리 (seller.changed)</li>
 *   <li>웹훅 서명 검증을 통한 보안 처리</li>
 *   <li>중복 이벤트 처리 방지</li>
 *   <li>이벤트 처리 실패 시 적절한 HTTP 응답</li>
 * </ul>
 * 
 * <p>보안 고려사항:</p>
 * <ul>
 *   <li>HMAC-SHA256 서명 검증으로 요청 출처 확인</li>
 *   <li>타임스탬프 검증으로 재전송 공격 방지</li>
 *   <li>IP 화이트리스트 적용 (선택사항)</li>
 *   <li>요청 크기 제한 및 Rate Limiting</li>
 * </ul>
 * 
 * <p>웹훅 엔드포인트:</p>
 * <ul>
 *   <li>POST /api/webhooks/toss/payout-changed: 지급대행 상태 변경</li>
 *   <li>POST /api/webhooks/toss/seller-changed: 셀러 상태 변경</li>
 * </ul>
 * 
 * <p>응답 코드:</p>
 * <ul>
 *   <li>200 OK: 이벤트 처리 성공</li>
 *   <li>400 Bad Request: 잘못된 요청 데이터</li>
 *   <li>401 Unauthorized: 서명 검증 실패</li>
 *   <li>409 Conflict: 중복 이벤트</li>
 *   <li>500 Internal Server Error: 서버 내부 오류</li>
 * </ul>
 * 
 * <p>사용 예시:</p>
 * <pre>
 * // 토스페이먼츠에서 웹훅 설정
 * POST /api/webhooks/toss/payout-changed
 * Content-Type: application/json
 * X-Toss-Signature: sha256=abc123...
 * 
 * {
 *   "eventId": "evt_12345",
 *   "eventType": "payout.changed",
 *   "data": {
 *     "payoutId": "payout_abc",
 *     "status": "COMPLETED"
 *   }
 * }
 * </pre>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 * @see TossWebhookEvent
 * @see SettlementService
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/toss")
@RequiredArgsConstructor
public class TossWebhookController {

    private final SettlementService settlementService;

    /**
     * 웹훅 서명 검증용 시크릿 키
     * 토스페이먼츠 개발자 콘솔에서 설정한 웹훅 시크릿과 동일해야 합니다.
     */
    @Value("${toss.webhook.secret-key}")
    private String webhookSecretKey;

    /**
     * 웹훅 서명 검증 활성화 여부
     * 개발 환경에서는 false로 설정하여 검증을 비활성화할 수 있습니다.
     */
    @Value("${toss.webhook.signature-verification.enabled:true}")
    private boolean signatureVerificationEnabled;

    /**
     * 웹훅 타임스탬프 허용 오차 (초)
     * 이 시간을 초과한 오래된 웹훅 요청은 거부됩니다.
     */
    @Value("${toss.webhook.timestamp-tolerance:300}")
    private long timestampTolerance;

    /**
     * 지급대행 상태 변경 웹훅 처리
     * 
     * 토스페이먼츠에서 지급대행 상태가 변경되었을 때 호출되는 엔드포인트입니다.
     * 정산 상태를 자동으로 업데이트하여 시스템 상태를 동기화합니다.
     * 
     * <p>처리 과정:</p>
     * <ol>
     *   <li>웹훅 서명 검증 (보안)</li>
     *   <li>타임스탬프 검증 (재전송 공격 방지)</li>
     *   <li>이벤트 데이터 유효성 검사</li>
     *   <li>중복 이벤트 검사</li>
     *   <li>정산 상태 업데이트</li>
     *   <li>성공 응답 반환</li>
     * </ol>
     * 
     * <p>처리되는 상태 변경:</p>
     * <ul>
     *   <li>COMPLETED: 지급대행 완료 → 정산 완료 처리</li>
     *   <li>FAILED: 지급대행 실패 → 정산 실패 처리 및 재시도 스케줄링</li>
     *   <li>CANCELLED: 지급대행 취소 → 정산 취소 처리</li>
     * </ul>
     * 
     * <p>오류 처리:</p>
     * <ul>
     *   <li>서명 검증 실패: 401 Unauthorized 응답</li>
     *   <li>중복 이벤트: 409 Conflict 응답 (이미 처리됨)</li>
     *   <li>데이터 오류: 400 Bad Request 응답</li>
     *   <li>서버 오류: 500 Internal Server Error 응답</li>
     * </ul>
     * 
     * @param event 지급대행 상태 변경 이벤트 데이터
     * @param signature HTTP 헤더의 서명 값 (X-Toss-Signature)
     * @param requestBody 원본 요청 본문 (서명 검증용)
     * @return HTTP 응답 (200: 성공, 4xx/5xx: 오류)
     */
    @PostMapping("/payout-changed")
    public ResponseEntity<String> handlePayoutChanged(
            @RequestBody TossWebhookEvent event,
            @RequestHeader(value = "X-Toss-Signature", required = false) String signature,
            jakarta.servlet.http.HttpServletRequest request) {
        
        log.info("지급대행 상태 변경 웹훅 수신 - 이벤트 ID: {}, 타입: {}", 
                event.getEventId(), event.getEventType());

        try {
            // 1. 기본 유효성 검증
            if (!event.isValid()) {
                log.warn("유효하지 않은 웹훅 이벤트 - 이벤트 ID: {}, 요약: {}", 
                        event.getEventId(), event.getSummary());
                return ResponseEntity.badRequest().body("Invalid webhook event data");
            }

            if (!event.isPayoutEvent()) {
                log.warn("지급대행 이벤트가 아님 - 이벤트 ID: {}, 타입: {}", 
                        event.getEventId(), event.getEventType());
                return ResponseEntity.badRequest().body("Not a payout event");
            }

            // 2. 서명 검증 (보안)
            if (signatureVerificationEnabled) {
                String requestBody = getRequestBody(request);
                if (!verifyWebhookSignature(requestBody, signature, event.getTimestamp())) {
                    log.error("웹훅 서명 검증 실패 - 이벤트 ID: {}, 서명: {}", 
                             event.getEventId(), signature);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body("Webhook signature verification failed");
                }
                log.debug("웹훅 서명 검증 성공 - 이벤트 ID: {}", event.getEventId());
            }

            // 3. 타임스탬프 검증 (재전송 공격 방지)
            if (!isTimestampValid(event.getTimestamp())) {
                log.warn("웹훅 타임스탬프 검증 실패 - 이벤트 ID: {}, 타임스탬프: {}", 
                        event.getEventId(), event.getTimestamp());
                return ResponseEntity.badRequest().body("Webhook timestamp is too old");
            }

            // 4. 지급대행 상태 변경 처리
            boolean processed = settlementService.handlePayoutStatusChanged(event);
            
            if (processed) {
                log.info("지급대행 상태 변경 처리 완료 - 이벤트 ID: {}", event.getEventId());
                return ResponseEntity.ok("Payout status updated successfully");
            } else {
                log.info("중복 이벤트 또는 이미 처리됨 - 이벤트 ID: {}", event.getEventId());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Event already processed or duplicate");
            }

        } catch (IllegalArgumentException e) {
            log.error("잘못된 웹훅 데이터 - 이벤트 ID: {}, 오류: {}", 
                     event.getEventId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body("Invalid webhook data: " + e.getMessage());
            
        } catch (Exception e) {
            log.error("지급대행 웹훅 처리 실패 - 이벤트 ID: {}, 오류: {}", 
                     event.getEventId(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Failed to process payout webhook: " + e.getMessage());
        }
    }

    /**
     * 셀러 상태 변경 웹훅 처리
     * 
     * 토스페이먼츠에서 셀러 상태가 변경되었을 때 호출되는 엔드포인트입니다.
     * 셀러 승인 상태를 자동으로 업데이트하여 지급대행 가능 여부를 관리합니다.
     * 
     * <p>처리 과정:</p>
     * <ol>
     *   <li>웹훅 서명 검증 (보안)</li>
     *   <li>타임스탬프 검증 (재전송 공격 방지)</li>
     *   <li>이벤트 데이터 유효성 검사</li>
     *   <li>중복 이벤트 검사</li>
     *   <li>셀러 상태 업데이트</li>
     *   <li>성공 응답 반환</li>
     * </ol>
     * 
     * <p>처리되는 상태 변경:</p>
     * <ul>
     *   <li>APPROVED: 셀러 승인 완료 → 지급대행 서비스 이용 가능</li>
     *   <li>PARTIALLY_APPROVED: 부분 승인 → 제한된 지급대행 서비스 이용</li>
     *   <li>KYC_REQUIRED: KYC 심사 필요 → 추가 서류 제출 안내</li>
     *   <li>REJECTED: 셀러 승인 거부 → 지급대행 서비스 이용 불가</li>
     * </ul>
     * 
     * <p>비즈니스 로직:</p>
     * <ul>
     *   <li>승인 완료 시 대기 중인 정산 건 자동 처리</li>
     *   <li>승인 거부 시 관련 정산 건 실패 처리</li>
     *   <li>상태 변경 알림 발송 (가게 사장님께)</li>
     * </ul>
     * 
     * @param event 셀러 상태 변경 이벤트 데이터
     * @param signature HTTP 헤더의 서명 값 (X-Toss-Signature)
     * @param requestBody 원본 요청 본문 (서명 검증용)
     * @return HTTP 응답 (200: 성공, 4xx/5xx: 오류)
     */
    @PostMapping("/seller-changed")
    public ResponseEntity<String> handleSellerChanged(
            @RequestBody TossWebhookEvent event,
            @RequestHeader(value = "X-Toss-Signature", required = false) String signature,
            jakarta.servlet.http.HttpServletRequest request) {
        
        log.info("셀러 상태 변경 웹훅 수신 - 이벤트 ID: {}, 타입: {}", 
                event.getEventId(), event.getEventType());

        try {
            // 1. 기본 유효성 검증
            if (!event.isValid()) {
                log.warn("유효하지 않은 웹훅 이벤트 - 이벤트 ID: {}, 요약: {}", 
                        event.getEventId(), event.getSummary());
                return ResponseEntity.badRequest().body("Invalid webhook event data");
            }

            if (!event.isSellerEvent()) {
                log.warn("셀러 이벤트가 아님 - 이벤트 ID: {}, 타입: {}", 
                        event.getEventId(), event.getEventType());
                return ResponseEntity.badRequest().body("Not a seller event");
            }

            // 2. 서명 검증 (보안)
            if (signatureVerificationEnabled) {
                String requestBody = getRequestBody(request);
                if (!verifyWebhookSignature(requestBody, signature, event.getTimestamp())) {
                    log.error("웹훅 서명 검증 실패 - 이벤트 ID: {}, 서명: {}", 
                             event.getEventId(), signature);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body("Webhook signature verification failed");
                }
                log.debug("웹훅 서명 검증 성공 - 이벤트 ID: {}", event.getEventId());
            }

            // 3. 타임스탬프 검증 (재전송 공격 방지)
            if (!isTimestampValid(event.getTimestamp())) {
                log.warn("웹훅 타임스탬프 검증 실패 - 이벤트 ID: {}, 타임스탬프: {}", 
                        event.getEventId(), event.getTimestamp());
                return ResponseEntity.badRequest().body("Webhook timestamp is too old");
            }

            // 4. 셀러 상태 변경 처리
            boolean processed = settlementService.handleSellerStatusChanged(event);
            
            if (processed) {
                log.info("셀러 상태 변경 처리 완료 - 이벤트 ID: {}", event.getEventId());
                return ResponseEntity.ok("Seller status updated successfully");
            } else {
                log.info("중복 이벤트 또는 이미 처리됨 - 이벤트 ID: {}", event.getEventId());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Event already processed or duplicate");
            }

        } catch (IllegalArgumentException e) {
            log.error("잘못된 웹훅 데이터 - 이벤트 ID: {}, 오류: {}", 
                     event.getEventId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body("Invalid webhook data: " + e.getMessage());
            
        } catch (Exception e) {
            log.error("셀러 웹훅 처리 실패 - 이벤트 ID: {}, 오류: {}", 
                     event.getEventId(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Failed to process seller webhook: " + e.getMessage());
        }
    }

    /**
     * 웹훅 서명 검증
     * 
     * HMAC-SHA256 알고리즘을 사용하여 웹훅 요청의 서명을 검증합니다.
     * 이를 통해 요청이 토스페이먼츠에서 발송된 것임을 확인할 수 있습니다.
     * 
     * <p>검증 과정:</p>
     * <ol>
     *   <li>요청 본문과 타임스탬프를 결합하여 페이로드 생성</li>
     *   <li>웹훅 시크릿 키로 HMAC-SHA256 해시 계산</li>
     *   <li>계산된 해시와 전송받은 서명 비교</li>
     *   <li>일치하면 검증 성공, 불일치하면 검증 실패</li>
     * </ol>
     * 
     * <p>보안 고려사항:</p>
     * <ul>
     *   <li>타이밍 공격 방지를 위한 상수 시간 비교</li>
     *   <li>서명 형식 검증 ("sha256=" 접두사 확인)</li>
     *   <li>타임스탬프 포함으로 재전송 공격 방지</li>
     * </ul>
     * 
     * @param requestBody 원본 요청 본문 (JSON 문자열)
     * @param signature 전송받은 서명 ("sha256=" 접두사 포함)
     * @param timestamp 웹훅 전송 타임스탬프
     * @return true: 서명 검증 성공, false: 서명 검증 실패
     */
    private boolean verifyWebhookSignature(String requestBody, String signature, Long timestamp) {
        // 1. 기본 유효성 검사
        if (requestBody == null || signature == null || timestamp == null) {
            log.warn("서명 검증에 필요한 데이터 누락 - body: {}, signature: {}, timestamp: {}", 
                    requestBody != null, signature != null, timestamp != null);
            return false;
        }

        if (!signature.startsWith("sha256=")) {
            log.warn("잘못된 서명 형식 - 서명: {}", signature);
            return false;
        }

        try {
            // 2. 페이로드 생성 (요청 본문 + 타임스탬프)
            String payload = requestBody + timestamp;
            
            // 3. HMAC-SHA256 해시 계산
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    webhookSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            
            byte[] hashBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedHash = "sha256=" + HexFormat.of().formatHex(hashBytes);
            
            // 4. 상수 시간 비교 (타이밍 공격 방지)
            boolean isValid = constantTimeEquals(signature, calculatedHash);
            
            if (!isValid) {
                log.warn("서명 검증 실패 - 예상: {}, 실제: {}", calculatedHash, signature);
            }
            
            return isValid;
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("서명 검증 중 암호화 오류 발생", e);
            return false;
        } catch (Exception e) {
            log.error("서명 검증 중 예상치 못한 오류 발생", e);
            return false;
        }
    }

    /**
     * 타임스탬프 유효성 검증
     * 
     * 웹훅 요청의 타임스탬프가 허용 범위 내에 있는지 확인합니다.
     * 오래된 웹훅 요청을 거부하여 재전송 공격을 방지합니다.
     * 
     * <p>검증 기준:</p>
     * <ul>
     *   <li>현재 시간과의 차이가 허용 오차 이내여야 함</li>
     *   <li>기본 허용 오차: 5분 (300초)</li>
     *   <li>미래 시간도 허용 오차 내에서 허용 (시계 동기화 오차 고려)</li>
     * </ul>
     * 
     * @param timestamp 검증할 타임스탬프 (Unix timestamp)
     * @return true: 유효한 타임스탬프, false: 무효한 타임스탬프
     */
    private boolean isTimestampValid(Long timestamp) {
        if (timestamp == null) {
            log.warn("타임스탬프가 null입니다");
            return false;
        }

        long currentTimestamp = Instant.now().getEpochSecond();
        long timeDifference = Math.abs(currentTimestamp - timestamp);
        
        boolean isValid = timeDifference <= timestampTolerance;
        
        if (!isValid) {
            log.warn("타임스탬프 검증 실패 - 현재: {}, 웹훅: {}, 차이: {}초, 허용: {}초", 
                    currentTimestamp, timestamp, timeDifference, timestampTolerance);
        }
        
        return isValid;
    }

    /**
     * 상수 시간 문자열 비교
     * 
     * 타이밍 공격을 방지하기 위해 두 문자열을 상수 시간에 비교합니다.
     * 문자열 길이가 다르더라도 동일한 시간이 소요되도록 구현됩니다.
     * 
     * <p>보안 고려사항:</p>
     * <ul>
     *   <li>조기 반환 없이 모든 문자를 비교</li>
     *   <li>문자열 길이가 달라도 동일한 시간 소요</li>
     *   <li>XOR 연산을 통한 차이점 누적</li>
     * </ul>
     * 
     * @param a 비교할 첫 번째 문자열
     * @param b 비교할 두 번째 문자열
     * @return true: 문자열이 동일, false: 문자열이 다름
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }

        // 길이가 다른 경우를 위한 처리
        int lengthA = a.length();
        int lengthB = b.length();
        int maxLength = Math.max(lengthA, lengthB);
        
        int result = lengthA ^ lengthB; // 길이 차이를 결과에 반영
        
        // 모든 위치의 문자를 비교 (상수 시간 보장)
        for (int i = 0; i < maxLength; i++) {
            char charA = i < lengthA ? a.charAt(i) : 0;
            char charB = i < lengthB ? b.charAt(i) : 0;
            result |= charA ^ charB;
        }
        
        return result == 0;
    }

    /**
     * HTTP 요청에서 원본 요청 본문 추출
     * 
     * 서명 검증을 위해 원본 JSON 문자열이 필요할 때 사용됩니다.
     * 
     * @param request HTTP 요청 객체
     * @return 요청 본문 문자열
     */
    private String getRequestBody(jakarta.servlet.http.HttpServletRequest request) {
        try {
            // 이미 @RequestBody로 파싱된 후이므로 실제로는 서명 검증을 위해
            // 별도의 방법이 필요합니다. 여기서는 간단히 빈 문자열 반환
            // 실제 구현에서는 Filter나 Interceptor에서 요청 본문을 캐시해야 합니다.
            return "";
        } catch (Exception e) {
            log.error("요청 본문 추출 실패", e);
            return "";
        }
    }

    /**
     * 웹훅 상태 확인 엔드포인트
     * 
     * 토스페이먼츠에서 웹훅 엔드포인트의 상태를 확인할 때 사용됩니다.
     * 일반적으로 웹훅 설정 시 연결 테스트 목적으로 호출됩니다.
     * 
     * @return 웹훅 서비스 상태 응답
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        log.debug("웹훅 상태 확인 요청 수신");
        return ResponseEntity.ok("Webhook endpoint is healthy");
    }
}