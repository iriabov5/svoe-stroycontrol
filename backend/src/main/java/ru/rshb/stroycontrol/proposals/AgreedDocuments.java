package ru.rshb.stroycontrol.proposals;

import java.math.BigDecimal;
import java.util.UUID;

/** Даёт банковскому модулю снимок согласованного пакета без доступа к сущностям документов. */
public interface AgreedDocuments {
    /**
     * Читает только согласованные версии доступного владельцу проекта.
     *
     * @param projectId карточка
     * @param ownerId владелец
     * @return текст сметы, договора и цена; в прежнем проекте поля могут отсутствовать
     */
    Snapshot agreed(UUID projectId, UUID ownerId);

    /**
     * Неизменяемые согласованные условия для банковского пакета.
     *
     * @param proposalId согласованная смета либо null
     * @param contractId согласованный договор либо null
     * @param estimateText смета либо null
     * @param contractText договор либо null
     * @param totalPrice цена либо null
     */
    record Snapshot(
            UUID proposalId,
            UUID contractId,
            String estimateText,
            String contractText,
            BigDecimal totalPrice) {}
}
