package com.luggagekeeper.keeper_app.store.repository;

import com.luggagekeeper.keeper_app.store.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {

    // 이메일로 가게 찾기 (로그인/회원가입용)
    Optional<Store> findByEmail(String email);

    // 이메일 중복 확인
    boolean existsByEmail(String email);

    // 사업자번호로 찾기
    Optional<Store> findByBusinessNumber(String businessNumber);

    // 사업자번호 중복 확인
    boolean existsByBusinessNumber(String businessNumber);

    // 전화번호로 찾기
    Optional<Store> findByPhoneNumber(String phoneNumber);

    // 활성화된 가게들만 조회
    List<Store> findByIsActiveTrue();

    // 지역별 가게 검색
    List<Store> findByAddressContainingAndIsActiveTrue(String address);

    // 가게명으로 검색
    List<Store> findByStoreNameContainingIgnoreCaseAndIsActiveTrue(String storeName);

    // 사업자 타입별 조회
    List<Store> findByBusinessTypeAndIsActiveTrue(String businessType);
}

