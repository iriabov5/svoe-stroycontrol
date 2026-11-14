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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** Запоминает команду приглашения даже при возврате уже открытого приглашения с другим ключом. */
@Entity
@Table(name = "contractor_invitation_keys")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class InvitationKey {
    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @Column(name = "invitation_id")
    private UUID invitationId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", length = 64, columnDefinition = "char(64)")
    private String requestHash;
}
