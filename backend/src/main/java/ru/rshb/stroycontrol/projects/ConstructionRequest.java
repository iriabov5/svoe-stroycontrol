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
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Хранит пожелания заказчика и состояние передачи заявки подрядчику.
 *
 * <p>После отправки поля заявки неизменяемы. Кредитный снимок — закрытая привязка, которая
 * исключается из общей модели заявки. Изменения координирует сервис проектов.
 */
@Entity
@Table(name = "construction_requests")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ConstructionRequest {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "location")
    private String location;

    @Column(name = "state")
    @Builder.Default
    private String state = "DRAFT";

    @Column(name = "version")
    private long version;

    @Column(name = "description")
    private String description;

    @Column(name = "technology")
    private String technology;

    @Column(name = "technology_recommendation")
    private boolean technologyRecommendation;

    @Column(name = "materials")
    private String materials;

    @Column(name = "materials_recommendation")
    private boolean materialsRecommendation;

    @Column(name = "length_m")
    private BigDecimal lengthM;

    @Column(name = "width_m")
    private BigDecimal widthM;

    @Column(name = "area_m2")
    private BigDecimal areaM2;

    @Column(name = "floors")
    private Integer floors;

    @Column(name = "completion_scope")
    private String completionScope;

    @Column(name = "desired_budget")
    private BigDecimal desiredBudget;

    @Column(name = "cadastral_number")
    private String cadastralNumber;

    @Column(name = "desired_start")
    private LocalDate desiredStart;

    @Column(name = "desired_finish")
    private LocalDate desiredFinish;

    @Column(name = "financing_source")
    private String financingSource;

    @Column(name = "mortgage_snapshot_id")
    private UUID mortgageSnapshotId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /**
     * Возвращает заявку для участника карточки.
     *
     * <p>Идентификатор кредитного снимка остаётся внутри модуля и в ответ не входит.
     *
     * @return пожелания заказчика без закрытой банковской привязки
     */
    RequestView view() {
        return new RequestView(
                id,
                projectId,
                location,
                state,
                version,
                description,
                technology,
                technologyRecommendation,
                materials,
                materialsRecommendation,
                lengthM,
                widthM,
                areaM2,
                floors,
                completionScope,
                desiredBudget,
                cadastralNumber,
                desiredStart,
                desiredFinish,
                financingSource,
                submittedAt);
    }
}
