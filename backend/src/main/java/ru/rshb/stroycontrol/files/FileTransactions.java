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
import ru.rshb.stroycontrol.stages.StageStatus;
import ru.rshb.stroycontrol.stages.Stages;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Владеет короткими транзакциями метаданных, общими для загрузки, публикации и очистки. */
@Service
@RequiredArgsConstructor
class FileTransactions implements Files {
    private static final String STATE_DELETED = "DELETED";
    private static final String STATE_DELETING = "DELETING";
    private static final String STATE_READY = "READY";

    private final AttachmentRepository repository;
    private final Projects projects;
    private final Stages stages;
    private final MortgageRequests requests;
    private final Clock clock;

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
        return attempt(a);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<AttachmentView> retain(UUID project, UUID stage, List<UUID> ids) {
        if (ids.size() > 10 || ids.stream().distinct().count() != ids.size())
            throw new ApiException("attachmentIds", "Выберите до 10 разных файлов");
        return ids.stream()
                .map(id -> {
                    var a =
                            repository
                                    .lockById(id)
                                    .filter(x ->
                                            x.getProjectId().equals(project)
                                                    && Objects.equals(x.getStageId(), stage))
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

    private void authorize(UUID actor, UUID project, UUID stage, String purpose) {
        var access = projects.lockParticipant(project, actor);
        if (purpose.equals("JOURNAL")) {
            projects.requireOpen(project, actor);
            if (!access.canPublishJournal()) throw new ApiException(ApiError.FORBIDDEN);
            if (stage == null) throw new ApiException("stageId", "Укажите этап");
            var s = stages.requireParticipant(project, stage, actor);
            if (s.status() == StageStatus.PLANNED)
                throw new ApiException(ApiError.JOURNAL_STAGE_STATE);
        } else if (purpose.equals("BANK")) {
            if (!access.owner() || !requests.mortgageSelected(project, actor))
                throw new ApiException(ApiError.FORBIDDEN);
            if (stage != null)
                throw new ApiException(
                        "stageId", "Для банковского дополнения этап задаётся в пакете");
        } else throw new ApiException("purpose", "Выберите назначение файла");
    }

    private Attempt attempt(Attachment a) {
        return new Attempt(a.getId(), a.getObjectKey(), a.getState(), a.view());
    }
}
