package com.munehisa.backend.exceptions;

import com.munehisa.backend.domain.inflation.InflationCurrency;

public class InflationUnavailableException extends RuntimeException {
    public InflationUnavailableException(InflationCurrency currency, Throwable cause) {
        super("No cached inflation data available for " + currency + " and refresh from data-service failed", cause);
    }
}
