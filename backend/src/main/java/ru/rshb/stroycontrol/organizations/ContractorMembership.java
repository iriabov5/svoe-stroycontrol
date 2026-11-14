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

import java.time.Instant;
import java.util.UUID;

/**
 * Хранит активное либо отозванное членство; повторное вступление получает новый ID и не
 * восстанавливает проектные назначения.
 */
@Entity
@Table(name = "contractor_memberships")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ContractorMembership {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "position")
    private String position;

    @Column(name = "role")
    private String role;

    @Column(name = "state")
    private String state;

    @Column(name = "version")
    private long version;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "removed_at")
    private Instant removedAt;
}
