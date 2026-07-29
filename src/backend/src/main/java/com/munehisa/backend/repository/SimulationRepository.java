package com.munehisa.backend.repository;

import com.munehisa.backend.domain.simulation.Simulation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SimulationRepository extends JpaRepository<Simulation, UUID> {
    List<Simulation> findByUserId(UUID userId);
}
