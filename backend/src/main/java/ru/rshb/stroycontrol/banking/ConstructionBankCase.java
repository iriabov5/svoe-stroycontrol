package ru.rshb.stroycontrol.banking;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Позднее подключённое синтетическое дело не меняет отправленную строительную заявку. */
@Entity
@Table(name = "bank_construction_cases")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class ConstructionBankCase {
    @Id
    private UUID projectId;
    @Builder.Default
    private UUID caseId = UUID.randomUUID();
    private UUID ownerId;
    private UUID idempotencyKey;
    private String approvalStatus;
    @Builder.Default
    private Instant createdAt = Instant.now();
}
