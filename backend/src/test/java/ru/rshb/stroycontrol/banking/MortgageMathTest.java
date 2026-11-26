package ru.rshb.stroycontrol.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import ru.rshb.stroycontrol.common.ApiException;

import java.math.BigDecimal;

/** Проверяет расчёт и границы синтетических условий независимо от HTTP и БД. */
class MortgageMathTest {
    @ParameterizedTest(name = "Кредит {0}, ставка {1}%, срок {2} лет → {3} ₽")
    @CsvSource({"4000000,3,20,22183.90", "1200,0,1,100.00", "1000,0,1,83.33", "1001,0,1,83.42"})
    @DisplayName("Аннуитет и нулевая ставка дают ожидаемый платёж до копеек")
    void payments(String principal, String rate, int years, String expected) {
        assertThat(MortgageMath.payment(new BigDecimal(principal), new BigDecimal(rate), years))
                .isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "Стоимость {0}, взнос {1}%, срок {2} лет")
    @CsvSource({
        "0,20,20",
        "999999,20,20",
        "15100000,20,20",
        "1050000,20,20",
        "1000000,19,20",
        "1000000,81,20",
        "1000000,22,20",
        "1000000,20,0",
        "1000000,20,26"
    })
    @DisplayName("Недопустимые значения не исправляются и дают ошибку поля")
    void invalid(String cost, int deposit, int years) {
        BigDecimal amount = new BigDecimal(cost);
        assertThatThrownBy(() -> MortgageMath.validate(amount, deposit, years))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Минимальные и максимальные допустимые значения принимаются")
    void boundaries() {
        MortgageMath.validate(new BigDecimal("1000000"), 20, 1);
        MortgageMath.validate(new BigDecimal("15000000"), 80, 25);
    }

    @ParameterizedTest(name = "Номер {0}")
    @ValueSource(strings = {"16:24:070801:412", "31:15:1203001:88", " 50:21:0040112:105 "})
    @DisplayName("Квартал из шести или семи цифр и краевые пробелы допустимы")
    void numbers(String number) {
        assertThat(PlotDemoData.number(number)).isEqualTo(number.strip());
    }

    @ParameterizedTest(name = "Неверный номер «{0}»")
    @ValueSource(
            strings = {
                "",
                "16:24:07080:1",
                "16:24:07080100:1",
                "16:24:070801:12345678",
                "xx:24:070801:1"
            })
    @DisplayName("Ошибочный номер отклоняется адресной ошибкой")
    void invalidNumbers(String number) {
        assertThatThrownBy(() -> PlotDemoData.number(number)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Три демопримера различают подходящие участки и неизвестный номер")
    void examples() {
        assertThat(PlotDemoData.result("16:24:070801:412").rate()).isEqualByComparingTo("3");
        assertThat(PlotDemoData.result("31:15:1203001:88").rate()).isEqualByComparingTo("0.1");
        assertThat(PlotDemoData.result("50:21:0040112:105").status()).isEqualTo("INELIGIBLE");
        assertThat(PlotDemoData.result("16:24:070801:999").status()).isEqualTo("NOT_FOUND");
    }
}
