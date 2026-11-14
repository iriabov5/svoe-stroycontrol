package ru.rshb.stroycontrol.journal;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.rshb.stroycontrol.accounts.AccountView;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * Предоставляет HTTP-операции ленты этапа, публикаций подрядчика и ответов заказчика.
 *
 * <p>Аутентифицированный аккаунт берётся из сессии; права на конкретную карточку проверяет сервис.
 * Изменяющие запросы дополнительно защищены общим CSRF-фильтром.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/stages/{stageId}/journal")
@RequiredArgsConstructor
class JournalController {
    private final JournalService journal;

    /**
     * Возвращает страницу публикаций с полным счётчиком ожидающих ответа.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа в указанной карточке
     * @param cursor курсор предыдущей страницы или null для первой страницы
     * @param awaitingOnly true для выбора только ожидающих ответа записей
     * @return страница публикаций, курсор продолжения и число ожидающих ответа
     */
    @GetMapping
    JournalPage list(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "false") boolean awaitingOnly) {
        return journal.list(actor, projectId, stageId, cursor, awaitingOnly);
    }

    /**
     * Возвращает одну публикацию указанного этапа.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа в указанной карточке
     * @param entryId идентификатор публикации этого этапа
     * @return публикация с автором, фотографиями и ответом
     */
    @GetMapping("/{entryId}")
    JournalEntryView get(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId,
            @PathVariable UUID entryId) {
        return journal.get(actor, projectId, stageId, entryId);
    }

    /**
     * Публикует запись подрядчика и возвращает её Location.
     *
     * <p>Создание возвращает 201, идентичный повтор — 200 с первоначальной публикацией.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа в указанной карточке
     * @param body вид записи, текст, фотографии, исходная публикация и ключ повтора
     * @return созданная или повторно найденная публикация со статусом 201 либо 200
     */
    @PostMapping
    ResponseEntity<JournalEntryView> publish(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId,
            @RequestBody Map<String, Object> body) {
        var result = journal.publish(actor, projectId, stageId, body);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(
                        URI.create(
                                "/api/v1/projects/%s/stages/%s/journal/%s"
                                        .formatted(projectId, stageId, result.entry().id())))
                .body(result.entry());
    }

    /**
     * Сохраняет единственный ответ или решение владельца по публикации.
     *
     * <p>Идентичный повтор возвращает первоначальный результат команды.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа в указанной карточке
     * @param entryId идентификатор публикации этого этапа
     * @param body версия публикации, ключ повтора, текст ответа и решение по согласованию
     * @return публикация с сохранённым ответом
     */
    @PostMapping("/{entryId}/response")
    JournalEntryView respond(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId,
            @PathVariable UUID entryId,
            @RequestBody Map<String, Object> body) {
        return journal.respond(actor, projectId, stageId, entryId, body);
    }
}
