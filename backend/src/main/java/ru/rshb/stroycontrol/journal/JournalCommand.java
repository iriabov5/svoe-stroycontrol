package ru.rshb.stroycontrol.journal;

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
import java.util.Map;
import java.util.UUID;

/**
 * Хранит отпечаток команды журнала и её первоначальный публичный результат в JSONB.
 *
 * <p>Повтор с тем же телом возвращает сохранённый ответ, даже если запись уже изменилась. Другое
 * тело с тем же ключом вызывает конфликт; результат доступен только участнику проекта.
 */
@Entity
@Table(name = "journal_commands")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class JournalCommand {
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "operation")
    private String operation;

    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
