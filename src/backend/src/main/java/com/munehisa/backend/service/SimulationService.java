package com.munehisa.backend.service;

import com.munehisa.backend.domain.simulation.Simulation;
import com.munehisa.backend.domain.simulation.Transaction;
import com.munehisa.backend.domain.simulation.TransactionType;
import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.CashMovementRequestDTO;
import com.munehisa.backend.dto.CashMovementResponseDTO;
import com.munehisa.backend.dto.CreateSimulationRequestDTO;
import com.munehisa.backend.dto.InflationDeflationResultDTO;
import com.munehisa.backend.dto.RenameSimulationRequestDTO;
import com.munehisa.backend.dto.SimulationResponseDTO;
import com.munehisa.backend.exceptions.FutureSimulationStartMonthException;
import com.munehisa.backend.exceptions.InsufficientCashBalanceException;
import com.munehisa.backend.exceptions.SimulationNotFoundException;
import com.munehisa.backend.repository.SimulationRepository;
import com.munehisa.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimulationService {
    private final SimulationRepository simulationRepository;
    private final TransactionRepository transactionRepository;
    private final InflationDeflationService inflationDeflationService;
    private final Clock clock;

    public SimulationResponseDTO create(CreateSimulationRequestDTO request, User user) {
        YearMonth currentMonth = YearMonth.now(clock);
        if (request.startMonth().isAfter(currentMonth)) {
            throw new FutureSimulationStartMonthException(request.startMonth(), currentMonth);
        }

        Simulation simulation = new Simulation();
        simulation.setUserId(user.getId());
        simulation.setName(request.name());
        simulation.setBaseCurrency(request.baseCurrency());
        simulation.setStartMonth(request.startMonth());
        simulation.setCurrentMonth(request.startMonth());
        simulation.setCashBalance(BigDecimal.ZERO);
        simulation.setTotalAssetValue(BigDecimal.ZERO);

        return toResponse(simulationRepository.save(simulation));
    }

    public List<SimulationResponseDTO> list(User user) {
        return simulationRepository.findByUserId(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public SimulationResponseDTO get(UUID id, User user) {
        return toResponse(findOwned(id, user));
    }

    public SimulationResponseDTO rename(UUID id, RenameSimulationRequestDTO request, User user) {
        Simulation simulation = findOwned(id, user);
        simulation.setName(request.name());
        return toResponse(simulationRepository.save(simulation));
    }

    public void delete(UUID id, User user) {
        simulationRepository.delete(findOwned(id, user));
    }

    public CashMovementResponseDTO deposit(UUID id, CashMovementRequestDTO request, User user) {
        return applyCashMovement(id, request, user, TransactionType.DEPOSIT);
    }

    public CashMovementResponseDTO withdraw(UUID id, CashMovementRequestDTO request, User user) {
        return applyCashMovement(id, request, user, TransactionType.WITHDRAWAL);
    }

    private CashMovementResponseDTO applyCashMovement(UUID id, CashMovementRequestDTO request, User user, TransactionType type) {
        Simulation simulation = findOwned(id, user);

        InflationDeflationResultDTO deflation = null;
        BigDecimal appliedAmount = request.amount();
        if (Boolean.TRUE.equals(request.todaysMoney())) {
            deflation = inflationDeflationService.deflate(request.amount(), simulation.getBaseCurrency(), simulation.getCurrentMonth());
            appliedAmount = deflation.deflatedValue();
        }

        if (type == TransactionType.WITHDRAWAL && appliedAmount.compareTo(simulation.getCashBalance()) > 0) {
            throw new InsufficientCashBalanceException(appliedAmount, simulation.getCashBalance());
        }

        simulation.setCashBalance(type == TransactionType.DEPOSIT
                ? simulation.getCashBalance().add(appliedAmount)
                : simulation.getCashBalance().subtract(appliedAmount));
        simulationRepository.save(simulation);

        Transaction transaction = new Transaction();
        transaction.setSimulationId(simulation.getId());
        transaction.setType(type);
        transaction.setMonth(simulation.getCurrentMonth());
        transaction.setAmount(appliedAmount);
        transactionRepository.save(transaction);

        return new CashMovementResponseDTO(
                simulation.getId(),
                appliedAmount,
                simulation.getCashBalance(),
                simulation.getTotalPatrimony(),
                deflation
        );
    }

    private Simulation findOwned(UUID id, User user) {
        return simulationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(SimulationNotFoundException::new);
    }

    private SimulationResponseDTO toResponse(Simulation simulation) {
        return new SimulationResponseDTO(
                simulation.getId(),
                simulation.getName(),
                simulation.getBaseCurrency(),
                simulation.getStartMonth(),
                simulation.getCurrentMonth(),
                simulation.getCashBalance(),
                simulation.getTotalPatrimony()
        );
    }
}
