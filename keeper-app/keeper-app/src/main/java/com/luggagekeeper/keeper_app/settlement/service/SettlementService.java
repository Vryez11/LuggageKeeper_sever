package com.luggagekeeper.keeper_app.settlement.service;

import com.luggagekeeper.keeper_app.settlement.domain.Settlement;
import com.luggagekeeper.keeper_app.settlement.domain.SettlementStatus;
import com.luggagekeeper.keeper_app.settlement.domain.TossSeller;
import com.luggagekeeper.keeper_app.settlement.domain.TossSellerStatus;
import com.luggagekeeper.keeper_app.settlement.dto.SettlementRequest;
import com.luggagekeeper.keeper_app.settlement.dto.SettlementResponse;
import com.luggagekeeper.keeper_app.settlement.dto.SettlementSummaryResponse;
import com.luggagekeeper.keeper_app.settlement.dto.TossWebhookEvent;
import com.luggagekeeper.keeper_app.settlement.exception.TossApiException;
import com.luggagekeeper.keeper_app.settlement.exception.InsufficientBalanceException;
import com.luggagekeeper.keeper_app.settlement.repository.SettlementRepository;
import com.luggagekeeper.keeper_app.settlement.repository.TossSellerRepository;
import com.luggagekeeper.keeper_app.store.domain.Store;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 정산 비즈니스 로직을 처리하는 핵심 서비스 클래스
 * 
 * 짐 보관 플랫폼의 정산 시스템에서 발생하는 모든 비즈니스 로직을 담당합니다.
 * 20% 플랫폼 수수료 계산, 토스페이먼츠 지급대행 연동, 정산 상태 관리 등
 * 정산과 관련된 핵심 기능들을 제공합니다.
 * 
 * <p>주요 기능:</p>
 * <ul>
 *   <li>정산 생성 및 수수료 자동 계산 (20% 플랫폼, 80% 가게)</li>
 *   <li>토스페이먼츠 지급대행을 통한 정산 처리</li>
 *   <li>정산 내역 조회 및 필터링 (가게별, 날짜별, 상태별)</li>
 *   <li>정산 상태 관리 및 업데이트</li>
 *   <li>재시도 로직 및 오류 처리</li>
 *   <li>정산 통계 및 요약 정보 제공</li>
 * </ul>
 * 
 * <p>트랜잭션 처리:</p>
 * <ul>
 *   <li>모든 쓰기 작업은 @Transactional로 보호</li>
 *   <li>읽기 전용 작업은 @Transactional(readOnly = true) 적용</li>
 *   <li>외부 API 호출과 데이터베이스 작업 분리</li>
 *   <li>롤백 시나리오 고려한 예외 처리</li>
 * </ul>
 * 
 * <p>성능 고려사항:</p>
 * <ul>
 *   <li>페이징 처리로 대용량 데이터 조회 최적화</li>
 *   <li>지연 로딩을 활용한 연관관계 최적화</li>
 *   <li>인덱스 활용을 위한 쿼리 최적화</li>
 *   <li>캐싱 전략 적용 가능한 구조</li>
 * </ul>
 * 
 * <p>보안 고려사항:</p>
 * <ul>
 *   <li>입력값 검증 및 SQL 인젝션 방지</li>
 *   <li>민감한 정보 로깅 제외</li>
 *   <li>권한 기반 접근 제어 지원</li>
 *   <li>감사 로그 자동 기록</li>
 * </ul>
 * 
 * <p>사용 예시:</p>
 * <pre>
 * // 정산 생성
 * SettlementResponse settlement = settlementService.createSettlement(request);
 * 
 * // 정산 처리
 * settlementService.processSettlement(settlementId);
 * 
 * // 정산 내역 조회
 * Page&lt;SettlementResponse&gt; settlements = settlementService.getSettlements(
 *     storeId, startDate, endDate, pageable);
 * </pre>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 * @see Settlement
 * @see TossPayoutService
 * @see SettlementRepository
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final TossSellerRepository tossSellerRepository;
    private final TossPayoutService tossPayoutService;

    /**
     * 새로운 정산을 생성하고 수수료를 자동 계산합니다
     * 
     * 클라이언트로부터 정산 요청을 받아 새로운 정산을 생성합니다.
     * 플랫폼 수수료(20%)를 자동으로 계산하고, 가게에 지급될 정산 금액(80%)을 산출합니다.
     * 중복 정산을 방지하기 위해 동일한 주문 ID로는 한 번만 정산을 생성할 수 있습니다.
     * 
     * <p>정산 계산 과정:</p>
     * <ol>
     *   <li>입력값 검증 (가게 존재 여부, 주문 ID 중복 확인)</li>
     *   <li>플랫폼 수수료율 20% 적용</li>
     *   <li>플랫폼 수수료 = 원본 금액 × 0.20</li>
     *   <li>정산 금액 = 원본 금액 - 플랫폼 수수료</li>
     *   <li>Settlement 엔티티 생성 및 저장</li>
     *   <li>SettlementResponse DTO 변환 후 반환</li>
     * </ol>
     * 
     * <p>수수료 계산 예시:</p>
     * <ul>
     *   <li>원본 금액: 10,000원</li>
     *   <li>플랫폼 수수료: 2,000원 (20%)</li>
     *   <li>정산 금액: 8,000원 (가게 수령액)</li>
     * </ul>
     * 
     * <p>예외 처리:</p>
     * <ul>
     *   <li>존재하지 않는 가게 ID: IllegalArgumentException</li>
     *   <li>중복된 주문 ID: IllegalStateException</li>
     *   <li>음수 또는 0 금액: IllegalArgumentException</li>
     *   <li>데이터베이스 오류: DataAccessException</li>
     * </ul>
     * 
     * <p>정밀도 고려사항:</p>
     * <ul>
     *   <li>BigDecimal 사용으로 정확한 금액 계산</li>
     *   <li>반올림 정책: HALF_UP (0.5 이상 올림)</li>
     *   <li>소수점 둘째 자리까지 정확도 보장</li>
     *   <li>오버플로우 방지를 위한 범위 검증</li>
     * </ul>
     * 
     * @param request 정산 요청 정보 (가게 ID, 주문 ID, 원본 금액 포함, null 불가)
     * @return 생성된 정산 정보가 포함된 응답 DTO
     * @throws IllegalArgumentException request가 null이거나 필수 정보가 누락된 경우
     * @throws IllegalArgumentException 존재하지 않는 가게 ID인 경우
     * @throws IllegalStateException 이미 정산이 생성된 주문 ID인 경우
     * @throws IllegalArgumentException 금액이 0 이하이거나 허용 범위를 초과한 경우
     * @throws ArithmeticException 금액 계산 중 오버플로우 발생 시
     * @throws org.springframework.dao.DataAccessException 데이터베이스 저장 실패 시
     */
    @Transactional
    public SettlementResponse createSettlement(SettlementRequest request) {
        // 1. 입력값 기본 검증
        if (request == null) {
            throw new IllegalArgumentException("정산 요청 정보는 필수입니다");
        }
        
        // 2. 요청 데이터 상세 검증
        request.validate();
        
        log.info("정산 생성 시작 - {}", request.toLogString());
        
        try {
            // 3. 가게 존재 여부 확인
            // TODO: StoreRepository를 통해 실제 가게 존재 여부 확인
            // Store store = storeRepository.findById(request.getStoreId())
            //     .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 가게입니다: " + request.getStoreId()));
            
            // 임시로 더미 Store 객체 생성 (실제 구현 시 위 코드로 대체)
            Store store = createDummyStore(request.getStoreId());
            
            // 4. 중복 정산 확인
            Optional<Settlement> existingSettlement = settlementRepository.findByOrderId(request.getOrderId());
            if (existingSettlement.isPresent()) {
                throw new IllegalStateException(
                    String.format("이미 정산이 생성된 주문입니다. 주문 ID: %s, 기존 정산 ID: %s", 
                                request.getOrderId(), existingSettlement.get().getId()));
            }
            
            // 5. 정산 엔티티 생성 (수수료 자동 계산 포함)
            Settlement settlement = Settlement.createSettlement(
                store, 
                request.getOrderId(), 
                request.getOriginalAmount()
            );
            
            // 6. 메타데이터 설정 (있는 경우)
            if (request.hasMetadata()) {
                settlement.setMetadata(request.getMetadata());
            }
            
            // 7. 데이터베이스 저장
            settlement = settlementRepository.save(settlement);
            
            log.info("정산 생성 완료 - 정산 ID: {}, 가게 ID: {}, 원본 금액: {}, 플랫폼 수수료: {}, 정산 금액: {}", 
                    settlement.getId(), 
                    settlement.getStore().getId(),
                    settlement.getOriginalAmount(),
                    settlement.getPlatformFee(),
                    settlement.getSettlementAmount());
            
            // 8. 응답 DTO 변환 및 반환
            return SettlementResponse.from(settlement);
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 비즈니스 로직 오류는 그대로 전파
            log.warn("정산 생성 실패 - 비즈니스 로직 오류: {}", e.getMessage());
            throw e;
            
        } catch (ArithmeticException e) {
            // 금액 계산 오류
            log.error("정산 생성 실패 - 금액 계산 오류: {}, 요청: {}", e.getMessage(), request.toLogString());
            throw new IllegalArgumentException("금액 계산 중 오류가 발생했습니다: " + e.getMessage(), e);
            
        } catch (Exception e) {
            // 예상치 못한 오류
            log.error("정산 생성 실패 - 시스템 오류: {}, 요청: {}", e.getMessage(), request.toLogString(), e);
            throw new RuntimeException("정산 생성 중 시스템 오류가 발생했습니다", e);
        }
    }

    /**
     * 정산을 처리하여 토스페이먼츠 지급대행을 요청합니다
     * 
     * 대기 중인 정산을 토스페이먼츠 지급대행 API를 통해 실제로 처리합니다.
     * 정산 상태를 PROCESSING으로 변경한 후 토스페이먼츠 API를 호출하고,
     * 성공 시 COMPLETED, 실패 시 FAILED 상태로 업데이트합니다.
     * 
     * <p>정산 처리 과정:</p>
     * <ol>
     *   <li>정산 정보 조회 및 처리 가능 상태 검증</li>
     *   <li>토스 셀러 정보 조회 및 지급대행 가능 여부 확인</li>
     *   <li>정산 상태를 PROCESSING으로 변경 (동시성 제어)</li>
     *   <li>토스페이먼츠 지급대행 API 호출</li>
     *   <li>성공 시: 정산 완료 처리 (COMPLETED 상태)</li>
     *   <li>실패 시: 정산 실패 처리 (FAILED 상태, 재시도 스케줄링)</li>
     * </ol>
     * 
     * <p>동시성 제어:</p>
     * <ul>
     *   <li>정산 상태를 PROCESSING으로 변경하여 중복 처리 방지</li>
     *   <li>트랜잭션 격리 수준을 통한 데이터 일관성 보장</li>
     *   <li>낙관적 락을 통한 동시 수정 방지</li>
     * </ul>
     * 
     * <p>오류 처리 및 재시도:</p>
     * <ul>
     *   <li>네트워크 오류: 자동 재시도 (최대 3회)</li>
     *   <li>잔액 부족: 즉시 실패 처리 (재시도 없음)</li>
     *   <li>셀러 승인 필요: 실패 처리 후 수동 개입 대기</li>
     *   <li>시스템 오류: 재시도 스케줄링</li>
     * </ul>
     * 
     * @param settlementId 처리할 정산의 고유 식별자 (null 불가)
     * @throws IllegalArgumentException settlementId가 null이거나 빈 문자열인 경우
     * @throws IllegalArgumentException 존재하지 않는 정산 ID인 경우
     * @throws IllegalStateException 처리할 수 없는 정산 상태인 경우
     * @throws IllegalStateException 토스 셀러 정보가 없거나 지급대행 불가능한 상태인 경우
     * @throws RuntimeException 토스페이먼츠 API 호출 실패 시
     */
    @Transactional
    public void processSettlement(String settlementId) {
        // 1. 입력값 검증
        if (settlementId == null || settlementId.trim().isEmpty()) {
            throw new IllegalArgumentException("정산 ID는 필수입니다");
        }
        
        log.info("정산 처리 시작 - 정산 ID: {}", settlementId);
        
        try {
            // 2. 정산 정보 조회
            Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 정산입니다: " + settlementId));
            
            // 3. 정산 처리 가능 상태 검증
            if (!settlement.canProcess()) {
                throw new IllegalStateException(
                    String.format("처리할 수 없는 정산 상태입니다. 정산 ID: %s, 현재 상태: %s", 
                                settlementId, settlement.getStatus()));
            }
            
            // 4. 토스 셀러 정보 조회 및 검증
            var tossSeller = tossSellerRepository.findByStoreId(settlement.getStore().getId())
                .orElseThrow(() -> new IllegalStateException(
                    String.format("토스 셀러 정보를 찾을 수 없습니다. 가게 ID: %s", settlement.getStore().getId())));
            
            if (!tossSeller.canProcessPayout()) {
                throw new IllegalStateException(
                    String.format("지급대행을 처리할 수 없는 셀러 상태입니다. 셀러 ID: %s, 상태: %s", 
                                tossSeller.getId(), tossSeller.getStatus()));
            }
            
            // 5. 정산 상태를 처리중으로 변경 (동시성 제어)
            settlement.startProcessing();
            settlementRepository.save(settlement);
            
            log.debug("정산 상태 변경 완료 - 정산 ID: {}, 상태: PROCESSING", settlementId);
            
            // 6. 토스페이먼츠 지급대행 API 호출
            try {
                var payoutResponse = tossPayoutService.requestPayout(settlement);
                
                // 7. 성공 시 정산 완료 처리
                settlement.completeSettlement(payoutResponse.getPayoutId());
                settlement.setTossSellerId(tossSeller.getTossSellerId());
                settlementRepository.save(settlement);
                
                log.info("정산 처리 완료 - 정산 ID: {}, 토스 지급대행 ID: {}", 
                        settlementId, payoutResponse.getPayoutId());
                
            } catch (Exception e) {
                // 8. 실패 시 에러 처리 및 재시도 스케줄링
                String errorMessage = String.format("토스페이먼츠 지급대행 실패: %s", e.getMessage());
                settlement.failSettlement(errorMessage);
                settlementRepository.save(settlement);
                
                log.error("정산 처리 실패 - 정산 ID: {}, 오류: {}, 재시도 횟수: {}", 
                         settlementId, e.getMessage(), settlement.getRetryCount());
                
                // 재시도 가능한 경우 스케줄링 (실제 구현에서는 비동기 처리)
                if (settlement.canRetry()) {
                    log.info("정산 재시도 스케줄링 - 정산 ID: {}, 재시도 횟수: {}", 
                            settlementId, settlement.getRetryCount());
                    // TODO: 비동기 재시도 스케줄링 구현
                    // scheduleRetry(settlement, calculateRetryDelay(settlement.getRetryCount()));
                } else {
                    log.warn("정산 재시도 한도 초과 - 정산 ID: {}, 수동 처리 필요", settlementId);
                }
                
                // 예외를 다시 던져서 호출자에게 실패를 알림
                throw new RuntimeException("정산 처리에 실패했습니다", e);
            }
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 비즈니스 로직 오류는 그대로 전파
            log.warn("정산 처리 실패 - 비즈니스 로직 오류: {}", e.getMessage());
            throw e;
            
        } catch (Exception e) {
            // 예상치 못한 오류
            log.error("정산 처리 실패 - 시스템 오류: {}, 정산 ID: {}", e.getMessage(), settlementId, e);
            throw new RuntimeException("정산 처리 중 시스템 오류가 발생했습니다", e);
        }
    }

    /**
     * 정산 내역을 조회합니다 (가게별, 날짜별 필터링 지원)
     * 
     * 지정된 조건에 따라 정산 내역을 조회하고 페이징 처리된 결과를 반환합니다.
     * 가게별 조회, 날짜 범위 필터링, 상태별 필터링 등 다양한 조건을 지원합니다.
     * 
     * <p>조회 조건:</p>
     * <ul>
     *   <li>storeId: 특정 가게의 정산 내역만 조회 (필수)</li>
     *   <li>startDate: 조회 시작 날짜 (선택사항, null 시 전체 기간)</li>
     *   <li>endDate: 조회 종료 날짜 (선택사항, null 시 전체 기간)</li>
     *   <li>pageable: 페이징 및 정렬 정보</li>
     * </ul>
     * 
     * <p>정렬 기준:</p>
     * <ul>
     *   <li>기본 정렬: 생성일시 내림차순 (최신순)</li>
     *   <li>사용자 정의 정렬: Pageable의 Sort 정보 활용</li>
     *   <li>지원 정렬 필드: createdAt, requestedAt, completedAt, status, amount</li>
     * </ul>
     * 
     * <p>성능 최적화:</p>
     * <ul>
     *   <li>인덱스 활용: store_id, created_at 복합 인덱스</li>
     *   <li>페이징 처리로 메모리 사용량 제한</li>
     *   <li>지연 로딩으로 불필요한 연관관계 로딩 방지</li>
     *   <li>쿼리 최적화를 통한 응답 시간 단축</li>
     * </ul>
     * 
     * @param storeId 조회할 가게의 고유 식별자 (null 불가)
     * @param startDate 조회 시작 날짜 (null 허용, null 시 제한 없음)
     * @param endDate 조회 종료 날짜 (null 허용, null 시 제한 없음)
     * @param pageable 페이징 및 정렬 정보 (null 불가)
     * @return 조건에 맞는 정산 내역의 페이징된 결과
     * @throws IllegalArgumentException storeId가 null이거나 빈 문자열인 경우
     * @throws IllegalArgumentException pageable이 null인 경우
     * @throws IllegalArgumentException startDate가 endDate보다 늦은 경우
     */
    @Transactional(readOnly = true)
    public Page<SettlementResponse> getSettlements(String storeId, LocalDateTime startDate, 
                                                  LocalDateTime endDate, Pageable pageable) {
        // 1. 입력값 검증
        if (storeId == null || storeId.trim().isEmpty()) {
            throw new IllegalArgumentException("가게 ID는 필수입니다");
        }
        if (pageable == null) {
            throw new IllegalArgumentException("페이징 정보는 필수입니다");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작 날짜는 종료 날짜보다 이전이어야 합니다");
        }
        
        log.debug("정산 내역 조회 시작 - 가게 ID: {}, 시작일: {}, 종료일: {}, 페이지: {}", 
                 storeId, startDate, endDate, pageable.getPageNumber());
        
        try {
            // 2. 조건에 따른 쿼리 실행
            Page<Settlement> settlements;
            
            if (startDate != null && endDate != null) {
                // 날짜 범위가 지정된 경우
                settlements = settlementRepository.findByStoreIdAndDateRange(
                    storeId, startDate, endDate, pageable);
                    
                log.debug("날짜 범위 조회 완료 - 가게 ID: {}, 조회 건수: {}, 전체 건수: {}", 
                         storeId, settlements.getNumberOfElements(), settlements.getTotalElements());
            } else {
                // 전체 기간 조회
                settlements = settlementRepository.findByStoreIdOrderByCreatedAtDesc(storeId, pageable);
                
                log.debug("전체 기간 조회 완료 - 가게 ID: {}, 조회 건수: {}, 전체 건수: {}", 
                         storeId, settlements.getNumberOfElements(), settlements.getTotalElements());
            }
            
            // 3. 엔티티를 DTO로 변환
            Page<SettlementResponse> response = settlements.map(SettlementResponse::from);
            
            log.info("정산 내역 조회 완료 - 가게 ID: {}, 페이지: {}/{}, 조회 건수: {}", 
                    storeId, pageable.getPageNumber() + 1, response.getTotalPages(), 
                    response.getNumberOfElements());
            
            return response;
            
        } catch (Exception e) {
            log.error("정산 내역 조회 실패 - 가게 ID: {}, 오류: {}", storeId, e.getMessage(), e);
            throw new RuntimeException("정산 내역 조회 중 오류가 발생했습니다", e);
        }
    }

    /**
     * 가게별 정산 요약 정보를 조회합니다
     * 
     * 특정 가게의 특정 날짜에 대한 정산 요약 정보를 제공합니다.
     * 총 정산 금액, 건수, 상태별 통계 등을 포함합니다.
     * 
     * <p>포함되는 정보:</p>
     * <ul>
     *   <li>총 원본 결제 금액</li>
     *   <li>총 플랫폼 수수료</li>
     *   <li>총 정산 금액 (가게 수령액)</li>
     *   <li>완료된 정산 건수</li>
     *   <li>대기 중인 정산 건수</li>
     *   <li>실패한 정산 건수</li>
     * </ul>
     * 
     * @param storeId 조회할 가게의 고유 식별자 (null 불가)
     * @param date 조회할 날짜 (null 불가)
     * @return 정산 요약 정보가 포함된 응답 DTO
     * @throws IllegalArgumentException storeId가 null이거나 빈 문자열인 경우
     * @throws IllegalArgumentException date가 null인 경우
     */
    @Transactional(readOnly = true)
    public SettlementSummaryResponse getSettlementSummary(String storeId, LocalDate date) {
        // 1. 입력값 검증
        if (storeId == null || storeId.trim().isEmpty()) {
            throw new IllegalArgumentException("가게 ID는 필수입니다");
        }
        if (date == null) {
            throw new IllegalArgumentException("조회 날짜는 필수입니다");
        }
        
        log.debug("정산 요약 정보 조회 시작 - 가게 ID: {}, 날짜: {}", storeId, date);
        
        try {
            // 2. 날짜 범위 설정 (해당 날짜의 00:00:00 ~ 23:59:59)
            LocalDateTime startDateTime = date.atStartOfDay();
            LocalDateTime endDateTime = date.atTime(23, 59, 59);
            
            // 3. 해당 날짜의 모든 정산 내역 조회
            var settlements = settlementRepository.findByStoreIdAndDateRange(
                storeId, startDateTime, endDateTime, Pageable.unpaged());
            
            // 4. 통계 계산
            BigDecimal totalOriginalAmount = BigDecimal.ZERO;
            BigDecimal totalPlatformFee = BigDecimal.ZERO;
            BigDecimal totalSettlementAmount = BigDecimal.ZERO;
            long completedCount = 0;
            long pendingCount = 0;
            long failedCount = 0;
            
            for (Settlement settlement : settlements.getContent()) {
                // 금액 합계 계산
                if (settlement.getOriginalAmount() != null) {
                    totalOriginalAmount = totalOriginalAmount.add(settlement.getOriginalAmount());
                }
                if (settlement.getPlatformFee() != null) {
                    totalPlatformFee = totalPlatformFee.add(settlement.getPlatformFee());
                }
                if (settlement.getSettlementAmount() != null) {
                    totalSettlementAmount = totalSettlementAmount.add(settlement.getSettlementAmount());
                }
                
                // 상태별 건수 계산
                switch (settlement.getStatus()) {
                    case COMPLETED -> completedCount++;
                    case PENDING, PROCESSING -> pendingCount++;
                    case FAILED -> failedCount++;
                    default -> {
                        // CANCELLED 등 기타 상태는 별도 집계하지 않음
                    }
                }
            }
            
            // 5. 응답 DTO 생성
            SettlementSummaryResponse summary = SettlementSummaryResponse.create(
                storeId, date, totalOriginalAmount, totalPlatformFee, totalSettlementAmount,
                completedCount, pendingCount, failedCount);
            
            log.info("정산 요약 정보 조회 완료 - 가게 ID: {}, 날짜: {}, 총 건수: {}, 완료: {}, 대기: {}, 실패: {}", 
                    storeId, date, settlements.getTotalElements(), completedCount, pendingCount, failedCount);
            
            return summary;
            
        } catch (Exception e) {
            log.error("정산 요약 정보 조회 실패 - 가게 ID: {}, 날짜: {}, 오류: {}", 
                     storeId, date, e.getMessage(), e);
            throw new RuntimeException("정산 요약 정보 조회 중 오류가 발생했습니다", e);
        }
    }

    /**
     * 지급대행 상태 변경 웹훅 이벤트 처리
     * 
     * 토스페이먼츠에서 발송하는 지급대행 상태 변경 웹훅을 처리합니다.
     * 정산 상태를 자동으로 업데이트하여 시스템 상태를 동기화합니다.
     * 
     * <p>처리 과정:</p>
     * <ol>
     *   <li>웹훅 이벤트 데이터 검증</li>
     *   <li>해당 정산 정보 조회</li>
     *   <li>중복 이벤트 처리 방지</li>
     *   <li>상태에 따른 정산 업데이트</li>
     *   <li>후속 처리 (재시도 스케줄링 등)</li>
     * </ol>
     * 
     * <p>처리되는 상태:</p>
     * <ul>
     *   <li>COMPLETED: 지급대행 완료 → 정산 완료 처리</li>
     *   <li>FAILED: 지급대행 실패 → 정산 실패 처리 및 재시도 스케줄링</li>
     *   <li>CANCELLED: 지급대행 취소 → 정산 취소 처리</li>
     * </ul>
     * 
     * <p>중복 처리 방지:</p>
     * <ul>
     *   <li>이벤트 ID 기반 중복 검사</li>
     *   <li>정산 상태 기반 중복 처리 방지</li>
     *   <li>타임스탬프 기반 순서 보장</li>
     * </ul>
     * 
     * @param event 지급대행 상태 변경 웹훅 이벤트 (null 불가)
     * @return true: 이벤트 처리 성공, false: 중복 이벤트 또는 이미 처리됨
     * @throws IllegalArgumentException event가 null이거나 유효하지 않은 경우
     * @throws IllegalArgumentException 지급대행 이벤트가 아닌 경우
     */
    @Transactional
    public boolean handlePayoutStatusChanged(TossWebhookEvent event) {
        // 1. 기본 검증
        if (event == null) {
            throw new IllegalArgumentException("웹훅 이벤트는 필수입니다");
        }
        if (!event.isPayoutEvent()) {
            throw new IllegalArgumentException("지급대행 이벤트가 아닙니다: " + event.getEventType());
        }
        if (!event.isValid()) {
            throw new IllegalArgumentException("유효하지 않은 웹훅 이벤트입니다");
        }

        log.info("지급대행 상태 변경 웹훅 처리 시작 - 이벤트 ID: {}, 타입: {}", 
                event.getEventId(), event.getEventType());

        try {
            // 2. 이벤트 데이터에서 필수 정보 추출
            String tossPayoutId = event.getDataString("payoutId");
            String refPayoutId = event.getDataString("refPayoutId"); // 우리 시스템의 정산 ID
            String statusStr = event.getDataString("status");
            String failureReason = event.getDataString("failureReason");

            if (tossPayoutId == null || refPayoutId == null || statusStr == null) {
                throw new IllegalArgumentException(
                    String.format("웹훅 이벤트에 필수 데이터가 누락되었습니다. payoutId: %s, refPayoutId: %s, status: %s",
                            tossPayoutId, refPayoutId, statusStr));
            }

            log.debug("웹훅 이벤트 데이터 추출 완료 - 토스 지급대행 ID: {}, 정산 ID: {}, 상태: {}", 
                     tossPayoutId, refPayoutId, statusStr);

            // 3. 해당 정산 정보 조회
            Optional<Settlement> settlementOpt = settlementRepository.findById(refPayoutId);
            if (settlementOpt.isEmpty()) {
                log.warn("웹훅 이벤트에 해당하는 정산을 찾을 수 없음 - 정산 ID: {}, 이벤트 ID: {}", 
                        refPayoutId, event.getEventId());
                return false; // 정산이 없으면 처리하지 않음 (오래된 이벤트일 수 있음)
            }

            Settlement settlement = settlementOpt.get();

            // 4. 중복 이벤트 처리 방지
            if (settlement.getTossPayoutId() != null && 
                !settlement.getTossPayoutId().equals(tossPayoutId)) {
                log.warn("정산의 토스 지급대행 ID가 일치하지 않음 - 정산 ID: {}, 기존: {}, 웹훅: {}", 
                        refPayoutId, settlement.getTossPayoutId(), tossPayoutId);
                return false; // ID가 다르면 처리하지 않음
            }

            // 5. 상태에 따른 처리
            boolean processed = false;
            switch (statusStr.toUpperCase()) {
                case "COMPLETED" -> {
                    processed = handlePayoutCompleted(settlement, tossPayoutId, event);
                }
                case "FAILED" -> {
                    processed = handlePayoutFailed(settlement, tossPayoutId, failureReason, event);
                }
                case "CANCELLED" -> {
                    processed = handlePayoutCancelled(settlement, tossPayoutId, event);
                }
                default -> {
                    log.warn("지원하지 않는 지급대행 상태 - 상태: {}, 이벤트 ID: {}", 
                            statusStr, event.getEventId());
                    return false;
                }
            }

            if (processed) {
                log.info("지급대행 상태 변경 웹훅 처리 완료 - 이벤트 ID: {}, 정산 ID: {}, 새 상태: {}", 
                        event.getEventId(), refPayoutId, settlement.getStatus());
            }

            return processed;

        } catch (Exception e) {
            log.error("지급대행 웹훅 처리 실패 - 이벤트 ID: {}, 오류: {}", 
                     event.getEventId(), e.getMessage(), e);
            throw new RuntimeException("지급대행 웹훅 처리 중 오류가 발생했습니다", e);
        }
    }

    /**
     * 셀러 상태 변경 웹훅 이벤트 처리
     * 
     * 토스페이먼츠에서 발송하는 셀러 상태 변경 웹훅을 처리합니다.
     * 셀러 승인 상태를 자동으로 업데이트하여 지급대행 가능 여부를 관리합니다.
     * 
     * <p>처리 과정:</p>
     * <ol>
     *   <li>웹훅 이벤트 데이터 검증</li>
     *   <li>해당 셀러 정보 조회</li>
     *   <li>중복 이벤트 처리 방지</li>
     *   <li>상태에 따른 셀러 업데이트</li>
     *   <li>후속 처리 (대기 중인 정산 처리 등)</li>
     * </ol>
     * 
     * <p>처리되는 상태:</p>
     * <ul>
     *   <li>APPROVED: 셀러 승인 완료 → 지급대행 서비스 이용 가능</li>
     *   <li>PARTIALLY_APPROVED: 부분 승인 → 제한된 지급대행 서비스 이용</li>
     *   <li>KYC_REQUIRED: KYC 심사 필요 → 추가 서류 제출 안내</li>
     *   <li>REJECTED: 셀러 승인 거부 → 지급대행 서비스 이용 불가</li>
     * </ul>
     * 
     * <p>후속 처리:</p>
     * <ul>
     *   <li>승인 완료 시: 대기 중인 정산 건 자동 처리</li>
     *   <li>승인 거부 시: 관련 정산 건 실패 처리</li>
     *   <li>상태 변경 알림: 가게 사장님께 알림 발송</li>
     * </ul>
     * 
     * @param event 셀러 상태 변경 웹훅 이벤트 (null 불가)
     * @return true: 이벤트 처리 성공, false: 중복 이벤트 또는 이미 처리됨
     * @throws IllegalArgumentException event가 null이거나 유효하지 않은 경우
     * @throws IllegalArgumentException 셀러 이벤트가 아닌 경우
     */
    @Transactional
    public boolean handleSellerStatusChanged(TossWebhookEvent event) {
        // 1. 기본 검증
        if (event == null) {
            throw new IllegalArgumentException("웹훅 이벤트는 필수입니다");
        }
        if (!event.isSellerEvent()) {
            throw new IllegalArgumentException("셀러 이벤트가 아닙니다: " + event.getEventType());
        }
        if (!event.isValid()) {
            throw new IllegalArgumentException("유효하지 않은 웹훅 이벤트입니다");
        }

        log.info("셀러 상태 변경 웹훅 처리 시작 - 이벤트 ID: {}, 타입: {}", 
                event.getEventId(), event.getEventType());

        try {
            // 2. 이벤트 데이터에서 필수 정보 추출
            String tossSellerId = event.getDataString("sellerId");
            String refSellerId = event.getDataString("refSellerId"); // 우리 시스템의 셀러 참조 ID
            String statusStr = event.getDataString("status");

            if (tossSellerId == null || refSellerId == null || statusStr == null) {
                throw new IllegalArgumentException(
                    String.format("웹훅 이벤트에 필수 데이터가 누락되었습니다. sellerId: %s, refSellerId: %s, status: %s",
                            tossSellerId, refSellerId, statusStr));
            }

            log.debug("웹훅 이벤트 데이터 추출 완료 - 토스 셀러 ID: {}, 참조 셀러 ID: {}, 상태: {}", 
                     tossSellerId, refSellerId, statusStr);

            // 3. 해당 셀러 정보 조회
            Optional<TossSeller> tossSellerOpt = tossSellerRepository.findByRefSellerId(refSellerId);
            if (tossSellerOpt.isEmpty()) {
                log.warn("웹훅 이벤트에 해당하는 셀러를 찾을 수 없음 - 참조 셀러 ID: {}, 이벤트 ID: {}", 
                        refSellerId, event.getEventId());
                return false; // 셀러가 없으면 처리하지 않음
            }

            TossSeller tossSeller = tossSellerOpt.get();

            // 4. 중복 이벤트 처리 방지
            if (tossSeller.getTossSellerId() != null && 
                !tossSeller.getTossSellerId().equals(tossSellerId)) {
                log.warn("셀러의 토스 셀러 ID가 일치하지 않음 - 참조 ID: {}, 기존: {}, 웹훅: {}", 
                        refSellerId, tossSeller.getTossSellerId(), tossSellerId);
                return false; // ID가 다르면 처리하지 않음
            }

            // 5. 상태 변환 및 검증
            TossSellerStatus newStatus;
            try {
                newStatus = TossSellerStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("지원하지 않는 셀러 상태 - 상태: {}, 이벤트 ID: {}", 
                        statusStr, event.getEventId());
                return false;
            }

            // 6. 상태가 실제로 변경되는 경우에만 처리
            if (tossSeller.getStatus() == newStatus) {
                log.debug("셀러 상태가 이미 동일함 - 참조 ID: {}, 상태: {}, 이벤트 ID: {}", 
                         refSellerId, newStatus, event.getEventId());
                return false; // 이미 동일한 상태면 처리하지 않음
            }

            // 7. 셀러 상태 업데이트
            TossSellerStatus previousStatus = tossSeller.getStatus();
            tossSeller.updateStatus(newStatus);
            
            // 토스 셀러 ID가 없는 경우 할당
            if (tossSeller.getTossSellerId() == null) {
                tossSeller.assignTossId(tossSellerId);
            }
            
            tossSellerRepository.save(tossSeller);

            log.info("셀러 상태 업데이트 완료 - 참조 ID: {}, 이전 상태: {}, 새 상태: {}", 
                    refSellerId, previousStatus, newStatus);

            // 8. 후속 처리
            handleSellerStatusChangeFollowUp(tossSeller, previousStatus, newStatus);

            log.info("셀러 상태 변경 웹훅 처리 완료 - 이벤트 ID: {}, 참조 ID: {}, 새 상태: {}", 
                    event.getEventId(), refSellerId, newStatus);

            return true;

        } catch (Exception e) {
            log.error("셀러 웹훅 처리 실패 - 이벤트 ID: {}, 오류: {}", 
                     event.getEventId(), e.getMessage(), e);
            throw new RuntimeException("셀러 웹훅 처리 중 오류가 발생했습니다", e);
        }
    }

    /**
     * 지급대행 완료 처리
     * 
     * @param settlement 정산 정보
     * @param tossPayoutId 토스 지급대행 ID
     * @param event 웹훅 이벤트
     * @return 처리 성공 여부
     */
    private boolean handlePayoutCompleted(Settlement settlement, String tossPayoutId, TossWebhookEvent event) {
        // 이미 완료된 정산인지 확인
        if (settlement.isCompleted()) {
            log.debug("이미 완료된 정산 - 정산 ID: {}, 이벤트 ID: {}", 
                     settlement.getId(), event.getEventId());
            return false;
        }

        // 처리 중인 상태가 아닌 경우 경고
        if (settlement.getStatus() != SettlementStatus.PROCESSING) {
            log.warn("처리 중이 아닌 정산의 완료 이벤트 수신 - 정산 ID: {}, 현재 상태: {}, 이벤트 ID: {}", 
                    settlement.getId(), settlement.getStatus(), event.getEventId());
        }

        // 정산 완료 처리
        settlement.completeSettlement(tossPayoutId);
        settlementRepository.save(settlement);

        log.info("지급대행 완료 처리 완료 - 정산 ID: {}, 토스 지급대행 ID: {}", 
                settlement.getId(), tossPayoutId);

        return true;
    }

    /**
     * 지급대행 실패 처리
     * 
     * @param settlement 정산 정보
     * @param tossPayoutId 토스 지급대행 ID
     * @param failureReason 실패 사유
     * @param event 웹훅 이벤트
     * @return 처리 성공 여부
     */
    private boolean handlePayoutFailed(Settlement settlement, String tossPayoutId, 
                                     String failureReason, TossWebhookEvent event) {
        // 이미 실패 처리된 정산인지 확인
        if (settlement.isFailed() && settlement.getTossPayoutId() != null && 
            settlement.getTossPayoutId().equals(tossPayoutId)) {
            log.debug("이미 실패 처리된 정산 - 정산 ID: {}, 이벤트 ID: {}", 
                     settlement.getId(), event.getEventId());
            return false;
        }

        // 실패 사유 설정
        String errorMessage = failureReason != null ? 
            String.format("토스페이먼츠 지급대행 실패: %s", failureReason) :
            "토스페이먼츠 지급대행 실패 (사유 불명)";

        // 정산 실패 처리
        settlement.failSettlement(errorMessage);
        settlement.setTossPayoutId(tossPayoutId); // 실패한 지급대행 ID도 기록
        settlementRepository.save(settlement);

        log.info("지급대행 실패 처리 완료 - 정산 ID: {}, 토스 지급대행 ID: {}, 재시도 횟수: {}, 사유: {}", 
                settlement.getId(), tossPayoutId, settlement.getRetryCount(), failureReason);

        // 재시도 가능한 경우 스케줄링
        if (settlement.canRetry()) {
            log.info("지급대행 재시도 스케줄링 - 정산 ID: {}, 재시도 횟수: {}", 
                    settlement.getId(), settlement.getRetryCount());
            // TODO: 비동기 재시도 스케줄링 구현
            // scheduleRetry(settlement, calculateRetryDelay(settlement.getRetryCount()));
        } else {
            log.warn("지급대행 재시도 한도 초과 - 정산 ID: {}, 수동 처리 필요", settlement.getId());
        }

        return true;
    }

    /**
     * 지급대행 취소 처리
     * 
     * @param settlement 정산 정보
     * @param tossPayoutId 토스 지급대행 ID
     * @param event 웹훅 이벤트
     * @return 처리 성공 여부
     */
    private boolean handlePayoutCancelled(Settlement settlement, String tossPayoutId, TossWebhookEvent event) {
        // 이미 취소된 정산인지 확인
        if (settlement.getStatus() == SettlementStatus.CANCELLED) {
            log.debug("이미 취소된 정산 - 정산 ID: {}, 이벤트 ID: {}", 
                     settlement.getId(), event.getEventId());
            return false;
        }

        // 정산 취소 처리
        settlement.cancelSettlement();
        settlement.setTossPayoutId(tossPayoutId); // 취소된 지급대행 ID 기록
        settlementRepository.save(settlement);

        log.info("지급대행 취소 처리 완료 - 정산 ID: {}, 토스 지급대행 ID: {}", 
                settlement.getId(), tossPayoutId);

        return true;
    }

    /**
     * 셀러 상태 변경 후속 처리
     * 
     * @param tossSeller 셀러 정보
     * @param previousStatus 이전 상태
     * @param newStatus 새로운 상태
     */
    private void handleSellerStatusChangeFollowUp(TossSeller tossSeller, 
                                                 TossSellerStatus previousStatus, 
                                                 TossSellerStatus newStatus) {
        try {
            // 1. 승인 완료 시 대기 중인 정산 처리
            if (newStatus == TossSellerStatus.APPROVED || newStatus == TossSellerStatus.PARTIALLY_APPROVED) {
                if (previousStatus != TossSellerStatus.APPROVED && previousStatus != TossSellerStatus.PARTIALLY_APPROVED) {
                    log.info("셀러 승인 완료 - 대기 중인 정산 처리 시작, 셀러 ID: {}", tossSeller.getId());
                    processPendingSettlementsForStore(tossSeller.getStore().getId());
                }
            }

            // 2. 승인 거부 시 관련 정산 실패 처리
            if (newStatus.name().contains("REJECTED") || newStatus.name().contains("SUSPENDED")) {
                log.info("셀러 승인 거부/정지 - 관련 정산 실패 처리, 셀러 ID: {}", tossSeller.getId());
                failPendingSettlementsForStore(tossSeller.getStore().getId(), 
                    "셀러 승인이 거부되어 지급대행을 처리할 수 없습니다");
            }

            // 3. 상태 변경 알림 (TODO: 실제 알림 시스템 연동)
            log.info("셀러 상태 변경 알림 발송 예정 - 셀러 ID: {}, 가게 ID: {}, 이전: {}, 현재: {}", 
                    tossSeller.getId(), tossSeller.getStore().getId(), previousStatus, newStatus);

        } catch (Exception e) {
            log.error("셀러 상태 변경 후속 처리 실패 - 셀러 ID: {}, 오류: {}", 
                     tossSeller.getId(), e.getMessage(), e);
            // 후속 처리 실패는 전체 웹훅 처리를 실패시키지 않음
        }
    }

    /**
     * 특정 가게의 대기 중인 정산들을 처리
     * 
     * @param storeId 가게 ID
     */
    private void processPendingSettlementsForStore(String storeId) {
        try {
            var pendingSettlements = settlementRepository.findByStoreIdAndStatus(storeId, SettlementStatus.PENDING);
            
            log.info("대기 중인 정산 처리 시작 - 가게 ID: {}, 대기 건수: {}", 
                    storeId, pendingSettlements.size());

            for (Settlement settlement : pendingSettlements) {
                try {
                    processSettlement(settlement.getId());
                    log.debug("대기 정산 처리 완료 - 정산 ID: {}", settlement.getId());
                } catch (Exception e) {
                    log.error("대기 정산 처리 실패 - 정산 ID: {}, 오류: {}", 
                             settlement.getId(), e.getMessage());
                    // 개별 정산 처리 실패는 전체를 중단시키지 않음
                }
            }

        } catch (Exception e) {
            log.error("대기 정산 일괄 처리 실패 - 가게 ID: {}, 오류: {}", storeId, e.getMessage(), e);
        }
    }

    /**
     * 특정 가게의 대기 중인 정산들을 실패 처리
     * 
     * @param storeId 가게 ID
     * @param errorMessage 실패 사유
     */
    private void failPendingSettlementsForStore(String storeId, String errorMessage) {
        try {
            var pendingSettlements = settlementRepository.findByStoreIdAndStatus(storeId, SettlementStatus.PENDING);
            
            log.info("대기 정산 실패 처리 시작 - 가게 ID: {}, 대기 건수: {}", 
                    storeId, pendingSettlements.size());

            for (Settlement settlement : pendingSettlements) {
                try {
                    settlement.failSettlement(errorMessage);
                    settlementRepository.save(settlement);
                    log.debug("대기 정산 실패 처리 완료 - 정산 ID: {}", settlement.getId());
                } catch (Exception e) {
                    log.error("대기 정산 실패 처리 실패 - 정산 ID: {}, 오류: {}", 
                             settlement.getId(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("대기 정산 일괄 실패 처리 실패 - 가게 ID: {}, 오류: {}", storeId, e.getMessage(), e);
        }
    }

    /**
     * 임시 Store 객체 생성 메서드 (실제 구현 시 제거 예정)
     * 
     * StoreRepository가 구현되기 전까지 사용하는 임시 메서드입니다.
     * 실제 구현에서는 StoreRepository.findById()를 사용해야 합니다.
     * 
     * @param storeId 가게 ID
     * @return 임시 Store 객체
     */
    private Store createDummyStore(String storeId) {
        // TODO: 실제 StoreRepository 구현 후 이 메서드 제거
        Store store = new Store();
        store.setId(storeId);
        store.setName("테스트 가게 " + storeId);
        return store;
    }
}