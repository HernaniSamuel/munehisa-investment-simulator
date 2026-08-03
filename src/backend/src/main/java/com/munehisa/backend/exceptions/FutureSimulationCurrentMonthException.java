package com.munehisa.backend.exceptions;

import java.time.YearMonth;

public class FutureSimulationCurrentMonthException extends RuntimeException {
    public FutureSimulationCurrentMonthException(YearMonth attemptedMonth, YearMonth realCurrentMonth) {
        super("Cannot advance the simulation's current month to (" + attemptedMonth
                + ") after the real current month (" + realCurrentMonth + ")");
    }
}
