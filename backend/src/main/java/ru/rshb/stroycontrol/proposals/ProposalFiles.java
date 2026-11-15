package ru.rshb.stroycontrol.proposals;

import ru.rshb.stroycontrol.common.ApiException;

import java.util.UUID;

/** Публичная граница видимости иллюстраций сметы без доступа к JPA других модулей. */
public interface ProposalFiles {
    /**
     * Проверяет право загрузить изображение в существующий редактируемый черновик.
     *
     * @param projectId карточка под блокировкой вызывающего модуля
     * @param actorId текущий участник
     * @throws ApiException при отсутствии доступа, права подготовки или черновика
     */
    void requireUpload(UUID projectId, UUID actorId);

    /**
     * Проверяет видимость изображения с учётом черновиков и отправленных версий.
     *
     * @param projectId доступный проект
     * @param actorId текущий читатель
     * @param attachmentId проверяемое вложение
     * @param uploaderId автор загрузки, которому доступна ещё не связанная картинка
     * @return разрешено ли чтение при текущих правах
     */
    boolean canRead(UUID projectId, UUID actorId, UUID attachmentId, UUID uploaderId);
}
