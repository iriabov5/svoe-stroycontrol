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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Хранит неизменяемый снимок синтетического одобрения из локального банковского API.
 *
 * <p>Данные доступны только владельцу карточки и не передаются подрядчику. Признак isDemo
 * обязателен; актуальность определяется сроком действия на московскую дату.
 */
@Entity
@Table(name = "mortgage_approval_snapshots")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class MortgageSnapshot {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "program")
    private String program;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "term_months")
    private int termMonths;

    @Column(name = "rate", precision = 6, scale = 2)
    private BigDecimal rate;

    @Column(name = "decision_date")
    private LocalDate decisionDate;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Column(name = "is_demo")
    @Builder.Default
    private boolean isDemo = true;

    /**
     * Возвращает снимок для владельца без связей с карточкой и аккаунтом.
     *
     * <p>Сумма и ставка приводятся к краткой десятичной записи, чтобы повтор после чтения numeric
     * из PostgreSQL совпадал с первым ответом.
     *
     * @return поля REST-контракта, включая признак синтетических данных
     */
    MortgageSnapshotView view() {
        return new MortgageSnapshotView(
                id,
                program,
                plain(amount),
                termMonths,
                plain(rate),
                decisionDate,
                validUntil,
                fetchedAt,
                isDemo);
    }

    /**
     * Убирает хвостовые нули дробной части, сохраняя обычную десятичную запись.
     *
     * @param value сумма или ставка из снимка
     * @return то же число без лишнего масштаба numeric
     */
    private static BigDecimal plain(BigDecimal value) {
        var stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
