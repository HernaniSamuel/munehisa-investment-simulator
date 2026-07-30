package com.munehisa.backend.repository;

import com.munehisa.backend.domain.simulation.SnapshotPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SnapshotPositionRepository extends JpaRepository<SnapshotPosition, UUID> {
    List<SnapshotPosition> findBySnapshotId(UUID snapshotId);
}
