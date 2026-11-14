package ru.rshb.stroycontrol.organizations;

import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.ActorSnapshot;
import ru.rshb.stroycontrol.common.ApiException;

import java.util.List;
import java.util.UUID;

/**
 * Публичная граница реквизитов, членства и авторства; сущности организаций наружу не передаются.
 */
public interface Organizations {
    /**
     * Возвращает текущее членство аккаунта без проектных полномочий.
     *
     * @param accountId аккаунт
     * @return актуальное членство либо null, без прав на проекты
     */
    AccountView.Membership membership(UUID accountId);

    /**
     * Возвращает реквизиты организации после проверки доступа вызывающим модулем.
     *
     * @param id организация
     * @return реквизиты для уже проверенного участника проекта
     */
    OrganizationView profile(UUID id);

    /**
     * Проверяет готовность профиля подрядчика для назначения на новую стройку.
     *
     * @param ownerAccountId основной контакт
     * @return полный профиль назначаемой организации
     * @throws ApiException если основной контакт не заполнил профиль
     */
    OrganizationView assignable(UUID ownerAccountId);

    /**
     * @param id организация
     * @return заблокированный профиль; вызывающий модуль проверяет конкретную карточку; нужна
     *     активная транзакция
     */
    OrganizationView lock(UUID id);

    /**
     * Находит активное членство внутри указанной организации.
     *
     * @param organizationId организация
     * @param membershipId членство
     * @return активный сотрудник этой организации либо null
     */
    Member member(UUID organizationId, UUID membershipId);

    /**
     * Возвращает сотрудников для уже авторизованной операции вызывающего модуля.
     *
     * @param organizationId организация
     * @return активные сотрудники для уже проверенного руководителя или участника
     */
    List<Member> members(UUID organizationId);

    /**
     * Формирует персональную подпись фактического автора для сохранения в истории.
     *
     * @param accountId фактический автор
     * @return текущая персональная подпись, сохраняемая модулем события
     */
    ActorSnapshot snapshot(UUID accountId);

    /**
     * Безопасные поля действующего сотрудника без доступа к другим стройкам.
     *
     * @param id членство
     * @param organizationId организация
     * @param accountId личный аккаунт
     * @param displayName актуальное имя
     * @param email вторичный контакт
     * @param position должность, не разрешение
     * @param role OWNER либо MEMBER
     * @param state ACTIVE либо REMOVED
     * @param version версия изменения
     */
    record Member(
            UUID id,
            UUID organizationId,
            UUID accountId,
            String displayName,
            String email,
            String position,
            String role,
            String state,
            long version) {}
}
