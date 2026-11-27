package ru.rshb.stroycontrol.journal;

import ru.rshb.stroycontrol.common.ActorSnapshot;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.files.AttachmentView;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Предоставляет неизменяемые подтверждения этапа для сдачи; право операции проверяет вызывающий
 * модуль.
 */
public interface Journal {
    /**
     * Снимает подтверждения опубликованных записей под блокировкой проекта.
     *
     * @param projectId проект
     * @param stageId этап
     * @param entryIds непустой уникальный список своих записей
     * @return снимки текста, автора и только реальных загруженных вложений
     * @throws ApiException при чужой записи или отсутствии фотографии
     */
    List<Evidence> evidence(UUID projectId, UUID stageId, List<UUID> entryIds);

    /**
     * Исторический снимок записи без ответа, способного измениться после сдачи.
     *
     * @param id публикация
     * @param text текст
     * @param authorSnapshot сохранённая подпись
     * @param createdAt время публикации
     * @param attachments закрытые вложения
     */
    record Evidence(
            UUID id,
            String text,
            ActorSnapshot authorSnapshot,
            Instant createdAt,
            List<AttachmentView> attachments) {}

    /**
     * Находит этапы с опубликованной NOTE и настоящим закрытым изображением.
     *
     * @param projects авторизованные карточки в активной транзакции
     * @return этапы с подтверждениями без текстов и адресов файлов
     */
    Set<UUID> confirmedStages(Set<UUID> projects);
}
