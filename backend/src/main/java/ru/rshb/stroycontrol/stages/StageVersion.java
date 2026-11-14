package ru.rshb.stroycontrol.stages;

import java.util.UUID;

/** Передаёт ID и ожидаемую версию этапа для атомарного изменения порядка. */
record StageVersion(UUID id, Long version) {}
