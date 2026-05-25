package com.banking.domain.model;

// Three states an account can be in. CLOSED is terminal — no coming back from it.
public enum AccountStatus {
    ACTIVE,  // normal operation
    FROZEN,  // temporarily blocked (e.g. fraud hold) — can be unfrozen
    CLOSED   // permanently shut down — cannot be reopened or transacted on
}
