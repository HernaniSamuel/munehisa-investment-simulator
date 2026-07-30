package com.munehisa.backend.domain.simulation;

import com.munehisa.backend.domain.inflation.YearMonthAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Total patrimony (cashBalance + totalAssetValue) and total invested (sum of
 * every open Position's costBasis) are intentionally not columns here: both
 * are trivial local aggregations with no external price lookups needed, so
 * they're computed on demand by whoever needs them rather than persisted as
 * a second source of truth.
 */
@Entity
@Table(name = "simulation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Simulation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Convert(converter = YearMonthAttributeConverter.class)
    @Column(name = "start_month", nullable = false)
    private YearMonth startMonth;

    @Convert(converter = YearMonthAttributeConverter.class)
    @Column(name = "current_month", nullable = false)
    private YearMonth currentMonth;

    @Column(name = "cash_balance", nullable = false)
    private BigDecimal cashBalance;

    @Column(name = "total_asset_value", nullable = false)
    private BigDecimal totalAssetValue;

    public BigDecimal getTotalPatrimony() {
        return cashBalance.add(totalAssetValue);
    }
}
