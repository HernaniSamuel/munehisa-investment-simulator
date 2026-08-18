package com.munehisa.backend.exceptions;

import com.munehisa.backend.domain.inflation.InflationCurrency;

public class InflationDataServiceException extends LocalizedRuntimeException {
    public InflationDataServiceException(InflationCurrency currency, Throwable cause) {
        super("error.inflationDataService", cause, currency);
    }
}
