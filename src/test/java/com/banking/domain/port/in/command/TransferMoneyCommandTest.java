package com.banking.domain.port.in.command;

import com.banking.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class TransferMoneyCommandTest {

    private final UUID fromId = UUID.randomUUID();
    private final UUID toId = UUID.randomUUID();
    private final Money amount = Money.of("100.00", "USD");

    @Test
    void create_validInputs_succeeds() {
        var command = new TransferMoneyCommand("key-1", fromId, toId, amount);

        assertThat(command.idempotencyKey()).isEqualTo("key-1");
        assertThat(command.fromAccountId()).isEqualTo(fromId);
        assertThat(command.toAccountId()).isEqualTo(toId);
        assertThat(command.amount()).isEqualTo(amount);
    }

    @Test
    void create_nullIdempotencyKey_throwsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new TransferMoneyCommand(null, fromId, toId, amount))
            .withMessageContaining("idempotencyKey");
    }

    @Test
    void create_blankIdempotencyKey_throwsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new TransferMoneyCommand("  ", fromId, toId, amount));
    }

    @Test
    void create_nullFromAccountId_throwsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new TransferMoneyCommand("key", null, toId, amount));
    }

    @Test
    void create_nullToAccountId_throwsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new TransferMoneyCommand("key", fromId, null, amount));
    }

    @Test
    void create_sameFromAndToAccountId_throwsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new TransferMoneyCommand("key", fromId, fromId, amount))
            .withMessageContaining("differ");
    }

    @Test
    void create_nullAmount_throwsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new TransferMoneyCommand("key", fromId, toId, null));
    }

    @Test
    void create_zeroAmount_throwsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new TransferMoneyCommand("key", fromId, toId, Money.zero("USD")));
    }
}
