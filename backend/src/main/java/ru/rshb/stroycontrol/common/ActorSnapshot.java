package ru.rshb.stroycontrol.common;

import ru.rshb.stroycontrol.accounts.AccountRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Неизменяемая подпись фактического автора, сохраняемая вместе с действием.
 *
 * @param accountId аккаунт автора
 * @param displayName имя на момент действия либо null у прежней записи
 * @param globalRole роль заказчика или подрядчика
 * @param organizationId организация либо null для заказчика и прежнего подрядчика
 * @param organizationName название на момент действия либо null
 * @param position должность на момент действия либо null
 * @param capturedAt время получения профиля, null у прежней записи
 * @param legacy сведения об имени на момент действия отсутствуют
 */
public record ActorSnapshot(
        UUID accountId,
        String displayName,
        AccountRole globalRole,
        UUID organizationId,
        String organizationName,
        String position,
        Instant capturedAt,
        boolean legacy) {
    /**
     * Обозначает прежнюю запись без выдуманного имени и должности.
     *
     * @param id известный аккаунт действия
     * @param role известная роль действия
     * @return маркер отсутствия персонального снимка
     */
    public static ActorSnapshot legacy(UUID id, AccountRole role) {
        return new ActorSnapshot(id, null, role, null, null, null, null, true);
    }
}
