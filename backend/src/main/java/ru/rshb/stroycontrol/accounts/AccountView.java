package ru.rshb.stroycontrol.accounts;

import java.io.Serializable;
import java.util.UUID;

/**
 * Безопасный профиль аккаунта для сессии, REST и межмодульных вызовов.
 *
 * @param id внутренний идентификатор аккаунта
 * @param email нормализованный адрес регистрации
 * @param displayName имя для подписи либо null до заполнения
 * @param profileVersion версия изменения имени
 * @param membership действующее членство либо null
 * @param role неизменяемая роль аккаунта
 */
public record AccountView(
        UUID id,
        String email,
        AccountRole role,
        String displayName,
        long profileVersion,
        Membership membership)
        implements Serializable {
    /**
     * Краткое действующее членство для личного профиля, без проектных прав.
     *
     * @param id идентификатор членства
     * @param organizationId организация
     * @param organizationName название организации
     * @param position должность
     * @param role OWNER либо MEMBER, не набор прав на стройку
     */
    public record Membership(
            UUID id, UUID organizationId, String organizationName, String position, String role)
            implements Serializable {}

    /**
     * Создаёт прежнюю модель без заполненного имени для совместимости внутренних клиентов.
     *
     * @param id аккаунт
     * @param email email входа
     * @param role глобальная роль
     */
    public AccountView(UUID id, String email, AccountRole role) {
        this(id, email, role, null, 0, null);
    }

    /**
     * Дополняет актуальный профиль членством без изменения данных сессии.
     *
     * @param member текущее членство либо null
     * @return независимая безопасная модель
     */
    public AccountView withMembership(Membership member) {
        return new AccountView(id, email, role, displayName, profileVersion, member);
    }
}
