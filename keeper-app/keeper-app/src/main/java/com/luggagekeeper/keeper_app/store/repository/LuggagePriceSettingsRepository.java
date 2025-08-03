package com.luggagekeeper.keeper_app.store.repository;

import com.luggagekeeper.keeper_app.store.domain.LuggagePriceSettings;
import com.luggagekeeper.keeper_app.store.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LuggagePriceSettingsRepository extends JpaRepository<LuggagePriceSettings, Long> {
    List<LuggagePriceSettings> findByStore(Store store);
    List<LuggagePriceSettings> findByStoreAndIsActiveTrue(Store store);
}