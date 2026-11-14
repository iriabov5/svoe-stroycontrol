package ru.rshb.stroycontrol.stages;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Этап и его инварианты; изменение статуса не изменяет плановые поля. */
@Entity
@Table(name = "construction_stages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ConstructionStage {
    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 16)
    private StageWorkType workType;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StageStatus status = StageStatus.PLANNED;

    @Column(name = "planned_start")
    private LocalDate plannedStart;

    @Column(name = "planned_finish")
    private LocalDate plannedFinish;

    @Column(name = "actual_start")
    private LocalDate actualStart;

    @Column(name = "actual_finish")
    private LocalDate actualFinish;

    @Version
    private long version;

    @Column(name = "source_proposal_stage_id")
    private UUID sourceProposalStageId;

    @Column(name = "expected_result")
    private String expectedResult;

    /**
     * Связывает рабочий этап с исходным плановым этапом согласованной сметы.
     *
     * <p>Вызывается при установке плана в транзакции согласования.
     *
     * @param id идентификатор планового этапа согласованной сметы
     * @param result ожидаемый результат этого этапа
     */
    void source(UUID id, String result) {
        sourceProposalStageId = id;
        expectedResult = result;
    }

    /**
     * Создаёт ещё не начатый этап из проверенного плана с собственной идентичностью.
     *
     * @param projectId карточка дома, к которой относится этап
     * @param position положительная позиция в полном порядке этапов
     * @param plan проверенное название, вид работ и плановые даты
     * @return новый этап PLANNED без фактических дат и с начальной версией
     */
    static ConstructionStage create(UUID projectId, int position, StagePlan plan) {
        return ConstructionStage.builder()
                .projectId(projectId)
                .position(position)
                .name(plan.name())
                .workType(plan.workType())
                .plannedStart(plan.plannedStart())
                .plannedFinish(plan.plannedFinish())
                .build();
    }

    /**
     * Заменяет плановые поля, сохраняя статус и фактические даты этапа.
     *
     * <p>Допустимость редактирования и версию проверяет сервис перед вызовом.
     *
     * @param plan новые название, вид работ и плановые даты
     */
    void updatePlan(StagePlan plan) {
        name = plan.name();
        workType = plan.workType();
        plannedStart = plan.plannedStart();
        plannedFinish = plan.plannedFinish();
    }

    /**
     * Проверяет ожидаемую версию перед изменением этапа.
     *
     * @param expected ожидаемая версия до изменения
     * @throws ApiException с VALIDATION_ERROR для отсутствующей версии или STALE_STAGE_VERSION при
     *     несовпадении
     */
    void requireVersion(Long expected) {
        if (expected == null || expected < 0) throw new ApiException(ApiError.VALIDATION_ERROR);
        if (version != expected) throw new ApiException(ApiError.STALE_STAGE_VERSION);
    }

    /**
     * Устанавливает позицию этапа в проверенном полном порядке.
     *
     * <p>Сервис проверяет состав и версии всего плана до первого изменения позиции.
     *
     * @param position позиция этапа в полном порядке
     */
    void moveTo(int position) {
        this.position = position;
    }

    /**
     * Начинает ещё не начатый этап указанной датой.
     *
     * <p>Повтор после успешного старта сохраняет первоначальную дату независимо от старой версии.
     *
     * @param expected ожидаемая версия до изменения
     * @param today текущая календарная дата общих часов приложения
     * @throws ApiException при неверной или устаревшей версии
     */
    void start(Long expected, LocalDate today) {
        if (actualStart != null) return;
        requireVersion(expected);
        actualStart = today;
        status = StageStatus.IN_PROGRESS;
    }

    /**
     * Завершает начатый этап, сохраняя первоначальные даты при повторе.
     *
     * <p>Дата завершения не может быть раньше фактического начала.
     *
     * @param expected ожидаемая версия до изменения
     * @param today текущая календарная дата общих часов приложения
     * @throws ApiException с INVALID_STAGE_TRANSITION, VALIDATION_ERROR или STALE_STAGE_VERSION
     */
    void complete(Long expected, LocalDate today) {
        if (actualFinish != null) return;
        requireVersion(expected);
        if (actualStart == null || today.isBefore(actualStart))
            throw new ApiException(ApiError.INVALID_STAGE_TRANSITION);
        actualFinish = today;
        status = StageStatus.WORK_COMPLETED;
    }

    /**
     * Формирует безопасную модель этапа с вычисленными сроками.
     *
     * <p>Просрочка незафиксированного действия определяется относительно today, а отклонение
     * завершённого — относительно фактической даты. Состояние сущности не меняется.
     *
     * @param today текущая календарная дата общих часов приложения
     * @return этап с актуальной версией, датами и вычисленными просрочками
     */
    StageView view(LocalDate today) {
        return new StageView(
                id,
                projectId,
                version,
                position,
                name,
                workType,
                status,
                plannedStart,
                plannedFinish,
                actualStart,
                actualFinish,
                overdue(plannedStart, actualStart, today),
                overdue(plannedFinish, actualFinish, today),
                late(plannedStart, actualStart),
                late(plannedFinish, actualFinish));
    }

    /**
     * Считает текущую просрочку только для ещё не зафиксированного действия.
     *
     * @param plan плановая дата либо null, если срок не задан
     * @param actual фактическая дата либо null до фиксации действия
     * @param today текущая календарная дата общих часов приложения
     * @return число дней текущей просрочки или ноль
     */
    private static long overdue(LocalDate plan, LocalDate actual, LocalDate today) {
        return actual == null ? late(plan, today) : 0;
    }

    /**
     * Считает положительное отставание фактической даты от плана.
     *
     * <p>Отсутствующие даты дают ноль, раннее выполнение не создаёт отрицательной просрочки.
     *
     * @param plan плановая дата либо null, если срок не задан
     * @param actual фактическая дата либо null до фиксации действия
     * @return число дней отставания или ноль
     */
    private static long late(LocalDate plan, LocalDate actual) {
        return plan == null || actual == null
                ? 0
                : Math.max(0, ChronoUnit.DAYS.between(plan, actual));
    }
}
