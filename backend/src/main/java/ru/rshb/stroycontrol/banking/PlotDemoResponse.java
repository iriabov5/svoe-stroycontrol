package ru.rshb.stroycontrol.banking;

import java.math.BigDecimal;

/**
 * Синтетический ответ локального источника; не является заключением об участке.
 *
 * @param cadastralNumber проверенный номер
 * @param status ELIGIBLE, INELIGIBLE или NOT_FOUND
 * @param address тестовый адрес либо null
 * @param region тестовый регион либо null
 * @param landCategory категория либо null
 * @param permittedUse использование либо null
 * @param reason причина отрицательного результата либо null
 * @param rate ставка подходящего примера либо null
 * @param termsVersion версия демоусловий либо null
 */
record PlotDemoResponse(
        String cadastralNumber,
        String status,
        String address,
        String region,
        String landCategory,
        String permittedUse,
        String reason,
        BigDecimal rate,
        String termsVersion) {}
