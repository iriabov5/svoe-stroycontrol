package ru.rshb.stroycontrol.proposals;

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

import java.time.LocalDate;
import java.util.UUID;

/**
 * Хранит плановый этап конкретной версии сметы до создания исполнительного этапа.
 *
 * <p>После согласования его ID становится источником рабочего этапа; порядок и даты сохраняются
 * независимо от фактического начала и завершения работ.
 */
@Entity
@Table(name = "proposal_stage_plans")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ProposalStagePlan {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "proposal_id")
    private UUID proposalId;

    @Column(name = "position")
    private int position;

    @Column(name = "name")
    private String name;

    @Column(name = "work_type")
    private String workType;

    @Column(name = "expected_result")
    private String expectedResult;

    @Column(name = "planned_start")
    private LocalDate plannedStart;

    @Column(name = "planned_finish")
    private LocalDate plannedFinish;
}
