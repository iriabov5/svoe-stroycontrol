package ru.rshb.stroycontrol.guidance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only паспорт: нет второй копии заявки и выдуманных характеристик.
 *
 * @param projectId доступный проект
 * @param areaM2 сохранённая площадь заявки
 * @param floors этажность заявки
 * @param wallMaterial материал стен
 * @param foundationType тип фундамента
 * @param roofType кровля
 * @param plannedFinish окончание полного действующего плана
 * @param desiredFinish пожелание заказчика отдельно от плана
 * @param sources источники значений; отсутствие обозначено null
 */
record ConstructionProfileView(
        UUID projectId,
        BigDecimal areaM2,
        Integer floors,
        String wallMaterial,
        String foundationType,
        String roofType,
        LocalDate plannedFinish,
        LocalDate desiredFinish,
        Map<String, String> sources) {}
