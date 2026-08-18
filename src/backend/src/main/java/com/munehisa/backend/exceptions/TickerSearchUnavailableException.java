package com.munehisa.backend.exceptions;

public class TickerSearchUnavailableException extends LocalizedRuntimeException {
    public TickerSearchUnavailableException(Throwable cause) {
        super("error.tickerSearchUnavailable", cause);
    }
}
