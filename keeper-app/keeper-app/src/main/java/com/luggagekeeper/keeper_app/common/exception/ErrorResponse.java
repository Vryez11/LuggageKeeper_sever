package com.luggagekeeper.keeper_app.common.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 표준화된 오류 응답 DTO (Data Transfer Object)
 * 
 * 애플리케이션에서 발생하는 모든 오류에 대해 일관된 형식의 응답을 제공하는 DTO입니다.
 * Flutter 앱과의 완벽한 호환성을 위해 설계되었으며, json_serializable 패키지와
 * 호환되는 구조로 되어 있습니다.
 * 
 * <p>주요 특징:</p>
 * <ul>
 *   <li>표준화된 오류 응답 형식</li>
 *   <li>Flutter json_serializable 호환</li>
 *   <li>다국어 지원 가능한 구조</li>
 *   <li>상세한 오류 정보 제공</li>
 *   <li>보안을 고려한 정보 노출 제한</li>
 * </ul>
 * 
 * <p>응답 구조:</p>
 * <pre>
 * {
 *   "timestamp": "2024-01-01T10:00:00",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "요청 데이터 검증에 실패했습니다",
 *   "path": "/api/v1/settlements",
 *   "details": {
 *     "field1": "필드별 오류 메시지",
 *     "field2": "필드별 오류 메시지"
 *   }
 * }
 * </pre>
 * 
 * <p>Flutter 사용 예시:</p>
 * <pre>
 * class ErrorResponse {
 *   final String timestamp;
 *   final int status;
 *   final String error;
 *   final String message;
 *   final String path;
 *   final Map<String, String>? details;
 * 
 *   ErrorResponse.fromJson(Map<String, dynamic> json)
 *     : timestamp = json['timestamp'],
 *       status = json['status'],
 *       error = json['error'],
 *       message = json['message'],
 *       path = json['path'],
 *       details = json['details']?.cast<String, String>();
 * }
 * </pre>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 * @see GlobalExceptionHandler
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    /**
     * 오류 발생 시각
     * 
     * 오류가 발생한 정확한 시간을 ISO 8601 형식으로 제공합니다.
     * 클라이언트에서 오류 로깅이나 디버깅 시 유용합니다.
     * 
     * 예시: "2024-01-01T10:00:00"
     */
    private LocalDateTime timestamp;

    /**
     * HTTP 상태 코드
     * 
     * 표준 HTTP 상태 코드를 숫자로 제공합니다.
     * Flutter 앱에서 오류 유형을 판단하고 적절한 처리를 할 수 있습니다.
     * 
     * 주요 상태 코드:
     * - 400: Bad Request (잘못된 요청)
     * - 401: Unauthorized (인증 실패)
     * - 403: Forbidden (권한 부족)
     * - 404: Not Found (리소스 없음)
     * - 409: Conflict (데이터 충돌)
     * - 422: Unprocessable Entity (처리 불가능한 엔티티)
     * - 500: Internal Server Error (서버 오류)
     */
    private int status;

    /**
     * HTTP 상태 코드 설명
     * 
     * HTTP 상태 코드에 대한 표준 영문 설명입니다.
     * 개발자가 오류 유형을 빠르게 파악할 수 있도록 도와줍니다.
     * 
     * 예시: "Bad Request", "Internal Server Error"
     */
    private String error;

    /**
     * 사용자 친화적인 오류 메시지
     * 
     * 최종 사용자가 이해할 수 있는 한국어 오류 메시지입니다.
     * Flutter 앱에서 사용자에게 직접 표시할 수 있는 메시지로,
     * 기술적인 내용보다는 해결 방법이나 안내를 포함합니다.
     * 
     * 특징:
     * - 한국어로 작성
     * - 사용자 친화적인 표현
     * - 해결 방법 제시
     * - 민감한 정보 제외
     * 
     * 예시: "요청 데이터 검증에 실패했습니다", "서버에서 일시적인 오류가 발생했습니다"
     */
    private String message;

    /**
     * 오류가 발생한 API 경로
     * 
     * 오류가 발생한 정확한 API 엔드포인트 경로입니다.
     * 디버깅이나 로깅 시 어떤 API에서 오류가 발생했는지 추적할 수 있습니다.
     * 
     * 예시: "/api/v1/settlements", "/api/v1/settlements/123/process"
     */
    private String path;

    /**
     * 상세한 오류 정보 (선택사항)
     * 
     * 추가적인 오류 정보를 키-값 쌍으로 제공합니다.
     * 주로 Bean Validation 오류 시 필드별 오류 메시지를 담거나,
     * 외부 API 오류 코드 등의 부가 정보를 제공할 때 사용됩니다.
     * 
     * 사용 예시:
     * - Bean Validation 오류: {"storeId": "가게 ID는 필수입니다", "amount": "금액은 0보다 커야 합니다"}
     * - 토스 API 오류: {"tossErrorCode": "INSUFFICIENT_BALANCE"}
     * - 비즈니스 로직 오류: {"conflictType": "DUPLICATE_ORDER_ID", "existingId": "order-123"}
     * 
     * 특징:
     * - 선택적 필드 (null 허용)
     * - 키와 값 모두 문자열 타입
     * - Flutter에서 Map<String, String>으로 직렬화
     * - 민감한 정보는 포함하지 않음
     */
    private Map<String, String> details;

    /**
     * 오류 응답의 간단한 문자열 표현
     * 
     * 로깅이나 디버깅 시 사용할 수 있는 간단한 문자열 표현을 제공합니다.
     * 민감한 정보는 제외하고 핵심 정보만 포함합니다.
     * 
     * @return 오류 응답의 문자열 표현
     */
    @Override
    public String toString() {
        return String.format("ErrorResponse{status=%d, error='%s', message='%s', path='%s', hasDetails=%s}",
                status, error, message, path, details != null && !details.isEmpty());
    }

    /**
     * 상세 정보가 있는지 확인하는 편의 메서드
     * 
     * details 필드에 유효한 정보가 있는지 확인합니다.
     * Flutter 앱에서 상세 정보 표시 여부를 결정할 때 사용할 수 있습니다.
     * 
     * @return true: 상세 정보 있음, false: 상세 정보 없음
     */
    public boolean hasDetails() {
        return details != null && !details.isEmpty();
    }

    /**
     * 클라이언트 오류인지 확인하는 편의 메서드
     * 
     * HTTP 상태 코드가 4xx 범위인지 확인합니다.
     * 클라이언트 측 오류와 서버 측 오류를 구분할 때 사용할 수 있습니다.
     * 
     * @return true: 클라이언트 오류 (4xx), false: 서버 오류 또는 기타
     */
    public boolean isClientError() {
        return status >= 400 && status < 500;
    }

    /**
     * 서버 오류인지 확인하는 편의 메서드
     * 
     * HTTP 상태 코드가 5xx 범위인지 확인합니다.
     * 서버 측 오류 발생 시 재시도 로직을 적용할지 결정할 때 사용할 수 있습니다.
     * 
     * @return true: 서버 오류 (5xx), false: 클라이언트 오류 또는 기타
     */
    public boolean isServerError() {
        return status >= 500 && status < 600;
    }
}
