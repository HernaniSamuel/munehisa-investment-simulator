package com.munehisa.backend.domain.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "asset_catalog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssetCatalog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String ticker;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    // Defaults to true (mirrors the column's DB default) so an entity built via the
    // no-args constructor + setters, without touching this field, still reflects the
    // same "split-adjusted" assumption a fresh DB row would have.
    @Column(name = "prices_split_adjusted", nullable = false)
    private boolean pricesSplitAdjusted = true;
}
