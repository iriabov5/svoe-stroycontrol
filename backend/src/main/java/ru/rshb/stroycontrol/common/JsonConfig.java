package ru.rshb.stroycontrol.common;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.type.LogicalType;

/** Настраивает строгие JSON-типы REST-контракта без неявного преобразования чисел и строк. */
@Configuration
class JsonConfig {
    /**
     * Отключает неявное приведение JSON-значений к типам контракта.
     *
     * <p>Число вместо строки, строка вместо числа и числовой enum отклоняются при чтении тела.
     *
     * @return настройка строгого JSON-маппера Spring Boot
     */
    @Bean
    JsonMapperBuilderCustomizer strictContractTypes() {
        return builder ->
                builder.enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                        .withCoercionConfig(
                                LogicalType.Textual,
                                config ->
                                        config.setCoercion(
                                                        CoercionInputShape.Integer,
                                                        CoercionAction.Fail)
                                                .setCoercion(
                                                        CoercionInputShape.Float,
                                                        CoercionAction.Fail)
                                                .setCoercion(
                                                        CoercionInputShape.Boolean,
                                                        CoercionAction.Fail))
                        .withCoercionConfig(
                                LogicalType.Integer,
                                config ->
                                        config.setCoercion(
                                                        CoercionInputShape.String,
                                                        CoercionAction.Fail)
                                                .setCoercion(
                                                        CoercionInputShape.Float,
                                                        CoercionAction.Fail));
    }
}
