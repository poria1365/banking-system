package com.banking.domain.exception;

// Base for all business-rule violations. Using a typed hierarchy means
// GlobalExceptionHandler can map each subclass to the right HTTP status without instanceof chains.
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
