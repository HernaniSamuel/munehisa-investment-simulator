package com.munehisa.backend.exceptions;

public class SnapshotNotFoundException extends RuntimeException {
    public SnapshotNotFoundException() {
        super("Snapshot not found");
    }
}
