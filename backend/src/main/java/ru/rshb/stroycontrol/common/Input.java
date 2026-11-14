package ru.rshb.stroycontrol.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Locale;
import java.util.regex.Pattern;

/** Общие правила ввода, соответствующие ограничениям OpenAPI. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Input {
    private static final Pattern EMAIL =
            Pattern.compile(
                    "[^\\s@]++@[^\\s@.]++(?:\\.[^\\s@.]++)++", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Проверяет email и возвращает ключ для поиска и хранения.
     *
     * @param raw введённый email с возможными пробелами по краям
     * @return нормализованный email длиной до 254 символов
     * @throws ApiException при нарушении ограничений ввода
     */
    public static String email(String raw) {
        require(raw != null && length(raw) >= 3 && length(raw) <= 320);
        var normalized = trim(raw).toLowerCase(Locale.ROOT);
        require(length(normalized) <= 254 && EMAIL.matcher(normalized).matches());
        return normalized;
    }

    /**
     * Проверяет длину пароля без изменения его содержимого.
     *
     * @param raw пароль, включая значимые пробелы
     * @throws ApiException при длине вне 12–128 символов
     */
    public static void password(String raw) {
        require(raw != null && length(raw) >= 12 && length(raw) <= 128);
    }

    /**
     * Проверяет текстовое поле и удаляет пробелы по краям.
     *
     * @param raw исходный текст
     * @param maxLength максимальная длина исходного текста по контракту
     * @return непустой текст для хранения
     * @throws ApiException при пустом или слишком длинном вводе
     */
    public static String text(String raw, int maxLength) {
        require(raw != null && length(raw) <= maxLength);
        var normalized = trim(raw);
        require(!normalized.isEmpty());
        return normalized;
    }

    /**
     * Удаляет пробельные символы только по краям текста по общему правилу ввода.
     *
     * @param raw значение входного JSON без неявного приведения типа
     * @return текст без пробелов по краям
     */
    private static String trim(String raw) {
        int start = 0;
        int end = raw.length();
        while (start < end && unicodeWhitespace(raw.charAt(start))) start++;
        while (end > start && unicodeWhitespace(raw.charAt(end - 1))) end--;
        return raw.substring(start, end);
    }

    /**
     * Unicode White_Space включает разделители категорий Z и управляющие пробелы
     * U+0009–000D/U+0085.
     */
    private static boolean unicodeWhitespace(char value) {
        return Character.isSpaceChar(value) || value >= '\t' && value <= '\r' || value == '\u0085';
    }

    /**
     * Считает длину текста в Unicode-кодовых точках, а не UTF-16 символах.
     *
     * @param raw значение входного JSON без неявного приведения типа
     * @return число кодовых точек Unicode
     */
    private static int length(String raw) {
        return raw.codePointCount(0, raw.length());
    }

    /**
     * Отклоняет нарушение общего правила ввода публичной ошибкой валидации.
     *
     * @param condition условие корректности входных данных
     * @throws ApiException с VALIDATION_ERROR при ложном условии
     */
    private static void require(boolean condition) {
        if (!condition) throw new ApiException(ApiError.VALIDATION_ERROR);
    }
}
