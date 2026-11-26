package ru.rshb.stroycontrol.banking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Неизменяемый предварительный расчёт, не являющийся банковским одобрением.
 *
 * @param id идентификатор результата
 * @param plotCheckId основание расчёта
 * @param cost стоимость дома вместе с участком в рублях
 * @param depositPercent первоначальный взнос в процентах
 * @param depositAmount первоначальный взнос в рублях
 * @param years целый срок кредита в годах
 * @param joint совместное оформление для выбора демонстрационного лимита
 * @param principal сумма кредита после вычета взноса
 * @param rate демонстрационная годовая ставка
 * @param creditLimit демонстрационный лимит суммы кредита
 * @param monthlyPayment платёж в рублях с точностью до копеек
 * @param withinLimit сумма кредита не превышает лимит
 * @param termsVersion версия демонстрационных условий
 * @param calculatedAt время сохранения серверного расчёта
 * @param isDemo всегда true для синтетического контура
 */
record ProjectMortgageCalculationView(
        UUID id,
        UUID plotCheckId,
        BigDecimal cost,
        int depositPercent,
        BigDecimal depositAmount,
        int years,
        boolean joint,
        BigDecimal principal,
        BigDecimal rate,
        BigDecimal creditLimit,
        BigDecimal monthlyPayment,
        boolean withinLimit,
        String termsVersion,
        Instant calculatedAt,
        boolean isDemo) {
    /** Сохраняет одинаковую JSON-запись чисел до и после numeric/jsonb round-trip. */
    ProjectMortgageCalculationView {
        cost = MortgageMath.plain(cost);
        depositAmount = MortgageMath.plain(depositAmount);
        principal = MortgageMath.plain(principal);
        rate = MortgageMath.plain(rate);
        creditLimit = MortgageMath.plain(creditLimit);
        monthlyPayment = MortgageMath.plain(monthlyPayment);
    }
}
