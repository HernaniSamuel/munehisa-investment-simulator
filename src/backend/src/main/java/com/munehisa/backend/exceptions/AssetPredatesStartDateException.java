package com.munehisa.backend.exceptions;

import java.time.LocalDate;
import java.time.YearMonth;

public class AssetPredatesStartDateException extends RuntimeException {
    public AssetPredatesStartDateException(String ticker, YearMonth requestedMonth, LocalDate startDate) {
        super("Requested month " + requestedMonth + " for " + ticker
                + " is before the asset's start date " + startDate + " - it did not exist yet");
    }
}
