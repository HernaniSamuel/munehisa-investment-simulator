package com.munehisa.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record SimulationPositionsResponseDTO(
        List<PositionWithValuationDTO> positions,
        BigDecimal totalAssetValue,
        BigDecimal totalGainAmount,
        BigDecimal totalGainPercent
) {
}
