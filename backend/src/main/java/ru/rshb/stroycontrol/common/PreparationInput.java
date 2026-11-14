package ru.rshb.stroycontrol.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Проверки полей подготовки стройки, общие для REST и хранимых документов. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PreparationInput {
    /**
     * Проверяет поле с адресной подсказкой.
     *
     * @param valid условие
     * @param name имя поля
     * @param message подсказка
     */
    public static void field(boolean valid, String name, String message) {
        if (!valid) throw new ApiException(name, message);
    }

    /**
     * Привязывает проверку значения к полю.
     *
     * @param name имя поля
     * @param read проверка
     * @param <T> тип значения
     * @return проверенное значение
     */
    public static <T> T fieldValue(String name, Supplier<T> read) {
        try {
            return read.get();
        } catch (ApiException _) {
            throw new ApiException(name, "Проверьте значение поля");
        }
    }

    /**
     * Отклоняет ложное условие как ошибку ввода.
     *
     * @param valid выполненное условие
     */
    public static void require(boolean valid) {
        if (!valid) throw new ApiException(ApiError.VALIDATION_ERROR);
    }

    /**
     * Проверяет состав JSON.
     *
     * @param data тело
     * @param names допустимые ключи
     */
    public static void keys(Map<String, Object> data, String... names) {
        require(data != null && Set.of(names).containsAll(data.keySet()));
    }

    /**
     * Читает текст с необязательным пустым значением.
     *
     * @param value значение
     * @param max предел
     * @return нормализованный текст
     */
    public static String text(Object value, int max) {
        if (value == null) return null;
        require(value instanceof String);
        String s = ((String) value).strip();
        require(s.codePointCount(0, s.length()) <= max);
        return s.isEmpty() ? null : s;
    }

    /**
     * Читает положительное десятичное число.
     *
     * @param value значение
     * @param max предел
     * @return число либо null
     */
    public static BigDecimal number(Object value, BigDecimal max) {
        if (value == null) return null;
        require(value instanceof Number);
        BigDecimal n;
        try {
            n = new BigDecimal(value.toString());
        } catch (NumberFormatException _) {
            throw new ApiException(ApiError.VALIDATION_ERROR);
        }
        require(n.signum() > 0 && n.scale() <= 2 && n.compareTo(max) <= 0);
        return n;
    }

    /**
     * Читает ISO дату.
     *
     * @param value значение
     * @return дата либо null
     */
    public static LocalDate date(Object value) {
        String s = text(value, 10);
        if (s == null) return null;
        try {
            require(s.matches("\\d{4}-\\d{2}-\\d{2}"));
            var d = LocalDate.parse(s);
            require(d.getYear() > 0);
            return d;
        } catch (DateTimeException _) {
            throw new ApiException(ApiError.VALIDATION_ERROR);
        }
    }

    /**
     * Проверяет плановые даты.
     *
     * @param start начало
     * @param finish конец
     */
    public static void dates(LocalDate start, LocalDate finish) {
        require(start == null || finish == null || !finish.isBefore(start));
    }

    /**
     * Проверяет неотрицательную целую версию.
     *
     * @param value значение
     * @return версия
     */
    public static long version(Object value) {
        require(value instanceof Integer || value instanceof Long);
        long n = ((Number) value).longValue();
        require(n >= 0);
        return n;
    }

    /**
     * Читает UUID в канонической записи.
     *
     * @param value значение JSON
     * @return идентификатор
     * @throws ApiException при неверном формате
     */
    public static UUID uuid(Object value) {
        if (!(value instanceof String string)) throw new ApiException(ApiError.VALIDATION_ERROR);
        require(
                string.matches(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"));
        return UUID.fromString(string);
    }

    /**
     * Проверяет HTTPS ссылку.
     *
     * @param value значение
     * @return ссылка без преобразования пути
     */
    public static String image(Object value) {
        String s = text(value, 2048);
        require(s != null);
        try {
            URI uri = URI.create(s);
            require(
                    "https".equalsIgnoreCase(uri.getScheme())
                            && uri.getHost() != null
                            && uri.getUserInfo() == null);
            return s;
        } catch (IllegalArgumentException _) {
            throw new ApiException(ApiError.VALIDATION_ERROR);
        }
    }
}
