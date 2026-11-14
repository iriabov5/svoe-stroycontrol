package ru.rshb.stroycontrol.banking;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Сохраняет результат каждой попытки без сетевых секретов и стектрейсов в пользовательской истории.
 */
@Getter
@Setter(AccessLevel.PACKAGE)
@Entity
@Table(name = "bank_stage_attempts")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class BankStageAttempt {
    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();
    private UUID submissionId;
    private int attemptNumber;
    private String outcome;
    @Builder.Default
    private Instant createdAt = Instant.now();
}
