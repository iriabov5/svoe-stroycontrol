package ru.rshb.stroycontrol.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class CommandFingerprintTest {
    @Test
    @DisplayName(
            "Отпечаток не зависит от порядка JSON-свойств, но зависит от ресурса и списка фото")
    void ordering() {
        var f = new CommandFingerprint(new ObjectMapper());
        var a = new LinkedHashMap<String, Object>();
        a.put("text", "Работы");
        a.put(
                "photos",
                List.of(
                        Map.of("url", "https://example.test/a"),
                        Map.of("url", "https://example.test/b")));
        var b = new LinkedHashMap<String, Object>();
        b.put("photos", a.get("photos"));
        b.put("text", "Работы");
        assertThat(f.of("stage", a)).isEqualTo(f.of("stage", b));
        assertThat(f.of("another-stage", a)).isNotEqualTo(f.of("stage", a));
        b.put(
                "photos",
                List.of(
                        Map.of("url", "https://example.test/b"),
                        Map.of("url", "https://example.test/a")));
        assertThat(f.of("stage", b)).isNotEqualTo(f.of("stage", a));
    }
}
