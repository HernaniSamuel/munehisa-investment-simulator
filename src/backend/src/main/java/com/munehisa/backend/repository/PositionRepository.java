package com.munehisa.backend.repository;

import com.munehisa.backend.domain.simulation.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {
    List<Position> findBySimulationId(UUID simulationId);
}
