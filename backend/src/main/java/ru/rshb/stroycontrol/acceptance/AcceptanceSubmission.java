package ru.rshb.stroycontrol.acceptance;

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
import ru.rshb.stroycontrol.journal.Journal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Владеет циклом сдачи, неизменяемыми подтверждениями и единственным решением под блокировкой
 * проекта.
 */
@Entity
@Table(name = "acceptance_submissions")
@Getter
@Setter(AccessLevel.PACKAGE)
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class AcceptanceSubmission {
    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();
    private UUID projectId;
    private UUID stageId;
    private int cycleNumber;

    @Column(columnDefinition = "text")
    private String summary;

    @Builder.Default
    private String state = "OPEN";
    private long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Journal.Evidence> evidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ActorSnapshot authorSnapshot;

    @Builder.Default
    private Instant createdAt = Instant.now();
    private String decision;

    @Column(columnDefinition = "text")
    private String comment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ActorSnapshot decidedBy;

    private Instant decidedAt;

    SubmissionView view() {
        return new SubmissionView(
                id,
                projectId,
                stageId,
                cycleNumber,
                summary,
                state,
                version,
                evidence,
                authorSnapshot,
                createdAt,
                decision,
                comment,
                decidedBy,
                decidedAt);
    }
}
