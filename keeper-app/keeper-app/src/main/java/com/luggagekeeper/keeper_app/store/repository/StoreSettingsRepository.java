// StoreSettingsRepository.java
package com.luggagekeeper.keeper_app.store.repository;

import com.luggagekeeper.keeper_app.store.domain.StoreSettings;
import com.luggagekeeper.keeper_app.store.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreSettingsRepository extends JpaRepository<StoreSettings, Long> {
    Optional<StoreSettings> findByStore(Store store);
}