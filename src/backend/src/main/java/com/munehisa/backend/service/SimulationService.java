package com.munehisa.backend.service;

import com.munehisa.backend.domain.asset.AssetCatalog;
import com.munehisa.backend.domain.simulation.Position;
import com.munehisa.backend.domain.simulation.Simulation;
import com.munehisa.backend.domain.simulation.Snapshot;
import com.munehisa.backend.domain.simulation.SnapshotPosition;
import com.munehisa.backend.domain.simulation.Transaction;
import com.munehisa.backend.domain.simulation.TransactionType;
import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.AdvanceMonthPositionDTO;
import com.munehisa.backend.dto.AdvanceMonthResponseDTO;
import com.munehisa.backend.dto.AssetLookupResultDTO;
import com.munehisa.backend.dto.AssetMonthDataDTO;
import com.munehisa.backend.dto.AssetSearchResponseDTO;
import com.munehisa.backend.dto.CashMovementRequestDTO;
import com.munehisa.backend.dto.CashMovementResponseDTO;
import com.munehisa.backend.dto.ConvertedCashBalanceDTO;
import com.munehisa.backend.dto.CreateSimulationRequestDTO;
import com.munehisa.backend.dto.ExchangeRateLookupResultDTO;
import com.munehisa.backend.dto.InflationDeflationResultDTO;
import com.munehisa.backend.dto.PositionResponseDTO;
import com.munehisa.backend.dto.RenameSimulationRequestDTO;
import com.munehisa.backend.dto.SimulationResponseDTO;
import com.munehisa.backend.dto.TradeRequestDTO;
import com.munehisa.backend.dto.TradeResponseDTO;
import com.munehisa.backend.exceptions.FutureSimulationCurrentMonthException;
import com.munehisa.backend.exceptions.FutureSimulationStartMonthException;
import com.munehisa.backend.exceptions.InsufficientCashBalanceException;
import com.munehisa.backend.exceptions.InsufficientCashForPurchaseException;
import com.munehisa.backend.exceptions.InsufficientPositionQuantityException;
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
import java.math.MathContext;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SimulationService {
    private static final MathContext MATH_CONTEXT = new MathContext(50);

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
    public TradeResponseDTO buy(UUID id, TradeRequestDTO request, User user) {
        Simulation simulation = findOwned(id, user);
        List<Position> positions = new ArrayList<>(positionRepository.findBySimulationId(simulation.getId()));

        // Resolving the asset's price is required to compute the cost, so this call - and the
        // AssetNotFoundException/AssetPredatesStartDateException it may throw - can never be
        // skipped, unlike a check that could be bypassed by a caller that "already searched".
        AssetLookupResultDTO lookup = assetCacheService.getAssetSeries(request.ticker(), simulation.getCurrentMonth());
        BigDecimal price = currentClose(lookup);
        BigDecimal costInAssetCurrency = price.multiply(BigDecimal.valueOf(request.quantity()), MATH_CONTEXT);

        ConvertedCashBalanceDTO convertedCash = convertCashBalance(simulation, lookup.baseCurrency());
        if (costInAssetCurrency.compareTo(convertedCash.amount()) > 0) {
            throw new InsufficientCashForPurchaseException(costInAssetCurrency, convertedCash.amount(), lookup.baseCurrency());
        }

        // Independent, opposite-direction rate lookup from convertCashBalance's above: that one
        // only produced a figure to check against, this one produces the value actually applied
        // to the cash balance and cost basis - the cash balance itself is never round-tripped
        // through the asset's currency and back.
        ExchangeRateLookupResultDTO purchaseRate = exchangeRateCacheService.getExchangeRate(
                lookup.baseCurrency(), simulation.getBaseCurrency(), simulation.getCurrentMonth());
        BigDecimal costInBaseCurrency = costInAssetCurrency.multiply(purchaseRate.close(), MATH_CONTEXT);

        simulation.setCashBalance(simulation.getCashBalance().subtract(costInBaseCurrency));

        AssetCatalog asset = assetCatalogRepository.findByTicker(lookup.ticker())
                .orElseThrow(() -> new IllegalStateException(
                        "No asset catalog entry for " + lookup.ticker() + " after a successful refresh"));

        Position position = positions.stream()
                .filter(p -> p.getAssetId().equals(asset.getId()))
                .findFirst()
                .orElseGet(() -> {
                    Position created = new Position();
                    created.setSimulationId(simulation.getId());
                    created.setAssetId(asset.getId());
                    created.setQuantity(0);
                    created.setCostBasis(BigDecimal.ZERO);
                    created.setWeight(BigDecimal.ZERO);
                    created.setTotalDividendsReceived(BigDecimal.ZERO);
                    positions.add(created);
                    return created;
                });
        position.setQuantity(position.getQuantity() + request.quantity());
        position.setCostBasis(position.getCostBasis().add(costInBaseCurrency));

        Transaction transaction = new Transaction();
        transaction.setSimulationId(simulation.getId());
        transaction.setType(TransactionType.BUY);
        transaction.setMonth(simulation.getCurrentMonth());
        transaction.setAmount(costInBaseCurrency);
        transaction.setTicker(lookup.ticker());
        transaction.setAssetName(lookup.name());
        transaction.setQuantity(request.quantity());
        transactionRepository.save(transaction);

        recalculatePositionsAndTotalValue(simulation, positions);
        positionRepository.saveAll(positions);
        simulationRepository.save(simulation);

        return new TradeResponseDTO(
                simulation.getId(), TransactionType.BUY, toPositionResponse(position, asset),
                costInBaseCurrency, simulation.getCashBalance(), simulation.getTotalAssetValue(), simulation.getTotalPatrimony());
    }

    @Transactional
    public TradeResponseDTO sell(UUID id, TradeRequestDTO request, User user) {
        Simulation simulation = findOwned(id, user);
        List<Position> positions = new ArrayList<>(positionRepository.findBySimulationId(simulation.getId()));

        String ticker = request.ticker().toUpperCase();
        Optional<AssetCatalog> assetLookup = assetCatalogRepository.findByTicker(ticker);
        Optional<Position> positionLookup = assetLookup.flatMap(asset -> positions.stream()
                .filter(p -> p.getAssetId().equals(asset.getId()))
                .findFirst());

        // Sell has no cash-conversion check at all, only a held-quantity check - so unlike buy,
        // there is no reason to force a price/data-service round trip just to reject a request
        // that is invalid regardless of price. A ticker that was never bought (or never fetched)
        // degenerates to "0 held", which this same check already rejects.
        long held = positionLookup.map(Position::getQuantity).orElse(0L);
        if (request.quantity() > held) {
            throw new InsufficientPositionQuantityException(ticker, request.quantity(), held);
        }

        AssetCatalog asset = assetLookup.get();
        Position position = positionLookup.get();

        AssetLookupResultDTO lookup = assetCacheService.getAssetSeries(asset.getTicker(), simulation.getCurrentMonth());
        BigDecimal price = currentClose(lookup);

        BigDecimal costBasisRemoved = position.getCostBasis()
                .multiply(BigDecimal.valueOf(request.quantity()), MATH_CONTEXT)
                .divide(BigDecimal.valueOf(position.getQuantity()), MATH_CONTEXT);

        ExchangeRateLookupResultDTO sellRate = exchangeRateCacheService.getExchangeRate(
                asset.getBaseCurrency(), simulation.getBaseCurrency(), simulation.getCurrentMonth());
        BigDecimal proceeds = price.multiply(BigDecimal.valueOf(request.quantity()), MATH_CONTEXT)
                .multiply(sellRate.close(), MATH_CONTEXT);

        simulation.setCashBalance(simulation.getCashBalance().add(proceeds));

        boolean fullSell = request.quantity() == position.getQuantity();
        if (fullSell) {
            positions.remove(position);
            positionRepository.delete(position);
            // Flushed immediately, mirroring resetToSnapshot's explicit flush, so the eviction
            // check's existsByAssetId query below is guaranteed to see this delete.
            positionRepository.flush();
            assetCacheService.evictIfOrphaned(asset.getId());
        } else {
            position.setQuantity(position.getQuantity() - request.quantity());
            position.setCostBasis(position.getCostBasis().subtract(costBasisRemoved));
        }

        Transaction transaction = new Transaction();
        transaction.setSimulationId(simulation.getId());
        transaction.setType(TransactionType.SELL);
        transaction.setMonth(simulation.getCurrentMonth());
        transaction.setAmount(proceeds);
        transaction.setTicker(asset.getTicker());
        transaction.setAssetName(asset.getName());
        transaction.setQuantity(request.quantity());
        transactionRepository.save(transaction);

        recalculatePositionsAndTotalValue(simulation, positions);
        positionRepository.saveAll(positions);
        simulationRepository.save(simulation);

        return new TradeResponseDTO(
                simulation.getId(), TransactionType.SELL, fullSell ? null : toPositionResponse(position, asset),
                proceeds, simulation.getCashBalance(), simulation.getTotalAssetValue(), simulation.getTotalPatrimony());
    }

    @Transactional
    public AdvanceMonthResponseDTO advanceMonth(UUID id, User user) {
        Simulation simulation = findOwned(id, user);

        YearMonth newMonth = simulation.getCurrentMonth().plusMonths(1);
        YearMonth realCurrentMonth = YearMonth.now(clock);
        if (newMonth.isAfter(realCurrentMonth)) {
            throw new FutureSimulationCurrentMonthException(newMonth, realCurrentMonth);
        }
        simulation.setCurrentMonth(newMonth);

        List<Position> positions = positionRepository.findBySimulationId(simulation.getId());
        List<AssetCatalog> assets = new ArrayList<>();
        List<BigDecimal> pricesInBase = new ArrayList<>();
        List<Boolean> truncatedFlags = new ArrayList<>();
        List<BigDecimal> dividendsInBase = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        BigDecimal totalAssetValue = BigDecimal.ZERO;

        for (Position position : positions) {
            AssetCatalog asset = assetCatalogRepository.findById(position.getAssetId())
                    .orElseThrow(() -> new IllegalStateException("No asset catalog entry for asset id " + position.getAssetId()));

            AssetLookupResultDTO lookup = assetCacheService.getAssetSeries(asset.getTicker(), simulation.getCurrentMonth());
            ExchangeRateLookupResultDTO rate = exchangeRateCacheService.getExchangeRate(
                    asset.getBaseCurrency(), simulation.getBaseCurrency(), simulation.getCurrentMonth());

            // Same row backs both price and dividend - the last element of the bounded,
            // truncation-aware series, exactly what currentClose() reads for price alone.
            AssetMonthDataDTO currentRow = lookup.series().get(lookup.series().size() - 1);
            BigDecimal priceInBase = currentRow.close().multiply(rate.close(), MATH_CONTEXT);
            // dividends is nullable (the data-service reports it as null when a month has none),
            // unlike close which is always populated - treated as zero rather than propagating a NPE.
            BigDecimal dividendPerShare = currentRow.dividends() == null ? BigDecimal.ZERO : currentRow.dividends();
            BigDecimal dividendInBase = dividendPerShare
                    .multiply(BigDecimal.valueOf(position.getQuantity()), MATH_CONTEXT)
                    .multiply(rate.close(), MATH_CONTEXT);

            if (dividendInBase.signum() != 0) {
                simulation.setCashBalance(simulation.getCashBalance().add(dividendInBase));
                position.setTotalDividendsReceived(position.getTotalDividendsReceived().add(dividendInBase));

                Transaction dividendTransaction = new Transaction();
                dividendTransaction.setSimulationId(simulation.getId());
                dividendTransaction.setType(TransactionType.DIVIDEND);
                dividendTransaction.setMonth(simulation.getCurrentMonth());
                dividendTransaction.setAmount(dividendInBase);
                dividendTransaction.setTicker(asset.getTicker());
                dividendTransaction.setAssetName(asset.getName());
                transactionRepository.save(dividendTransaction);
            }

            // Value is derived from priceInBase above, not a fresh lookup - reusing
            // recalculatePositionsAndTotalValue here would re-fetch each position's price/rate a
            // second time, and for a position whose price is truncated or mid-refresh-retry, that
            // second fetch is not guaranteed to resolve identically to the first (see the
            // note on AssetCacheService.ensureFreshData), which would let the reported per-position
            // price/wasTruncated diverge from what actually backs totalAssetValue/weight.
            BigDecimal value = priceInBase.multiply(BigDecimal.valueOf(position.getQuantity()), MATH_CONTEXT);
            totalAssetValue = totalAssetValue.add(value);

            assets.add(asset);
            pricesInBase.add(priceInBase);
            truncatedFlags.add(lookup.truncated());
            dividendsInBase.add(dividendInBase);
            values.add(value);
        }

        for (int i = 0; i < positions.size(); i++) {
            BigDecimal weight = totalAssetValue.signum() == 0
                    ? BigDecimal.ZERO
                    : values.get(i).divide(totalAssetValue, MATH_CONTEXT);
            positions.get(i).setWeight(weight);
        }
        simulation.setTotalAssetValue(totalAssetValue);

        positionRepository.saveAll(positions);
        simulationRepository.save(simulation);

        List<AdvanceMonthPositionDTO> positionResults = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            Position position = positions.get(i);
            positionResults.add(new AdvanceMonthPositionDTO(
                    assets.get(i).getTicker(), assets.get(i).getName(), position.getQuantity(),
                    pricesInBase.get(i), truncatedFlags.get(i), dividendsInBase.get(i),
                    position.getWeight(), position.getCostBasis(), position.getTotalDividendsReceived()));
        }

        return new AdvanceMonthResponseDTO(
                simulation.getId(), simulation.getCurrentMonth(), simulation.getCashBalance(),
                simulation.getTotalAssetValue(), simulation.getTotalPatrimony(), positionResults);
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

    // Reprices every position uniformly (including the one just traded, with no reuse of a
    // price/rate already fetched earlier in buy/sell) so weight and totalAssetValue always
    // reflect every holding, not just the traded one - nothing else in the codebase derives
    // these from live prices, they are otherwise only ever copied verbatim to/from a snapshot.
    private void recalculatePositionsAndTotalValue(Simulation simulation, List<Position> positions) {
        BigDecimal[] values = new BigDecimal[positions.size()];
        BigDecimal totalAssetValue = BigDecimal.ZERO;

        for (int i = 0; i < positions.size(); i++) {
            Position position = positions.get(i);
            AssetCatalog asset = assetCatalogRepository.findById(position.getAssetId())
                    .orElseThrow(() -> new IllegalStateException("No asset catalog entry for asset id " + position.getAssetId()));
            AssetLookupResultDTO lookup = assetCacheService.getAssetSeries(asset.getTicker(), simulation.getCurrentMonth());
            BigDecimal price = currentClose(lookup);
            ExchangeRateLookupResultDTO rate = exchangeRateCacheService.getExchangeRate(
                    asset.getBaseCurrency(), simulation.getBaseCurrency(), simulation.getCurrentMonth());
            BigDecimal value = price.multiply(BigDecimal.valueOf(position.getQuantity()), MATH_CONTEXT)
                    .multiply(rate.close(), MATH_CONTEXT);
            values[i] = value;
            totalAssetValue = totalAssetValue.add(value);
        }

        for (int i = 0; i < positions.size(); i++) {
            BigDecimal weight = totalAssetValue.signum() == 0
                    ? BigDecimal.ZERO
                    : values[i].divide(totalAssetValue, MATH_CONTEXT);
            positions.get(i).setWeight(weight);
        }

        simulation.setTotalAssetValue(totalAssetValue);
    }

    // The series is ascending and bounded at the effective month (the latest cached month if
    // the target month is beyond it), so the last element is always the correct current-month
    // close, truncated or not - see AssetCacheService.getAssetSeries.
    private BigDecimal currentClose(AssetLookupResultDTO lookup) {
        var series = lookup.series();
        return series.get(series.size() - 1).close();
    }

    private PositionResponseDTO toPositionResponse(Position position, AssetCatalog asset) {
        return new PositionResponseDTO(
                asset.getTicker(), asset.getName(), position.getQuantity(),
                position.getCostBasis(), position.getWeight(), position.getTotalDividendsReceived());
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
