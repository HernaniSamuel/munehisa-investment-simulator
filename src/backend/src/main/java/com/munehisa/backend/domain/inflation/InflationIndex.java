package com.munehisa.backend.domain.inflation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

@Entity
@Table(name = "inflation_index")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InflationIndex {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private InflationCurrency currency;

    @Convert(converter = YearMonthAttributeConverter.class)
    @Column(name = "reference_month", nullable = false)
    private YearMonth referenceMonth;

    @Column(name = "accumulated_index", nullable = false)
    private BigDecimal accumulatedIndex;
}
