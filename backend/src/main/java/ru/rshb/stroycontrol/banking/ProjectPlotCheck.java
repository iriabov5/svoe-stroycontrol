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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Неизменяемый результат синтетической проверки участка владельца. */
@Entity
@Table(name = "project_plot_checks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ProjectPlotCheck {
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Id
    @Builder.Default
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "cadastral_number")
    private String cadastralNumber;

    @Column(name = "request_version")
    private long requestVersion;

    @Column(name = "status")
    private String status;

    @Column(name = "address")
    private String address;

    @Column(name = "region")
    private String region;

    @Column(name = "land_category")
    private String landCategory;

    @Column(name = "permitted_use")
    private String permittedUse;

    @Column(name = "reason")
    private String reason;

    @Column(name = "rate", precision = 6, scale = 3)
    private BigDecimal rate;

    @Column(name = "terms_version")
    private String termsVersion;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @Builder.Default
    @Column(name = "is_demo")
    private boolean isDemo = true;

    /**
     * Возвращает REST-снимок без projectId и ownerId после проверки доступа.
     *
     * @return поля результата для владельца
     */
    ProjectPlotCheckView view() {
        return new ProjectPlotCheckView(
                id,
                cadastralNumber,
                status,
                address,
                region,
                landCategory,
                permittedUse,
                reason,
                rate,
                termsVersion,
                checkedAt,
                isDemo);
    }
}
