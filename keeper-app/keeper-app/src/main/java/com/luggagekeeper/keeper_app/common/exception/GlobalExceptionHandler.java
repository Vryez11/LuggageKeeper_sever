package com.luggagekeeper.keeper_app.common.exception;

import com.luggagekeeper.keeper_app.settlement.exception.InsufficientBalanceException;
import com.luggagekeeper.keeper_app.settlement.exception.SettlementException;
import com.luggagekeeper.keeper_app.settlement.exception.SettlementNotFoundException;
import com.luggagekeeper.keeper_app.settlement.exception.SettlementStatusException;
import com.luggagekeeper.keeper_app.settlement.exception.SettlementValidationException;
import com.luggagekeeper.keeper_app.settlement.exception.SettlementProcessingException;
import com.luggagekeeper.keeper_app.settlement.exception.TossApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리기 (Global Exception Handler)
 * 
 * 애플리케이션 전체에서 발생하는 예외를 일관된 형태로 처리하는 글로벌 예외 핸들러입니다.
 * Flutter 앱과의 호환성을 위해 표준화된 오류 응답 형식을 제공하며,
 * 각 예외 유형별로 적절한 HTTP 상태 코드와 오류 메시지를 반환합니다.
 * 
 * <p>주요 기능:</p>
 * <ul>
 *   <li>Bean Validation 오류 처리 (@Valid 어노테이션 검증 실패)</li>
 *   <li>비즈니스 로직 예외 처리 (IllegalArgumentException, IllegalStateException)</li>
 *   <li>정산 시스템 특화 예외 처리 (TossApiException, InsufficientBalanceException)</li>
 *   <li>일반적인 런타임 예외 처리</li>
 *   <li>예상치 못한 서버 오류 처리</li>
 * </ul>
 * 
 * <p>표준 오류 응답 형식:</p>
 * <pre>
 * {
 *   "timestamp": "2024-01-01T10:00:00",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "상세한 오류 메시지",
 *   "path": "/api/v1/settlements",
 *   "details": {
 *     "field1": "필드별 오류 메시지",
 *     "field2": "필드별 오류 메시지"
 *   }
 * }
 * </pre>
 * 
 * <p>HTTP 상태 코드 매핑:</p>
 * <ul>
 *   <li>400 Bad Request: 요청 데이터 검증 실패, 비즈니스 규칙 위반</li>
 *   <li>404 Not Found: 리소스를 찾을 수 없음</li>
 *   <li>409 Conflict: 데이터 충돌 (중복 등)</li>
 *   <li>422 Unprocessable Entity: 토스페이먼츠 API 오류</li>
 *   <li>500 Internal Server Error: 예상치 못한 서버 오류</li>
 * </ul>
 * 
 * <p>보안 고려사항:</p>
 * <ul>
 *   <li>민감한 정보 노출 방지 (스택 트레이스 제외)</li>
 *   <li>사용자 친화적인 오류 메시지 제공</li>
 *   <li>내부 시스템 정보 숨김</li>
 *   <li>상세한 오류 로깅 (서버 측에서만)</li>
 * </ul>
 * 
 * <p>Flutter 연동 고려사항:</p>
 * <ul>
 *   <li>json_serializable 호환 응답 구조</li>
 *   <li>일관된 필드명 (camelCase)</li>
 *   <li>null safety 고려</li>
 *   <li>다국어 지원 가능한 구조</li>
 * </ul>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 * @see ControllerAdvice
 * @see ExceptionHandler
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Bean Validation 검증 실패 예외 처리
     * 
     * @Valid 어노테이션을 통한 요청 데이터 검증이 실패했을 때 발생하는 예외를 처리합니다.
     * 각 필드별 검증 오류 메시지를 수집하여 클라이언트에게 상세한 오류 정보를 제공합니다.
     * 
     * <p>처리하는 검증 오류:</p>
     * <ul>
     *   <li>@NotNull, @NotBlank, @NotEmpty 위반</li>
     *   <li>@Positive, @Min, @Max 위반</li>
     *   <li>@Pattern, @Email 등 형식 검증 위반</li>
     *   <li>커스텀 검증 어노테이션 위반</li>
     * </ul>
     * 
     * <p>응답 예시:</p>
     * <pre>
     * {
     *   "timestamp": "2024-01-01T10:00:00",
     *   "status": 400,
     *   "error": "Bad Request",
     *   "message": "요청 데이터 검증에 실패했습니다",
     *   "path": "/api/v1/settlements",
     *   "details": {
     *     "storeId": "가게 ID는 필수입니다",
     *     "originalAmount": "결제 금액은 0보다 커야 합니다"
     *   }
     * }
     * </pre>
     * 
     * @param ex MethodArgumentNotValidException 검증 실패 예외
     * @param request WebRequest 요청 정보
     * @return ResponseEntity<ErrorResponse> 표준화된 오류 응답
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        log.warn("요청 데이터 검증 실패 - 경로: {}, 오류 개수: {}", 
                request.getDescription(false), ex.getBindingResult().getErrorCount());
        
        // 필드별 오류 메시지 수집
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError) {
                FieldError fieldError = (FieldError) error;
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
                log.debug("검증 실패 필드: {} - {}", fieldError.getField(), fieldError.getDefaultMessage());
            } else {
                fieldErrors.put("global", error.getDefaultMessage());
                log.debug("전역 검증 실패: {}", error.getDefaultMessage());
            }
        });
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("요청 데이터 검증에 실패했습니다")
                .path(extractPath(request))
                .details(fieldErrors)
                .build();
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * 잘못된 인수 예외 처리 (IllegalArgumentException)
     * 
     * 메서드 인수가 유효하지 않거나 비즈니스 규칙에 위반될 때 발생하는 예외를 처리합니다.
     * 주로 서비스 레이어에서 입력값 검증 실패 시 발생합니다.
     * 
     * <p>발생 시나리오:</p>
     * <ul>
     *   <li>존재하지 않는 리소스 ID 요청</li>
     *   <li>잘못된 형식의 데이터 입력</li>
     *   <li>비즈니스 규칙 위반</li>
     *   <li>필수 파라미터 누락</li>
     * </ul>
     * 
     * @param ex IllegalArgumentException 잘못된 인수 예외
     * @param request WebRequest 요청 정보
     * @return ResponseEntity<ErrorResponse> 표준화된 오류 응답 (HTTP 400)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        log.warn("잘못된 요청 인수 - 경로: {}, 메시지: {}", request.getDescription(false), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * 잘못된 상태 예외 처리 (IllegalStateException)
     * 
     * 객체의 현재 상태가 메서드 호출에 적합하지 않을 때 발생하는 예외를 처리합니다.
     * 주로 정산 상태 관련 비즈니스 로직에서 발생합니다.
     * 
     * <p>발생 시나리오:</p>
     * <ul>
     *   <li>이미 처리된 정산을 다시 처리하려고 할 때</li>
     *   <li>취소된 정산을 처리하려고 할 때</li>
     *   <li>필수 연관 데이터가 없을 때</li>
     *   <li>상태 전이가 불가능할 때</li>
     * </ul>
     * 
     * @param ex IllegalStateException 잘못된 상태 예외
     * @param request WebRequest 요청 정보
     * @return ResponseEntity<ErrorResponse> 표준화된 오류 응답 (HTTP 400)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex, WebRequest request) {
        
        log.warn("잘못된 상태 요청 - 경로: {}, 메시지: {}", request.getDescription(false), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage())
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * 토스페이먼츠 API 예외 처리
     * 
     * 토스페이먼츠 외부 API 호출 시 발생하는 예외를 처리합니다.
     * API 응답 코드에 따라 적절한 HTTP 상태 코드와 사용자 친화적인 메시지를 제공합니다.
     * 
     * <p>처리하는 토스 API 오류:</p>
     * <ul>
     *   <li>인증 실패 (401)</li>
     *   <li>권한 부족 (403)</li>
     *   <li>리소스 없음 (404)</li>
     *   <li>요청 한도 초과 (429)</li>
     *   <li>서버 오류 (500)</li>
     * </ul>
     * 
     * @param ex TossApiException 토스페이먼츠 API 예외
     * @param request WebRequest 요청 정보
     * @return ResponseEntity<ErrorResponse> 표준화된 오류 응답 (HTTP 422)
     */
    @ExceptionHandler(TossApiException.class)
    public ResponseEntity<ErrorResponse> handleTossApiException(
            TossApiException ex, WebRequest request) {
        
        log.error("토스페이먼츠 API 오류 - 경로: {}, 메시지: {}, 코드: {}", 
                request.getDescription(false), ex.getMessage(), ex.getTossErrorCode());
        
        // 토스 API 오류를 사용자 친화적인 메시지로 변환
        String userMessage = convertTossErrorToUserMessage(ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Toss API Error")
                .message(userMessage)
                .path(extractPath(request))
                .details(Map.of("tossErrorCode", ex.getTossErrorCode() != null ? ex.getTossErrorCode() : "UNKNOWN"))
                .build();
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    /**
     * 잔액 부족 예외 처리
     * 
     * 정산 처리 시 토스페이먼츠 계정의 잔액이 부족할 때 발생하는 예외를 처리합니다.
     * 
     * @param ex InsufficientBalanceException 잔액 부족 예외
     * @param request WebRequest 요청 정보
     * @return ResponseEntity<ErrorResponse> 표준화된 오류 응답 (HTTP 422)
     */
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalanceException(
            InsufficientBalanceException ex, WebRequest request) {
        
        log.warn("잔액 부족 오류 - 경로: {}, 메시지: {}", request.getDescription(false), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Insufficient Balance")
                .message("정산 처리를 위한 잔액이 부족합니다. 계정 충전 후 다시 시도해주세요.")
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    /**
     * 정산 시스템 기본 예외 처리
     * 
     * 정산 시스템에서 발생하는 모든 비즈니스 로직 관련 예외를 처리합니다.
     * 각 구체적인 예외 타입별로 적절한 HTTP 상태 코드와 메시지를 제공합니다.
     * 
     * @param ex SettlementException 정산 시스템 예외
     * @param request WebRequest 요청 정보
     * @return ResponseEntity<ErrorResponse> 표준화된 오류 응답
     */
    @ExceptionHandler(SettlementException.class)
    public ResponseEntity<ErrorResponse> handleSettlementException(
            SettlementException ex, WebRequest request) {
        
        log.warn("정산 시스템 예외 발생 - 경로: {}, 오류코드: {}, 메시지: {}", 
                request.getDescription(false), ex.getErrorCode(), ex.getMessage());
        
        // 예외 타입에 따른 HTTP 상태 코드 결정 (기본값: 400)
        HttpStatus status = HttpStatus.BAD_REQUEST;
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(ex.getUserFriendlyMessage())
                .path(extractPath(request))
                .build();
        
        // 오류 코드가 있는 경우 details에 추가
        if (ex.getErrorCode() != null) {
            Map<String, String> details = new HashMap<>();
            details.put("errorCode", ex.getErrorCode());
            if (ex.getErrorDetail() != null) {
                details.put("errorDetail", ex.getErrorDetail());
            }
            errorResponse.setDetails(details);
        }
        
        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * 정산 정보 없음 예외 처리
     * 
     * 요청한 정산 ID에 해당하는 정산 정보가 존재하지 않을 때 발생하는 예외를 처리합니다.
     * 
     * @param ex SettlementNotFoundException 정산 정보 없음 예외
     * @param request WebRequest 요청 정보
     * @return ResponseEntity<ErrorResponse> 표준화된 오류 응답 (HTTP 404)
     */
    @ExceptionHandler(SettlementNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSettlementNotFoundException(
            SettlementNotFoundException ex, WebRequest request) {
        
        log.warn("정산 정보 없음 - 경로: {}, 정산ID: {}, 메시지: {}", 
                request.getDescription(false), ex.getSettlementId(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(ex.getUserFriendlyMessage())
                .path(extractPath(request))
                .build();
        
        if (ex.getSettlementId() != null) {
            Map<String, String> details = new HashMap<>();
            details.put("settlementId", ex.getSettlementId());
            errorResponse.setDetails(details);
        }
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 정산 상태 오류 예외 처리
     * 
     * 정산의 현재 상태로 인해 요청한 작업을 수행할 수 없을 때 발생하는 예외를 처리합니다.
     * 
     * @param ex SettlementStatusException 정산 상태 오류 예외
     * @param request WebRequest 요청 정보
     * @return ResponseEntity<ErrorResponse> 표준화된 오류 응답 (HTTP 409)
     */
    @ExceptionHandler(SettlementStatusException.class)
    public ResponseEntity<ErrorResponse> handleSettlementStatusException(
            SettlementStatusException ex, WebRequest request) {
        
        log.warn("정산 상태 오류 - 경로: {}, 정산ID: {}, 현재상태: {}, 요청작업: {}", 
                request.getDescription(false), ex.getSettlementId(), 
                ex.getCurrentStatus(), ex.getRequestedAction());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .message(ex.getUserFriendlyMessage())
                .path(extractPath(request))
                .build();
        
        Map<String, String> details = new HashMap<>();
        if (ex.getSettlementId() != null) {
            details.put("settlementId", ex.getSettlementId());
        }
        if (ex.getCurrentStatus() != null) {
            details.put("currentStatus", ex.getCurrentStatus());
        }
        if (ex.getRequestedAction() != null) {
            details.put("requestedAction", ex.getRequestedAction());
        }
        if (!details.isEmpty()) {
            errorResponse.setDetails(details);
        }
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * 정산 데이터 검증 오류 예외 처리
     * 
     * 정산 생성이나 수정 시 입력 데이터의 유효성 검증에 실패했을 때 발생하는 예외를 처리합니다.
     * 
     * @param ex SettlementValidationException 정산 데이터 검증 오류 예외
     * @param request WebRequest 요청 정보
     * @return ResponseEntity<ErrorResponse> 표준화된 오류 응답 (HTTP 400)
     */
    @ExceptionHandler(SettlementValidationException.class)
    public ResponseEntity<ErrorResponse> handleSettlementValidationException(
            SettlementValidationException ex, WebRequest request) {
        
        log.warn("정산 데이터 검증 오류 - 경로: {}, 메시지: {}", 
                request.getDescription(false), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getUserFriendlyMessage())
                .path(extractPath(request))
                .build();
        
        // 검증 오류는 복잡한 구조이므로 별도 처리
        if (ex.hasErrors()) {
            Map<String, String> details = new HashMap<>();
            details.put("validationErrors", "상세한 검증 오류 정보가 있습니다.");
            errorResponse.setDetails(details);
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 정산 처리 오류 예외 처리
     * 
     * 정산 처리 과정에서 예상치 못한 오류가 발생했을 때 사용하는 예외를 처리합니다.
     * 
     * @param ex SettlementProcessingException 정산 처리 오류 예외
     * @param request WebRequest 요청 정보
     * @return ResponseEntity<ErrorResponse> 표준화된 오류 응답 (HTTP 500)
     */
    @ExceptionHandler(SettlementProcessingException.class)
    public ResponseEntity<ErrorResponse> handleSettlementProcessingException(
            SettlementProcessingException ex, WebRequest request) {
        
        log.error("정산 처리 오류 - 경로: {}, 정산ID: {}, 처리단계: {}, 메시지: {}", 
                request.getDescription(false), ex.getSettlementId(), 
                ex.getProcessingStage(), ex.getMessage(), ex);
        
        // 재시도 가능한 오류인 경우 503, 그렇지 않으면 500
        HttpStatus status = ex.isRetryable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.INTERNAL_SERVER_ERROR;
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(ex.getUserFriendlyMessage())
                .path(extractPath(request))
                .build();
        
        Map<String, String> details = new HashMap<>();
        if (ex.getSettlementId() != null) {
            details.put("settlementId", ex.getSettlementId());
        }
        if (ex.getProcessingStage() != null) {
            details.put("processingStage", ex.getProcessingStage());
        }
        details.put("retryable", String.valueOf(ex.isRetryable()));
        errorResponse.setDetails(details);
        
        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * 일반적인 런타임 예외 처리
     * 
     * 위에서 처리되지 않은 모든 RuntimeException을 처리합니다.
     * 예상치 못한 오류에 대해 안전한 응답을 제공합니다.
     * 
     * @param ex RuntimeException 런타임 예외
     * @param request WebRequest 요청 정보
     * @return ResponseEntity<ErrorResponse> 표준화된 오류 응답 (HTTP 500)
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        log.error("예상치 못한 런타임 오류 - 경로: {}, 메시지: {}", 
                request.getDescription(false), ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("서버에서 예상치 못한 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * 모든 예외의 최종 처리기
     * 
     * 위의 모든 핸들러에서 처리되지 않은 예외들을 처리하는 최종 안전망입니다.
     * 
     * @param ex Exception 모든 예외
     * @param request WebRequest 요청 정보
     * @return ResponseEntity<ErrorResponse> 표준화된 오류 응답 (HTTP 500)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(
            Exception ex, WebRequest request) {
        
        log.error("처리되지 않은 예외 발생 - 경로: {}, 타입: {}, 메시지: {}", 
                request.getDescription(false), ex.getClass().getSimpleName(), ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("서버에서 오류가 발생했습니다. 관리자에게 문의해주세요.")
                .path(extractPath(request))
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * 요청 경로 추출 헬퍼 메서드
     * 
     * WebRequest에서 실제 API 경로를 추출합니다.
     * 
     * @param request WebRequest 요청 정보
     * @return String API 경로
     */
    private String extractPath(WebRequest request) {
        String description = request.getDescription(false);
        // "uri=/api/v1/settlements" 형태에서 경로만 추출
        if (description.startsWith("uri=")) {
            return description.substring(4);
        }
        return description;
    }

    /**
     * 토스페이먼츠 오류를 사용자 친화적인 메시지로 변환
     * 
     * 토스페이먼츠 API의 기술적인 오류 메시지를 일반 사용자가 이해할 수 있는
     * 친화적인 메시지로 변환합니다.
     * 
     * @param ex TossApiException 토스 API 예외
     * @return String 사용자 친화적인 오류 메시지
     */
    private String convertTossErrorToUserMessage(TossApiException ex) {
        String errorCode = ex.getTossErrorCode();
        
        if (errorCode == null) {
            return "결제 서비스에서 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
        }
        
        // 토스페이먼츠 주요 오류 코드별 메시지 매핑
        switch (errorCode) {
            case "UNAUTHORIZED":
                return "결제 서비스 인증에 실패했습니다. 관리자에게 문의해주세요.";
            case "FORBIDDEN":
                return "결제 서비스 접근 권한이 없습니다. 관리자에게 문의해주세요.";
            case "NOT_FOUND":
                return "요청하신 결제 정보를 찾을 수 없습니다.";
            case "INVALID_REQUEST":
                return "결제 요청 정보가 올바르지 않습니다. 다시 확인해주세요.";
            case "INSUFFICIENT_BALANCE":
                return "정산 처리를 위한 잔액이 부족합니다. 계정 충전 후 다시 시도해주세요.";
            case "RATE_LIMIT_EXCEEDED":
                return "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
            case "INTERNAL_SERVER_ERROR":
                return "결제 서비스에서 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
            default:
                return "결제 서비스에서 오류가 발생했습니다. (" + errorCode + ")";
        }
    }
}
