package ru.rshb.stroycontrol.banking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Неизменяемый результат синтетической проверки участка владельца.
 *
 * @param id идентификатор результата
 * @param cadastralNumber номер проверенного участка
 * @param status ELIGIBLE, INELIGIBLE либо отсутствие в демобазе
 * @param address синтетический адрес, null при отсутствии участка
 * @param region синтетический регион, null при отсутствии участка
 * @param landCategory категория земель, null при отсутствии участка
 * @param permittedUse разрешённое использование, null при отсутствии участка
 * @param reason причина отрицательного результата, null для подходящего участка
 * @param rate демонстрационная годовая ставка
 * @param termsVersion версия демонстрационных условий
 * @param checkedAt время получения и сохранения проверки
 * @param isDemo всегда true для синтетического контура
 */
record ProjectPlotCheckView(
        UUID id,
        String cadastralNumber,
        String status,
        String address,
        String region,
        String landCategory,
        String permittedUse,
        String reason,
        BigDecimal rate,
        String termsVersion,
        Instant checkedAt,
        boolean isDemo) {
    /** Сохраняет одинаковую JSON-запись чисел до и после numeric/jsonb round-trip. */
    ProjectPlotCheckView {
        rate = MortgageMath.plain(rate);
    }
}
