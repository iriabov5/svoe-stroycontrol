package ru.rshb.stroycontrol.accounts;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Проверяет отказ регистрации при сбое кодировщика, не допускающий сохранение пустого хеша. */
class AccountEncodingTest {
    @Test
    @DisplayName("Кодировщик без результата не создаёт аккаунт с пустым хешем пароля")
    void nullEncodingDoesNotPersistAccount() {
        var repository = mock(AccountRepository.class);
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode("example-password-123")).thenReturn(null);
        var service = new AccountService(repository, encoder);

        assertThatThrownBy(() ->
                        service.register(
                                "safe@example.test",
                                "example-password-123",
                                AccountRole.CUSTOMER,
                                "Иван"))
                .isInstanceOf(NullPointerException.class);
        verify(repository, never()).saveAndFlush(any(Account.class));
    }
}
