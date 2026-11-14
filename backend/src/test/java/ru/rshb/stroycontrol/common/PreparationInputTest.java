package ru.rshb.stroycontrol.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

class PreparationInputTest {
    @ParameterizedTest(name = "URL = {0}")
    @ValueSource(
            strings = {
                "javascript:alert(1)",
                "data:image/png;base64,QQ==",
                "file:///secret",
                "http://example.test/a.png",
                "https://user:pass@example.test/a.png"
            })
    @DisplayName("Недопустимая ссылка картинки отклоняется")
    void unsafeUrl(String url) {
        assertThatThrownBy(() -> PreparationInput.image(url)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Безопасная HTTPS ссылка сохраняет путь и параметры")
    void safeUrl() {
        assertThat(PreparationInput.image("https://example.test/plan.png?size=100"))
                .isEqualTo("https://example.test/plan.png?size=100");
    }

    @Test
    @DisplayName("Денежное значение не принимает ноль, отрицательные числа и лишнюю точность")
    void money() {
        var maximum = new BigDecimal("100");
        for (var n :
                new BigDecimal[] {
                    new BigDecimal("0"), new BigDecimal("-1"), new BigDecimal("1.001")
                })
            assertThatThrownBy(() -> PreparationInput.number(n, maximum))
                    .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Плановые даты проверяются без нормализации неверного календаря")
    void dates() {
        assertThatThrownBy(() -> PreparationInput.date("2026-02-30"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PreparationInput.date("0000-01-01"))
                .isInstanceOf(ApiException.class);
    }
}
