package ru.rshb.stroycontrol.projects;

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
 * Связывает отклонённую карточку с единственной независимой копией заявки.
 *
 * <p>Связь доступна только владельцу и не включается в общий ответ подрядчика. Уникальные
 * ограничения защищают источник, копию и ключ повторной команды.
 */
@Entity
@Table(name = "project_request_reissues")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class RequestReissue {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "source_project_id")
    private UUID sourceProjectId;

    @Column(name = "target_project_id")
    private UUID targetProjectId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "target_contractor_id")
    private UUID targetContractorId;

    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
