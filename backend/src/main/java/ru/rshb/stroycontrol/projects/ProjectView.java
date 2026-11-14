package ru.rshb.stroycontrol.projects;

import ru.rshb.stroycontrol.common.ActorSnapshot;
import ru.rshb.stroycontrol.organizations.OrganizationView;

import java.time.Instant;
import java.util.UUID;

/**
 * Карточка дома, доступная участнику строительства.
 *
 * @param id идентификатор карточки
 * @param name название дома
 * @param address адрес объекта свободным текстом
 * @param customerId идентификатор заказчика, владеющего карточкой
 * @param contractorId идентификатор подрядчика или {@code null} до назначения
 * @param processMode прежний процесс либо подготовка через предложение
 * @param requestSubmitted заявка отправлена подрядчику
 * @param approvedProposalId согласованная смета либо null
 * @param approvedContractId согласованный договор либо null
 * @param preparationClosed подготовка закрыта окончательным отказом
 * @param contractorOrganizationId назначенная организация либо null в прежнем проекте
 * @param contractorOrganization реквизиты назначенного подрядчика
 * @param ownerPresentation текущая подпись заказчика
 * @param primaryContact текущая подпись основного контакта
 * @param permissions возможности запрашивающего аккаунта
 * @param completedAt подтверждённое завершение либо null
 * @param createdAt время создания в UTC
 */
public record ProjectView(
        UUID id,
        String name,
        String address,
        UUID customerId,
        UUID contractorId,
        Instant createdAt,
        String processMode,
        boolean requestSubmitted,
        UUID approvedProposalId,
        UUID approvedContractId,
        boolean preparationClosed,
        UUID contractorOrganizationId,
        OrganizationView contractorOrganization,
        ActorSnapshot ownerPresentation,
        ActorSnapshot primaryContact,
        Permissions permissions,
        Instant completedAt) {
    /**
     * Вычисленные возможности конкретного участника, перепроверяемые backend при каждой записи.
     *
     * @param canManageTeam управление командой этой стройки
     * @param canPrepareDocuments создание/изменение/отправка документов
     * @param canPublishJournal публикации подрядчика
     * @param canRecordWork начало и завершение этапов
     * @param canAnswerJournal ответы заказчика
     * @param canDecideDocuments решения заказчика
     */
    public record Permissions(
            boolean canManageTeam,
            boolean canPrepareDocuments,
            boolean canPublishJournal,
            boolean canRecordWork,
            boolean canAnswerJournal,
            boolean canDecideDocuments) {}

    /**
     * Создаёт прежнюю модель без представлений; адаптер доступа дополняет её для участника.
     *
     * @param id карточка
     * @param name название
     * @param address адрес
     * @param customerId владелец
     * @param contractorId основной контакт
     * @param createdAt время создания
     * @param processMode процесс подготовки
     * @param requestSubmitted заявка отправлена
     * @param approvedProposalId согласованная смета
     * @param approvedContractId согласованный договор
     * @param preparationClosed подготовка закрыта
     */
    public ProjectView(
            UUID id,
            String name,
            String address,
            UUID customerId,
            UUID contractorId,
            Instant createdAt,
            String processMode,
            boolean requestSubmitted,
            UUID approvedProposalId,
            UUID approvedContractId,
            boolean preparationClosed) {
        this(
                id,
                name,
                address,
                customerId,
                contractorId,
                createdAt,
                processMode,
                requestSubmitted,
                approvedProposalId,
                approvedContractId,
                preparationClosed,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
