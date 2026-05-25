package com.banking.application.service;

import com.banking.domain.model.Account;
import com.banking.domain.model.AccountStatus;
import com.banking.domain.model.Money;
import com.banking.domain.port.in.command.CreateAccountCommand;
import com.banking.domain.port.out.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateAccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private CreateAccountService service;

    @Test
    void createAccount_savesAndReturnsAccount() {
        Money balance = Money.of("500.00", "USD");
        var command = new CreateAccountCommand("owner-1", balance);

        Account accountToReturn = Account.create("owner-1", balance);
        when(accountRepository.save(any(Account.class))).thenReturn(accountToReturn);

        Account result = service.createAccount(command);

        assertThat(result).isNotNull();
        assertThat(result.getOwnerId()).isEqualTo("owner-1");
        assertThat(result.getBalance()).isEqualTo(balance);
        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void createAccount_passesCorrectAccountToRepository() {
        Money balance = Money.of("1000.00", "USD");
        var command = new CreateAccountCommand("owner-alice", balance);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createAccount(command);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());

        Account savedAccount = captor.getValue();
        assertThat(savedAccount.getOwnerId()).isEqualTo("owner-alice");
        assertThat(savedAccount.getBalance()).isEqualTo(balance);
        assertThat(savedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(savedAccount.getId()).isNotNull();
        assertThat(savedAccount.getVersion()).isZero();
    }

    @Test
    void createAccount_repositorySaveCalledExactlyOnce() {
        var command = new CreateAccountCommand("owner-1", Money.of("0.00", "USD"));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createAccount(command);

        verify(accountRepository, times(1)).save(any(Account.class));
    }
}
