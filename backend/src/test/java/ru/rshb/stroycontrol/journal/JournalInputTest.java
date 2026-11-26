package ru.rshb.stroycontrol.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.stages.StageStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class JournalInputTest {
    Map<String, Object> input(String kind) {
        return new HashMap<>(
                Map.of(
                        "kind",
                        kind,
                        "text",
                        "Ход работ",
                        "photos",
                        List.of(),
                        "idempotencyKey",
                        UUID.randomUUID().toString()));
    }

    @Test
    @DisplayName(
            "Запись о ходе работ допускается после завершения, вопросы — только во время работ")
    void states() {
        JournalInput.parse(input("NOTE")).requireStage(StageStatus.WORK_COMPLETED);
        for (var kind : List.of("QUESTION", "APPROVAL")) {
            var in = JournalInput.parse(input(kind));
            in.requireStage(StageStatus.IN_PROGRESS);
            assertThatThrownBy(() -> in.requireStage(StageStatus.WORK_COMPLETED))
                    .isInstanceOf(ApiException.class);
        }
    }

    @ParameterizedTest(name = "тип = {0}")
    @ValueSource(strings = {"NOTE", "QUESTION", "APPROVAL"})
    @DisplayName("До начала этапа публикация любого типа отклоняется")
    void planned(String kind) {
        var parsed = JournalInput.parse(input(kind));
        assertThatThrownBy(() -> parsed.requireStage(StageStatus.PLANNED))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Подпись каждой фотографии обязательна и ошибка указывает поле")
    void caption() {
        var body = input("NOTE");
        body.put(
                "photos", List.of(Map.of("url", "https://example.test/photo.png", "caption", " ")));
        assertThatThrownBy(() -> JournalInput.parse(body))
                .isInstanceOf(ApiException.class)
                .satisfies(e ->
                        assertThat(((ApiException) e).getFieldErrors())
                                .containsKey("photos.0.caption"));
    }

    @Test
    @DisplayName("Принимает только известную локальную иллюстрацию демо-сценария")
    void acceptsKnownDemoPhoto() {
        var body = input("NOTE");
        body.put(
                "photos",
                List.of(
                        Map.of(
                                "url",
                                "/demo/foundation-reinforcement-example.png",
                                "caption",
                                "Демонстрационный пример")));

        assertThat(JournalInput.parse(body).photos()).hasSize(1);
    }

    @Test
    @DisplayName("Вопрос требует текста ответа, согласование не принимает неизвестное решение")
    void answer() {
        var body = new HashMap<String, Object>();
        body.put("text", "Ответ");
        body.put("decision", null);
        assertThat(JournalInput.answer(body, "QUESTION").state()).isEqualTo("ANSWERED");
        body.put("decision", "UNKNOWN");
        assertThatThrownBy(() -> JournalInput.answer(body, "APPROVAL"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Отрицательное решение требует комментария, положительное допускает пустой")
    void negative() {
        var body = new HashMap<String, Object>();
        body.put("text", null);
        body.put("decision", "DECLINED");
        assertThatThrownBy(() -> JournalInput.answer(body, "APPROVAL"))
                .isInstanceOf(ApiException.class);
        body.put("decision", "APPROVED");
        assertThat(JournalInput.answer(body, "APPROVAL").state()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("Фотографии и текст нормализуются без исполнения HTML")
    void normalization() {
        var body = input("NOTE");
        body.put("text", "  <script>test</script>  ");
        assertThat(JournalInput.parse(body).text()).isEqualTo("<script>test</script>");
    }
}
