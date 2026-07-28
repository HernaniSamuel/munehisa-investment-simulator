package com.munehisa.backend.exceptions;

import java.time.YearMonth;

public class FutureDeflationTargetException extends RuntimeException {
    public FutureDeflationTargetException(YearMonth targetMonth, YearMonth currentMonth) {
        super("Cannot deflate to a target month (" + targetMonth + ") after the real current month (" + currentMonth + ")");
    }
}
