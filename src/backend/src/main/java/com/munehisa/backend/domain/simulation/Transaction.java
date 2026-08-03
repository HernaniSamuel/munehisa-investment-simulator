package com.munehisa.backend.domain.simulation;

import com.munehisa.backend.domain.inflation.YearMonthAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Convert(converter = YearMonthAttributeConverter.class)
    @Column(nullable = false)
    private YearMonth month;

    @Column(nullable = false)
    private BigDecimal amount;

    private String ticker;

    @Column(name = "asset_name")
    private String assetName;

    private BigDecimal quantity;
}
