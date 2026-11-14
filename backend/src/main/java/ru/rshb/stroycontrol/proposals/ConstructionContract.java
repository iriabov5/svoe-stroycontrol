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
 * Хранит отдельную версию текста договора к согласованной смете.
 *
 * <p>Возврат на уточнение создаёт новую версию, сохраняя текст и решение предыдущей. Согласование
 * фиксируется в общей транзакции с изменением карточки проекта.
 */
@Entity
@Table(name = "construction_contracts")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ConstructionContract {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "revision_no")
    private int revisionNo;

    @Column(name = "state")
    @Builder.Default
    private String state = "DRAFT";

    @Column(name = "version")
    private long version;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "proposal_id")
    private UUID proposalId;

    @Column(name = "contract_text")
    @Builder.Default
    private String contractText = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "created_by_snapshot", columnDefinition = "jsonb")
    private ActorSnapshot createdBySnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "submitted_by_snapshot", columnDefinition = "jsonb")
    private ActorSnapshot submittedBySnapshot;

    @Column(name = "submitted_by_id")
    private UUID submittedById;
}
