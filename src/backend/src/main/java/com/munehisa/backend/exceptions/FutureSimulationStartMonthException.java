package com.munehisa.backend.exceptions;

import java.time.YearMonth;

public class FutureSimulationStartMonthException extends RuntimeException {
    public FutureSimulationStartMonthException(YearMonth startMonth, YearMonth currentMonth) {
        super("Cannot start a simulation in a month (" + startMonth + ") after the real current month (" + currentMonth + ")");
    }
}
