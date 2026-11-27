package ru.rshb.stroycontrol.acceptance;

import static ru.rshb.stroycontrol.common.PreparationInput.field;
import static ru.rshb.stroycontrol.common.PreparationInput.keys;
import static ru.rshb.stroycontrol.common.PreparationInput.text;
import static ru.rshb.stroycontrol.common.PreparationInput.uuid;
import static ru.rshb.stroycontrol.common.PreparationInput.version;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.banking.BankStageStatus;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.common.CommandFingerprint;
import ru.rshb.stroycontrol.journal.Journal;
import ru.rshb.stroycontrol.organizations.Organizations;
import ru.rshb.stroycontrol.projects.Projects;
import ru.rshb.stroycontrol.stages.StageStatus;
import ru.rshb.stroycontrol.stages.Stages;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Ведёт сдачу и решения заказчика; все изменения упорядочены общей блокировкой проекта. */
@Service
@RequiredArgsConstructor
class AcceptanceService implements Acceptance {
    private static final String FIELD_ENTRY_IDS = "entryIds";
    private static final String FIELD_SUMMARY = "summary";
    private static final String FIELD_IDEMPOTENCY_KEY = "idempotencyKey";
    private static final String FIELD_DECISION = "decision";
    private static final String FIELD_COMMENT = "comment";
    private static final String STATE_ACCEPTED = "ACCEPTED";

    private final SubmissionRepository submissions;
    private final AcceptanceCommandRepository commands;
    private final Projects projects;
    private final Stages stages;
    private final Journal journal;
    private final Organizations organizations;
    private final CommandFingerprint fingerprint;
    private final Clock clock;
    private final BankStageStatus bankStatus;

    /**
     * Сводка этапа не содержит банковских причин или кредитного дела.
     *
     * @param state текущее состояние приёмки
     * @param submissions история циклов
     * @param mortgage выбран ли ипотечный источник
     * @param bankStatus краткий статус банковского обмена либо null
     */
    record Page(
            String state, List<SubmissionView> submissions, String bankStatus, boolean mortgage) {}

    /**
     * Возвращает историю только участнику карточки и этапа.
     *
     * @param actor участник
     * @param project проект
     * @param stage этап
     * @return актуальная история
     */
    @Transactional(readOnly = true)
    Page list(AccountView actor, UUID project, UUID stage) {
        stages.requireParticipant(project, stage, actor.id());
        var all = submissions.findByProjectIdAndStageIdOrderByCycleNumberDesc(project, stage);
        return new Page(
                all.isEmpty() ? "NOT_SUBMITTED" : all.getFirst().getState(),
                all.stream().map(AcceptanceSubmission::view).toList(),
                bankStatus.latest(project, stage),
                "RSHB_RURAL_MORTGAGE".equals(projects.info(project, actor.id()).financingSource()));
    }

    /**
     * Сдаёт завершённый этап с неизменяемым набором записей и хотя бы одной загруженной
     * фотографией.
     *
     * @param actor сотрудник с доступом Работы
     * @param project проект
     * @param stage этап
     * @param body версия этапа, итог, записи и ключ
     * @return новая сдача либо сохранённый первоначальный результат
     * @throws ApiException при чужих данных, конфликте версии или недопустимом состоянии
     */
    @Transactional
    Mutation submit(AccountView actor, UUID project, UUID stage, Map<String, Object> body) {
        var access = projects.lockParticipant(project, actor.id());
        if (!access.canRecordWork()) throw new ApiException(ApiError.FORBIDDEN);
        var stageView = stages.requireParticipant(project, stage, actor.id());
        keys(body, "expectedStageVersion", FIELD_SUMMARY, FIELD_ENTRY_IDS, FIELD_IDEMPOTENCY_KEY);
        var key = uuid(body.get(FIELD_IDEMPOTENCY_KEY));
        var hash = fingerprint.of(stage.toString(), body);
        String op = "SUBMIT:" + stage;
        var old = cached(actor.id(), project, op, key, hash);
        if (old != null) return new Mutation(old, false);
        projects.requireOpen(project, actor.id());
        if (stageView.version() != version(body.get("expectedStageVersion")))
            throw new ApiException(ApiError.STALE_STAGE_VERSION);
        if (stageView.status() != StageStatus.WORK_COMPLETED)
            throw new ApiException(ApiError.INVALID_ACCEPTANCE_STATE);
        var all = submissions.findByProjectIdAndStageIdOrderByCycleNumberDesc(project, stage);
        if (!all.isEmpty() && !all.getFirst().getState().equals("RETURNED"))
            throw new ApiException(ApiError.INVALID_ACCEPTANCE_STATE);
        String summary = text(body.get(FIELD_SUMMARY), 5000);
        field(summary != null, FIELD_SUMMARY, "Опишите итог работ");
        field(
                body.get(FIELD_ENTRY_IDS) instanceof List<?>,
                FIELD_ENTRY_IDS,
                "Выберите записи журнала");
        var ids = ((List<?>) body.get(FIELD_ENTRY_IDS)).stream().map(v -> uuid(v)).toList();
        var evidence = journal.evidence(project, stage, ids);
        var entity =
                AcceptanceSubmission.builder()
                        .projectId(project)
                        .stageId(stage)
                        .cycleNumber(all.size() + 1)
                        .summary(summary)
                        .evidence(evidence)
                        .authorSnapshot(organizations.snapshot(actor.id()))
                        .createdAt(clock.instant())
                        .build();
        var result = submissions.saveAndFlush(entity).view();
        remember(actor.id(), project, op, key, hash, result);
        projects.touchCompletion(project, actor.id());
        return new Mutation(result, true);
    }

    /**
     * Сохраняет единственное решение текущего цикла с проверкой версии и обязательного замечания
     * при возврате.
     *
     * @param actor владелец
     * @param project проект
     * @param stage этап
     * @param id цикл
     * @param body версия, решение, замечание и ключ
     * @return решение либо исходный результат повтора
     */
    @Transactional
    SubmissionView decide(
            AccountView actor, UUID project, UUID stage, UUID id, Map<String, Object> body) {
        var access = projects.lockParticipant(project, actor.id());
        stages.requireParticipant(project, stage, actor.id());
        if (!access.owner()) throw new ApiException(ApiError.FORBIDDEN);
        keys(body, "expectedVersion", FIELD_DECISION, FIELD_COMMENT, FIELD_IDEMPOTENCY_KEY);
        var key = uuid(body.get(FIELD_IDEMPOTENCY_KEY));
        var hash = fingerprint.of(id.toString(), body);
        String op = "DECIDE:" + id;
        var cached = cached(actor.id(), project, op, key, hash);
        if (cached != null) return cached;
        projects.requireOpen(project, actor.id());
        var entity =
                submissions
                        .findById(id)
                        .filter(s ->
                                s.getProjectId().equals(project) && s.getStageId().equals(stage))
                        .orElseThrow(() -> new ApiException(ApiError.ACCEPTANCE_NOT_FOUND));
        if (entity.getVersion() != version(body.get("expectedVersion")))
            throw new ApiException(ApiError.STALE_ACCEPTANCE_VERSION);
        if (!entity.getState().equals("OPEN"))
            throw new ApiException(ApiError.INVALID_ACCEPTANCE_STATE);
        String decision = text(body.get(FIELD_DECISION), 10);
        String comment = text(body.get(FIELD_COMMENT), 5000);
        field(
                Set.of("ACCEPT", "RETURN").contains(decision == null ? "" : decision),
                FIELD_DECISION,
                "Выберите решение");
        field(
                !"RETURN".equals(decision) || comment != null,
                FIELD_COMMENT,
                "Укажите, что нужно исправить");
        entity.setState("ACCEPT".equals(decision) ? STATE_ACCEPTED : "RETURNED");
        entity.setDecision(decision);
        entity.setComment(comment);
        entity.setDecidedBy(organizations.snapshot(actor.id()));
        entity.setDecidedAt(clock.instant());
        entity.setVersion(entity.getVersion() + 1);
        submissions.flush();
        var result = entity.view();
        remember(actor.id(), project, op, key, hash, result);
        projects.touchCompletion(project, actor.id());
        return result;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public SubmissionView accepted(UUID project, UUID stage) {
        return submissions.findByProjectIdAndStageIdOrderByCycleNumberDesc(project, stage).stream()
                .filter(s -> s.getState().equals(STATE_ACCEPTED))
                .findFirst()
                .orElseThrow(() -> new ApiException(ApiError.INVALID_ACCEPTANCE_STATE))
                .view();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Set<UUID> acceptedStages(UUID project) {
        return submissions.findByProjectIdAndState(project, STATE_ACCEPTED).stream()
                .map(AcceptanceSubmission::getStageId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Результат сдачи для HTTP-статуса создания.
     *
     * @param submission сдача
     * @param created новая ли команда
     */
    record Mutation(SubmissionView submission, boolean created) {}

    private SubmissionView cached(UUID actor, UUID project, String op, UUID key, String hash) {
        var c =
                commands.findByActorIdAndProjectIdAndOperationAndIdempotencyKey(
                        actor, project, op, key);
        if (c.isEmpty()) return null;
        if (!hash.equals(c.get().getRequestHash()))
            throw new ApiException(ApiError.IDEMPOTENCY_CONFLICT);
        return c.get().getResult();
    }

    private void remember(
            UUID actor, UUID project, String op, UUID key, String hash, SubmissionView result) {
        commands.saveAndFlush(
                AcceptanceCommand.builder()
                        .actorId(actor)
                        .projectId(project)
                        .operation(op)
                        .idempotencyKey(key)
                        .requestHash(hash)
                        .result(result)
                        .createdAt(clock.instant())
                        .build());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Cycle> latestCycles(Set<UUID> projects) {
        if (projects.isEmpty()) return Map.of();
        return submissions.latest(projects).stream()
                .collect(
                        Collectors.toMap(
                                AcceptanceSubmission::getStageId,
                                s ->
                                        new Cycle(
                                                s.getId(),
                                                s.getProjectId(),
                                                s.getStageId(),
                                                s.getCycleNumber(),
                                                s.getState())));
    }
}
