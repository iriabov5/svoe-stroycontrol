package ru.rshb.stroycontrol.acceptance;

import ru.rshb.stroycontrol.common.ActorSnapshot;
import ru.rshb.stroycontrol.journal.Journal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Сохранённая сдача и единственное решение заказчика; повтор не вычисляет заново автора или состав.
 *
 * @param id сдача
 * @param projectId проект
 * @param stageId этап
 * @param cycleNumber номер цикла
 * @param summary итог работ
 * @param state OPEN, ACCEPTED или RETURNED
 * @param version версия решения
 * @param evidence снимки выбранных записей
 * @param authorSnapshot отправитель сдачи
 * @param createdAt время сдачи
 * @param decision ACCEPT, RETURN либо null
 * @param comment замечание либо null
 * @param decidedBy заказчик либо null
 * @param decidedAt время решения либо null
 */
public record SubmissionView(
        UUID id,
        UUID projectId,
        UUID stageId,
        int cycleNumber,
        String summary,
        String state,
        long version,
        List<Journal.Evidence> evidence,
        ActorSnapshot authorSnapshot,
        Instant createdAt,
        String decision,
        String comment,
        ActorSnapshot decidedBy,
        Instant decidedAt) {}
