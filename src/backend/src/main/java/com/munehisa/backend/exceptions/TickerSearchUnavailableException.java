package com.munehisa.backend.exceptions;

public class TickerSearchUnavailableException extends RuntimeException {
    public TickerSearchUnavailableException(Throwable cause) {
        super("Ticker search is unavailable: the data-service request failed", cause);
    }
}
