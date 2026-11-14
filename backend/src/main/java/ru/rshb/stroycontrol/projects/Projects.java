package ru.rshb.stroycontrol.projects;

import org.springframework.transaction.IllegalTransactionStateException;

import ru.rshb.stroycontrol.common.ApiException;

import java.util.UUID;

/** Публичная граница проверки участия и координации записей в карточке дома. */
public interface Projects {
    /**
     * Запрещает новые действия строительства после подтверждения завершения владельцем.
     *
     * @param projectId доступный проект
     * @param actorId участник
     * @throws ApiException с CONSTRUCTION_COMPLETED для завершённого проекта
     */
    void requireOpen(UUID projectId, UUID actorId);

    /**
     * Увеличивает версию готовности в транзакции изменения плана или приёмки.
     *
     * @param projectId проект под блокировкой
     * @param actorId участник
     */
    void touchCompletion(UUID projectId, UUID actorId);

    /**
     * Возвращает минимальное описание проекта для банковского снимка.
     *
     * @param projectId доступный проект
     * @param actorId участник
     * @return данные без кредитных подробностей
     */
    Info info(UUID projectId, UUID actorId);

    /**
     * Публичное описание проекта без банковских реквизитов.
     *
     * @param id карточка
     * @param name название
     * @param address участок
     * @param financingSource выбранное финансирование либо null в прежнем проекте
     */
    record Info(UUID id, String name, String address, String financingSource) {}

    /**
     * Проверяет участие аккаунта в карточке без передачи JPA-сущности.
     *
     * @param projectId идентификатор карточки
     * @param accountId идентификатор аутентифицированного аккаунта
     * @return права участника на карточку
     * @throws ApiException с PROJECT_NOT_FOUND для отсутствующей или чужой карточки
     */
    ProjectAccess requireParticipant(UUID projectId, UUID accountId);

    /**
     * Блокирует карточку до завершения активной транзакции вызывающего модуля. Блокировка
     * координирует назначение участников и изменения порядка дочерних объектов.
     *
     * @param projectId идентификатор карточки
     * @param accountId идентификатор аутентифицированного аккаунта
     * @return права участника под блокировкой
     * @throws ApiException с PROJECT_NOT_FOUND для отсутствующей или чужой карточки
     * @throws IllegalTransactionStateException если активной транзакции нет
     */
    ProjectAccess lockParticipant(UUID projectId, UUID accountId);

    /**
     * Фиксирует согласованное предложение в активной транзакции владельца.
     *
     * <p>Вызывающий модуль предварительно блокирует проект через lockParticipant и проверяет
     * состояние и принадлежность версии сметы. Изменение карточки откатывается вместе с решением.
     *
     * @param projectId проект
     * @param actorId владелец
     * @param proposalId идентификатор проверенной согласованной версии сметы
     * @throws ApiException с PROJECT_NOT_FOUND при отсутствии доступа или FORBIDDEN для другого
     *     участника
     * @throws IllegalTransactionStateException при отсутствии активной транзакции
     */
    void approveProposal(UUID projectId, UUID actorId, UUID proposalId);

    /**
     * Фиксирует согласованный договор для допуска к началу работ.
     *
     * <p>Вызывается после блокировки проекта и проверки версии договора в общей транзакции
     * согласования; сущность договора не передаётся между модулями.
     *
     * @param projectId проект
     * @param actorId владелец
     * @param contractId идентификатор проверенной согласованной версии договора
     * @throws ApiException с PROJECT_NOT_FOUND при отсутствии доступа или FORBIDDEN для другого
     *     участника
     * @throws IllegalTransactionStateException при отсутствии активной транзакции
     */
    void approveContract(UUID projectId, UUID actorId, UUID contractId);

    /**
     * Закрывает подготовку после окончательного отказа в активной транзакции.
     *
     * @param projectId проект
     * @param actorId владелец
     * @throws ApiException с INVALID_PROPOSAL_STATE для уже согласованной сметы или LEGACY,
     *     PROJECT_NOT_FOUND при отсутствии доступа или FORBIDDEN для другого участника
     * @throws IllegalTransactionStateException при отсутствии активной транзакции
     */
    void closePreparation(UUID projectId, UUID actorId);

    /**
     * Возвращает идентификатор отправленной заявки для создания предложения подрядчика.
     *
     * @param projectId карточка дома
     * @param actorId аутентифицированный участник
     * @return идентификатор неизменяемой отправленной заявки
     * @throws ApiException с PROJECT_NOT_FOUND при отсутствии доступа или REQUEST_LOCKED, если
     *     заявка ещё не отправлена
     */
    UUID submittedRequest(UUID projectId, UUID actorId);
}
