package com.munehisa.backend.service;

import com.munehisa.backend.domain.asset.AssetCatalog;
import com.munehisa.backend.domain.simulation.Position;
import com.munehisa.backend.domain.simulation.Simulation;
import com.munehisa.backend.domain.simulation.Snapshot;
import com.munehisa.backend.domain.simulation.SnapshotPosition;
import com.munehisa.backend.domain.simulation.Transaction;
import com.munehisa.backend.domain.simulation.TransactionType;
import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.AssetLookupResultDTO;
import com.munehisa.backend.dto.AssetSearchResponseDTO;
import com.munehisa.backend.dto.CashMovementRequestDTO;
import com.munehisa.backend.dto.CashMovementResponseDTO;
import com.munehisa.backend.dto.ConvertedCashBalanceDTO;
import com.munehisa.backend.dto.CreateSimulationRequestDTO;
import com.munehisa.backend.dto.ExchangeRateLookupResultDTO;
import com.munehisa.backend.dto.InflationDeflationResultDTO;
import com.munehisa.backend.dto.RenameSimulationRequestDTO;
import com.munehisa.backend.dto.SimulationResponseDTO;
import com.munehisa.backend.exceptions.FutureSimulationStartMonthException;
import com.munehisa.backend.exceptions.InsufficientCashBalanceException;
import com.munehisa.backend.exceptions.SimulationNotFoundException;
import com.munehisa.backend.exceptions.SnapshotNotFoundException;
import com.munehisa.backend.repository.AssetCatalogRepository;
import com.munehisa.backend.repository.PositionRepository;
import com.munehisa.backend.repository.SimulationRepository;
import com.munehisa.backend.repository.SnapshotPositionRepository;
import com.munehisa.backend.repository.SnapshotRepository;
import com.munehisa.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SimulationService {
    private final SimulationRepository simulationRepository;
    private final TransactionRepository transactionRepository;
    private final InflationDeflationService inflationDeflationService;
    private final Clock clock;
    private final SnapshotRepository snapshotRepository;
    private final SnapshotPositionRepository snapshotPositionRepository;
    private final PositionRepository positionRepository;
    private final AssetCatalogRepository assetCatalogRepository;
    private final AssetCacheService assetCacheService;
    private final ExchangeRateCacheService exchangeRateCacheService;

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

    public AssetSearchResponseDTO searchAsset(UUID id, String ticker, User user) {
        Simulation simulation = findOwned(id, user);
        AssetLookupResultDTO lookup = assetCacheService.getAssetSeries(ticker, simulation.getCurrentMonth());
        ConvertedCashBalanceDTO cashBalance = convertCashBalance(simulation, lookup.baseCurrency());
        return new AssetSearchResponseDTO(
                lookup.ticker(), lookup.name(), lookup.baseCurrency(),
                lookup.requestedMonth(), lookup.returnedMonth(), lookup.truncated(),
                lookup.series(), cashBalance);
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

    @Transactional
    public void createSnapshot(UUID id, User user) {
        Simulation simulation = findOwned(id, user);

        Snapshot snapshot = snapshotRepository.findBySimulationId(simulation.getId()).orElseGet(Snapshot::new);
        snapshot.setSimulationId(simulation.getId());
        snapshot.setCashBalance(simulation.getCashBalance());
        snapshot.setTotalAssetValue(simulation.getTotalAssetValue());
        UUID snapshotId = snapshotRepository.save(snapshot).getId();

        snapshotPositionRepository.deleteAll(snapshotPositionRepository.findBySnapshotId(snapshotId));

        List<SnapshotPosition> snapshotPositions = positionRepository.findBySimulationId(simulation.getId()).stream()
                .map(position -> toSnapshotPosition(position, snapshotId))
                .toList();
        snapshotPositionRepository.saveAll(snapshotPositions);
    }

    @Transactional
    public SimulationResponseDTO resetToSnapshot(UUID id, User user) {
        Simulation simulation = findOwned(id, user);
        Snapshot snapshot = snapshotRepository.findBySimulationId(simulation.getId())
                .orElseThrow(SnapshotNotFoundException::new);
        List<SnapshotPosition> snapshotPositions = snapshotPositionRepository.findBySnapshotId(snapshot.getId());

        List<Position> oldPositions = positionRepository.findBySimulationId(simulation.getId());
        Set<UUID> oldAssetIds = oldPositions.stream().map(Position::getAssetId).collect(Collectors.toSet());

        simulation.setCashBalance(snapshot.getCashBalance());
        simulation.setTotalAssetValue(snapshot.getTotalAssetValue());
        simulationRepository.save(simulation);

        // Flushed immediately: Hibernate would otherwise defer this delete until after the
        // inserts below in the same flush, and the recreated position can share a (simulation,
        // asset) pair with the one being deleted here, tripping the table's unique constraint.
        positionRepository.deleteAll(oldPositions);
        positionRepository.flush();

        Set<UUID> newAssetIds = new HashSet<>();
        List<Position> newPositions = new ArrayList<>();
        for (SnapshotPosition snapshotPosition : snapshotPositions) {
            AssetLookupResultDTO lookup = assetCacheService.getAssetSeries(snapshotPosition.getTicker(), simulation.getCurrentMonth());
            AssetCatalog asset = assetCatalogRepository.findByTicker(lookup.ticker())
                    .orElseThrow(() -> new IllegalStateException(
                            "No asset catalog entry for " + lookup.ticker() + " after a successful refresh"));

            Position position = new Position();
            position.setSimulationId(simulation.getId());
            position.setAssetId(asset.getId());
            position.setQuantity(snapshotPosition.getQuantity());
            position.setWeight(snapshotPosition.getWeight());
            position.setCostBasis(snapshotPosition.getCostBasis());
            position.setTotalDividendsReceived(snapshotPosition.getTotalDividendsReceived());
            newPositions.add(position);
            newAssetIds.add(asset.getId());
        }
        positionRepository.saveAll(newPositions);

        for (UUID oldAssetId : oldAssetIds) {
            if (!newAssetIds.contains(oldAssetId)) {
                assetCacheService.evictIfOrphaned(oldAssetId);
            }
        }

        List<Transaction> monthTransactions = transactionRepository.findBySimulationId(simulation.getId()).stream()
                .filter(transaction -> transaction.getMonth().equals(simulation.getCurrentMonth())
                        && transaction.getType() != TransactionType.DIVIDEND)
                .toList();
        transactionRepository.deleteAll(monthTransactions);

        return toResponse(simulation);
    }

    private SnapshotPosition toSnapshotPosition(Position position, UUID snapshotId) {
        AssetCatalog asset = assetCatalogRepository.findById(position.getAssetId())
                .orElseThrow(() -> new IllegalStateException("No asset catalog entry for asset id " + position.getAssetId()));

        SnapshotPosition snapshotPosition = new SnapshotPosition();
        snapshotPosition.setSnapshotId(snapshotId);
        snapshotPosition.setTicker(asset.getTicker());
        snapshotPosition.setAssetName(asset.getName());
        snapshotPosition.setQuantity(position.getQuantity());
        snapshotPosition.setWeight(position.getWeight());
        snapshotPosition.setCostBasis(position.getCostBasis());
        snapshotPosition.setTotalDividendsReceived(position.getTotalDividendsReceived());
        return snapshotPosition;
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

    // Always goes through the exchange-rate cache rather than special-casing currency
    // equality here - getExchangeRate already short-circuits same-currency pairs to a fixed
    // 1:1 rate without touching the repository or data-service, so wasConverted can just be
    // read off its result instead of being recomputed.
    private ConvertedCashBalanceDTO convertCashBalance(Simulation simulation, String assetCurrency) {
        ExchangeRateLookupResultDTO rate = exchangeRateCacheService.getExchangeRate(
                simulation.getBaseCurrency(), assetCurrency, simulation.getCurrentMonth());
        boolean wasConverted = !rate.fromCurrency().equals(rate.toCurrency());
        BigDecimal amount = wasConverted
                ? simulation.getCashBalance().multiply(rate.close())
                : simulation.getCashBalance();
        return new ConvertedCashBalanceDTO(amount, assetCurrency, wasConverted);
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
