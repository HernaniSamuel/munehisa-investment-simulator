package com.munehisa.backend.exceptions;

public class SimulationNotFoundException extends LocalizedRuntimeException {
    public SimulationNotFoundException() {
        super("error.simulationNotFound");
    }
}
