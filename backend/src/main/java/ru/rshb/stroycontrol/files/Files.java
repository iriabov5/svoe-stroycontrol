package ru.rshb.stroycontrol.files;

import ru.rshb.stroycontrol.common.ApiException;

import java.util.List;
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
     * Возвращает метаданные опубликованных файлов после проверки проекта вызывающим модулем.
     *
     * @param projectId доступный проект
     * @param ids сохранённые ID
     * @return готовые метаданные в исходном порядке
     */
    List<AttachmentView> describe(UUID projectId, List<UUID> ids);
}
