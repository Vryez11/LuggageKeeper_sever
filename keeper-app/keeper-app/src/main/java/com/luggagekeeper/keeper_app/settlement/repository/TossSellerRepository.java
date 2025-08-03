package com.luggagekeeper.keeper_app.settlement.repository;

import com.luggagekeeper.keeper_app.settlement.domain.TossSeller;
import com.luggagekeeper.keeper_app.settlement.domain.TossSellerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * TossSeller 엔티티를 위한 Repository 인터페이스
 * 토스 셀러 데이터의 CRUD 및 커스텀 쿼리 제공
 */
@Repository
public interface TossSellerRepository extends JpaRepository<TossSeller, String> {

    /**
     * 가게 ID로 토스 셀러 조회
     */
    Optional<TossSeller> findByStoreId(String storeId);

    /**
     * 토스 셀러 ID로 조회
     */
    Optional<TossSeller> findByTossSellerId(String tossSellerId);

    /**
     * 참조 셀러 ID로 조회
     */
    Optional<TossSeller> findByRefSellerId(String refSellerId);

    /**
     * 상태별 토스 셀러 조회
     */
    List<TossSeller> findByStatusOrderByCreatedAtDesc(TossSellerStatus status);

    /**
     * 지급대행 가능한 셀러 조회
     */
    @Query("SELECT ts FROM TossSeller ts WHERE ts.tossSellerId IS NOT NULL " +
           "AND (ts.status = 'APPROVED' OR ts.status = 'PARTIALLY_APPROVED')")
    List<TossSeller> findPayoutEligibleSellers();

    /**
     * 승인 대기 중인 셀러 조회
     */
    @Query("SELECT ts FROM TossSeller ts WHERE ts.status = 'APPROVAL_REQUIRED' " +
           "OR ts.status = 'KYC_REQUIRED'")
    List<TossSeller> findPendingApprovalSellers();

    /**
     * 가게 존재 여부 확인
     */
    boolean existsByStoreId(String storeId);
}