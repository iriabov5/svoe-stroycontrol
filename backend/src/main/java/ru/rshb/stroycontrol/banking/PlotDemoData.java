package ru.rshb.stroycontrol.banking;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import ru.rshb.stroycontrol.common.ApiException;

import java.math.BigDecimal;

/** Изолированный набор синтетических условий PR; действующие банковские правила не моделирует. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PlotDemoData {
    private static final String LAND_CATEGORY = "Земли населённых пунктов";

    static final String TERMS_VERSION = "pr1-demo-v1";

    /**
     * Проверяет только формат команды, не ограничивая существующее поле строительной заявки.
     *
     * @param value сохранённый кадастровый номер
     * @return номер без краевых пробелов
     * @throws ApiException с адресной ошибкой кадастрового номера
     */
    static String number(String value) {
        if (value == null || !value.strip().matches("\\d{2}:\\d{2}:\\d{6,7}:\\d{1,7}")) {
            throw new ApiException(
                    "cadastralNumber",
                    "Введите номер в формате 16:24:070801:412 (6 или 7 цифр в квартале)");
        }
        return value.strip();
    }

    /**
     * Возвращает один из трёх примеров либо отсутствие в демобазе.
     *
     * @param number проверенный формат номера
     * @return синтетические сведения, без обращения к реестрам
     */
    static PlotDemoResponse result(String number) {
        return switch (number) {
            case "16:24:070801:412" ->
                    new PlotDemoResponse(
                            number,
                            "ELIGIBLE",
                            "Республика Татарстан, Лаишевский район, село Сокуры",
                            "Республика Татарстан",
                            LAND_CATEGORY,
                            "ИЖС",
                            null,
                            new BigDecimal("3"),
                            TERMS_VERSION);
            case "31:15:1203001:88" ->
                    new PlotDemoResponse(
                            number,
                            "ELIGIBLE",
                            "Белгородская область, приграничная сельская территория",
                            "Белгородская область",
                            LAND_CATEGORY,
                            "ЛПХ",
                            null,
                            new BigDecimal("0.1"),
                            TERMS_VERSION);
            case "50:21:0040112:105" ->
                    new PlotDemoResponse(
                            number,
                            "INELIGIBLE",
                            "Московская область, Ленинский городской округ",
                            "Московская область",
                            LAND_CATEGORY,
                            "ИЖС",
                            "В демосценарии Московская область входит в список регионов-исключений программы «Сельская ипотека».",
                            null,
                            null);
            default ->
                    new PlotDemoResponse(
                            number,
                            "NOT_FOUND",
                            null,
                            null,
                            null,
                            null,
                            "Участок отсутствует в демобазе",
                            null,
                            null);
        };
    }
}
