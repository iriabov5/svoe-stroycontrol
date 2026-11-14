package ru.rshb.stroycontrol.accounts;

import ru.rshb.stroycontrol.common.ApiException;

import java.util.Optional;
import java.util.UUID;

/** Публичный поиск аккаунтов для назначения участников карточки. */
public interface Accounts {
    /**
     * Ищет зарегистрированного подрядчика, не раскрывая данные входа.
     *
     * @param email email с нормализацией по правилам регистрации
     * @return профиль подрядчика или пустой результат, в том числе для роли заказчика
     * @throws ApiException при некорректном email
     */
    Optional<AccountView> findContractor(String email);

    /**
     * Читает актуальные несекретные поля аккаунта, а не сохранённую копию сессии.
     *
     * @param id аккаунт
     * @return актуальный профиль
     * @throws ApiException с AUTH_REQUIRED для отсутствующего аккаунта
     */
    AccountView profile(UUID id);

    /**
     * Блокирует аккаунт в активной транзакции для единственного активного членства.
     *
     * @param id аккаунт
     * @return актуальный профиль под блокировкой до commit
     * @throws ApiException с AUTH_REQUIRED для отсутствующего аккаунта
     */
    AccountView lock(UUID id);
}
