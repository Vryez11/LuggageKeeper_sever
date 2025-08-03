package com.luggagekeeper.keeper_app.store.repository;

import com.luggagekeeper.keeper_app.store.domain.StorageItem;
import com.luggagekeeper.keeper_app.store.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StorageItemRepository extends JpaRepository<StorageItem, String> {

    // 가게별 보관 아이템 조회
    List<StorageItem> findByStore(Store store);

    // 가게별 + 상태별 조회
    List<StorageItem> findByStoreAndStatus(Store store, StorageItem.StorageStatus status);

    // QR 코드로 조회
    Optional<StorageItem> findByQrCode(String qrCode);

    // 고객 ID로 조회
    List<StorageItem> findByCustomerId(String customerId);

    // 고객 전화번호로 조회
    List<StorageItem> findByCustomerPhone(String customerPhone);

    // 연체된 아이템들 조회
    @Query("SELECT s FROM StorageItem s WHERE s.expectedCheckoutTime < :now AND s.status = 'STORED'")
    List<StorageItem> findOverdueItems(@Param("now") LocalDateTime now);

    // 가게별 보관중인 아이템 개수
    long countByStoreAndStatus(Store store, StorageItem.StorageStatus status);

    // 오늘 체크인된 아이템들
    @Query("SELECT s FROM StorageItem s WHERE s.store = :store AND DATE(s.checkInTime) = CURRENT_DATE")
    List<StorageItem> findTodayCheckIns(@Param("store") Store store);
}