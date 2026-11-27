package ru.rshb.stroycontrol.files;

import ru.rshb.stroycontrol.common.ApiException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Публичная граница готовых вложений; вызывающий модуль предварительно проверяет права операции.
 */
public interface Files {
    /**
     * Связывает готовые файлы со снимком записи и удерживает их от очистки в общей транзакции.
     *
     * @param projectId проект под блокировкой
     * @param stageId этап; null допустим для банковского документа
     * @param ids уникальные ID готовых файлов
     * @return неизменяемые метаданные в переданном порядке
     * @throws ApiException при чужом, неготовом или уже очищаемом файле
     */
    List<AttachmentView> retain(UUID projectId, UUID stageId, List<UUID> ids);

    /**
     * Удерживает изображения сметы в транзакции сохранения её версии.
     *
     * @param projectId проект под блокировкой, права записи проверяет модуль смет
     * @param ids уникальные READY-файлы назначения PROPOSAL, не более десяти
     * @return метаданные в заданном порядке
     * @throws ApiException если файл чужой, неготовый или имеет другое назначение
     */
    List<AttachmentView> retainProposal(UUID projectId, List<UUID> ids);

    /**
     * Возвращает метаданные опубликованных файлов после проверки проекта вызывающим модулем.
     *
     * @param projectId доступный проект
     * @param ids сохранённые ID
     * @return готовые метаданные в исходном порядке
     */
    List<AttachmentView> describe(UUID projectId, List<UUID> ids);

    /**
     * Читает принадлежность готовых удерживаемых изображений без S3 и изменения удержания.
     *
     * @param ids опубликованные ID, проверяемые вызывающим владельцем публикаций
     * @return только готовые изображения назначения JOURNAL
     */
    Map<UUID, ImageLocation> journalImages(Set<UUID> ids);

    /**
     * Принадлежность закрытого изображения без объектного ключа и адреса содержимого.
     *
     * @param projectId карточка
     * @param stageId этап
     */
    record ImageLocation(UUID projectId, UUID stageId) {}
}
