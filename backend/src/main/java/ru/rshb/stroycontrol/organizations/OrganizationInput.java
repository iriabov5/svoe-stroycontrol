package ru.rshb.stroycontrol.organizations;

import static ru.rshb.stroycontrol.common.PreparationInput.field;
import static ru.rshb.stroycontrol.common.PreparationInput.text;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.common.PreparationInput;

import java.util.Map;
import java.util.Set;

/** Проверяет реквизиты без внешней проверки существования компании и полномочий. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OrganizationInput {
    /**
     * Проверяет обязательное текстовое поле и связывает ошибку с его именем.
     *
     * @param body поля формы
     * @param key имя поля
     * @param max предел длины
     * @return непустая строка
     * @throws ApiException при ошибке поля
     */
    static String required(Map<String, Object> body, String key, int max) {
        String value = PreparationInput.fieldValue(key, () -> text(body.get(key), max));
        field(value != null, key, "Заполните поле");
        return value;
    }

    /**
     * Проверяет длину и контрольные цифры ИНН для выбранного типа подрядчика.
     *
     * @param type LEGAL_ENTITY либо SOLE_TRADER
     * @param inn введённый ИНН
     * @throws ApiException при неверном формате или контрольных цифрах
     */
    static void inn(String type, String inn) {
        field(
                Set.of("LEGAL_ENTITY", "SOLE_TRADER").contains(type),
                "type",
                "Выберите организацию или ИП");
        int size = type.equals("LEGAL_ENTITY") ? 10 : 12;
        field(inn.matches("[0-9]{" + size + "}"), "inn", "Проверьте длину ИНН");
        boolean valid =
                size == 10
                        ? digit(inn, new int[] {2, 4, 10, 3, 5, 9, 4, 6, 8}) == inn.charAt(9) - '0'
                        : digit(inn, new int[] {7, 2, 4, 10, 3, 5, 9, 4, 6, 8})
                                        == inn.charAt(10) - '0'
                                && digit(inn, new int[] {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8})
                                        == inn.charAt(11) - '0';
        field(valid && !inn.matches("0+"), "inn", "Проверьте контрольные цифры ИНН");
    }

    private static int digit(String inn, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) sum += (inn.charAt(i) - '0') * weights[i];
        return sum % 11 % 10;
    }
}
