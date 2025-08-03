package com.luggagekeeper.keeper_app.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 토스페이먼츠 웹훅 이벤트 수신용 DTO
 * 
 * 토스페이먼츠에서 발송하는 웹훅 이벤트를 수신하기 위한 데이터 전송 객체입니다.
 * 지급대행 상태 변경(payout.changed)과 셀러 상태 변경(seller.changed) 이벤트를 처리합니다.
 * 
 * <p>주요 기능:</p>
 * <ul>
 *   <li>웹훅 이벤트 타입별 데이터 수신</li>
 *   <li>이벤트 발생 시간 및 메타데이터 관리</li>
 *   <li>중복 이벤트 처리 방지를 위한 이벤트 ID 제공</li>
 *   <li>이벤트 검증을 위한 서명 정보 포함</li>
 * </ul>
 * 
 * <p>지원하는 이벤트 타입:</p>
 * <ul>
 *   <li>payout.changed: 지급대행 상태 변경 (COMPLETED, FAILED 등)</li>
 *   <li>seller.changed: 셀러 상태 변경 (APPROVED, KYC_REQUIRED 등)</li>
 * </ul>
 * 
 * <p>웹훅 보안:</p>
 * <ul>
 *   <li>서명 검증을 통한 요청 출처 확인</li>
 *   <li>타임스탬프 검증을 통한 재전송 공격 방지</li>
 *   <li>중복 이벤트 ID 검사를 통한 중복 처리 방지</li>
 * </ul>
 * 
 * <p>JSON 예시 (payout.changed):</p>
 * <pre>
 * {
 *   "eventId": "evt_12345678901234567890",
 *   "eventType": "payout.changed",
 *   "createdAt": "2024-01-15T10:30:00",
 *   "data": {
 *     "payoutId": "payout_abc123",
 *     "refPayoutId": "settlement-456",
 *     "status": "COMPLETED",
 *     "amount": 8000,
 *     "completedAt": "2024-01-15T10:29:45"
 *   },
 *   "signature": "sha256=abc123...",
 *   "timestamp": 1705123800
 * }
 * </pre>
 * 
 * <p>JSON 예시 (seller.changed):</p>
 * <pre>
 * {
 *   "eventId": "evt_98765432109876543210",
 *   "eventType": "seller.changed",
 *   "createdAt": "2024-01-15T11:00:00",
 *   "data": {
 *     "sellerId": "seller_xyz789",
 *     "refSellerId": "store-123",
 *     "status": "APPROVED",
 *     "businessType": "INDIVIDUAL_BUSINESS",
 *     "approvedAt": "2024-01-15T10:59:30"
 *   },
 *   "signature": "sha256=def456...",
 *   "timestamp": 1705125600
 * }
 * </pre>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 * @see TossWebhookController
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TossWebhookEvent {

    /**
     * 웹훅 이벤트 고유 식별자
     * 
     * 토스페이먼츠에서 생성하는 이벤트별 고유 ID입니다.
     * 중복 이벤트 처리 방지를 위해 사용되며,
     * 일반적으로 "evt_" 접두사를 가진 20자리 문자열입니다.
     * 
     * <p>사용 목적:</p>
     * <ul>
     *   <li>중복 이벤트 처리 방지 (멱등성 보장)</li>
     *   <li>이벤트 추적 및 디버깅</li>
     *   <li>로그 상관관계 분석</li>
     * </ul>
     */
    private String eventId;

    /**
     * 웹훅 이벤트 타입
     * 
     * 발생한 이벤트의 종류를 나타내는 문자열입니다.
     * 이벤트 타입에 따라 data 필드의 구조가 달라집니다.
     * 
     * <p>지원하는 이벤트 타입:</p>
     * <ul>
     *   <li>"payout.changed": 지급대행 상태 변경</li>
     *   <li>"seller.changed": 셀러 상태 변경</li>
     * </ul>
     */
    private String eventType;

    /**
     * 이벤트 발생 시각
     * 
     * 토스페이먼츠 시스템에서 이벤트가 발생한 시간입니다.
     * ISO 8601 형식으로 전송되며, 한국 시간(KST) 기준입니다.
     * 
     * <p>용도:</p>
     * <ul>
     *   <li>이벤트 순서 정렬</li>
     *   <li>지연 시간 분석</li>
     *   <li>타임스탬프 검증</li>
     * </ul>
     */
    private LocalDateTime createdAt;

    /**
     * 이벤트 상세 데이터
     * 
     * 이벤트 타입에 따른 구체적인 데이터를 포함하는 맵입니다.
     * 동적인 구조를 가지므로 Map<String, Object>로 정의됩니다.
     * 
     * <p>payout.changed 이벤트 데이터:</p>
     * <ul>
     *   <li>payoutId: 토스 지급대행 ID</li>
     *   <li>refPayoutId: 우리 시스템의 정산 ID</li>
     *   <li>status: 변경된 상태 (COMPLETED, FAILED 등)</li>
     *   <li>amount: 지급 금액</li>
     *   <li>completedAt: 완료 시간 (완료된 경우)</li>
     *   <li>failureReason: 실패 사유 (실패한 경우)</li>
     * </ul>
     * 
     * <p>seller.changed 이벤트 데이터:</p>
     * <ul>
     *   <li>sellerId: 토스 셀러 ID</li>
     *   <li>refSellerId: 우리 시스템의 셀러 참조 ID</li>
     *   <li>status: 변경된 상태 (APPROVED, KYC_REQUIRED 등)</li>
     *   <li>businessType: 사업자 타입</li>
     *   <li>approvedAt: 승인 시간 (승인된 경우)</li>
     * </ul>
     */
    private Map<String, Object> data;

    /**
     * 웹훅 서명 (선택사항)
     * 
     * 토스페이먼츠에서 생성한 HMAC-SHA256 서명입니다.
     * 웹훅 요청의 무결성과 출처를 검증하는 데 사용됩니다.
     * 
     * <p>서명 검증 과정:</p>
     * <ol>
     *   <li>요청 본문과 타임스탬프를 결합</li>
     *   <li>웹훅 시크릿 키로 HMAC-SHA256 해시 생성</li>
     *   <li>생성된 해시와 서명 비교</li>
     * </ol>
     * 
     * <p>형식:</p>
     * <ul>
     *   <li>"sha256=" 접두사 + 64자리 16진수 해시</li>
     *   <li>예: "sha256=abc123def456..."</li>
     * </ul>
     */
    private String signature;

    /**
     * 웹훅 전송 타임스탬프 (Unix timestamp)
     * 
     * 토스페이먼츠에서 웹훅을 전송한 시간의 Unix timestamp입니다.
     * 서명 검증과 재전송 공격 방지에 사용됩니다.
     * 
     * <p>용도:</p>
     * <ul>
     *   <li>서명 검증 시 페이로드에 포함</li>
     *   <li>오래된 웹훅 요청 거부 (5분 이상 지연)</li>
     *   <li>타임스탬프 기반 중복 검사</li>
     * </ul>
     */
    private Long timestamp;

    /**
     * 지급대행 상태 변경 이벤트인지 확인
     * 
     * @return true: 지급대행 이벤트, false: 다른 이벤트
     */
    public boolean isPayoutEvent() {
        return "payout.changed".equals(eventType);
    }

    /**
     * 셀러 상태 변경 이벤트인지 확인
     * 
     * @return true: 셀러 이벤트, false: 다른 이벤트
     */
    public boolean isSellerEvent() {
        return "seller.changed".equals(eventType);
    }

    /**
     * 이벤트 데이터에서 특정 필드 값 추출
     * 
     * @param key 추출할 필드명
     * @return 필드 값 (없으면 null)
     */
    public Object getDataValue(String key) {
        return data != null ? data.get(key) : null;
    }

    /**
     * 이벤트 데이터에서 문자열 필드 값 추출
     * 
     * @param key 추출할 필드명
     * @return 문자열 값 (없거나 null이면 null)
     */
    public String getDataString(String key) {
        Object value = getDataValue(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 웹훅 이벤트의 유효성 검증
     * 
     * 필수 필드들이 모두 존재하는지 확인합니다.
     * 컨트롤러에서 이벤트 처리 전에 호출됩니다.
     * 
     * @return true: 유효한 이벤트, false: 무효한 이벤트
     */
    public boolean isValid() {
        return eventId != null && !eventId.trim().isEmpty() &&
               eventType != null && !eventType.trim().isEmpty() &&
               createdAt != null &&
               data != null && !data.isEmpty();
    }

    /**
     * 웹훅 이벤트가 처리 가능한 타입인지 확인
     * 
     * 현재 시스템에서 지원하는 이벤트 타입인지 검사합니다.
     * 
     * @return true: 처리 가능한 이벤트, false: 지원하지 않는 이벤트
     */
    public boolean isSupportedEventType() {
        return isPayoutEvent() || isSellerEvent();
    }

    /**
     * 이벤트 요약 정보 생성
     * 
     * 로깅 및 디버깅을 위한 이벤트 요약 문자열을 생성합니다.
     * 
     * @return 이벤트 요약 문자열
     */
    public String getSummary() {
        return String.format("WebhookEvent[id=%s, type=%s, createdAt=%s, dataKeys=%s]",
                eventId, eventType, createdAt,
                data != null ? data.keySet() : "null");
    }

    /**
     * 디버깅용 toString 메서드
     * 
     * 민감한 정보는 제외하고 주요 정보만 포함합니다.
     * 
     * @return 디버깅용 문자열 표현
     */
    @Override
    public String toString() {
        return String.format("TossWebhookEvent{eventId='%s', eventType='%s', createdAt=%s, hasData=%s, hasSignature=%s}",
                eventId, eventType, createdAt, data != null, signature != null);
    }
}