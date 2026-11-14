package ru.rshb.stroycontrol.stages;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Этап строительства с вычисленными относительно текущей даты задержками.
 *
 * @param id идентификатор этапа
 * @param projectId идентификатор карточки дома
 * @param version версия для изменяющих команд
 * @param position позиция в плане с единицы
 * @param name название этапа
 * @param workType вид работ
 * @param status состояние выполнения работ
 * @param plannedStart плановый старт или null
 * @param plannedFinish плановый финиш или null
 * @param actualStart фактический старт или null
 * @param actualFinish фактический финиш или null
 * @param startOverdueDays текущая просрочка старта в днях
 * @param finishOverdueDays текущая просрочка финиша в днях
 * @param startLateDays фактическое опоздание старта в днях
 * @param finishLateDays фактическое опоздание финиша в днях
 */
public record StageView(
        UUID id,
        UUID projectId,
        long version,
        int position,
        String name,
        StageWorkType workType,
        StageStatus status,
        LocalDate plannedStart,
        LocalDate plannedFinish,
        LocalDate actualStart,
        LocalDate actualFinish,
        long startOverdueDays,
        long finishOverdueDays,
        long startLateDays,
        long finishLateDays) {}
