package com.banking.infrastructure.lock;

// Thrown when the distributed lock cannot be acquired within the wait timeout.
// The caller should surface this as a 503 / retry-later response.
public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException(String message) {
        super(message);
    }

    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
