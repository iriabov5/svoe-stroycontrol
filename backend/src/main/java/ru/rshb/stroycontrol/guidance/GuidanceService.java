package ru.rshb.stroycontrol.guidance;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.acceptance.Acceptance;
import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.journal.Journal;
import ru.rshb.stroycontrol.projects.ConstructionContexts;
import ru.rshb.stroycontrol.projects.ConstructionContexts.Context;
import ru.rshb.stroycontrol.stages.StageView;
import ru.rshb.stroycontrol.stages.Stages;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Собирает персональные проекции в стабильном снимке PostgreSQL; GET не создаёт данные. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class GuidanceService {
    private static final String PROFILE_SOURCE = "PROFILE";
    private final ConstructionContexts contexts;
    private final Stages stages;
    private final Acceptance acceptance;
    private final Journal journal;
    private final Clock clock;

    /**
     * Проверяет карточку до чтения источников.
     *
     * @param project карточка
     * @param actor текущий участник
     * @return актуальные точки
     */
    GuidanceView gates(UUID project, UUID actor) {
        var context = contexts.get(project, actor);
        return calculate(List.of(context)).get(project);
    }

    /**
     * Читает весь доступный набор без клиентского запроса на каждый объект.
     *
     * @param actor текущий аккаунт
     * @return уникальные объекты с собственным доступным действием
     */
    ConstructionActionsView actions(AccountView actor) {
        var allowed = contexts.all(actor);
        var results = calculate(allowed);
        var items = new ArrayList<ConstructionActionsView.Item>();
        for (var c : allowed) {
            var view = results.get(c.projectId());
            if (view.actionablePointCount() == 0) continue;
            var point =
                    view.groups().stream()
                            .flatMap(g -> g.points().stream())
                            .filter(p -> p.id().equals(view.primaryPointId()))
                            .findFirst()
                            .orElseThrow();
            items.add(
                    new ConstructionActionsView.Item(
                            c.projectId(), c.name(), point.id(), point.target()));
        }
        items.sort(
                Comparator.comparing((ConstructionActionsView.Item i) ->
                                i.projectName().trim().toLowerCase(Locale.ROOT))
                        .thenComparing(i -> i.projectId().toString()));
        return new ConstructionActionsView(clock.instant(), items.size(), List.copyOf(items));
    }

    /**
     * Сопоставляет источники паспорта, не дублируя заявку или план.
     *
     * @param project доступная карточка
     * @param actor текущий аккаунт
     * @return известные значения и их происхождение
     */
    ConstructionProfileView profile(UUID project, UUID actor) {
        var c = contexts.get(project, actor);
        var plan = stages.plans(Set.of(project));
        LocalDate finish =
                plan.isEmpty() || plan.stream().anyMatch(s -> s.plannedFinish() == null)
                        ? null
                        : plan.stream()
                                .map(StageView::plannedFinish)
                                .max(Comparator.naturalOrder())
                                .orElse(null);
        var sources = new HashMap<String, String>();
        sources.put("areaM2", c.areaM2() == null ? null : c.requestSource());
        sources.put("floors", c.floors() == null ? null : c.requestSource());
        sources.put("desiredFinish", c.desiredFinish() == null ? null : c.requestSource());
        sources.put("wallMaterial", c.wallMaterial() == null ? null : PROFILE_SOURCE);
        sources.put("foundationType", c.foundationType() == null ? null : PROFILE_SOURCE);
        sources.put("roofType", c.roofType() == null ? null : PROFILE_SOURCE);
        String finishSource = null;
        if (finish != null) {
            finishSource =
                    "LEGACY".equals(c.access().processMode()) ? "LEGACY_PLAN" : "AGREED_PLAN";
        }
        sources.put("plannedFinish", finishSource);
        return new ConstructionProfileView(
                project,
                c.areaM2(),
                c.floors(),
                c.wallMaterial(),
                c.foundationType(),
                c.roofType(),
                finish,
                c.desiredFinish(),
                sources);
    }

    /**
     * Читает планы, циклы и подтверждения наборами, затем выполняет чистый расчёт.
     *
     * @param allowed авторизованные контексты в одном снимке
     * @return точки по карточке
     */
    private Map<UUID, GuidanceView> calculate(List<Context> allowed) {
        if (allowed.isEmpty()) return Map.of();
        Set<UUID> ids = allowed.stream().map(Context::projectId).collect(Collectors.toSet());
        var plans = stages.plans(ids).stream().collect(Collectors.groupingBy(StageView::projectId));
        var cycles =
                acceptance.latestCycles(ids).values().stream()
                        .collect(Collectors.groupingBy(Acceptance.Cycle::projectId));
        var confirmed = journal.confirmedStages(ids);
        var result = new HashMap<UUID, GuidanceView>();
        for (var c : allowed) {
            var current =
                    cycles.getOrDefault(c.projectId(), List.of()).stream()
                            .collect(Collectors.toMap(Acceptance.Cycle::stageId, v -> v));
            result.put(
                    c.projectId(),
                    GuidanceCalculation.calculate(
                            c,
                            plans.getOrDefault(c.projectId(), List.of()),
                            current,
                            confirmed,
                            clock.instant()));
        }
        return result;
    }
}
