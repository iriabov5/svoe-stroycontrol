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

import java.time.Instant;
import java.util.UUID;

/**
 * Хранит результат попытки демонстрационного импорта и исходную версию заявки.
 *
 * <p>Уникальный ключ владельца, проекта и команды предотвращает повторный импорт. Ссылка на снимок
 * может отсутствовать, если одобрение не найдено.
 */
@Entity
@Table(name = "mortgage_import_requests")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class MortgageImport {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @Column(name = "request_version")
    private long requestVersion;

    @Column(name = "result_status")
    private String resultStatus;

    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
