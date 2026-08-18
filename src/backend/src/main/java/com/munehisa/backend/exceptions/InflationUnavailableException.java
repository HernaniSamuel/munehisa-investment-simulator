package com.munehisa.backend.exceptions;

import com.munehisa.backend.domain.inflation.InflationCurrency;

public class InflationUnavailableException extends LocalizedRuntimeException {
    public InflationUnavailableException(InflationCurrency currency, Throwable cause) {
        super("error.inflationUnavailable", cause, currency);
    }
}
