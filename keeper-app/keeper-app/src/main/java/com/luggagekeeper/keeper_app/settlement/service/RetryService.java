package com.luggagekeeper.keeper_app.settlement.service;

import com.luggagekeeper.keeper_app.settlement.exception.SettlementProcessingException;
import com.luggagekeeper.keeper_app.settlement.exception.TossApiException;
import lombok.extern.slf4j.Slf4j;
// Custom retry implementation without external dependencies
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 정산 시스템 재시도 로직 서비스
 * 
 * 지급대행 실패 시 자동 재시도 메커니즘을 제공하는 서비스입니다.
 * Spring Retry를 사용하여 지수 백오프 알고리즘을 적용한 재시도 로직을 구현합니다.
 * 
 * <p>재시도 정책:</p>
 * <ul>
 *   <li>최대 재시도 횟수: 3회</li>
 *   <li>초기 대기 시간: 1초</li>
 *   <li>지수 백오프 배수: 2.0 (1초 → 2초 → 4초)</li>
 *   <li>최대 대기 시간: 10초</li>
 *   <li>재시도 가능한 예외만 재시도</li>
 * </ul>
 * 
 * <p>재시도 대상 예외:</p>
 * <ul>
 *   <li>TossApiException (재시도 가능한 경우만)</li>
 *   <li>SettlementProcessingException (재시도 가능한 경우만)</li>
 *   <li>일시적인 네트워크 오류</li>
 *   <li>서버 과부하 (HTTP 503)</li>
 * </ul>
 * 
 * <p>재시도 제외 예외:</p>
 * <ul>
 *   <li>InsufficientBalanceException (잔액 부족)</li>
 *   <li>SettlementValidationException (데이터 검증 오류)</li>
 *   <li>SettlementNotFoundException (정산 정보 없음)</li>
 *   <li>인증/권한 오류 (HTTP 401, 403)</li>
 * </ul>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Service
public class RetryService {

    /**
     * 재시도 가능한 작업 실행
     * 
     * 주어진 작업을 재시도 정책에 따라 실행합니다.
     * 재시도 가능한 예외가 발생하면 지수 백오프 알고리즘을 적용하여 재시도합니다.
     * 
     * @param <T> 반환 타입
     * @param operation 실행할 작업
     * @param operationName 작업 이름 (로깅용)
     * @return 작업 실행 결과
     * @throws Exception 재시도 후에도 실패한 경우
     */
    public <T> T executeWithRetry(Supplier<T> operation, String operationName) {
        final int maxAttempts = 3;
        final long initialDelay = 1000; // 1초
        final double multiplier = 2.0;
        final long maxDelay = 10000; // 10초
        
        log.info("재시도 가능한 작업 실행 시작 - 작업명: {}, 시간: {}", operationName, LocalDateTime.now());
        
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = operation.get();
                if (attempt > 1) {
                    log.info("작업 실행 성공 ({}회 재시도 후) - 작업명: {}", attempt - 1, operationName);
                } else {
                    log.info("작업 실행 성공 - 작업명: {}", operationName);
                }
                return result;
            } catch (Exception ex) {
                lastException = ex;
                
                // 재시도 가능한 예외인지 확인
                if (!isRetryableException(ex)) {
                    log.error("재시도 불가능한 예외 발생 - 작업명: {}, 예외: {}, 메시지: {}", 
                            operationName, ex.getClass().getSimpleName(), ex.getMessage());
                    throw ex;
                }
                
                if (attempt == maxAttempts) {
                    log.error("재시도 최종 실패 - 작업명: {}, 시도횟수: {}, 예외: {}, 메시지: {}", 
                            operationName, attempt, ex.getClass().getSimpleName(), ex.getMessage());
                    break;
                }
                
                // 지수 백오프 계산
                long delay = Math.min((long) (initialDelay * Math.pow(multiplier, attempt - 1)), maxDelay);
                
                log.warn("재시도 가능한 예외 발생 - 작업명: {}, 시도: {}/{}, 다음 재시도까지: {}ms, 예외: {}, 메시지: {}", 
                        operationName, attempt, maxAttempts, delay, ex.getClass().getSimpleName(), ex.getMessage());
                
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SettlementProcessingException(
                        String.format("재시도 중 인터럽트 발생: %s", operationName), 
                        operationName, 
                        "retry_interrupted", 
                        ie
                    );
                }
            }
        }
        
        // 모든 재시도 실패
        throw new SettlementProcessingException(
            String.format("작업 재시도 최종 실패: %s", operationName), 
            operationName, 
            "retry_final_failure", 
            lastException
        );
    }

    /**
     * 비동기 재시도 가능한 작업 실행
     * 
     * 주어진 작업을 비동기로 실행하며, 재시도 정책을 적용합니다.
     * 
     * @param <T> 반환 타입
     * @param operation 실행할 작업
     * @param operationName 작업 이름 (로깅용)
     * @return CompletableFuture로 래핑된 작업 실행 결과
     */
    public <T> CompletableFuture<T> executeWithRetryAsync(Supplier<T> operation, String operationName) {
        return CompletableFuture.supplyAsync(() -> executeWithRetry(operation, operationName));
    }

    /**
     * 정산 처리 전용 재시도 메서드
     * 
     * 정산 처리 작업에 특화된 재시도 로직을 제공합니다.
     * 정산 ID와 함께 상세한 로깅을 수행합니다.
     * 
     * @param settlementId 정산 ID
     * @param operation 실행할 정산 처리 작업
     * @throws Exception 재시도 후에도 실패한 경우
     */
    public void processSettlementWithRetry(String settlementId, Runnable operation) {
        final int maxAttempts = 3;
        final long initialDelay = 1000;
        final double multiplier = 2.0;
        final long maxDelay = 10000;
        
        log.info("정산 처리 재시도 시작 - 정산ID: {}, 시간: {}", settlementId, LocalDateTime.now());
        
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                operation.run();
                if (attempt > 1) {
                    log.info("정산 처리 성공 ({}회 재시도 후) - 정산ID: {}", attempt - 1, settlementId);
                } else {
                    log.info("정산 처리 성공 - 정산ID: {}", settlementId);
                }
                return;
            } catch (Exception ex) {
                lastException = ex;
                
                if (!isRetryableException(ex)) {
                    log.error("정산 처리 재시도 불가능한 예외 - 정산ID: {}, 예외: {}, 메시지: {}", 
                            settlementId, ex.getClass().getSimpleName(), ex.getMessage());
                    throw ex;
                }
                
                if (attempt == maxAttempts) {
                    log.error("정산 처리 재시도 최종 실패 - 정산ID: {}, 시도횟수: {}, 예외: {}, 메시지: {}", 
                            settlementId, attempt, ex.getClass().getSimpleName(), ex.getMessage());
                    break;
                }
                
                long delay = Math.min((long) (initialDelay * Math.pow(multiplier, attempt - 1)), maxDelay);
                
                log.warn("정산 처리 재시도 가능한 예외 - 정산ID: {}, 시도: {}/{}, 다음 재시도까지: {}ms, 예외: {}, 메시지: {}", 
                        settlementId, attempt, maxAttempts, delay, ex.getClass().getSimpleName(), ex.getMessage());
                
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SettlementProcessingException(
                        String.format("정산 처리 재시도 중 인터럽트 발생: %s", settlementId), 
                        settlementId, 
                        "retry_interrupted", 
                        ie
                    );
                }
            }
        }
        
        // 모든 재시도 실패
        throw new SettlementProcessingException(
            String.format("정산 처리 재시도 최종 실패: %s", settlementId), 
            settlementId, 
            "retry_final_failure", 
            lastException
        );
    }

    /**
     * 실패한 작업에 대한 후처리
     * 
     * 재시도가 최종 실패한 후 호출되는 후처리 메서드입니다.
     * 실패한 작업에 대한 로깅과 알림을 수행합니다.
     * 
     * @param operationName 작업 이름
     * @param ex 최종 실패 예외
     */
    public void handleFinalFailure(String operationName, Exception ex) {
        log.error("작업 최종 실패 후처리 - 작업명: {}, 예외: {}, 메시지: {}, 시간: {}", 
                operationName, ex.getClass().getSimpleName(), ex.getMessage(), LocalDateTime.now());
        
        // 실패 알림 또는 추가 처리 로직을 여기에 구현
        // 예: 관리자 알림, 실패 큐에 추가, 수동 처리 요청 등
        
        // 실제 구현에서는 알림 서비스나 큐 시스템과 연동
        // notificationService.sendFailureAlert(operationName, ex);
        // failureQueueService.addToManualProcessingQueue(operationName, ex);
    }

    /**
     * 정산 처리 실패 후처리
     * 
     * 정산 처리 재시도가 최종 실패한 경우의 후처리 메서드입니다.
     * 
     * @param settlementId 정산 ID
     * @param ex 최종 실패 예외
     */
    public void handleSettlementProcessingFailure(String settlementId, Exception ex) {
        log.error("정산 처리 최종 실패 후처리 - 정산ID: {}, 예외: {}, 메시지: {}, 시간: {}", 
                settlementId, ex.getClass().getSimpleName(), ex.getMessage(), LocalDateTime.now());
        
        // 정산 상태를 실패로 업데이트하거나 수동 처리 큐에 추가하는 로직
        // 실제 구현에서는 SettlementService를 주입받아 상태 업데이트 수행
        
        // settlementService.updateStatusToFailed(settlementId, ex.getMessage());
        // manualProcessingService.addSettlementForManualReview(settlementId, ex);
    }

    /**
     * 예외가 재시도 가능한지 판단
     * 
     * 주어진 예외가 재시도 가능한 예외인지 판단합니다.
     * 
     * @param ex 판단할 예외
     * @return true: 재시도 가능, false: 재시도 불가능
     */
    private boolean isRetryableException(Exception ex) {
        // TossApiException의 경우 isRetryable() 메서드 확인
        if (ex instanceof TossApiException tossEx) {
            return tossEx.isRetryable();
        }
        
        // SettlementProcessingException의 경우 isRetryable() 메서드 확인
        if (ex instanceof SettlementProcessingException processingEx) {
            return processingEx.isRetryable();
        }
        
        // 일반적인 네트워크 오류나 일시적 오류는 재시도 가능
        String exceptionName = ex.getClass().getSimpleName().toLowerCase();
        String message = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        
        return exceptionName.contains("timeout") ||
               exceptionName.contains("connection") ||
               exceptionName.contains("network") ||
               message.contains("timeout") ||
               message.contains("connection") ||
               message.contains("network") ||
               message.contains("temporary");
    }

    /**
     * 재시도 통계 정보 반환
     * 
     * 현재까지의 재시도 통계 정보를 반환합니다.
     * 실제 구현에서는 메트릭 수집 시스템과 연동할 수 있습니다.
     * 
     * @return 재시도 통계 정보
     */
    public RetryStatistics getRetryStatistics() {
        // 실제 구현에서는 메트릭 수집 시스템에서 데이터를 가져옴
        return RetryStatistics.builder()
                .totalRetryAttempts(0L)
                .successfulRetries(0L)
                .failedRetries(0L)
                .averageRetryDelay(0.0)
                .build();
    }

    /**
     * 재시도 통계 정보 DTO
     */
    public static class RetryStatistics {
        private final Long totalRetryAttempts;
        private final Long successfulRetries;
        private final Long failedRetries;
        private final Double averageRetryDelay;

        private RetryStatistics(Long totalRetryAttempts, Long successfulRetries, 
                               Long failedRetries, Double averageRetryDelay) {
            this.totalRetryAttempts = totalRetryAttempts;
            this.successfulRetries = successfulRetries;
            this.failedRetries = failedRetries;
            this.averageRetryDelay = averageRetryDelay;
        }

        public static RetryStatisticsBuilder builder() {
            return new RetryStatisticsBuilder();
        }

        public Long getTotalRetryAttempts() { return totalRetryAttempts; }
        public Long getSuccessfulRetries() { return successfulRetries; }
        public Long getFailedRetries() { return failedRetries; }
        public Double getAverageRetryDelay() { return averageRetryDelay; }

        public static class RetryStatisticsBuilder {
            private Long totalRetryAttempts;
            private Long successfulRetries;
            private Long failedRetries;
            private Double averageRetryDelay;

            public RetryStatisticsBuilder totalRetryAttempts(Long totalRetryAttempts) {
                this.totalRetryAttempts = totalRetryAttempts;
                return this;
            }

            public RetryStatisticsBuilder successfulRetries(Long successfulRetries) {
                this.successfulRetries = successfulRetries;
                return this;
            }

            public RetryStatisticsBuilder failedRetries(Long failedRetries) {
                this.failedRetries = failedRetries;
                return this;
            }

            public RetryStatisticsBuilder averageRetryDelay(Double averageRetryDelay) {
                this.averageRetryDelay = averageRetryDelay;
                return this;
            }

            public RetryStatistics build() {
                return new RetryStatistics(totalRetryAttempts, successfulRetries, failedRetries, averageRetryDelay);
            }
        }
    }
}
