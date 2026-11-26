package ru.rshb.stroycontrol.projects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import ru.rshb.stroycontrol.common.ActorSnapshot;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;

import java.time.Instant;
import java.util.UUID;

/**
 * Карточка строящегося дома. Владелец и подрядчик записаны идентификаторами аккаунтов. Внешние
 * ключи проверяет база, JPA-связи с аккаунтом нет.
 */
@Entity
@Table(name = "house_projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class HouseProject {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 120)
    @NonNull
    private String name;

    @Column(nullable = false, length = 300)
    @NonNull
    private String address;

    @Column(name = "area_sqm")
    private Integer areaSqm;

    @Column
    private Integer floors;

    @Column(name = "wall_material", length = 120)
    private String wallMaterial;

    @Column(name = "foundation_type", length = 120)
    private String foundationType;

    @Column(name = "roof_type", length = 120)
    private String roofType;

    @Column(name = "planned_completion", length = 60)
    private String plannedCompletion;

    @Column(name = "customer_id", nullable = false)
    @NonNull
    private UUID customerId;

    /** Идентификатор подрядчика или {@code null}, пока он не назначен. */
    @Column(name = "contractor_id")
    private UUID contractorId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "process_mode", nullable = false)
    @Builder.Default
    private String processMode = "PROPOSAL_REQUIRED";

    @Column(name = "request_submitted", nullable = false)
    private boolean requestSubmitted;

    @Column(name = "approved_proposal_id")
    private UUID approvedProposalId;

    @Column(name = "approved_contract_id")
    private UUID approvedContractId;

    @Column(name = "preparation_closed", nullable = false)
    private boolean preparationClosed;

    @Column(name = "contractor_organization_id")
    @Setter(AccessLevel.PACKAGE)
    private UUID contractorOrganizationId;

    @Column(name = "team_version", nullable = false)
    @Setter(AccessLevel.PACKAGE)
    private long teamVersion;

    @Setter(AccessLevel.PACKAGE)
    private long completionVersion;

    @Setter(AccessLevel.PACKAGE)
    private UUID completionKey;

    @Setter(AccessLevel.PACKAGE)
    private String completionHash;

    @Setter(AccessLevel.PACKAGE)
    private Instant completedAt;

    @Setter(AccessLevel.PACKAGE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ActorSnapshot completedBySnapshot;

    /**
     * Закрывает подготовку исходной карточки после окончательного отказа от сметы.
     *
     * <p>Разрешённость отказа и блокировку проекта обеспечивает сервис документов.
     */
    void closePreparation() {
        preparationClosed = true;
    }

    /**
     * Открывает назначенному подрядчику карточку после отправки заявки.
     *
     * <p>Изменяется вместе с состоянием заявки в общей транзакции.
     */
    void submitRequest() {
        requestSubmitted = true;
    }

    /**
     * Запоминает единственную согласованную версию сметы.
     *
     * <p>Вызывается сервисом проектов в транзакции согласования под блокировкой карточки.
     *
     * @param id идентификатор согласованной версии сметы
     */
    void approveProposal(UUID id) {
        approvedProposalId = id;
    }

    /**
     * Запоминает согласованную версию договора для допуска к началу работ.
     *
     * <p>Вызывается в той же транзакции, что и решение по договору.
     *
     * @param id идентификатор согласованной версии договора
     */
    void approveContract(UUID id) {
        approvedContractId = id;
    }

    /**
     * Проверяет видимость карточки владельцу или назначенному подрядчику.
     *
     * <p>Для подрядчика нового процесса требуется отправленная заявка; LEGACY доступна сразу.
     *
     * @param accountId идентификатор аутентифицированного аккаунта
     * @return true, если карточка доступна аккаунту
     */
    boolean visibleTo(UUID accountId) {
        return customerId.equals(accountId)
                || (accountId.equals(contractorId)
                        && (requestSubmitted || "LEGACY".equals(processMode)));
    }

    /**
     * Проверяет назначение аккаунта участником без учёта отправки заявки.
     *
     * <p>Для выдачи карточки пользователю применяется более строгая проверка visibleTo.
     *
     * @param accountId идентификатор аутентифицированного аккаунта
     * @return true для владельца или назначенного подрядчика
     */
    boolean includes(UUID accountId) {
        return customerId.equals(accountId) || accountId.equals(contractorId);
    }

    /**
     * Назначает подрядчика, сохраняя прежний результат идентичного повтора.
     *
     * @param contractorId идентификатор назначенного подрядчика
     * @throws ApiException с CONTRACTOR_ALREADY_ASSIGNED при попытке заменить участника
     */
    void assign(UUID contractorId) {
        if (this.contractorId != null && !this.contractorId.equals(contractorId)) {
            throw new ApiException(ApiError.CONTRACTOR_ALREADY_ASSIGNED);
        }
        this.contractorId = contractorId;
    }

    /**
     * Возвращает заполненный паспорт дома либо {@code null} для обычной или исторической карточки.
     *
     * <p>Паспорт публикуется только целиком: частично записанные значения не должны создавать для
     * участника ложное ощущение завершённости данных.
     *
     * @return общие характеристики объекта либо null
     */
    HouseProfile houseProfile() {
        if (areaSqm == null
                || floors == null
                || wallMaterial == null
                || foundationType == null
                || roofType == null
                || plannedCompletion == null) return null;
        return new HouseProfile(
                areaSqm, floors, wallMaterial, foundationType, roofType, plannedCompletion);
    }

    /**
     * Возвращает общую модель карточки без банковских сведений и закрытых связей копирования.
     *
     * @return общие поля карточки и состояния подготовки
     */
    ProjectView view() {
        return new ProjectView(
                id,
                name,
                address,
                houseProfile(),
                customerId,
                contractorId,
                createdAt,
                processMode,
                requestSubmitted,
                approvedProposalId,
                approvedContractId,
                preparationClosed);
    }
}
