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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Сохраняет исходный результат JSON-команды приёмки для точного повтора после решения или
 * переименования.
 */
@Entity
@Table(name = "acceptance_commands")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class AcceptanceCommand {
    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();
    private UUID actorId;
    private UUID projectId;
    private String operation;
    private UUID idempotencyKey;
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private SubmissionView result;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
