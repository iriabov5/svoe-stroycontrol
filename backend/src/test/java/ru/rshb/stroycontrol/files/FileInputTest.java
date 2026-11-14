package ru.rshb.stroycontrol.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.rshb.stroycontrol.common.ApiException;

import java.nio.charset.StandardCharsets;

class FileInputTest {
    @Test
    @DisplayName("HTML под именем картинки и обрезанный PDF отклоняются")
    void rejectDisguisedFiles() {
        var html = "<svg></svg>".getBytes(StandardCharsets.UTF_8);
        var brokenPdf = "%PDF-1.4 broken".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> FileInput.contentType(html)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> FileInput.contentType(brokenPdf)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Имя не содержит каталогов и управляющих символов, SHA-256 стабилен")
    void safeMetadata() {
        assertThat(FileInput.name("../folder/passport.pdf")).isEqualTo("passport.pdf");
        assertThat(FileInput.sha256("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
