package com.munehisa.backend.exceptions;

public class SimulationNotFoundException extends RuntimeException {
    public SimulationNotFoundException() {
        super("Simulation not found");
    }
}
