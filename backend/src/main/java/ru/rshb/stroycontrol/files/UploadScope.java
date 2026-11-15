package ru.rshb.stroycontrol.files;

import java.util.UUID;

/**
 * Контекст доступа и повторной загрузки, общий для проверки файла и его сохранения.
 *
 * @param actor автор загрузки
 * @param project карточка дома
 * @param stage этап либо null для банковского дополнения или сметы
 * @param purpose назначение JOURNAL, BANK либо PROPOSAL
 * @param key ключ идемпотентности
 */
record UploadScope(UUID actor, UUID project, UUID stage, String purpose, UUID key) {}
