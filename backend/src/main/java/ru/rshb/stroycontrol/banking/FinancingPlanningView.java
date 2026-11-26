package ru.rshb.stroycontrol.banking;

/**
 * Личный план владельца; отсутствие результатов не создаёт записей в БД.
 *
 * @param version версия плана для конкурирующих команд
 * @param editable разрешена новая запись
 * @param plotCheck последняя проверка либо null
 * @param calculation последний расчёт либо null
 * @param plotCheckCurrent номер совпадает с текущей заявкой
 * @param calculationCurrent расчёт основан на актуальной проверке и условиях
 */
record FinancingPlanningView(
        long version,
        boolean editable,
        ProjectPlotCheckView plotCheck,
        ProjectMortgageCalculationView calculation,
        boolean plotCheckCurrent,
        boolean calculationCurrent) {}
