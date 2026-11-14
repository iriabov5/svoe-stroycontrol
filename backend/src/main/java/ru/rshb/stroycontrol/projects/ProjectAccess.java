package ru.rshb.stroycontrol.projects;

import java.util.UUID;

/**
 * Права проверенного участника карточки.
 *
 * @param canPrepareDocuments разрешена подготовка документов
 * @param canPublishJournal разрешена публикация подрядчика
 * @param canRecordWork разрешено фиксировать работы
 * @param canManageTeam разрешено управление командой
 * @param organizationId назначенная организация либо null
 * @param projectId идентификатор карточки
 * @param owner аккаунт владеет карточкой
 * @param processMode режим подготовки проекта
 * @param approvedProposalId согласованная смета либо null
 * @param approvedContractId согласованный договор либо null
 * @param preparationClosed подготовка закрыта
 * @param contractor аккаунт является назначенным подрядчиком
 */
public record ProjectAccess(
        UUID projectId,
        boolean owner,
        boolean contractor,
        String processMode,
        UUID approvedProposalId,
        UUID approvedContractId,
        boolean preparationClosed,
        boolean canPrepareDocuments,
        boolean canPublishJournal,
        boolean canRecordWork,
        boolean canManageTeam,
        UUID organizationId) {
    /**
     * Совместимость проверок прежнего режима.
     *
     * @param projectId проект
     * @param owner владелец
     * @param contractor подрядчик
     */
    public ProjectAccess(UUID projectId, boolean owner, boolean contractor) {
        this(
                projectId,
                owner,
                contractor,
                "LEGACY",
                null,
                null,
                false,
                contractor,
                contractor,
                contractor,
                contractor,
                null);
    }

    /**
     * Сохраняет прежний внутренний контракт одиночного подрядчика.
     *
     * @param projectId проект
     * @param owner владелец
     * @param contractor прежний назначенный подрядчик
     * @param processMode процесс
     * @param approvedProposalId смета
     * @param approvedContractId договор
     * @param preparationClosed закрытая подготовка
     */
    public ProjectAccess(
            UUID projectId,
            boolean owner,
            boolean contractor,
            String processMode,
            UUID approvedProposalId,
            UUID approvedContractId,
            boolean preparationClosed) {
        this(
                projectId,
                owner,
                contractor,
                processMode,
                approvedProposalId,
                approvedContractId,
                preparationClosed,
                contractor,
                contractor,
                contractor,
                contractor,
                null);
    }
}
