package ru.rshb.stroycontrol.guidance;

import static ru.rshb.stroycontrol.accounts.AccountRole.CONTRACTOR;
import static ru.rshb.stroycontrol.accounts.AccountRole.CUSTOMER;

import lombok.RequiredArgsConstructor;

import ru.rshb.stroycontrol.acceptance.Acceptance;
import ru.rshb.stroycontrol.acceptance.Acceptance.Cycle;
import ru.rshb.stroycontrol.accounts.AccountRole;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.projects.ConstructionContexts.Context;
import ru.rshb.stroycontrol.stages.StageStatus;
import ru.rshb.stroycontrol.stages.StageView;
import ru.rshb.stroycontrol.stages.StageWorkType;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Чистый расчёт полного цикла; единый алгоритм для карточки и сводки объектов. */
final class GuidanceCalculation {
    private static final String SECTION_ACCEPTANCE = "ACCEPTANCE";
    private static final String SECTION_PROJECT = "PROJECT";
    private static final String SECTION_COMPLETION = "COMPLETION";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final String STATUS_WAITING_CONTRACTOR = "WAITING_CONTRACTOR";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private GuidanceCalculation() {}

    /**
     * Собирает точки из уже проверенных источников без чтения и записи БД.
     *
     * @param context актуальные права и безопасный контекст
     * @param stages весь исполняемый план
     * @param cycles последние циклы только этого проекта
     * @param confirmed этапы с подходящим опубликованным изображением
     * @param now время расчёта
     * @return персональный навигатор
     * @throws ApiException если источники противоречат существующему процессу
     */
    static GuidanceView calculate(
            Context context,
            List<StageView> stages,
            Map<UUID, Cycle> cycles,
            Set<UUID> confirmed,
            Instant now) {
        validate(context, stages, cycles);
        var progress =
                Acceptance.Readiness.from(
                        stages.stream().map(StageView::id).collect(Collectors.toSet()),
                        cycles.values());
        if (context.completedAt() != null && !progress.ready())
            throw new ApiException(ApiError.GUIDANCE_READ_FAILED);
        var groups =
                List.of(
                        group(
                                context,
                                stages,
                                cycles,
                                confirmed,
                                StageWorkType.FOUNDATION,
                                "Перед заливкой фундамента"),
                        group(
                                context,
                                stages,
                                cycles,
                                confirmed,
                                StageWorkType.UTILITIES,
                                "Перед закрытием инженерных сетей"),
                        new GuidanceView.Group(
                                "HANDOVER",
                                "Перед передачей дома",
                                List.of(handover(context, progress))));
        var points = groups.stream().flatMap(g -> g.points().stream()).toList();
        var primary =
                points.stream()
                        .min(Comparator.comparingInt(GuidanceCalculation::priority))
                        .orElseThrow();
        return new GuidanceView(
                context.projectId(),
                now,
                context.completedAt(),
                groups,
                primary.id(),
                (int) points.stream().filter(GuidanceView.Point::canAct).count());
    }

    /** Отвергает неверную принадлежность и невозможный цикл приёмки до вывода подсказки. */
    private static void validate(Context c, List<StageView> stages, Map<UUID, Cycle> cycles) {
        var states = stages.stream().collect(Collectors.toMap(StageView::id, StageView::status));
        if (stages.stream().anyMatch(s -> !s.projectId().equals(c.projectId())))
            throw new ApiException(ApiError.GUIDANCE_READ_FAILED);
        for (var cycle : cycles.values()) {
            if (!cycle.projectId().equals(c.projectId())
                    || states.get(cycle.stageId()) != StageStatus.WORK_COMPLETED
                    || !Set.of("OPEN", "RETURNED", "ACCEPTED").contains(cycle.state()))
                throw new ApiException(ApiError.GUIDANCE_READ_FAILED);
        }
    }

    /** Сохраняет все этапы выбранного вида, а не только первый. */
    private static GuidanceView.Group group(
            Context c,
            List<StageView> stages,
            Map<UUID, Cycle> cycles,
            Set<UUID> confirmed,
            StageWorkType kind,
            String title) {
        var points =
                stages.stream()
                        .filter(s -> s.workType() == kind)
                        .sorted(
                                Comparator.comparingInt(StageView::position)
                                        .thenComparing(s -> s.id().toString()))
                        .map(s ->
                                point(
                                        c,
                                        kind.name() + ":" + s.id(),
                                        s.id(),
                                        title + " · " + s.name(),
                                        state(c, s, cycles.get(s.id()), confirmed.contains(s.id())),
                                        cycles.get(s.id()),
                                        null))
                        .toList();
        return new GuidanceView.Group(kind.name(), title, points);
    }

    /** Применяет нормативный приоритет завершения, циклов, допуска к работам и подтверждений. */
    private static Reason state(Context c, StageView s, Cycle cycle, boolean confirmed) {
        if (c.completedAt() != null) return Reason.PROJECT_COMPLETED;
        if (c.access().preparationClosed()) return Reason.PREPARATION_CLOSED;
        if (cycle != null)
            return switch (cycle.state()) {
                case "ACCEPTED" -> Reason.STAGE_ACCEPTED;
                case "OPEN" -> Reason.ACCEPTANCE_OPEN;
                case "RETURNED" -> Reason.CORRECTIONS_REQUIRED;
                default -> throw new ApiException(ApiError.GUIDANCE_READ_FAILED);
            };
        if (!"LEGACY".equals(c.access().processMode()) && c.access().approvedContractId() == null)
            return Reason.CONTRACT_REQUIRED;
        return switch (s.status()) {
            case PLANNED -> Reason.START_REQUIRED;
            case IN_PROGRESS -> confirmed ? Reason.EVIDENCE_PUBLISHED : Reason.EVIDENCE_REQUIRED;
            case WORK_COMPLETED -> Reason.SUBMISSION_REQUIRED;
        };
    }

    /** Передача использует готовность всего плана, включая этапы вне двух групп. */
    private static GuidanceView.Point handover(Context c, Acceptance.Readiness progress) {
        Reason reason;
        if (c.completedAt() != null) reason = Reason.PROJECT_COMPLETED;
        else if (c.access().preparationClosed()) reason = Reason.PREPARATION_CLOSED;
        else if (progress.total() == 0) reason = Reason.PREPARATION;
        else reason = progress.ready() ? Reason.COMPLETION_REQUIRED : Reason.ACCEPTANCE_REQUIRED;
        return point(
                c,
                "HANDOVER:" + c.projectId(),
                null,
                "Перед передачей дома",
                reason,
                null,
                progress);
    }

    /** Создаёт только доступное действие; навигация не заменяет авторизацию команды. */
    private static GuidanceView.Point point(
            Context c,
            String id,
            UUID stage,
            String title,
            Reason r,
            Cycle cycle,
            Acceptance.Readiness progress) {
        boolean canAct =
                switch (r) {
                    case ACCEPTANCE_OPEN, COMPLETION_REQUIRED -> c.access().owner();
                    case START_REQUIRED, SUBMISSION_REQUIRED, CORRECTIONS_REQUIRED ->
                            c.access().canRecordWork();
                    case EVIDENCE_REQUIRED -> c.access().canPublishJournal();
                    default -> false;
                };
        var action = canAct ? new GuidanceView.Action(r.actionCode, r.actionLabel) : null;
        var target =
                new GuidanceView.Target(
                        c.projectId(),
                        stage,
                        r == Reason.PROJECT_COMPLETED && stage != null
                                ? SECTION_ACCEPTANCE
                                : r.section,
                        cycle == null ? null : cycle.id());
        String detail = r.detail;
        if (progress != null && r == Reason.ACCEPTANCE_REQUIRED)
            detail =
                    "Принято этапов: %d из %d. Дождитесь приёмки всего плана."
                            .formatted(progress.accepted(), progress.total());
        return new GuidanceView.Point(
                id,
                stage,
                title,
                r.status,
                r.name(),
                detail,
                r.role,
                canAct,
                action,
                target,
                progress == null ? null : progress.accepted(),
                progress == null ? null : progress.total());
    }

    /** Стабильный приоритет: своё действие, ожидание другой стороны, блокировка, итог. */
    private static int priority(GuidanceView.Point p) {
        if (p.canAct()) return 0;
        if (p.status().startsWith("WAITING")) return 1;
        return p.status().equals(STATUS_BLOCKED) ? 2 : 3;
    }

    /** Пользовательские состояния без предположения о качестве работ или решении банка. */
    @RequiredArgsConstructor
    private enum Reason {
        PREPARATION(
                STATUS_BLOCKED,
                null,
                SECTION_PROJECT,
                null,
                null,
                "Подготовьте и согласуйте план строительства. Контрольные точки появятся вместе с этапами."),
        PREPARATION_CLOSED(
                STATUS_BLOCKED,
                null,
                SECTION_PROJECT,
                null,
                null,
                "Подготовка этой карточки закрыта. История доступна для чтения."),
        CONTRACT_REQUIRED(
                STATUS_BLOCKED,
                CONTRACTOR,
                SECTION_PROJECT,
                null,
                null,
                "Для начала работ согласуйте договор в подготовке строительства."),
        START_REQUIRED(
                STATUS_WAITING_CONTRACTOR,
                CONTRACTOR,
                "WORK",
                "OPEN_START",
                "Перейти к началу работ",
                "Подрядчику нужно начать этап в существующем плане."),
        EVIDENCE_REQUIRED(
                STATUS_WAITING_CONTRACTOR,
                CONTRACTOR,
                "JOURNAL",
                "OPEN_JOURNAL",
                "Добавить подтверждения",
                "Опубликуйте в журнале запись с загруженной фотографией до скрытия результата работ."),
        EVIDENCE_PUBLISHED(
                STATUS_COMPLETED,
                null,
                "JOURNAL",
                null,
                null,
                "Подтверждения добавлены. Публикация фотографии не означает приёмку или проверку качества работ."),
        SUBMISSION_REQUIRED(
                STATUS_WAITING_CONTRACTOR,
                CONTRACTOR,
                SECTION_ACCEPTANCE,
                "OPEN_SUBMISSION",
                "Подготовить сдачу этапа",
                "Работы завершены подрядчиком. Подготовьте и отправьте сдачу с подтверждениями."),
        ACCEPTANCE_OPEN(
                "WAITING_CUSTOMER",
                CUSTOMER,
                SECTION_ACCEPTANCE,
                "OPEN_ACCEPTANCE",
                "Проверить сдачу этапа",
                "Заказчику нужно проверить открытую сдачу и принять этап или вернуть с замечаниями."),
        CORRECTIONS_REQUIRED(
                STATUS_WAITING_CONTRACTOR,
                CONTRACTOR,
                SECTION_ACCEPTANCE,
                "OPEN_CORRECTIONS",
                "Исправить замечания и сдать повторно",
                "Заказчик вернул сдачу. Изучите замечания, добавьте подтверждения исправлений и подготовьте повторную сдачу."),
        STAGE_ACCEPTED(
                STATUS_COMPLETED,
                null,
                SECTION_ACCEPTANCE,
                null,
                null,
                "Этап принят. Повторное решение заказчика не требуется."),
        ACCEPTANCE_REQUIRED(
                STATUS_BLOCKED,
                null,
                SECTION_COMPLETION,
                null,
                null,
                "Дождитесь приёмки всех этапов."),
        COMPLETION_REQUIRED(
                "WAITING_CUSTOMER",
                CUSTOMER,
                SECTION_COMPLETION,
                "OPEN_COMPLETION",
                "Подтвердить завершение дома",
                "Все этапы приняты. Заказчик может явно подтвердить завершение строительства."),
        PROJECT_COMPLETED(
                STATUS_COMPLETED,
                null,
                SECTION_COMPLETION,
                null,
                null,
                "Строительство завершено. История и подтверждения доступны для чтения.");
        private final String status;
        private final AccountRole role;
        private final String section;
        private final String actionCode;
        private final String actionLabel;
        private final String detail;
    }
}
