package ru.rshb.stroycontrol.banking;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import ru.rshb.stroycontrol.common.ApiException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Чистые правила предварительного деморасчёта; результат не является кредитным решением. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MortgageMath {
    private static final MathContext PRECISION = MathContext.DECIMAL128;

    /**
     * Проверяет диапазоны и шаги синтетического калькулятора, не исправляя ввод молча.
     *
     * @param cost стоимость дома и участка
     * @param depositPercent доля взноса
     * @param years целое число лет
     */
    static void validate(BigDecimal cost, int depositPercent, int years) {
        if (cost == null
                || cost.compareTo(new BigDecimal("1000000")) < 0
                || cost.compareTo(new BigDecimal("15000000")) > 0
                || cost.remainder(new BigDecimal("100000")).signum() != 0) {
            throw new ApiException("cost", "Стоимость — от 1 до 15 млн ₽ с шагом 100 000 ₽");
        }
        if (depositPercent < 20 || depositPercent > 80 || depositPercent % 5 != 0) {
            throw new ApiException("depositPercent", "Взнос — от 20 до 80% с шагом 5%");
        }
        if (years < 1 || years > 25) throw new ApiException("years", "Срок — от 1 до 25 целых лет");
    }

    /**
     * Вычисляет аннуитет с десятичной точностью и единственным финальным округлением HALF_UP.
     *
     * @param principal сумма кредита в рублях
     * @param annualRate годовая ставка в процентах, включая ноль
     * @param years положительный целый срок в годах
     * @return ежемесячный платёж до копеек
     */
    static BigDecimal payment(BigDecimal principal, BigDecimal annualRate, int years) {
        BigDecimal n = BigDecimal.valueOf(years * 12L);
        if (annualRate.signum() == 0) return principal.divide(n, 2, RoundingMode.HALF_UP);
        BigDecimal r = annualRate.divide(new BigDecimal("1200"), PRECISION);
        BigDecimal factor = BigDecimal.ONE.add(r, PRECISION).pow(years * 12, PRECISION);
        return principal
                .multiply(r, PRECISION)
                .multiply(factor, PRECISION)
                .divide(factor.subtract(BigDecimal.ONE, PRECISION), 2, RoundingMode.HALF_UP);
    }

    /**
     * Убирает масштаб PostgreSQL numeric из JSON; null ставки остаётся null.
     *
     * @param value десятичное число либо null
     * @return эквивалентное число в обычной десятичной записи
     */
    static BigDecimal plain(BigDecimal value) {
        if (value == null) return null;
        BigDecimal result = value.stripTrailingZeros();
        return result.scale() < 0 ? result.setScale(0) : result;
    }
}
