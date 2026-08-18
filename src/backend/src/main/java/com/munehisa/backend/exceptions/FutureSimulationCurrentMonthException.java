package com.munehisa.backend.exceptions;

import java.time.YearMonth;

public class FutureSimulationCurrentMonthException extends LocalizedRuntimeException {
    public FutureSimulationCurrentMonthException(YearMonth attemptedMonth, YearMonth realCurrentMonth) {
        super("error.futureSimulationCurrentMonth", attemptedMonth, realCurrentMonth);
    }
}
