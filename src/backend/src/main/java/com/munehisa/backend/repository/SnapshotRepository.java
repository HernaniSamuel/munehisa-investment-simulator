package com.munehisa.backend.repository;

import com.munehisa.backend.domain.simulation.Snapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SnapshotRepository extends JpaRepository<Snapshot, UUID> {
    Optional<Snapshot> findBySimulationId(UUID simulationId);
}
