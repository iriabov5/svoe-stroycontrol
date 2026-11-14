package ru.rshb.stroycontrol.banking;

import java.time.Instant;
import java.util.UUID;

/**
 * Полная история отправки только для владельца карточки.
 *
 * @param id отправка
 * @param projectId проект
 * @param stageId этап
 * @param acceptanceId принятая сдача
 * @param revision номер версии пакета
 * @param previousId предыдущая версия либо null
 * @param state состояние доставки или проверки
 * @param version версия для команды повтора
 * @param bankReference квитанция демо-банка либо null
 * @param reason причина дополнения или безопасное сообщение о сбое
 * @param packet неизменяемый состав
 * @param createdAt создание
 * @param updatedAt последнее изменение
 * @param attemptCount число сохранённых попыток
 */
record BankStageView(
        UUID id,
        UUID projectId,
        UUID stageId,
        UUID acceptanceId,
        int revision,
        UUID previousId,
        String state,
        long version,
        String bankReference,
        String reason,
        BankStagePacket packet,
        Instant createdAt,
        Instant updatedAt,
        int attemptCount) {}
