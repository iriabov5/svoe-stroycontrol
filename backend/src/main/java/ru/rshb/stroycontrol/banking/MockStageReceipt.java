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

/** Сохраняет квитанцию локального банка по уникальному ключу, включая перезапуск приложения. */
@Entity
@Table(name = "bank_mock_stage_receipts")
@Getter
@Setter(AccessLevel.PACKAGE)
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class MockStageReceipt {
    @Id
    private UUID id;
    private String payloadHash;
    @Builder.Default
    private String state = "RECEIVED";
    private String targetState;
    private String reason;
    @Builder.Default
    private Instant receivedAt = Instant.now();
}
