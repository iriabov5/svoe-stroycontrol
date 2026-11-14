package ru.rshb.stroycontrol.journal;

import com.fasterxml.jackson.annotation.JsonInclude;

import ru.rshb.stroycontrol.common.ActorSnapshot;
import ru.rshb.stroycontrol.files.AttachmentView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Публикация этапа для участника карточки.
 *
 * <p>Автор сохранён снимком на момент публикации. Ответ отсутствует, пока заказчик не ответил.
 *
 * @param id идентификатор публикации
 * @param projectId идентификатор карточки дома
 * @param stageId идентификатор этапа этой карточки
 * @param kind NOTE, QUESTION или APPROVAL
 * @param status PUBLISHED, PENDING, ANSWERED, APPROVED или DECLINED
 * @param text текст публикации
 * @param sourceEntryId исходная публикация того же этапа или null
 * @param createdAt время публикации в UTC
 * @param version версия для ответа заказчика
 * @param author снимок автора
 * @param photos фотографии в порядке показа
 * @param attachments готовые закрытые вложения; null допустим в прежнем результате команды
 * @param authorSnapshot персональная подпись автора либо legacy-маркер
 * @param response ответ заказчика или null
 */
record JournalEntryView(
        UUID id,
        UUID projectId,
        UUID stageId,
        String kind,
        String status,
        String text,
        UUID sourceEntryId,
        Instant createdAt,
        long version,
        Author author,
        List<Photo> photos,
        Response response,
        ActorSnapshot authorSnapshot,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<AttachmentView> attachments) {

    /**
     * Снимок автора, не требующий чтения таблицы аккаунтов.
     *
     * @param id идентификатор аккаунта автора
     * @param email email автора на момент публикации
     * @param role роль автора на момент публикации
     */
    record Author(UUID id, String email, String role) {}

    /**
     * Внешняя фотография публикации.
     *
     * @param id идентификатор фотографии
     * @param url HTTPS-ссылка без учётных данных
     * @param caption обязательная подпись
     */
    record Photo(UUID id, String url, String caption) {}

    /**
     * Ответ заказчика без внутренних связей публикации.
     *
     * @param actorId идентификатор владельца, оставившего ответ
     * @param text текст ответа или пояснение отказа; null допустим при согласовании
     * @param decision APPROVED, DECLINED или null для текстового ответа
     * @param authorSnapshot сохранённая подпись заказчика
     * @param createdAt время ответа в UTC
     */
    record Response(
            UUID actorId,
            String text,
            String decision,
            Instant createdAt,
            ActorSnapshot authorSnapshot) {}
}
