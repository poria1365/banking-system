package com.banking.domain.port.in.command;

import com.banking.domain.model.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CreateAccountCommandTest {

    @Test
    void create_validInputs_succeeds() {
        var command = new CreateAccountCommand("owner-1", Money.of("500.00", "USD"));

        assertThat(command.ownerId()).isEqualTo("owner-1");
        assertThat(command.initialBalance()).isEqualTo(Money.of("500.00", "USD"));
    }

    @Test
    void create_nullOwnerId_throwsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CreateAccountCommand(null, Money.of("100.00", "USD")))
            .withMessageContaining("ownerId");
    }

    @Test
    void create_blankOwnerId_throwsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CreateAccountCommand("  ", Money.of("100.00", "USD")))
            .withMessageContaining("ownerId");
    }

    @Test
    void create_nullInitialBalance_throwsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CreateAccountCommand("owner-1", null))
            .withMessageContaining("initialBalance");
    }
}
