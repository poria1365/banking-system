package com.banking.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void create_validAmount_succeeds() {
        Money money = Money.of(new BigDecimal("100.00"), "USD");

        assertThat(money.getAmount()).isEqualByComparingTo("100.00");
        assertThat(money.getCurrency()).isEqualTo("USD");
    }

    @Test
    void create_currencyNormalizedToUpperCase() {
        Money money = Money.of("50.00", "usd");

        assertThat(money.getCurrency()).isEqualTo("USD");
    }

    @Test
    void create_amountScaledToTwoDecimals() {
        Money money = Money.of("100.5", "USD");

        assertThat(money.getAmount()).isEqualByComparingTo("100.50");
    }

    @Test
    void create_negativeAmount_throwsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Money.of("-1.00", "USD"))
            .withMessageContaining("negative");
    }

    @Test
    void create_nullCurrency_throwsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Money.of(BigDecimal.TEN, null));
    }

    @Test
    void create_blankCurrency_throwsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Money.of(BigDecimal.TEN, "  "));
    }

    @Test
    void zero_factory_createsZeroBalance() {
        Money zero = Money.zero("EUR");

        assertThat(zero.isZero()).isTrue();
        assertThat(zero.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void add_sameCurrency_returnsCorrectSum() {
        Money a = Money.of("100.00", "USD");
        Money b = Money.of("50.00", "USD");

        Money result = a.add(b);

        assertThat(result.getAmount()).isEqualByComparingTo("150.00");
        assertThat(result.getCurrency()).isEqualTo("USD");
    }

    @Test
    void add_differentCurrency_throwsException() {
        Money usd = Money.of("100.00", "USD");
        Money eur = Money.of("50.00", "EUR");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> usd.add(eur))
            .withMessageContaining("Currency mismatch");
    }

    @Test
    void subtract_sameCurrency_returnsCorrectDifference() {
        Money a = Money.of("100.00", "USD");
        Money b = Money.of("30.00", "USD");

        Money result = a.subtract(b);

        assertThat(result.getAmount()).isEqualByComparingTo("70.00");
    }

    @Test
    void subtract_exceedsAmount_throwsException() {
        Money a = Money.of("50.00", "USD");
        Money b = Money.of("100.00", "USD");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> a.subtract(b));
    }

    @Test
    void subtract_differentCurrency_throwsException() {
        Money usd = Money.of("100.00", "USD");
        Money eur = Money.of("50.00", "EUR");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> usd.subtract(eur));
    }

    @Test
    void isLessThan_returnsTrue_whenLess() {
        assertThat(Money.of("10.00", "USD").isLessThan(Money.of("20.00", "USD"))).isTrue();
    }

    @Test
    void isLessThan_returnsFalse_whenGreater() {
        assertThat(Money.of("20.00", "USD").isLessThan(Money.of("10.00", "USD"))).isFalse();
    }

    @Test
    void isLessThan_returnsFalse_whenEqual() {
        assertThat(Money.of("10.00", "USD").isLessThan(Money.of("10.00", "USD"))).isFalse();
    }

    @Test
    void isGreaterThan_returnsTrue_whenGreater() {
        assertThat(Money.of("20.00", "USD").isGreaterThan(Money.of("10.00", "USD"))).isTrue();
    }

    @Test
    void isZero_returnsTrue_forZeroAmount() {
        assertThat(Money.zero("USD").isZero()).isTrue();
    }

    @Test
    void isZero_returnsFalse_forPositiveAmount() {
        assertThat(Money.of("0.01", "USD").isZero()).isFalse();
    }

    @Test
    void equals_sameAmountAndCurrency_areEqual() {
        Money a = Money.of("100.00", "USD");
        Money b = Money.of("100.00", "USD");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void equals_differentAmounts_areNotEqual() {
        assertThat(Money.of("100.00", "USD")).isNotEqualTo(Money.of("99.99", "USD"));
    }

    @Test
    void equals_differentCurrencies_areNotEqual() {
        assertThat(Money.of("100.00", "USD")).isNotEqualTo(Money.of("100.00", "EUR"));
    }

    @Test
    void add_isImmutable_originalUnchanged() {
        Money original = Money.of("100.00", "USD");
        original.add(Money.of("50.00", "USD"));

        assertThat(original.getAmount()).isEqualByComparingTo("100.00");
    }
}
