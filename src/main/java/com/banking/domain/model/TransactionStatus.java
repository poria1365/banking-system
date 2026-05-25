package com.banking.domain.model;

// Full lifecycle of a transfer. Anything past PROCESSING is moving toward a terminal state.
// NEEDS_MANUAL_REVIEW means the system gave up and a human needs to sort it out.
public enum TransactionStatus {
    PENDING,             // intent recorded, locks not yet acquired
    PROCESSING,          // saga is running, locks are held
    COMPLETED,           // both accounts updated, money transferred — done
    FAILED,              // failed before any money moved, nothing to roll back
    COMPENSATING,        // debit happened but credit failed — reversing the debit
    COMPENSATED,         // debit successfully reversed, accounts back to original state
    NEEDS_MANUAL_REVIEW  // compensation also failed — escalate to ops team
}
