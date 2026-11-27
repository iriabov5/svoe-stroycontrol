package ru.rshb.stroycontrol.files;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.projects.MortgageRequests;
import ru.rshb.stroycontrol.projects.Projects;
import ru.rshb.stroycontrol.proposals.ProposalFiles;
import ru.rshb.stroycontrol.stages.StageStatus;
import ru.rshb.stroycontrol.stages.Stages;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Владеет короткими транзакциями метаданных, общими для загрузки, публикации и очистки. */
@Service
@RequiredArgsConstructor
class FileTransactions implements Files {
    private static final String PURPOSE_PROPOSAL = "PROPOSAL";
    private static final String STATE_DELETED = "DELETED";
    private static final String STATE_DELETING = "DELETING";
    private static final String STATE_READY = "READY";

    private final AttachmentRepository repository;
    private final Projects projects;
    private final Stages stages;
    private final MortgageRequests requests;
    private final Clock clock;
    private final ProposalFiles proposalFiles;

    /**
     * Снимок намерения вне контекста JPA.
     *
     * @param id файл
     * @param key закрытый объектный ключ
     * @param state состояние загрузки
     * @param view метаданные
     */
    record Attempt(UUID id, String key, String state, AttachmentView view) {}

    /**
     * Проверенные свойства файла без его бинарного содержимого.
     *
     * @param name безопасное имя
     * @param caption подпись
     * @param type определённый MIME
     * @param size размер в байтах
     * @param hash SHA-256 содержимого
     */
    record Metadata(String name, String caption, String type, long size, String hash) {}

    /**
     * Сохраняет намерение или проверяет прежний ключ под блокировкой проекта.
     *
     * @param scope доступ и ключ повтора
     * @param metadata проверенные свойства содержимого
     * @return состояние попытки вне транзакции
     */
    @Transactional
    Attempt prepare(UploadScope scope, Metadata metadata) {
        UUID actor = scope.actor();
        UUID project = scope.project();
        UUID stage = scope.stage();
        String purpose = scope.purpose();
        UUID key = scope.key();
        String name = metadata.name();
        String caption = metadata.caption();
        String type = metadata.type();
        long size = metadata.size();
        String hash = metadata.hash();
        authorize(actor, project, stage, purpose);
        String fingerprint =
                FileInput.sha256(
                        (hash + ":" + stage + ":" + purpose + ":" + name + ":" + caption)
                                .getBytes(StandardCharsets.UTF_8));
        var existing = repository.findByActorIdAndProjectIdAndIdempotencyKey(actor, project, key);
        if (existing.isPresent()) {
            var a = existing.get();
            if (!a.getRequestHash().equals(fingerprint))
                throw new ApiException(ApiError.IDEMPOTENCY_CONFLICT);
            if (a.getState().equals(STATE_DELETING) || a.getState().equals(STATE_DELETED))
                throw new ApiException(ApiError.ATTACHMENT_NOT_READY);
            a.setUploadStartedAt(clock.instant());
            return attempt(a);
        }
        var a =
                Attachment.builder()
                        .projectId(project)
                        .stageId(stage)
                        .actorId(actor)
                        .purpose(purpose)
                        .idempotencyKey(key)
                        .requestHash(fingerprint)
                        .name(name)
                        .caption(caption)
                        .contentType(type)
                        .size(size)
                        .sha256(hash)
                        .objectKey(project + "/" + UUID.randomUUID())
                        .createdAt(clock.instant())
                        .uploadStartedAt(clock.instant())
                        .build();
        return attempt(repository.saveAndFlush(a));
    }

    /**
     * Повторно проверяет полномочия перед публикацией готовой загрузки.
     *
     * @param actor автор
     * @param project проект
     * @param id намерение
     * @return готовые метаданные
     */
    @Transactional
    AttachmentView complete(UUID actor, UUID project, UUID id) {
        projects.lockParticipant(project, actor);
        var a = repository.lockById(id).orElseThrow();
        authorize(actor, project, a.getStageId(), a.getPurpose());
        if (a.getState().equals(STATE_DELETING) || a.getState().equals(STATE_DELETED))
            throw new ApiException(ApiError.ATTACHMENT_NOT_READY);
        a.setState(STATE_READY);
        return a.view();
    }

    /**
     * Сохраняет неопределённый исход, не удаляя возможно записанный объект.
     *
     * @param id намерение
     */
    @Transactional
    void failed(UUID id) {
        repository
                .lockById(id)
                .ifPresent(a -> {
                    if (a.getState().equals("UPLOADING")) a.setState("FAILED");
                });
    }

    /**
     * Читает готовое опубликованное вложение либо собственную загрузку до публикации.
     *
     * @param actor читатель
     * @param project проект
     * @param id файл
     * @return модель ключа для последующего S3-чтения без транзакции
     */
    @Transactional(readOnly = true)
    Attempt read(UUID actor, UUID project, UUID id) {
        var access = projects.requireParticipant(project, actor);
        var a =
                repository
                        .findById(id)
                        .filter(x ->
                                x.getProjectId().equals(project)
                                        && x.getState().equals(STATE_READY))
                        .orElseThrow(() -> new ApiException(ApiError.ATTACHMENT_NOT_FOUND));
        if (!a.isRetained() && !a.getActorId().equals(actor))
            throw new ApiException(ApiError.ATTACHMENT_NOT_FOUND);
        if (a.getPurpose().equals("BANK") && !access.owner())
            throw new ApiException(ApiError.ATTACHMENT_NOT_FOUND);
        if (a.getPurpose().equals(PURPOSE_PROPOSAL)
                && !proposalFiles.canRead(project, actor, id, a.getActorId())) {
            throw new ApiException(ApiError.ATTACHMENT_NOT_FOUND);
        }
        return attempt(a);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<AttachmentView> retain(UUID project, UUID stage, List<UUID> ids) {
        return retainFiles(project, stage, ids, stage == null ? "BANK" : "JOURNAL");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<AttachmentView> retainProposal(UUID project, List<UUID> ids) {
        return retainFiles(project, null, ids, PURPOSE_PROPOSAL);
    }

    /**
     * Проверяет назначение вместе с проектом до удержания, чтобы файлы банка не попали в смету.
     *
     * @param project доступный проект под блокировкой
     * @param stage этап либо null
     * @param ids уникальные идентификаторы
     * @param purpose ожидаемое назначение
     * @return удержанные метаданные
     */
    private List<AttachmentView> retainFiles(
            UUID project, UUID stage, List<UUID> ids, String purpose) {
        if (ids.size() > 10 || ids.stream().distinct().count() != ids.size())
            throw new ApiException("attachmentIds", "Выберите до 10 разных файлов");
        return ids.stream()
                .map(id -> {
                    var a =
                            repository
                                    .lockById(id)
                                    .filter(x ->
                                            x.getProjectId().equals(project)
                                                    && Objects.equals(x.getStageId(), stage)
                                                    && x.getPurpose().equals(purpose))
                                    .orElseThrow(
                                            () -> new ApiException(ApiError.ATTACHMENT_NOT_FOUND));
                    if (!a.getState().equals(STATE_READY))
                        throw new ApiException(ApiError.ATTACHMENT_NOT_READY);
                    a.setRetained(true);
                    return a.view();
                })
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AttachmentView> describe(UUID project, List<UUID> ids) {
        return ids.stream()
                .map(id ->
                        repository
                                .findById(id)
                                .filter(a ->
                                        a.getProjectId().equals(project)
                                                && a.getState().equals(STATE_READY))
                                .orElseThrow(() -> new ApiException(ApiError.ATTACHMENT_NOT_FOUND))
                                .view())
                .toList();
    }

    /**
     * Находит ограниченную порцию устаревших несвязанных загрузок.
     *
     * @return не более 100 кандидатов
     */
    @Transactional(readOnly = true)
    List<UUID> candidates() {
        return repository.cleanupCandidates(
                clock.instant().minus(Duration.ofDays(1)), PageRequest.of(0, 100));
    }

    /**
     * Отмечает объект перед внешним удалением; блокировка не даёт публикации удержать его
     * одновременно.
     *
     * @param id кандидат
     * @return ключ доказанно временного объекта либо null
     */
    @Transactional
    String claimCleanup(UUID id) {
        var a = repository.lockById(id).orElse(null);
        if (a == null
                || a.isRetained()
                || a.getUploadStartedAt().isAfter(clock.instant().minus(Duration.ofDays(1)))
                || a.getState().equals(STATE_DELETED)) return null;
        a.setState(STATE_DELETING);
        return a.getObjectKey();
    }

    /**
     * Завершает уборку после успешного S3 delete.
     *
     * @param id временный объект
     */
    @Transactional
    void deleted(UUID id) {
        repository
                .lockById(id)
                .filter(a -> a.getState().equals(STATE_DELETING))
                .ifPresent(a -> a.setState(STATE_DELETED));
    }

    /**
     * Проверяет назначение загрузки под блокировкой карточки до записи намерения и READY.
     *
     * @param actor автор текущей операции
     * @param project карточка, права на которую проверяются заново
     * @param stage обязателен только для журнала
     * @param purpose JOURNAL, BANK либо PROPOSAL
     * @throws ApiException при недопустимом состоянии или отсутствии полномочий
     */
    private void authorize(UUID actor, UUID project, UUID stage, String purpose) {
        var access = projects.lockParticipant(project, actor);
        switch (purpose) {
            case "JOURNAL" -> {
                projects.requireOpen(project, actor);
                if (!access.canPublishJournal()) throw new ApiException(ApiError.FORBIDDEN);
                if (stage == null) throw new ApiException("stageId", "Укажите этап");
                var state = stages.requireParticipant(project, stage, actor);
                if (state.status() == StageStatus.PLANNED)
                    throw new ApiException(ApiError.JOURNAL_STAGE_STATE);
            }
            case "BANK" -> {
                if (!access.owner() || !requests.mortgageSelected(project, actor))
                    throw new ApiException(ApiError.FORBIDDEN);
                if (stage != null)
                    throw new ApiException(
                            "stageId", "Для банковского дополнения этап задаётся в пакете");
            }
            case PURPOSE_PROPOSAL -> {
                if (stage != null)
                    throw new ApiException("stageId", "Изображение относится к смете, а не этапу");
                proposalFiles.requireUpload(project, actor);
            }
            default -> throw new ApiException("purpose", "Выберите назначение файла");
        }
    }

    private Attempt attempt(Attachment a) {
        return new Attempt(a.getId(), a.getObjectKey(), a.getState(), a.view());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Map<UUID, ImageLocation> journalImages(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return repository.journalImages(ids).stream()
                .collect(
                        Collectors.toMap(
                                Attachment::getId,
                                a -> new ImageLocation(a.getProjectId(), a.getStageId())));
    }
}
