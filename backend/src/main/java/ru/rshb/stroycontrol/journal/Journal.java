package ru.rshb.stroycontrol.journal;

import ru.rshb.stroycontrol.common.ActorSnapshot;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.files.AttachmentView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Предоставляет неизменяемые подтверждения этапа для сдачи; право операции проверяет вызывающий
 * модуль.
 */
public interface Journal {
    /**
     * Проверяет наличие опубликованного фото-подтверждения по этапу.
     *
     * <p>Метод возвращает только факт, не раскрывая текст публикации, автора или адрес файла. Он
     * используется навигатором контрольных точек для снятия выполненного действия подрядчика.
     *
     * @param projectId карточка строительства
     * @param stageId этап этой карточки
     * @return true, если подрядчик опубликовал хотя бы одно фото
     */
    boolean hasPublishedPhoto(UUID projectId, UUID stageId);

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
}
