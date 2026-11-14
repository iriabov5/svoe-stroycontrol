package ru.rshb.stroycontrol.banking;

import java.util.UUID;

/** Публикует только краткий статус доставки без кредитных сведений и причин банка. */
public interface BankStageStatus {
    /**
     * Читает последний статус для уже авторизованного участника этапа.
     *
     * @param projectId проект
     * @param stageId этап
     * @return статус либо null, если отправок не было
     */
    String latest(UUID projectId, UUID stageId);
}
