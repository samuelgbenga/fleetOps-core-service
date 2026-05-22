package com.fleetops.core.shared.exception;

public class TenantAccessException extends RuntimeException {
    public TenantAccessException(String message) {
        super(message);
    }
}
