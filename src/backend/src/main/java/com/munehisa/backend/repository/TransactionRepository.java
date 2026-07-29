package com.munehisa.backend.repository;

import com.munehisa.backend.domain.simulation.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findBySimulationId(UUID simulationId);
}
