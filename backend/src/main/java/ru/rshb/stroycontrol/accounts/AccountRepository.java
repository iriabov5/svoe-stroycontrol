package ru.rshb.stroycontrol.accounts;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/** Хранит аккаунты модуля accounts; нормализацию email и проверку пароля выполняет сервис. */
interface AccountRepository extends JpaRepository<Account, UUID> {
    /**
     * Ищет аккаунт по уже нормализованному email.
     *
     * <p>Хеш пароля используется только внутри accounts и не передаётся другим модулям.
     *
     * @param email email аккаунта; нормализуется перед поиском или сохранением
     * @return найденный объект либо пустой Optional
     */
    Optional<Account> findByEmail(String email);

    /**
     * Блокирует идентичность перед вступлением в организацию.
     *
     * @param id аккаунт
     * @return найденная запись либо пустой результат
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id=:id")
    Optional<Account> lockById(UUID id);
}
