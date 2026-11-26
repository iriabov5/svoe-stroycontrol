package ru.rshb.stroycontrol.projects;

/**
 * Передаёт общие параметры строящегося дома участникам проекта.
 *
 * <p>Паспорт не содержит персональных, банковских или юридически значимых сведений. Он может
 * отсутствовать у ранее созданной карточки; тогда API передаёт {@code null}, а не вымышленные
 * значения.
 *
 * @param areaSqm ориентировочная площадь дома в квадратных метрах
 * @param floors число этажей
 * @param wallMaterial конструкция наружных стен
 * @param foundationType тип фундамента
 * @param roofType тип кровли
 * @param plannedCompletion понятный участникам ориентир срока завершения
 */
public record HouseProfile(
        int areaSqm,
        int floors,
        String wallMaterial,
        String foundationType,
        String roofType,
        String plannedCompletion) {}
