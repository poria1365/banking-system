package com.banking.domain.model;

import com.banking.domain.exception.AccountInactiveException;
import com.banking.domain.exception.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AccountTest {

    private Money initialBalance;
    private Account account;

    @BeforeEach
    void setUp() {
        initialBalance = Money.of("1000.00", "USD");
        account = Account.create("owner-1", initialBalance);
    }

    // --- Creation ---

    @Test
    void create_setsCorrectInitialState() {
        assertThat(account.getId()).isNotNull();
        assertThat(account.getOwnerId()).isEqualTo("owner-1");
        assertThat(account.getBalance()).isEqualTo(initialBalance);
        assertThat(account.getCurrency()).isEqualTo("USD");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getCreatedAt()).isNotNull();
        assertThat(account.getUpdatedAt()).isNotNull();
        assertThat(account.getVersion()).isZero();
    }

    @Test
    void create_isActive() {
        assertThat(account.isActive()).isTrue();
    }

    // --- Debit ---

    @Test
    void debit_reducesBalance() {
        account.debit(Money.of("300.00", "USD"));

        assertThat(account.getBalance()).isEqualTo(Money.of("700.00", "USD"));
    }

    @Test
    void debit_incrementsVersion() {
        long versionBefore = account.getVersion();

        account.debit(Money.of("100.00", "USD"));

        assertThat(account.getVersion()).isEqualTo(versionBefore + 1);
    }

    @Test
    void debit_updatesTimestamp() throws InterruptedException {
        var before = account.getUpdatedAt();
        Thread.sleep(5);

        account.debit(Money.of("100.00", "USD"));

        assertThat(account.getUpdatedAt()).isAfter(before);
    }

    @Test
    void debit_exactBalance_reducesToZero() {
        account.debit(Money.of("1000.00", "USD"));

        assertThat(account.getBalance().isZero()).isTrue();
    }

    @Test
    void debit_insufficientFunds_throwsInsufficientFundsException() {
        assertThatExceptionOfType(InsufficientFundsException.class)
            .isThrownBy(() -> account.debit(Money.of("1000.01", "USD")));
    }

    @Test
    void debit_frozenAccount_throwsAccountInactiveException() {
        account.freeze();

        assertThatExceptionOfType(AccountInactiveException.class)
            .isThrownBy(() -> account.debit(Money.of("100.00", "USD")));
    }

    @Test
    void debit_closedAccount_throwsAccountInactiveException() {
        account.close();

        assertThatExceptionOfType(AccountInactiveException.class)
            .isThrownBy(() -> account.debit(Money.of("100.00", "USD")));
    }

    // --- Credit ---

    @Test
    void credit_increasesBalance() {
        account.credit(Money.of("500.00", "USD"));

        assertThat(account.getBalance()).isEqualTo(Money.of("1500.00", "USD"));
    }

    @Test
    void credit_incrementsVersion() {
        long versionBefore = account.getVersion();

        account.credit(Money.of("100.00", "USD"));

        assertThat(account.getVersion()).isEqualTo(versionBefore + 1);
    }

    @Test
    void credit_frozenAccount_throwsAccountInactiveException() {
        account.freeze();

        assertThatExceptionOfType(AccountInactiveException.class)
            .isThrownBy(() -> account.credit(Money.of("100.00", "USD")));
    }

    @Test
    void credit_closedAccount_throwsAccountInactiveException() {
        account.close();

        assertThatExceptionOfType(AccountInactiveException.class)
            .isThrownBy(() -> account.credit(Money.of("100.00", "USD")));
    }

    // --- Freeze ---

    @Test
    void freeze_activeAccount_setsFrozen() {
        account.freeze();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.FROZEN);
        assertThat(account.isActive()).isFalse();
    }

    @Test
    void freeze_closedAccount_throwsException() {
        account.close();

        assertThatExceptionOfType(AccountInactiveException.class)
            .isThrownBy(account::freeze);
    }

    @Test
    void freeze_alreadyFrozen_succeeds() {
        account.freeze();

        assertThatCode(account::freeze).doesNotThrowAnyException();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.FROZEN);
    }

    // --- Close ---

    @Test
    void close_activeAccount_setsClosed() {
        account.close();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void close_frozenAccount_setsClosed() {
        account.freeze();
        account.close();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    // --- Multiple mutations ---

    @Test
    void multipleDebitsAndCredits_maintainCorrectBalance() {
        account.debit(Money.of("200.00", "USD"));
        account.credit(Money.of("100.00", "USD"));
        account.debit(Money.of("50.00", "USD"));

        assertThat(account.getBalance()).isEqualTo(Money.of("850.00", "USD"));
        assertThat(account.getVersion()).isEqualTo(3L);
    }

    // --- Reconstitution ---

    @Test
    void reconstitute_preservesAllFields() {
        var original = account;
        var copy = Account.reconstitute(
            original.getId(), original.getOwnerId(), original.getBalance(),
            original.getCurrency(), original.getStatus(),
            original.getCreatedAt(), original.getUpdatedAt(), original.getVersion()
        );

        assertThat(copy.getId()).isEqualTo(original.getId());
        assertThat(copy.getBalance()).isEqualTo(original.getBalance());
        assertThat(copy.getVersion()).isEqualTo(original.getVersion());
    }
}
