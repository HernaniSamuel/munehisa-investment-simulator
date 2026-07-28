package com.munehisa.backend.domain.asset;

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

@Entity
@Table(name = "asset_monthly_price")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssetMonthlyPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String ticker;

    @Convert(converter = YearMonthAttributeConverter.class)
    @Column(name = "reference_month", nullable = false)
    private YearMonth referenceMonth;

    @Column(nullable = false)
    private BigDecimal open;

    @Column(nullable = false)
    private BigDecimal high;

    @Column(nullable = false)
    private BigDecimal low;

    @Column(nullable = false)
    private BigDecimal close;

    @Column(nullable = false)
    private long volume;

    private BigDecimal dividends;

    private BigDecimal splits;
}
