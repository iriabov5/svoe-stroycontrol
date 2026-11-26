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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** Неизменяемый ответ успешной команды для безопасного повтора после потери ответа HTTP. */
@Entity
@Table(name = "financing_planning_commands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class FinancingPlanningCommand {
    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();
    private UUID projectId;
    private UUID ownerId;
    private UUID idempotencyKey;
    private String operation;
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private FinancingPlanningView result;
}
