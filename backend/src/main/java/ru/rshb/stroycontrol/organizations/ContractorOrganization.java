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
 * Хранит реквизиты подрядчика; редактирование разрешено руководителю под блокировкой организации.
 */
@Entity
@Table(name = "contractor_organizations")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ContractorOrganization {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "owner_account_id")
    private UUID ownerAccountId;

    @Column(name = "type")
    private String type;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "inn")
    private String inn;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "version")
    private long version;

    @Column(name = "creation_key")
    private UUID creationKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "creation_hash", length = 64, columnDefinition = "char(64)")
    private String creationHash;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
