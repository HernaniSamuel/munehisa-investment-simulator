package com.munehisa.backend.exceptions;

import java.time.LocalDate;
import java.time.YearMonth;

public class AssetPredatesStartDateException extends LocalizedRuntimeException {
    public AssetPredatesStartDateException(String ticker, YearMonth requestedMonth, LocalDate startDate) {
        super("error.assetPredatesStartDate", requestedMonth, ticker, startDate);
    }
}
