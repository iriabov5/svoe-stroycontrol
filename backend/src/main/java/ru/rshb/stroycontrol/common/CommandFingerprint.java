package ru.rshb.stroycontrol.common;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Сравнивает тела повторных команд независимо от порядка JSON-свойств. */
@Component
@RequiredArgsConstructor
public class CommandFingerprint {
    private final ObjectMapper mapper;

    /**
     * Возвращает SHA-256 команды с привязкой к ресурсу.
     *
     * @param target идентификатор или путь ресурса
     * @param body тело команды
     * @return шестнадцатеричный отпечаток
     */
    public String of(String target, Map<String, Object> body) {
        try {
            String canonical = target + "\n" + mapper.writeValueAsString(sorted(body));
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Канонизирует тело команды для сравнения повторов.
     *
     * <p>Ключи объектов упорядочиваются, порядок списков сохраняется; числовой масштаб не влияет на
     * эквивалентность. Исходные карты и списки не изменяются.
     *
     * @param v значение JSON-команды для канонизации
     * @return каноническая копия структуры JSON с упорядоченными ключами
     */
    private Object sorted(Object v) {
        if (v instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((k, value) -> result.put(k.toString(), sorted(value)));
            return result;
        }
        if (v instanceof List<?> list) return list.stream().map(this::sorted).toList();
        if (v instanceof Number n) return new BigDecimal(n.toString()).stripTrailingZeros();
        return v;
    }
}
