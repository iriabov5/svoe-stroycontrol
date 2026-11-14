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
 * Хранит единственное решение заказчика по отправленной версии договора.
 *
 * <p>Возврат на уточнение требует комментария. Решение фиксируется вместе с состоянием договора;
 * последующая редакция создаётся отдельной версией.
 */
@Entity
@Table(name = "contract_decisions")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ContractDecision {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "contract_id")
    private UUID contractId;

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
