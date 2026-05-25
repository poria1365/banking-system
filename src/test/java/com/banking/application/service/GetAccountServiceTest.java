package com.banking.application.service;

import com.banking.domain.exception.AccountNotFoundException;
import com.banking.domain.model.Account;
import com.banking.domain.model.Money;
import com.banking.domain.port.out.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetAccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private GetAccountService service;

    @Test
    void getAccount_returnsAccount_whenFound() {
        Account account = Account.create("alice", Money.of("1000.00", "USD"));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        Account result = service.getAccount(account.getId());

        assertThat(result).isEqualTo(account);
    }

    @Test
    void getAccount_throwsAccountNotFoundException_whenNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(accountRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(AccountNotFoundException.class)
            .isThrownBy(() -> service.getAccount(unknownId))
            .withMessageContaining(unknownId.toString());
    }

    @Test
    void getAccount_queriesRepositoryWithCorrectId() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.empty());

        assertThatExceptionOfType(AccountNotFoundException.class)
            .isThrownBy(() -> service.getAccount(id));

        verify(accountRepository).findById(id);
    }
}
