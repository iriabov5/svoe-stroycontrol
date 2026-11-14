package ru.rshb.stroycontrol.projects;

import java.util.UUID;

/**
 * Закрытая ссылка владельца на копию заявки.
 *
 * @param targetProjectId идентификатор созданной карточки или null, пока копии нет
 */
record ReissueLink(UUID targetProjectId) {}
