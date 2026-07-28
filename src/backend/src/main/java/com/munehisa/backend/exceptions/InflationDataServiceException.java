package com.munehisa.backend.exceptions;

import com.munehisa.backend.domain.inflation.InflationCurrency;

public class InflationDataServiceException extends RuntimeException {
    public InflationDataServiceException(InflationCurrency currency, Throwable cause) {
        super("Failed to fetch inflation series for " + currency + " from data-service", cause);
    }
}
