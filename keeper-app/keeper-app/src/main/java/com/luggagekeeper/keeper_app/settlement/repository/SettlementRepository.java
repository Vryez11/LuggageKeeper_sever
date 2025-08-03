package com.luggagekeeper.keeper_app.settlement.repository;

import com.luggagekeeper.keeper_app.settlement.domain.Settlement;
import com.luggagekeeper.keeper_app.settlement.domain.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Settlement 엔티티를 위한 Repository 인터페이스
 * 정산 데이터의 CRUD 및 커스텀 쿼리 제공
 */
@Repository
public interface SettlementRepository extends JpaRepository<Settlement, String> {

    /**
     * 가게별 정산 내역 조회
     */
    Page<Settlement> findByStoreIdOrderByCreatedAtDesc(String storeId, Pageable pageable);

    /**
     * 가게별 특정 기간 정산 내역 조회
     */
    @Query("SELECT s FROM Settlement s WHERE s.store.id = :storeId " +
           "AND s.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY s.createdAt DESC")
    Page<Settlement> findByStoreIdAndDateRange(
            @Param("storeId") String storeId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * 상태별 정산 내역 조회
     */
    List<Settlement> findByStatusOrderByCreatedAtDesc(SettlementStatus status);

    /**
     * 가게별 상태별 정산 내역 조회
     */
    List<Settlement> findByStoreIdAndStatus(String storeId, SettlementStatus status);

    /**
     * 주문 ID로 정산 내역 조회
     */
    Optional<Settlement> findByOrderId(String orderId);

    /**
     * 토스 지급대행 ID로 정산 내역 조회
     */
    Optional<Settlement> findByTossPayoutId(String tossPayoutId);

    /**
     * 재시도 가능한 실패 정산 내역 조회
     */
    @Query("SELECT s FROM Settlement s WHERE s.status = 'FAILED' AND s.retryCount < 3")
    List<Settlement> findRetryableFailedSettlements();

    /**
     * 가게별 정산 통계 조회
     */
    @Query("SELECT COUNT(s), SUM(s.settlementAmount) FROM Settlement s " +
           "WHERE s.store.id = :storeId AND s.status = 'COMPLETED'")
    Object[] getSettlementStatsByStoreId(@Param("storeId") String storeId);
}