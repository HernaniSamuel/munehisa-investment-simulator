package com.munehisa.backend.domain.simulation;

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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "snapshot_position")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotPosition {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(nullable = false)
    private String ticker;

    @Column(name = "asset_name", nullable = false)
    private String assetName;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private BigDecimal weight;

    @Column(name = "cost_basis", nullable = false)
    private BigDecimal costBasis;

    @Column(name = "total_dividends_received", nullable = false)
    private BigDecimal totalDividendsReceived;
}
