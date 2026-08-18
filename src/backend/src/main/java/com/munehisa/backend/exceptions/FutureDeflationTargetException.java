package com.munehisa.backend.exceptions;

import java.time.YearMonth;

public class FutureDeflationTargetException extends LocalizedRuntimeException {
    public FutureDeflationTargetException(YearMonth targetMonth, YearMonth currentMonth) {
        super("error.futureDeflationTarget", targetMonth, currentMonth);
    }
}
