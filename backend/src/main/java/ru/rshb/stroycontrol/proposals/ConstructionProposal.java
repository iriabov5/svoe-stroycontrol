package ru.rshb.stroycontrol.proposals;

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

import ru.rshb.stroycontrol.common.ActorSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Хранит отдельную версию сметы подрядчика и её состояние согласования.
 *
 * <p>Изображения, план этапов и решение принадлежат этой версии; отправленные версии сохраняются
 * для истории. Ссылка на заявку другого модуля представлена только UUID.
 */
@Entity
@Table(name = "construction_proposals")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ConstructionProposal {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "revision_no")
    private int revisionNo;

    @Column(name = "state")
    @Builder.Default
    private String state = "DRAFT";

    @Column(name = "version")
    private long version;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "estimate_text")
    @Builder.Default
    private String estimateText = "";

    @Column(name = "terms_text")
    private String termsText;

    @Column(name = "total_price")
    private BigDecimal totalPrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "created_by_snapshot", columnDefinition = "jsonb")
    private ActorSnapshot createdBySnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "submitted_by_snapshot", columnDefinition = "jsonb")
    private ActorSnapshot submittedBySnapshot;

    @Column(name = "submitted_by_id")
    private UUID submittedById;
}
