package com.luggagekeeper.keeper_app.settlement;

import com.luggagekeeper.keeper_app.store.domain.BusinessType;
import com.luggagekeeper.keeper_app.store.domain.Store;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;

/**
 * 정산 시스템 테스트를 위한 설정 클래스
 */
@TestConfiguration
public class SettlementTestConfig {

    /**
     * 테스트용 Store 객체 생성
     */
    @Bean
    public Store testStore() {
        Store store = new Store();
        store.setId("test-store-id");
        store.setEmail("test@example.com");
        store.setName("테스트 매장");
        store.setPhoneNumber("010-1234-5678");
        store.setBusinessType(BusinessType.INDIVIDUAL);
        store.setBusinessNumber("123-45-67890");
        store.setBusinessName("테스트 사업체");
        store.setRepresentativeName("홍길동");
        store.setAddress("서울시 강남구 테스트로 123");
        store.setDetailAddress("1층");
        store.setLatitude(37.5665);
        store.setLongitude(126.9780);
        store.setBankName("국민은행");
        store.setAccountNumber("123456-78-901234");
        store.setAccountHolder("홍길동");
        store.setHasCompletedSetup(true);
        store.setCreatedAt(LocalDateTime.now());
        return store;
    }
}