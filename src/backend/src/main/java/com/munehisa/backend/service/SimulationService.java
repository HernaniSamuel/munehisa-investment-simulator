package com.munehisa.backend.service;

import com.munehisa.backend.domain.simulation.Simulation;
import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.CreateSimulationRequestDTO;
import com.munehisa.backend.dto.RenameSimulationRequestDTO;
import com.munehisa.backend.dto.SimulationResponseDTO;
import com.munehisa.backend.exceptions.FutureSimulationStartMonthException;
import com.munehisa.backend.exceptions.SimulationNotFoundException;
import com.munehisa.backend.repository.SimulationRepository;
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
