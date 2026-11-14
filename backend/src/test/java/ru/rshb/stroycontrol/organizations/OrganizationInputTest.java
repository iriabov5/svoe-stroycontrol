package ru.rshb.stroycontrol.organizations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ru.rshb.stroycontrol.common.ApiException;

class OrganizationInputTest {
    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({"LEGAL_ENTITY,7707083893", "SOLE_TRADER,500100732259"})
    @DisplayName("ИНН с корректными контрольными цифрами принимается для своего типа")
    void valid(String type, String inn) {
        assertThatCode(() -> OrganizationInput.inn(type, inn)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
        "LEGAL_ENTITY,7707083894",
        "LEGAL_ENTITY,0000000000",
        "SOLE_TRADER,500100732258",
        "SOLE_TRADER,7707083893",
        "LEGAL_ENTITY,abcdefghij"
    })
    @DisplayName("Неверные длина, символы и контрольные цифры ИНН отклоняются")
    void invalid(String type, String inn) {
        assertThatThrownBy(() -> OrganizationInput.inn(type, inn)).isInstanceOf(ApiException.class);
    }
}
