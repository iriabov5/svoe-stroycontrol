package ru.rshb.stroycontrol.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

class InputTest {
    @Test
    @DisplayName("Email нормализуется независимо от локали JVM")
    void normalizeEmail() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(Input.email("  IVAN@Example.Test \t")).isEqualTo("ivan@example.test");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest(name = "email = [{0}]")
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "a",
                "no-at.example",
                "a@b",
                "a @b.test",
                "a@b..test",
                "a@.test",
                "a@b.test\nother"
            })
    @DisplayName("Некорректный email отклоняется с ошибкой валидации")
    void rejectEmail(String email) {
        assertThatThrownBy(() -> Input.email(email))
                .isInstanceOf(ApiException.class)
                .extracting("error")
                .isEqualTo(ApiError.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("Проверка пароля учитывает границы 12–128 и не удаляет пробелы")
    void passwordBoundaries() {
        var shortPassword = "x".repeat(11);
        var longPassword = "x".repeat(129);
        assertThatCode(() -> Input.password(" ".repeat(12))).doesNotThrowAnyException();
        assertThatCode(() -> Input.password("я".repeat(128))).doesNotThrowAnyException();
        assertThatThrownBy(() -> Input.password(shortPassword)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Input.password(longPassword)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Input.password(null)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Текст обрезается по краям, пустой и слишком длинный ввод отклоняется")
    void textBoundaries() {
        var longText = "x".repeat(121);
        assertThat(Input.text(" \tДом\n", 120)).isEqualTo("Дом");
        assertThatThrownBy(() -> Input.text("\u2003\t", 120)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Input.text(longText, 120)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Input.text(null, 120)).isInstanceOf(ApiException.class);
    }
}
