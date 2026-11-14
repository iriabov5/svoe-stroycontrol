package ru.rshb.stroycontrol.stages;

import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.common.Input;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Передаёт проверенный план этапа: название, вид работ и необязательные даты.
 *
 * <p>Название нормализуется, вид работ обязателен, завершение не может предшествовать началу.
 */
record StagePlan(
        String name, StageWorkType workType, LocalDate plannedStart, LocalDate plannedFinish) {
    /**
     * Проверяет и нормализует значения плана до сохранения этапа.
     *
     * @param name непустое название до 120 символов
     * @param workType обязательный вид строительных работ
     * @param plannedStart плановая дата начала либо null
     * @param plannedFinish плановая дата завершения либо null
     * @throws ApiException с VALIDATION_ERROR при неверных полях или INVALID_STAGE_DATES при
     *     завершении раньше начала
     */
    StagePlan {
        name = Input.text(name, 120);
        if (workType == null) throw new ApiException(ApiError.VALIDATION_ERROR);
        if (plannedStart != null && plannedFinish != null && plannedFinish.isBefore(plannedStart)) {
            throw new ApiException(ApiError.INVALID_STAGE_DATES);
        }
    }

    /**
     * Разбирает ISO-даты и формирует проверенный план этапа.
     *
     * <p>Отсутствующие даты допустимы; название и вид работ обязательны.
     *
     * @param name название карточки дома
     * @param type вид строительных работ
     * @param start необязательная плановая дата начала
     * @param finish необязательная плановая дата завершения
     * @return проверенный план с нормализованным названием и разобранными датами
     * @throws ApiException при неверном названии, виде работ или датах
     */
    static StagePlan parse(String name, StageWorkType type, String start, String finish) {
        return new StagePlan(name, type, date(start), date(finish));
    }

    /**
     * Разбирает необязательную дату в строгом формате YYYY-MM-DD.
     *
     * @param input входные поля операции по её REST-контракту
     * @return ISO-дата либо null при отсутствии входного значения
     * @throws ApiException с INVALID_STAGE_DATES при неверном формате или годе
     */
    private static LocalDate date(String input) {
        if (input == null) return null;
        try {
            if (!input.matches("\\d{4}-\\d{2}-\\d{2}"))
                throw new ApiException(ApiError.INVALID_STAGE_DATES);
            var date = LocalDate.parse(input);
            if (date.getYear() < 1) throw new ApiException(ApiError.INVALID_STAGE_DATES);
            return date;
        } catch (DateTimeParseException _) {
            throw new ApiException(ApiError.INVALID_STAGE_DATES);
        }
    }
}
