package ru.rshb.stroycontrol.organizations;

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
 * Хранит адресованное аккаунту приглашение и решение; до принятия не даёт членства и доступа к
 * стройке.
 */
@Entity
@Table(name = "contractor_invitations")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ContractorInvitation {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "recipient_account_id")
    private UUID recipientAccountId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "position")
    private String position;

    @Column(name = "state")
    private String state;

    @Column(name = "version")
    private long version;

    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;
}
