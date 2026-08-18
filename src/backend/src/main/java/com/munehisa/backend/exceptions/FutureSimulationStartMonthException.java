package com.munehisa.backend.exceptions;

import java.time.YearMonth;

public class FutureSimulationStartMonthException extends LocalizedRuntimeException {
    public FutureSimulationStartMonthException(YearMonth startMonth, YearMonth currentMonth) {
        super("error.futureSimulationStartMonth", startMonth, currentMonth);
    }
}
