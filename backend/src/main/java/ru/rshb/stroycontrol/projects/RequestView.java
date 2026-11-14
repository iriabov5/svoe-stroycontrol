package ru.rshb.stroycontrol.projects;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Заявка заказчика без закрытой привязки кредитного снимка.
 *
 * @param id идентификатор заявки
 * @param projectId идентификатор карточки дома
 * @param location адрес участка
 * @param state DRAFT или SUBMITTED
 * @param version версия для изменяющих команд
 * @param description пожелания к дому или null
 * @param technology выбранная технология или null
 * @param technologyRecommendation заказчик просит рекомендовать технологию
 * @param materials выбранные материалы или null
 * @param materialsRecommendation заказчик просит рекомендовать материалы
 * @param lengthM длина дома в метрах или null
 * @param widthM ширина дома в метрах или null
 * @param areaM2 площадь дома в квадратных метрах или null
 * @param floors число этажей или null
 * @param completionScope комплектация или null
 * @param desiredBudget желаемый бюджет или null
 * @param cadastralNumber кадастровый номер или null
 * @param desiredStart желаемое начало или null
 * @param desiredFinish желаемое завершение или null
 * @param financingSource OWN_FUNDS, RSHB_RURAL_MORTGAGE или null
 * @param submittedAt время отправки или null
 */
record RequestView(
        UUID id,
        UUID projectId,
        String location,
        String state,
        long version,
        String description,
        String technology,
        boolean technologyRecommendation,
        String materials,
        boolean materialsRecommendation,
        BigDecimal lengthM,
        BigDecimal widthM,
        BigDecimal areaM2,
        Integer floors,
        String completionScope,
        BigDecimal desiredBudget,
        String cadastralNumber,
        LocalDate desiredStart,
        LocalDate desiredFinish,
        String financingSource,
        Instant submittedAt) {}
