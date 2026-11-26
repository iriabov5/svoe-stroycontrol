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

/** Неизменяемый предварительный расчёт, не являющийся банковским одобрением. */
@Entity
@Table(name = "project_mortgage_calculations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ProjectMortgageCalculation {
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Id
    @Builder.Default
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "plot_check_id")
    private UUID plotCheckId;

    @Column(name = "cost", precision = 15, scale = 2)
    private BigDecimal cost;

    @Column(name = "deposit_percent")
    private int depositPercent;

    @Column(name = "deposit_amount", precision = 15, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "years")
    private int years;

    @Column(name = "joint")
    private boolean joint;

    @Column(name = "principal", precision = 15, scale = 2)
    private BigDecimal principal;

    @Column(name = "rate", precision = 6, scale = 3)
    private BigDecimal rate;

    @Column(name = "credit_limit", precision = 15, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "monthly_payment", precision = 15, scale = 2)
    private BigDecimal monthlyPayment;

    @Column(name = "within_limit")
    private boolean withinLimit;

    @Column(name = "terms_version")
    private String termsVersion;

    @Column(name = "calculated_at")
    private Instant calculatedAt;

    @Builder.Default
    @Column(name = "is_demo")
    private boolean isDemo = true;

    /**
     * Возвращает REST-снимок без projectId и ownerId после проверки доступа.
     *
     * @return поля результата для владельца
     */
    ProjectMortgageCalculationView view() {
        return new ProjectMortgageCalculationView(
                id,
                plotCheckId,
                cost,
                depositPercent,
                depositAmount,
                years,
                joint,
                principal,
                rate,
                creditLimit,
                monthlyPayment,
                withinLimit,
                termsVersion,
                calculatedAt,
                isDemo);
    }
}
