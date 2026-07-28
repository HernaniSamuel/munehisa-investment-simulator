package com.munehisa.backend.repository;

import com.munehisa.backend.domain.asset.AssetCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssetCatalogRepository extends JpaRepository<AssetCatalog, UUID> {
    boolean existsByTicker(String ticker);

    Optional<AssetCatalog> findByTicker(String ticker);
}
