package ru.rshb.stroycontrol.files;

import java.util.UUID;

/**
 * Контекст доступа и повторной загрузки, общий для проверки файла и его сохранения.
 *
 * @param actor автор загрузки
 * @param project карточка дома
 * @param stage этап либо null для банковского дополнения
 * @param purpose назначение JOURNAL либо BANK
 * @param key ключ идемпотентности
 */
record UploadScope(UUID actor, UUID project, UUID stage, String purpose, UUID key) {}
