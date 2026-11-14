package ru.rshb.stroycontrol.banking;

import static ru.rshb.stroycontrol.common.PreparationInput.field;
import static ru.rshb.stroycontrol.common.PreparationInput.keys;
import static ru.rshb.stroycontrol.common.PreparationInput.text;
import static ru.rshb.stroycontrol.common.PreparationInput.uuid;
import static ru.rshb.stroycontrol.common.PreparationInput.version;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rshb.stroycontrol.acceptance.Acceptance;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.common.CommandFingerprint;
import ru.rshb.stroycontrol.files.AttachmentView;
import ru.rshb.stroycontrol.files.Files;
import ru.rshb.stroycontrol.projects.MortgageRequests;
import ru.rshb.stroycontrol.projects.Projects;
import ru.rshb.stroycontrol.proposals.AgreedDocuments;
import ru.rshb.stroycontrol.stages.StageView;
import ru.rshb.stroycontrol.stages.Stages;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Сохраняет пакет перед HTTP, доставляет вне транзакции и фиксирует результат по одноразовой
 * аренде.
 */
@Service
@RequiredArgsConstructor
class BankStageService {
    private static final String STATE_NEEDS_INFORMATION = "NEEDS_INFORMATION";
    private static final String FIELD_EXPECTED_VERSION = "expectedVersion";
    private static final String STATE_UNKNOWN = "UNKNOWN";
    private static final String STATE_RECEIVED = "RECEIVED";
    private static final String FIELD_PREVIOUS_ID = "previousId";
    private static final String FIELD_ATTACHMENT_IDS = "attachmentIds";

    private final BankStageRepository repository;
    private final BankStageAttemptRepository attempts;
    private final Projects projects;
    private final Stages stages;
    private final Acceptance acceptance;
    private final AgreedDocuments documents;
    private final MortgageRequests requests;
    private final ConstructionBankCases cases;
    private final Files files;
    private final BankStageGateway gateway;
    private final CommandFingerprint fingerprint;
    private final TransactionTemplate tx;
    private final Clock clock;

    /**
     * Состояние банковского блока только владельца.
     *
     * @param mortgage ипотечный проект
     * @param connected связано синтетическое дело
     * @param canSend допустима явная отправка
     * @param version версия последнего пакета либо ноль
     * @param preview предварительный пакет либо null до приёмки/подключения
     * @param submissions полная история отправок
     */
    record Confirmation(
            boolean mortgage,
            boolean connected,
            boolean canSend,
            long version,
            BankStagePacket preview,
            List<BankStageView> submissions) {}

    /**
     * Возвращает предпросмотр без отправки или изменения банковского статуса.
     *
     * @param owner владелец
     * @param project карточка
     * @param stage этап
     * @return состав и история
     */
    Confirmation get(UUID owner, UUID project, UUID stage) {
        return tx.execute(status -> {
            owner(project, owner, false);
            var stageView = stages.requireParticipant(project, stage, owner);
            if (!requests.mortgageSelected(project, owner))
                return new Confirmation(false, false, false, 0, null, List.of());
            var credit = cases.connected(project, owner);
            var history = repository.findByProjectIdAndStageIdOrderByRevisionDesc(project, stage);
            var latest = history.isEmpty() ? null : history.getFirst();
            BankStagePacket preview = preview(owner, project, stageView, credit, latest);
            return new Confirmation(
                    true,
                    credit != null,
                    preview != null
                            && (latest == null
                                    || latest.getState().equals(STATE_NEEDS_INFORMATION)),
                    latest == null ? 0 : latest.getVersion(),
                    preview,
                    history.stream().map(BankStageSubmission::view).toList());
        });
    }

    /** Собирает предпросмотр принятого этапа; отсутствие приёмки не является ошибкой чтения. */
    private BankStagePacket preview(
            UUID owner, UUID project, StageView stage, UUID credit, BankStageSubmission latest) {
        if (credit == null) return null;
        try {
            return new BankStagePacket(
                    projects.info(project, owner),
                    documents.agreed(project, owner),
                    stage,
                    acceptance.accepted(project, stage.id()),
                    credit,
                    latest == null ? List.of() : latest.getPacket().additionalFiles(),
                    latest == null ? List.of() : latest.getPacket().clarifications());
        } catch (ApiException exception) {
            if (exception.getError() != ApiError.INVALID_ACCEPTANCE_STATE) throw exception;
            return null;
        }
    }

    /**
     * Создаёт неизменяемую версию по явной команде владельца, затем пытается доставить её вне БД.
     *
     * @param owner владелец
     * @param project карточка
     * @param stage этап
     * @param body ссылка на приёмку, дополнения, версия и ключ
     * @return созданный пакет либо текущий результат его повтора
     */
    SendResult send(UUID owner, UUID project, UUID stage, Map<String, Object> body) {
        var prepared = tx.execute(status -> prepare(owner, project, stage, body));
        var id = prepared.id();
        dispatch(id);
        return tx.execute(status -> {
            owner(project, owner, false);
            return new SendResult(row(project, stage, id).view(), prepared.created());
        });
    }

    /**
     * Возобновляет ту же доставку с прежним банковским ключом и проверкой версии.
     *
     * @param owner владелец
     * @param project проект
     * @param stage этап
     * @param id отправка
     * @param body ожидаемая версия
     * @return актуальная квитанция
     */
    BankStageView retry(UUID owner, UUID project, UUID stage, UUID id, Map<String, Object> body) {
        tx.executeWithoutResult(status -> {
            owner(project, owner, true);
            keys(body, FIELD_EXPECTED_VERSION);
            var row = row(project, stage, id);
            if (row.getVersion() != version(body.get(FIELD_EXPECTED_VERSION)))
                throw new ApiException(ApiError.STALE_BANK_VERSION);
            if (!Set.of("PENDING", STATE_UNKNOWN, STATE_RECEIVED).contains(row.getState()))
                throw new ApiException(ApiError.INVALID_BANK_SUBMISSION_STATE);
            row.setAutoAttempts(0);
        });
        dispatch(id);
        return tx.execute(status -> {
            owner(project, owner, false);
            return row(project, stage, id).view();
        });
    }

    /**
     * Повторяет не более трёх автоматических попыток; неизвестный результат восстанавливается по
     * прежнему ключу.
     */
    @Scheduled(
            fixedDelayString = "${bank.stage.worker-delay-ms:3000}",
            initialDelayString = "${bank.stage.worker-delay-ms:3000}")
    void work() {
        var ids = tx.execute(status -> repository.due(clock.instant(), PageRequest.of(0, 10)));
        for (var id : ids) {
            try {
                dispatch(id);
            } catch (RuntimeException _) {
                /* История PENDING/UNKNOWN остаётся для следующего прохода либо ручного повтора. */
            }
        }
    }

    private Prepared prepare(UUID ownerId, UUID project, UUID stage, Map<String, Object> body) {
        owner(project, ownerId, true);
        stages.requireParticipant(project, stage, ownerId);
        keys(
                body,
                "acceptanceId",
                FIELD_PREVIOUS_ID,
                FIELD_EXPECTED_VERSION,
                "note",
                FIELD_ATTACHMENT_IDS,
                "idempotencyKey");
        var key = uuid(body.get("idempotencyKey"));
        var hash = fingerprint.of(stage.toString(), body);
        var old = repository.findByActorIdAndProjectIdAndIdempotencyKey(ownerId, project, key);
        if (old.isPresent()) {
            if (!hash.equals(old.get().getRequestHash()))
                throw new ApiException(ApiError.IDEMPOTENCY_CONFLICT);
            return new Prepared(old.get().getId(), false);
        }
        if (!requests.mortgageSelected(project, ownerId))
            throw new ApiException(ApiError.INVALID_BANK_SUBMISSION_STATE);
        var credit = cases.connected(project, ownerId);
        if (credit == null) throw new ApiException(ApiError.BANK_CASE_REQUIRED);
        var accepted = acceptance.accepted(project, stage);
        if (!accepted.id().equals(uuid(body.get("acceptanceId"))))
            throw new ApiException(ApiError.INVALID_ACCEPTANCE_STATE);
        var history = repository.findByProjectIdAndStageIdOrderByRevisionDesc(project, stage);
        var latest = history.isEmpty() ? null : history.getFirst();
        if ((latest == null ? 0 : latest.getVersion()) != version(body.get(FIELD_EXPECTED_VERSION)))
            throw new ApiException(ApiError.STALE_BANK_VERSION);
        UUID previous =
                body.get(FIELD_PREVIOUS_ID) == null ? null : uuid(body.get(FIELD_PREVIOUS_ID));
        if (latest == null
                ? previous != null
                : !latest.getState().equals(STATE_NEEDS_INFORMATION)
                        || !latest.getId().equals(previous))
            throw new ApiException(ApiError.INVALID_BANK_SUBMISSION_STATE);
        String note = text(body.get("note"), 5000);
        var ids = additionalIds(body, latest, note);
        var added = files.retain(project, null, ids);
        var combined = new LinkedHashMap<UUID, AttachmentView>();
        var clarifications = new ArrayList<String>();
        if (latest != null) {
            latest.getPacket().additionalFiles().forEach(f -> combined.put(f.id(), f));
            clarifications.addAll(latest.getPacket().clarifications());
        }
        added.forEach(f -> combined.put(f.id(), f));
        if (note != null) clarifications.add(note);
        var packet =
                new BankStagePacket(
                        projects.info(project, ownerId),
                        documents.agreed(project, ownerId),
                        stages.requireParticipant(project, stage, ownerId),
                        accepted,
                        credit,
                        List.copyOf(combined.values()),
                        List.copyOf(clarifications));
        var row =
                BankStageSubmission.builder()
                        .projectId(project)
                        .stageId(stage)
                        .actorId(ownerId)
                        .acceptanceId(accepted.id())
                        .revision(history.size() + 1)
                        .previousId(previous)
                        .idempotencyKey(key)
                        .requestHash(hash)
                        .packet(packet)
                        .createdAt(clock.instant())
                        .updatedAt(clock.instant())
                        .build();
        return new Prepared(repository.saveAndFlush(row).getId(), true);
    }

    /** Проверяет новые дополнения до удержания файлов; повторный пакет должен что-то добавлять. */
    private List<UUID> additionalIds(
            Map<String, Object> body, BankStageSubmission latest, String note) {
        var raw = body.getOrDefault(FIELD_ATTACHMENT_IDS, List.of());
        field(raw instanceof List<?>, FIELD_ATTACHMENT_IDS, "Проверьте список дополнений");
        var ids = ((List<?>) raw).stream().map(v -> uuid(v)).toList();
        if (latest != null)
            field(note != null || !ids.isEmpty(), "note", "Добавьте пояснение или документ");
        return ids;
    }

    /**
     * Резервирует попытку под проектной блокировкой, затем освобождает БД перед внешним вызовом.
     */
    private void dispatch(UUID id) {
        var reserved = claimDelivery(id);
        if (reserved.isEmpty()) return;
        var claim = reserved.get();
        var result = deliver(claim);
        finishDelivery(id, claim, result);
    }

    /** Резервирует попытку; занятая аренда и исчерпанный лимит оставляют пакет для повтора. */
    private Optional<Delivery> claimDelivery(UUID id) {
        return tx.execute(status -> {
            var route = repository.route(id).orElse(null);
            if (route == null) return Optional.empty();
            projects.lockParticipant(route.getProjectId(), route.getActorId());
            var row = repository.findById(id).orElseThrow();
            if (!Set.of("PENDING", STATE_UNKNOWN, STATE_RECEIVED).contains(row.getState())
                    || row.getAutoAttempts() >= 3
                    || row.getLeaseUntil() != null && row.getLeaseUntil().isAfter(clock.instant()))
                return Optional.empty();
            var token = UUID.randomUUID();
            row.setLeaseToken(token);
            row.setLeaseUntil(clock.instant().plus(Duration.ofSeconds(45)));
            row.setAttemptCount(row.getAttemptCount() + 1);
            row.setAutoAttempts(row.getAutoAttempts() + 1);
            attempts.save(
                    BankStageAttempt.builder()
                            .submissionId(id)
                            .attemptNumber(row.getAttemptCount())
                            .outcome("STARTED")
                            .createdAt(clock.instant())
                            .build());
            return Optional.of(
                    new Delivery(
                            row.getId(),
                            row.getProjectId(),
                            row.getActorId(),
                            row.getBankKey(),
                            row.getRevision(),
                            row.getPacket(),
                            token,
                            row.getAttemptCount()));
        });
    }

    /** Неверная или потерянная квитанция считается неизвестным исходом, а не решением банка. */
    private BankStageGateway.Receipt deliver(Delivery claim) {
        try {
            var receipt = gateway.deliver(claim.key(), claim.revision(), claim.packet());
            if (receipt == null) return null;
            if (!Set.of(STATE_RECEIVED, "ACCEPTED", STATE_NEEDS_INFORMATION)
                    .contains(receipt.state())) return null;
            return receipt;
        } catch (RuntimeException _) {
            return null;
        }
    }

    /** Фиксирует результат только для действующей аренды и сохраняет уже известное получение. */
    private void finishDelivery(UUID id, Delivery claim, BankStageGateway.Receipt result) {
        tx.executeWithoutResult(status -> {
            projects.lockParticipant(claim.project(), claim.actor());
            var row = repository.findById(id).orElseThrow();
            if (!claim.token().equals(row.getLeaseToken())) return;
            String nextState =
                    row.getState().equals(STATE_RECEIVED) ? STATE_RECEIVED : STATE_UNKNOWN;
            if (result != null) nextState = result.state();
            row.setState(nextState);
            if (result != null) {
                row.setBankReference(result.reference());
            }
            String reason =
                    row.getState().equals(STATE_RECEIVED)
                            ? "Пакет получен. Не удалось уточнить результат проверки"
                            : "Доставка не подтверждена. Можно безопасно повторить отправку";
            if (result != null) reason = result.reason();
            row.setReason(reason);
            row.setUpdatedAt(clock.instant());
            row.setVersion(row.getVersion() + 1);
            row.setLeaseToken(null);
            row.setLeaseUntil(null);
            attempts.findBySubmissionIdAndAttemptNumber(id, claim.attempt())
                    .orElseThrow()
                    .setOutcome(row.getState());
        });
    }

    /**
     * Результат команды с признаком новой отправки.
     *
     * @param submission пакет с актуальным состоянием
     * @param created новая ли отправка
     */
    record SendResult(BankStageView submission, boolean created) {}

    private record Prepared(UUID id, boolean created) {}

    /**
     * Данные попытки вне контекста JPA.
     *
     * @param id отправка
     * @param project проект
     * @param actor первоначальный владелец
     * @param key банковский ключ
     * @param revision версия пакета
     * @param packet неизменяемый состав
     * @param token аренда этой попытки
     * @param attempt номер попытки
     */
    record Delivery(
            UUID id,
            UUID project,
            UUID actor,
            UUID key,
            int revision,
            BankStagePacket packet,
            UUID token,
            int attempt) {}

    private void owner(UUID project, UUID actor, boolean lock) {
        var access =
                lock
                        ? projects.lockParticipant(project, actor)
                        : projects.requireParticipant(project, actor);
        if (!access.owner()) throw new ApiException(ApiError.PROJECT_NOT_FOUND);
    }

    private BankStageSubmission row(UUID project, UUID stage, UUID id) {
        return repository
                .findById(id)
                .filter(s -> s.getProjectId().equals(project) && s.getStageId().equals(stage))
                .orElseThrow(() -> new ApiException(ApiError.ACCEPTANCE_NOT_FOUND));
    }
}
