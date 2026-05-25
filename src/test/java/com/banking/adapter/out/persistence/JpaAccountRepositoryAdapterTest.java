package com.banking.adapter.out.persistence;

import com.banking.domain.model.Account;
import com.banking.domain.model.AccountStatus;
import com.banking.domain.model.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAccountRepositoryAdapter.class)
@ActiveProfiles("test")
class JpaAccountRepositoryAdapterTest {

    @Autowired
    private JpaAccountRepositoryAdapter adapter;

    // ─── save ─────────────────────────────────────────────────────────────────

    @Test
    void save_persistsNewAccount_andReturnsWithVersion() {
        Account account = Account.create("user-1", money("500.00", "USD"));

        Account saved = adapter.save(account);

        assertThat(saved.getId()).isEqualTo(account.getId());
        assertThat(saved.getOwnerId()).isEqualTo("user-1");
        assertThat(saved.getBalance().getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    void save_updatesExistingAccount_incrementsVersionInDb() {
        Account account = Account.create("user-2", money("1000.00", "EUR"));
        adapter.save(account);

        account.debit(money("200.00", "EUR"));
        Account updated = adapter.save(account);

        assertThat(updated.getBalance().getAmount()).isEqualByComparingTo(new BigDecimal("800.00"));
        // DB version increments because Hibernate performed an UPDATE
        assertThat(updated.getVersion()).isOne();
    }

    @Test
    void save_freeze_persistsStatusChange() {
        Account account = Account.create("user-3", money("100.00", "USD"));
        adapter.save(account);

        account.freeze();
        Account frozen = adapter.save(account);

        assertThat(frozen.getStatus()).isEqualTo(AccountStatus.FROZEN);
    }

    // ─── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEmpty_whenAccountDoesNotExist() {
        Optional<Account> result = adapter.findById(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void findById_returnsAccount_whenExists() {
        Account account = Account.create("user-4", money("250.00", "GBP"));
        adapter.save(account);

        Optional<Account> found = adapter.findById(account.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(account.getId());
        assertThat(found.get().getCurrency()).isEqualTo("GBP");
    }

    @Test
    void findById_roundTripsAllFields() {
        Account original = Account.create("owner-round-trip", money("999.99", "USD"));
        adapter.save(original);

        Account loaded = adapter.findById(original.getId()).orElseThrow();

        assertThat(loaded.getOwnerId()).isEqualTo("owner-round-trip");
        assertThat(loaded.getBalance().getAmount()).isEqualByComparingTo(new BigDecimal("999.99"));
        assertThat(loaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    // ─── existsById ───────────────────────────────────────────────────────────

    @Test
    void existsById_returnsTrue_whenAccountExists() {
        Account account = Account.create("user-5", money("1.00", "USD"));
        adapter.save(account);

        assertThat(adapter.existsById(account.getId())).isTrue();
    }

    @Test
    void existsById_returnsFalse_whenAccountDoesNotExist() {
        assertThat(adapter.existsById(UUID.randomUUID())).isFalse();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Money money(String amount, String currency) {
        return Money.of(new BigDecimal(amount), currency);
    }
}
