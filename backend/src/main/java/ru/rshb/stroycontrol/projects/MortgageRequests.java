package ru.rshb.stroycontrol.projects;

import org.springframework.transaction.IllegalTransactionStateException;

import ru.rshb.stroycontrol.common.ApiException;

import java.util.UUID;

/**
 * Предоставляет банковскому модулю доступ к ипотечной привязке заявки без передачи сущностей.
 *
 * <p>Чтение доступно только владельцу карточки. Запись выполняется в транзакции импорта под общей
 * блокировкой проекта; таблицы заявки остаются собственностью модуля projects.
 */
public interface MortgageRequests {
    /**
     * Возвращает ID кредитного снимка после проверки владельца карточки.
     *
     * @param projectId идентификатор карточки дома
     * @param actorId аутентифицированный владелец
     * @return ID снимка либо null, если он не подключён
     * @throws ApiException с PROJECT_NOT_FOUND для отсутствующей карточки или другого участника
     */
    UUID mortgage(UUID projectId, UUID actorId);

    /**
     * Проверяет выбор сельской ипотеки владельцем карточки.
     *
     * @param projectId идентификатор карточки дома
     * @param actorId аутентифицированный владелец
     * @return true при источнике RSHB_RURAL_MORTGAGE
     * @throws ApiException с PROJECT_NOT_FOUND для отсутствующей карточки или другого участника
     */
    boolean mortgageSelected(UUID projectId, UUID actorId);

    /**
     * Блокирует ипотечный черновик и проверяет его ожидаемую версию.
     *
     * <p>Требует активной транзакции; блокировка проекта удерживается до её завершения.
     *
     * @param projectId идентификатор карточки дома
     * @param actorId аутентифицированный владелец
     * @param expected ожидаемая версия черновика
     * @return ID уже подключённого снимка либо null
     * @throws ApiException с PROJECT_NOT_FOUND, REQUEST_LOCKED, STALE_REQUEST_VERSION либо
     *     VALIDATION_ERROR при другом источнике финансирования
     * @throws IllegalTransactionStateException при отсутствии активной транзакции
     */
    UUID lockMortgage(UUID projectId, UUID actorId, long expected);

    /**
     * Привязывает сохранённый снимок и увеличивает версию ипотечного черновика.
     *
     * <p>Вызывается после {@link #lockMortgage} в той же активной транзакции. Вызывающий банковский
     * адаптер отвечает за принадлежность снимка указанным проекту и владельцу.
     *
     * @param projectId идентификатор заблокированной карточки
     * @param snapshotId ID сохранённого снимка банковского модуля
     * @throws IllegalTransactionStateException при отсутствии активной транзакции
     */
    void attachMortgage(UUID projectId, UUID snapshotId);
}
