package ru.rshb.stroycontrol.journal;

import static ru.rshb.stroycontrol.common.PreparationInput.fieldValue;
import static ru.rshb.stroycontrol.common.PreparationInput.keys;
import static ru.rshb.stroycontrol.common.PreparationInput.require;
import static ru.rshb.stroycontrol.common.PreparationInput.uuid;
import static ru.rshb.stroycontrol.common.PreparationInput.version;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.accounts.AccountRole;
import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.ActorSnapshot;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.common.CommandFingerprint;
import ru.rshb.stroycontrol.files.Files;
import ru.rshb.stroycontrol.organizations.Organizations;
import ru.rshb.stroycontrol.projects.Projects;
import ru.rshb.stroycontrol.stages.Stages;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ведёт неизменяемую историю хода работ, вопросов и локальных согласований этапа.
 *
 * <p>Чтение доступно участникам карточки; публикация — назначенному подрядчику, ответ — владельцу.
 * Изменения сериализованы блокировкой проекта. Запись, фотографии и результат команды фиксируются
 * одной транзакцией; решения ленты не меняют смету, договор или сроки этапа.
 */
@Service
@RequiredArgsConstructor
class JournalService implements Journal {
    private static final String FIELD_IDEMPOTENCY_KEY = "idempotencyKey";

    private static final TypeReference<Map<String, Object>> RESULT = new TypeReference<>() {};

    private final JournalEntryRepository entries;
    private final JournalPhotoRepository photos;
    private final JournalResponseRepository responses;
    private final JournalCommandRepository commands;
    private final Projects projects;
    private final Stages stages;
    private final CommandFingerprint fingerprint;
    private final Clock clock;
    private final Organizations organizations;
    private final ObjectMapper mapper;
    private final Files files;

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean hasPublishedPhoto(UUID projectId, UUID stageId) {
        return entries.hasPublishedPhoto(projectId, stageId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Journal.Evidence> evidence(UUID projectId, UUID stageId, List<UUID> entryIds) {
        if (entryIds.isEmpty()
                || entryIds.size() > 20
                || entryIds.stream().distinct().count() != entryIds.size())
            throw new ApiException("entryIds", "Выберите от 1 до 20 разных записей этапа");
        var selected =
                entryIds.stream()
                        .map(id -> {
                            var e = row(projectId, stageId, id);
                            return new Journal.Evidence(
                                    e.getId(),
                                    e.getText(),
                                    e.getAuthorSnapshot() == null
                                            ? ActorSnapshot.legacy(
                                                    e.getAuthorId(), AccountRole.CONTRACTOR)
                                            : e.getAuthorSnapshot(),
                                    e.getCreatedAt(),
                                    files.describe(projectId, e.getAttachmentIds()));
                        })
                        .toList();
        if (selected.stream()
                .flatMap(e -> e.attachments().stream())
                .noneMatch(a -> a.contentType().startsWith("image/")))
            throw new ApiException("entryIds", "Добавьте запись с загруженной фотографией");
        return selected;
    }

    /**
     * Публикация и результат распознавания повторной команды для HTTP-адаптера.
     *
     * @param entry первоначальная модель публикации
     * @param created true при создании в текущей команде, false при идентичном повторе
     */
    record Mutation(JournalEntryView entry, boolean created) {}

    /**
     * Возвращает публикацию с фотографиями, автором и единственным ответом.
     *
     * <p>Запись должна принадлежать указанным проекту и этапу; недоступные данные не раскрываются.
     *
     * @param actor аутентифицированный участник карточки
     * @param project идентификатор карточки дома
     * @param stage идентификатор этапа этой карточки
     * @param entry идентификатор публикации этого этапа
     * @return публичная публикация с фотографиями и ответом
     * @throws ApiException с PROJECT_NOT_FOUND, STAGE_NOT_FOUND или JOURNAL_ENTRY_NOT_FOUND
     */
    @Transactional(readOnly = true)
    JournalEntryView get(AccountView actor, UUID project, UUID stage, UUID entry) {
        stages.requireParticipant(project, stage, actor.id());
        return view(row(project, stage, entry));
    }

    /**
     * Возвращает до 20 публикаций этапа и курсор следующей страницы.
     *
     * <p>Порядок устойчив по дате и UUID; счётчик ожиданий охватывает весь этап, а не только
     * текущую страницу. Курсор проверяется на принадлежность этому этапу.
     *
     * @param actor аутентифицированный участник карточки
     * @param project идентификатор карточки дома
     * @param stage идентификатор этапа этой карточки
     * @param cursor курсор предыдущей страницы или null для первой страницы
     * @param awaitingOnly true для выбора только ожидающих ответа записей
     * @return элементы страницы, nextCursor либо null и полный awaitingCount
     * @throws ApiException при отсутствии доступа или неверном курсоре
     */
    @Transactional(readOnly = true)
    JournalPage list(
            AccountView actor, UUID project, UUID stage, String cursor, boolean awaitingOnly) {
        stages.requireParticipant(project, stage, actor.id());
        Cursor boundary = cursor == null ? null : decodeCursor(cursor, project, stage);
        var all =
                entries.page(
                        project,
                        stage,
                        awaitingOnly,
                        boundary == null ? null : boundary.at(),
                        boundary == null ? null : boundary.id(),
                        PageRequest.of(0, 21));
        var selected = all.subList(0, Math.min(all.size(), 20));
        return new JournalPage(
                selected.stream().map(this::view).toList(),
                all.size() > 20 ? encodeCursor(selected.getLast()) : null,
                entries.countAwaiting(project, stage));
    }

    /**
     * Публикует ход работ, вопрос или запрос согласования от подрядчика.
     *
     * <p>Запись, фотографии и первоначальный ответ команды сохраняются атомарно. Повтор с тем же
     * ключом возвращает сохранённый результат; другой отпечаток даёт конфликт. Вопросы и
     * согласования разрешены только во время выполнения этапа.
     *
     * @param actor аутентифицированный участник карточки
     * @param project идентификатор карточки дома
     * @param stage идентификатор этапа этой карточки
     * @param body вид записи, текст, фотографии, исходная публикация и ключ повтора
     * @return публикация и признак её создания в текущем запросе
     * @throws ApiException с FORBIDDEN, JOURNAL_STAGE_STATE, IDEMPOTENCY_CONFLICT или ошибкой поля
     */
    @Transactional
    Mutation publish(AccountView actor, UUID project, UUID stage, Map<String, Object> body) {
        var access = projects.lockParticipant(project, actor.id());
        var state = stages.requireParticipant(project, stage, actor.id());
        if (!access.canPublishJournal()) throw new ApiException(ApiError.FORBIDDEN);
        var input = JournalInput.parse(body);
        String hash = fingerprint.of(stage.toString(), body);
        var cached = cached(actor.id(), project, "PUBLISH", input.key(), hash);
        if (cached != null) return new Mutation(cached, false);
        projects.requireOpen(project, actor.id());
        input.requireStage(state.status());
        if (input.sourceEntryId() != null) row(project, stage, input.sourceEntryId());
        files.retain(project, stage, input.attachmentIds());
        UUID id = UUID.randomUUID();
        var entry =
                JournalEntry.builder()
                        .id(id)
                        .projectId(project)
                        .stageId(stage)
                        .authorId(actor.id())
                        .authorEmail(actor.email())
                        .authorRole(actor.role().name())
                        .authorSnapshot(organizations.snapshot(actor.id()))
                        .kind(input.kind())
                        .status(input.kind().equals("NOTE") ? "PUBLISHED" : "PENDING")
                        .text(input.text())
                        .attachmentIds(input.attachmentIds())
                        .sourceEntryId(input.sourceEntryId())
                        .createdAt(clock.instant())
                        .build();
        var stored = new ArrayList<JournalPhoto>();
        int pos = 1;
        for (var photo : input.photos()) {
            stored.add(
                    JournalPhoto.builder()
                            .entryId(id)
                            .position(pos++)
                            .externalUrl(photo.url())
                            .caption(photo.caption())
                            .build());
        }
        entries.save(entry);
        photos.saveAll(stored);
        entries.flush();
        var result = view(row(project, stage, id));
        remember(actor.id(), project, "PUBLISH", input.key(), hash, id, result);
        return new Mutation(result, true);
    }

    /**
     * Сохраняет единственный ответ или решение владельца по публикации.
     *
     * <p>Ключ повтора проверяется до версии: повтор успешной команды возвращает исходный результат.
     * Ответ и состояние записи фиксируются одной транзакцией; ответить на ранее заданный вопрос
     * можно и после завершения этапа.
     *
     * @param actor аутентифицированный участник карточки
     * @param project идентификатор карточки дома
     * @param stage идентификатор этапа этой карточки
     * @param entry идентификатор публикации этого этапа
     * @param body версия публикации, ключ повтора, текст ответа и решение по согласованию
     * @return публикация с ответом и новой версией либо исходный результат повтора
     * @throws ApiException с FORBIDDEN, STALE_JOURNAL_VERSION, JOURNAL_ALREADY_RESOLVED или
     *     IDEMPOTENCY_CONFLICT
     */
    @Transactional
    JournalEntryView respond(
            AccountView actor, UUID project, UUID stage, UUID entry, Map<String, Object> body) {
        var access = projects.lockParticipant(project, actor.id());
        stages.requireParticipant(project, stage, actor.id());
        if (!access.owner()) throw new ApiException(ApiError.FORBIDDEN);
        keys(body, "version", FIELD_IDEMPOTENCY_KEY, "text", "decision");
        long expected = version(body.get("version"));
        UUID key = fieldValue(FIELD_IDEMPOTENCY_KEY, () -> uuid(body.get(FIELD_IDEMPOTENCY_KEY)));
        String hash = fingerprint.of(stage + ":" + entry, body);
        var cached = cached(actor.id(), project, "RESPONSE", key, hash);
        if (cached != null) return cached;
        projects.requireOpen(project, actor.id());
        var old = row(project, stage, entry);
        if (old.getVersion() != expected) throw new ApiException(ApiError.STALE_JOURNAL_VERSION);
        if (!old.getStatus().equals("PENDING"))
            throw new ApiException(ApiError.JOURNAL_ALREADY_RESOLVED);
        var answer = JournalInput.answer(body, old.getKind());
        responses.save(
                JournalResponse.builder()
                        .entryId(entry)
                        .actorId(actor.id())
                        .authorSnapshot(organizations.snapshot(actor.id()))
                        .text(answer.text())
                        .decision(answer.decision())
                        .createdAt(clock.instant())
                        .build());
        old.setStatus(answer.state());
        old.setVersion(old.getVersion() + 1);
        entries.saveAndFlush(old);
        var result = view(row(project, stage, entry));
        remember(actor.id(), project, "RESPONSE", key, hash, entry, result);
        return result;
    }

    /**
     * Находит публикацию только внутри заданных проекта и этапа.
     *
     * <p>Доступ к проекту проверяется вызывающей операцией до поиска.
     *
     * @param project идентификатор карточки дома
     * @param stage идентификатор этапа этой карточки
     * @param entry идентификатор публикации этого этапа
     * @return публикация указанного этапа
     * @throws ApiException с JOURNAL_ENTRY_NOT_FOUND для отсутствующей или чужой записи
     */
    private JournalEntry row(UUID project, UUID stage, UUID entry) {
        return entries.findByIdAndProjectIdAndStageId(entry, project, stage)
                .orElseThrow(() -> new ApiException(ApiError.JOURNAL_ENTRY_NOT_FOUND));
    }

    /**
     * Собирает публичную публикацию из собственных данных модуля журнала.
     *
     * <p>Снимок автора не требует чтения таблиц аккаунтов. Ответ включает упорядоченные фото и не
     * раскрывает внутренние связи ответа.
     *
     * @param entity публикация текущего модуля
     * @return модель публикации для REST-контракта
     */
    private JournalEntryView view(JournalEntry entity) {
        var response =
                responses
                        .findByEntryId(entity.getId())
                        .map(stored ->
                                new JournalEntryView.Response(
                                        stored.getActorId(),
                                        stored.getText(),
                                        stored.getDecision(),
                                        stored.getCreatedAt(),
                                        stored.getAuthorSnapshot() == null
                                                ? ActorSnapshot.legacy(
                                                        stored.getActorId(), AccountRole.CUSTOMER)
                                                : stored.getAuthorSnapshot()))
                        .orElse(null);
        return new JournalEntryView(
                entity.getId(),
                entity.getProjectId(),
                entity.getStageId(),
                entity.getKind(),
                entity.getStatus(),
                entity.getText(),
                entity.getSourceEntryId(),
                entity.getCreatedAt(),
                entity.getVersion(),
                new JournalEntryView.Author(
                        entity.getAuthorId(), entity.getAuthorEmail(), entity.getAuthorRole()),
                photos.findByEntryIdOrderByPositionAsc(entity.getId()).stream()
                        .map(photo ->
                                new JournalEntryView.Photo(
                                        photo.getId(), photo.getExternalUrl(), photo.getCaption()))
                        .toList(),
                response,
                entity.getAuthorSnapshot() == null
                        ? ActorSnapshot.legacy(entity.getAuthorId(), AccountRole.CONTRACTOR)
                        : entity.getAuthorSnapshot(),
                files.describe(entity.getProjectId(), entity.getAttachmentIds()));
    }

    /**
     * Возвращает первоначальный результат ранее выполненной команды.
     *
     * <p>Вызывается после проверки доступа и под блокировкой проекта. Не вычисляет ответ из
     * текущего состояния записи, которое могло измениться.
     *
     * @param actor идентификатор автора команды
     * @param project идентификатор карточки дома
     * @param operation тип команды в области ключа повтора
     * @param key ключ повторной команды
     * @param hash отпечаток ресурса и нормализованного тела команды
     * @return исходный результат либо null, если команда ещё не выполнялась
     * @throws ApiException с IDEMPOTENCY_CONFLICT при повторном ключе с другим телом
     */
    private JournalEntryView cached(
            UUID actor, UUID project, String operation, UUID key, String hash) {
        var command =
                commands.findByActorIdAndProjectIdAndOperationAndIdempotencyKey(
                        actor, project, operation, key);
        if (command.isEmpty()) return null;
        if (!hash.equals(command.get().getRequestHash()))
            throw new ApiException(ApiError.IDEMPOTENCY_CONFLICT);
        return mapper.convertValue(command.get().getResult(), JournalEntryView.class);
    }

    /**
     * Фиксирует исходный ответ команды в общей транзакции публикации или ответа.
     *
     * <p>Сохранённый JSONB используется для точного повтора без повторного изменения записи.
     *
     * @param actor идентификатор автора команды
     * @param project идентификатор карточки дома
     * @param operation тип команды в области ключа повтора
     * @param key ключ повторной команды
     * @param hash отпечаток ресурса и нормализованного тела команды
     * @param entry идентификатор публикации
     * @param result первоначальный результат команды
     */
    private void remember(
            UUID actor,
            UUID project,
            String operation,
            UUID key,
            String hash,
            UUID entry,
            JournalEntryView result) {
        commands.saveAndFlush(
                JournalCommand.builder()
                        .actorId(actor)
                        .projectId(project)
                        .operation(operation)
                        .idempotencyKey(key)
                        .requestHash(hash)
                        .entryId(entry)
                        .result(mapper.convertValue(result, RESULT))
                        .createdAt(clock.instant())
                        .build());
    }

    /**
     * Проверенная граница страницы внутри одного этапа.
     *
     * @param at время публикации границы
     * @param id идентификатор публикации для упорядочения совпадающих дат
     */
    private record Cursor(Instant at, UUID id) {}

    /**
     * Кодирует время и UUID последней записи в непрозрачную границу страницы.
     *
     * @param last последняя публикация текущей страницы
     * @return Base64url-граница без дополнения
     */
    private String encodeCursor(JournalEntry last) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        (last.getCreatedAt() + "|" + last.getId())
                                .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Проверяет курсор относительно исходного этапа и существующей записи.
     *
     * <p>Курсор содержит время и UUID; несоответствие границы не допускает перехода к произвольной
     * позиции чужой ленты.
     *
     * @param cursor курсор предыдущей страницы
     * @param project идентификатор карточки дома
     * @param stage идентификатор этапа этой карточки
     * @return проверенные время и идентификатор границы страницы
     * @throws ApiException при неверном формате или отсутствующей границе
     */
    private Cursor decodeCursor(String cursor, UUID project, UUID stage) {
        try {
            require(cursor.length() <= 200 && !cursor.isBlank());
            String[] split =
                    new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                            .split(Pattern.quote("|"), -1);
            require(split.length == 2);
            Instant at = Instant.parse(split[0]);
            UUID id = uuid(split[1]);
            var anchor = entries.findByIdAndProjectIdAndStageId(id, project, stage);
            require(anchor.isPresent() && anchor.get().getCreatedAt().equals(at));
            return new Cursor(at, id);
        } catch (IllegalArgumentException | DateTimeException _) {
            throw new ApiException("cursor", "Обновите ленту: неверный курсор");
        }
    }
}
