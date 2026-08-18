package com.munehisa.backend.exceptions;

public class SnapshotNotFoundException extends LocalizedRuntimeException {
    public SnapshotNotFoundException() {
        super("error.snapshotNotFound");
    }
}
