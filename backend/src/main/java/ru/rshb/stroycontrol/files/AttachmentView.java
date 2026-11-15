package ru.rshb.stroycontrol.files;

import java.time.Instant;
import java.util.UUID;

/**
 * Метаданные готового закрытого файла; адрес содержимого требует действующей сессии.
 *
 * @param id файл
 * @param projectId проект
 * @param stageId этап либо null для банковского дополнения и иллюстрации сметы
 * @param name безопасное исходное имя
 * @param contentType проверенный формат
 * @param size размер в байтах
 * @param sha256 контрольная сумма содержимого
 * @param caption подпись фотографии либо пустая строка для документа
 * @param state READY
 * @param createdAt время загрузки
 * @param contentUrl защищённый адрес содержимого
 */
public record AttachmentView(
        UUID id,
        UUID projectId,
        UUID stageId,
        String name,
        String contentType,
        long size,
        String sha256,
        String caption,
        String state,
        Instant createdAt,
        String contentUrl) {}
