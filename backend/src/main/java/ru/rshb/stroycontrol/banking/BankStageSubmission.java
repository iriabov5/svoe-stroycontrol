package ru.rshb.stroycontrol.banking;

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

import java.time.Instant;
import java.util.UUID;

/**
 * Хранит версию банковского пакета и отдельное состояние доставки с арендой одной активной попытки.
 */
@Entity
@Table(name = "bank_stage_submissions")
@Getter
@Setter(AccessLevel.PACKAGE)
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class BankStageSubmission {
    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();
    private UUID projectId;
    private UUID stageId;
    private UUID actorId;
    private UUID acceptanceId;
    private int revision;
    private UUID previousId;
    @Builder.Default
    private String state = "PENDING";
    private long version;
    private UUID idempotencyKey;
    private String requestHash;
    @Builder.Default
    private UUID bankKey = UUID.randomUUID();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private BankStagePacket packet;

    private String bankReference;
    private String reason;
    private int attemptCount;
    private int autoAttempts;
    private UUID leaseToken;
    private Instant leaseUntil;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();

    BankStageView view() {
        return new BankStageView(
                id,
                projectId,
                stageId,
                acceptanceId,
                revision,
                previousId,
                state,
                version,
                bankReference,
                reason,
                packet,
                createdAt,
                updatedAt,
                attemptCount);
    }
}
