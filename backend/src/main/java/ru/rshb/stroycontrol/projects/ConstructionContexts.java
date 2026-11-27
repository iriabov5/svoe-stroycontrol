package ru.rshb.stroycontrol.projects;

import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.ApiException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Авторизованные безопасные источники навигатора, без личных банковских данных. */
public interface ConstructionContexts {
    /**
     * Проверяет доступ до чтения источников; сущности не покидают модуль.
     *
     * @param project карточка
     * @param actor текущий аккаунт
     * @return неизменяемый контекст
     * @throws ApiException если карточка отсутствует или недоступна
     */
    Context get(UUID project, UUID actor);

    /**
     * Собирает контексты только доступных карточек в текущем снимке транзакции.
     *
     * @param actor текущий аккаунт
     * @return контексты без скрытых карточек
     */
    List<Context> all(AccountView actor);

    /**
     * Безопасные источники и проектные полномочия; незаполненные характеристики равны null.
     *
     * @param projectId карточка
     * @param name название
     * @param access действующие полномочия
     * @param completedAt подтверждённое завершение либо null
     * @param areaM2 площадь из заявки
     * @param floors этажность из заявки
     * @param desiredFinish пожелание из заявки
     * @param requestSource REQUEST_DRAFT, REQUEST_SUBMITTED либо null
     * @param wallMaterial материал стен
     * @param foundationType тип фундамента
     * @param roofType тип кровли
     */
    record Context(
            UUID projectId,
            String name,
            ProjectAccess access,
            Instant completedAt,
            BigDecimal areaM2,
            Integer floors,
            LocalDate desiredFinish,
            String requestSource,
            String wallMaterial,
            String foundationType,
            String roofType) {}
}
