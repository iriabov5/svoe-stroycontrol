package ru.rshb.stroycontrol.proposals;

import ru.rshb.stroycontrol.common.ActorSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Ответ HTTP по версии сметы или договора. Конкретные поля зависят от вида документа. */
sealed interface DocumentView permits ProposalView, ContractView {}

/**
 * Иллюстрация сметы без позиции хранения.
 *
 * @param url HTTPS-ссылка
 * @param caption подпись иллюстрации
 */
record ProposalImageView(String url, String caption) {}

/**
 * Плановый этап сметы в составе ответа.
 *
 * @param id идентификатор планового этапа
 * @param name название этапа
 * @param workType вид работ
 * @param expectedResult ожидаемый результат или пустая строка черновика
 * @param plannedStart плановое начало или null
 * @param plannedFinish плановое завершение или null
 */
record ProposalStageView(
        UUID id,
        String name,
        String workType,
        String expectedResult,
        LocalDate plannedStart,
        LocalDate plannedFinish) {}

/**
 * Решение заказчика без внутренних связей версии документа.
 *
 * @param actorId идентификатор владельца, принявшего решение
 * @param decision APPROVED, CHANGES_REQUESTED или REJECTED
 * @param comment пояснение или null, если оно необязательно
 * @param actorSnapshot имя автора решения на момент записи
 * @param decidedAt время решения в UTC
 */
record DecisionView(
        UUID actorId,
        String decision,
        String comment,
        Instant decidedAt,
        ActorSnapshot actorSnapshot) {}

/**
 * Версия сметы с иллюстрациями, планом и решением.
 *
 * <p>Ссылка на заявку в ответ не входит. Валюта итога всегда RUB.
 *
 * @param id идентификатор версии сметы
 * @param projectId идентификатор карточки дома
 * @param revisionNo номер редакции с единицы
 * @param state DRAFT, SUBMITTED, APPROVED, CHANGES_REQUESTED или REJECTED
 * @param version версия для изменяющих команд
 * @param createdBy идентификатор подрядчика, создавшего редакцию
 * @param createdAt время создания редакции в UTC
 * @param createdBySnapshot сохранённый создатель версии
 * @param submittedById фактический отправитель либо null для старого документа
 * @param submittedBySnapshot подпись отправителя либо null для старого документа
 * @param submittedAt время первой отправки или null
 * @param estimateText описание работ и цен
 * @param termsText дополнительные условия или null
 * @param totalPrice итог или null в неполном черновике
 * @param images иллюстрации в порядке показа
 * @param stages плановые этапы в порядке выполнения
 * @param currency валюта итога, в контракте RUB
 * @param decision решение заказчика или null
 */
record ProposalView(
        UUID id,
        UUID projectId,
        int revisionNo,
        String state,
        long version,
        UUID createdBy,
        Instant createdAt,
        Instant submittedAt,
        ActorSnapshot createdBySnapshot,
        UUID submittedById,
        ActorSnapshot submittedBySnapshot,
        String estimateText,
        String termsText,
        BigDecimal totalPrice,
        List<ProposalImageView> images,
        List<ProposalStageView> stages,
        String currency,
        DecisionView decision)
        implements DocumentView {}

/**
 * Версия договора с решением заказчика.
 *
 * @param id идентификатор версии договора
 * @param projectId идентификатор карточки дома
 * @param revisionNo номер редакции с единицы
 * @param state DRAFT, SUBMITTED, APPROVED или CHANGES_REQUESTED
 * @param version версия для изменяющих команд
 * @param createdBy идентификатор подрядчика, создавшего редакцию
 * @param createdAt время создания редакции в UTC
 * @param createdBySnapshot сохранённый создатель версии
 * @param submittedById фактический отправитель либо null для старого документа
 * @param submittedBySnapshot подпись отправителя либо null для старого документа
 * @param submittedAt время первой отправки или null
 * @param proposalId идентификатор согласованной сметы-основания
 * @param contractText текст договора
 * @param decision решение заказчика или null
 */
record ContractView(
        UUID id,
        UUID projectId,
        int revisionNo,
        String state,
        long version,
        UUID createdBy,
        Instant createdAt,
        Instant submittedAt,
        ActorSnapshot createdBySnapshot,
        UUID submittedById,
        ActorSnapshot submittedBySnapshot,
        UUID proposalId,
        String contractText,
        DecisionView decision)
        implements DocumentView {}
