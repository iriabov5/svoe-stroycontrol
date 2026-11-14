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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import ru.rshb.stroycontrol.common.ActorSnapshot;

import java.time.Instant;
import java.util.UUID;

/**
 * Хранит единственное решение заказчика по отправленной версии сметы.
 *
 * <p>Возврат на уточнение и окончательный отказ требуют причины. Решение и смена состояния
 * документа фиксируются атомарно и не переписывают предыдущие версии.
 */
@Entity
@Table(name = "proposal_decisions")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ProposalDecision {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "proposal_id")
    private UUID proposalId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "decision")
    private String decision;

    @Column(name = "comment")
    private String comment;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actor_snapshot", columnDefinition = "jsonb")
    private ActorSnapshot actorSnapshot;
}
