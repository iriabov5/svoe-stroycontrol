package ru.rshb.stroycontrol.guidance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ru.rshb.stroycontrol.acceptance.Acceptance.Cycle;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.projects.ConstructionContexts.Context;
import ru.rshb.stroycontrol.projects.ProjectAccess;
import ru.rshb.stroycontrol.stages.StageStatus;
import ru.rshb.stroycontrol.stages.StageView;
import ru.rshb.stroycontrol.stages.StageWorkType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Проверяет наблюдаемые переходы, персональные полномочия и независимость этапов. */
class GuidanceCalculationTest {
    private final UUID project = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-27T00:00:00Z");

    /** Создаёт отдельный этап для сценария без общей изменяемой фикстуры. */
    private StageView stage(StageWorkType type, StageStatus state, int position) {
        return new StageView(
                UUID.randomUUID(),
                project,
                0,
                position,
                type.name(),
                type,
                state,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0);
    }

    /** Формирует права текущего участника без банковских источников. */
    private Context context(
            boolean owner, boolean works, boolean closed, boolean contract, Instant completed) {
        var access =
                new ProjectAccess(
                        project,
                        owner,
                        !owner,
                        "PROPOSAL_REQUIRED",
                        UUID.randomUUID(),
                        contract ? UUID.randomUUID() : null,
                        closed,
                        false,
                        works,
                        works,
                        false,
                        null);
        return new Context(
                project, "Дом", access, completed, null, null, null, null, null, null, null);
    }

    /** Находит точку этапа в готовом ответе. */
    private GuidanceView.Point point(GuidanceView view, UUID stage) {
        return view.groups().stream()
                .flatMap(g -> g.points().stream())
                .filter(p -> stage.equals(p.stageId()))
                .findFirst()
                .orElseThrow();
    }

    @ParameterizedTest(name = "{0}, цикл={1}, фото={2} → {3}")
    @CsvSource({
        "PLANNED,NONE,false,START_REQUIRED,WAITING_CONTRACTOR",
        "IN_PROGRESS,NONE,false,EVIDENCE_REQUIRED,WAITING_CONTRACTOR",
        "IN_PROGRESS,NONE,true,EVIDENCE_PUBLISHED,COMPLETED",
        "WORK_COMPLETED,NONE,true,SUBMISSION_REQUIRED,WAITING_CONTRACTOR",
        "WORK_COMPLETED,OPEN,true,ACCEPTANCE_OPEN,WAITING_CUSTOMER",
        "WORK_COMPLETED,RETURNED,true,CORRECTIONS_REQUIRED,WAITING_CONTRACTOR",
        "WORK_COMPLETED,ACCEPTED,true,STAGE_ACCEPTED,COMPLETED"
    })
    @DisplayName("Точка различает публикацию, сдачу, возврат и приёмку")
    void lifecycle(
            StageStatus state, String cycleState, boolean photo, String reason, String expected) {
        var s = stage(StageWorkType.FOUNDATION, state, 1);
        Map<UUID, Cycle> cycles =
                cycleState.equals("NONE")
                        ? Map.of()
                        : Map.of(
                                s.id(),
                                new Cycle(UUID.randomUUID(), project, s.id(), 2, cycleState));
        var view =
                GuidanceCalculation.calculate(
                        context(false, true, false, true, null),
                        List.of(s),
                        cycles,
                        photo ? Set.of(s.id()) : Set.of(),
                        now);
        var point = point(view, s.id());
        assertThat(point.reasonCode()).isEqualTo(reason);
        assertThat(point.status()).isEqualTo(expected);
        if (cycleState.equals("OPEN")) assertThat(point.canAct()).isFalse();
        if (cycleState.equals("RETURNED")) assertThat(point.canAct()).isTrue();
    }

    @Test
    @DisplayName("Действие подрядчика имеет приоритет перед ожиданием заказчика по первому этапу")
    void personalPriorityAndAllStages() {
        var first = stage(StageWorkType.FOUNDATION, StageStatus.WORK_COMPLETED, 1);
        var second = stage(StageWorkType.FOUNDATION, StageStatus.IN_PROGRESS, 2);
        var cycle = new Cycle(UUID.randomUUID(), project, first.id(), 1, "OPEN");
        var view =
                GuidanceCalculation.calculate(
                        context(false, true, false, true, null),
                        List.of(second, first),
                        Map.of(first.id(), cycle),
                        Set.of(),
                        now);
        assertThat(view.groups().getFirst().points()).hasSize(2);
        assertThat(view.primaryPointId()).isEqualTo("FOUNDATION:" + second.id());
        assertThat(point(view, first.id()).target().submissionId()).isEqualTo(cycle.id());
    }

    @Test
    @DisplayName("Сотрудник без Работ видит состояние без собственной команды")
    void limitedPermissions() {
        var s = stage(StageWorkType.UTILITIES, StageStatus.IN_PROGRESS, 1);
        var view =
                GuidanceCalculation.calculate(
                        context(false, false, false, true, null),
                        List.of(s),
                        Map.of(),
                        Set.of(),
                        now);
        assertThat(view.actionablePointCount()).isZero();
        assertThat(point(view, s.id()).action()).isNull();
    }

    @ParameterizedTest(name = "договор={0}, закрыта={1} → {2}")
    @CsvSource({"false,false,CONTRACT_REQUIRED", "true,true,PREPARATION_CLOSED"})
    @DisplayName("Подготовка не предлагает запрещённое начало работ")
    void preparation(boolean contract, boolean closed, String reason) {
        var s = stage(StageWorkType.FOUNDATION, StageStatus.PLANNED, 1);
        var view =
                GuidanceCalculation.calculate(
                        context(false, true, closed, contract, null),
                        List.of(s),
                        Map.of(),
                        Set.of(),
                        now);
        assertThat(point(view, s.id()).reasonCode()).isEqualTo(reason);
        assertThat(view.actionablePointCount()).isZero();
    }

    @Test
    @DisplayName("Пустой план объясняет подготовку без выдуманного фундамента")
    void emptyPlan() {
        var view =
                GuidanceCalculation.calculate(
                        context(true, false, false, false, null),
                        List.of(),
                        Map.of(),
                        Set.of(),
                        now);
        assertThat(view.groups()).hasSize(3);
        assertThat(view.groups().getFirst().points()).isEmpty();
        assertThat(view.groups().getLast().points().getFirst().reasonCode())
                .isEqualTo("PREPARATION");
    }

    @Test
    @DisplayName("Непринятая отделка блокирует передачу всего дома")
    void fullPlanReadiness() {
        var f = stage(StageWorkType.FOUNDATION, StageStatus.WORK_COMPLETED, 1);
        var other = stage(StageWorkType.OTHER, StageStatus.WORK_COMPLETED, 2);
        var accepted = new Cycle(UUID.randomUUID(), project, f.id(), 1, "ACCEPTED");
        var view =
                GuidanceCalculation.calculate(
                        context(true, false, false, true, null),
                        List.of(f, other),
                        Map.of(f.id(), accepted),
                        Set.of(),
                        now);
        var h = view.groups().getLast().points().getFirst();
        assertThat(h.status()).isEqualTo("BLOCKED");
        assertThat(h.acceptedStageCount()).isEqualTo(1);
        assertThat(h.totalStageCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("После приёмки всего плана заказчик завершает дом, затем действия исчезают")
    void handoverAndCompletion() {
        var s = stage(StageWorkType.UTILITIES, StageStatus.WORK_COMPLETED, 1);
        var cycles = Map.of(s.id(), new Cycle(UUID.randomUUID(), project, s.id(), 2, "ACCEPTED"));
        var view =
                GuidanceCalculation.calculate(
                        context(true, false, false, true, null), List.of(s), cycles, Set.of(), now);
        var handover = view.groups().getLast().points().getFirst();
        assertThat(handover.canAct()).isTrue();
        assertThat(handover.target().stageId()).isNull();
        assertThat(handover.target().section()).isEqualTo("COMPLETION");
        var done =
                GuidanceCalculation.calculate(
                        context(true, false, false, true, now), List.of(s), cycles, Set.of(), now);
        assertThat(done.actionablePointCount()).isZero();
        assertThat(done.completedAt()).isEqualTo(now);
        assertThat(point(done, s.id()).target().section()).isEqualTo("ACCEPTANCE");
    }

    @Test
    @DisplayName("Невозможный цикл приёмки работающего этапа не выдаётся за успех")
    void inconsistentSources() {
        var s = stage(StageWorkType.FOUNDATION, StageStatus.IN_PROGRESS, 1);
        var c = context(true, false, false, true, null);
        var cycles = Map.of(s.id(), new Cycle(UUID.randomUUID(), project, s.id(), 1, "ACCEPTED"));
        var stages = List.of(s);
        Set<UUID> confirmed = Set.of();
        assertThatThrownBy(() -> GuidanceCalculation.calculate(c, stages, cycles, confirmed, now))
                .isInstanceOf(ApiException.class);
    }
}
