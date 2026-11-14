package ru.rshb.stroycontrol.projects;

import ru.rshb.stroycontrol.common.ActorSnapshot;

import java.time.Instant;
import java.util.UUID;

/**
 * Итог приёмки и явное завершение владельцем; банковское решение считается отдельно.
 *
 * @param projectId проект
 * @param total полный непустой план для завершения
 * @param accepted число принятых этапов
 * @param canComplete можно ли текущему владельцу подтвердить завершение
 * @param reason причина недоступности либо null
 * @param version версия готовности
 * @param completedAt время подтверждения либо null
 * @param completedBy историческая подпись заказчика либо null
 */
record CompletionView(
        UUID projectId,
        int total,
        int accepted,
        boolean canComplete,
        String reason,
        long version,
        Instant completedAt,
        ActorSnapshot completedBy) {}
