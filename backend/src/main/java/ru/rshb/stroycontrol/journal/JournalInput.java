package ru.rshb.stroycontrol.journal;

import static ru.rshb.stroycontrol.common.PreparationInput.field;
import static ru.rshb.stroycontrol.common.PreparationInput.fieldValue;
import static ru.rshb.stroycontrol.common.PreparationInput.image;
import static ru.rshb.stroycontrol.common.PreparationInput.keys;
import static ru.rshb.stroycontrol.common.PreparationInput.require;
import static ru.rshb.stroycontrol.common.PreparationInput.uuid;

import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.common.PreparationInput;
import ru.rshb.stroycontrol.stages.StageStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Проверяет поля публикации и ответа до изменения журнала этапа.
 *
 * <p>Принимает только известные поля, HTTPS-ссылки и обязательные подписи. Тип записи определяет
 * допустимое состояние этапа и форму ответа заказчика.
 */
record JournalInput(
        String kind,
        String text,
        List<Photo> photos,
        UUID sourceEntryId,
        UUID key,
        List<UUID> attachmentIds) {
    private static final String FIELD_IDEMPOTENCY_KEY = "idempotencyKey";
    private static final String FIELD_SOURCE_ENTRY_ID = "sourceEntryId";
    private static final String FIELD_PHOTOS = "photos";
    private static final String FIELD_ATTACHMENT_IDS = "attachmentIds";
    private static final String FIELD_CAPTION = "caption";
    private static final String FIELD_DECISION = "decision";

    /**
     * Проверенная внешняя фотография публикации.
     *
     * @param url HTTPS-ссылка без учётных данных в адресе
     * @param caption обязательная подпись фотографии
     */
    record Photo(String url, String caption) {}

    /**
     * Проверяет публикацию, подписи фотографий и ключ повтора.
     *
     * <p>Текст трактуется как обычный текст; внешние изображения принимаются только по HTTPS.
     *
     * @param body вид записи, текст, фотографии, исходная публикация и ключ повтора
     * @return проверенные поля публикации без изменения входной карты
     * @throws ApiException с ошибкой соответствующего поля при неверном значении
     */
    static JournalInput parse(Map<String, Object> body) {
        keys(
                body,
                "kind",
                "text",
                FIELD_PHOTOS,
                FIELD_SOURCE_ENTRY_ID,
                FIELD_IDEMPOTENCY_KEY,
                FIELD_ATTACHMENT_IDS);
        String kind = fieldValue("kind", () -> PreparationInput.text(body.get("kind"), 16));
        field(
                Set.of("NOTE", "QUESTION", "APPROVAL").contains(kind == null ? "" : kind),
                "kind",
                "Выберите тип записи");
        String text = fieldValue("text", () -> PreparationInput.text(body.get("text"), 10000));
        field(text != null, "text", "Добавьте текст записи");
        field(
                body.get(FIELD_PHOTOS) instanceof List<?>
                        && ((List<?>) body.get(FIELD_PHOTOS)).size() <= 10,
                FIELD_PHOTOS,
                "Можно добавить до 10 фотографий");
        var photos = new ArrayList<Photo>();
        var raw = (List<?>) body.get(FIELD_PHOTOS);
        for (int i = 0; i < raw.size(); i++) {
            String prefix = "photos." + i + ".";
            field(raw.get(i) instanceof Map<?, ?>, FIELD_PHOTOS, "Проверьте фотографии");
            var row = (Map<?, ?>) raw.get(i);
            require(Set.of("url", FIELD_CAPTION).containsAll(row.keySet()));
            String url = fieldValue(prefix + "url", () -> image(row.get("url")));
            String caption =
                    fieldValue(
                            prefix + FIELD_CAPTION,
                            () -> PreparationInput.text(row.get(FIELD_CAPTION), 300));
            field(caption != null, prefix + FIELD_CAPTION, "Подпишите фотографию");
            photos.add(new Photo(url, caption));
        }
        UUID source =
                body.get(FIELD_SOURCE_ENTRY_ID) == null
                        ? null
                        : fieldValue(
                                FIELD_SOURCE_ENTRY_ID, () -> uuid(body.get(FIELD_SOURCE_ENTRY_ID)));
        return new JournalInput(
                kind,
                text,
                List.copyOf(photos),
                source,
                fieldValue(FIELD_IDEMPOTENCY_KEY, () -> uuid(body.get(FIELD_IDEMPOTENCY_KEY))),
                attachmentIds(body));
    }

    /**
     * Проверяет необязательный список загруженных файлов, не меняя прежний текстовый контракт.
     *
     * @param body публикация
     * @return уникальные идентификаторы в порядке показа
     */
    private static List<UUID> attachmentIds(Map<String, Object> body) {
        if (!body.containsKey(FIELD_ATTACHMENT_IDS)) return List.of();
        field(
                body.get(FIELD_ATTACHMENT_IDS) instanceof List<?>,
                FIELD_ATTACHMENT_IDS,
                "Проверьте список файлов");
        var raw = (List<?>) body.get(FIELD_ATTACHMENT_IDS);
        field(raw.size() <= 10, FIELD_ATTACHMENT_IDS, "До 10 вложений на запись");
        var ids = raw.stream().map(v -> fieldValue(FIELD_ATTACHMENT_IDS, () -> uuid(v))).toList();
        field(
                ids.stream().distinct().count() == ids.size(),
                FIELD_ATTACHMENT_IDS,
                "Файлы не должны повторяться");
        return ids;
    }

    /**
     * Проверяет допустимость типа публикации для текущего состояния этапа.
     *
     * <p>Ход работ разрешён во время выполнения и после завершения; вопросы и запросы согласования
     * — только во время выполнения.
     *
     * @param status текущее состояние строительного этапа
     * @throws ApiException с JOURNAL_STAGE_STATE при запрещённом сочетании
     */
    void requireStage(StageStatus status) {
        if (status != StageStatus.IN_PROGRESS
                && !(kind.equals("NOTE") && status == StageStatus.WORK_COMPLETED))
            throw new ApiException(ApiError.JOURNAL_STAGE_STATE);
    }

    /**
     * Проверяет форму единственного ответа для типа исходной записи.
     *
     * <p>Вопрос требует текста; запрос согласования — решения; отказ требует пояснения.
     *
     * @param body версия публикации, ключ повтора, текст ответа и решение по согласованию
     * @param kind тип исходной публикации: QUESTION или APPROVAL
     * @return нормализованный текст, решение и новое состояние публикации
     * @throws ApiException с ошибкой поля при неполном ответе
     */
    static Answer answer(Map<String, Object> body, String kind) {
        keys(body, "version", FIELD_IDEMPOTENCY_KEY, "text", FIELD_DECISION);
        String text = fieldValue("text", () -> PreparationInput.text(body.get("text"), 5000));
        String decision =
                fieldValue(
                        FIELD_DECISION, () -> PreparationInput.text(body.get(FIELD_DECISION), 16));
        if (kind.equals("QUESTION")) {
            field(text != null, "text", "Напишите ответ на вопрос");
            field(decision == null, FIELD_DECISION, "Для вопроса требуется текстовый ответ");
            return new Answer(text, null, "ANSWERED");
        }
        if (kind.equals("APPROVAL")) {
            field(
                    decision != null && Set.of("APPROVED", "DECLINED").contains(decision),
                    FIELD_DECISION,
                    "Выберите решение");
            field(
                    !"DECLINED".equals(decision) || text != null,
                    "text",
                    "Объясните, что нужно изменить");
            return new Answer(text, decision, decision);
        }
        throw new ApiException(ApiError.JOURNAL_ALREADY_RESOLVED);
    }

    /**
     * Проверенный ответ заказчика без произвольного изменения публикации.
     *
     * @param text текст ответа либо пояснение решения, null допустим при согласовании
     * @param decision APPROVED, DECLINED или null для текстового ответа
     * @param state новое состояние ANSWERED, APPROVED или DECLINED
     */
    record Answer(String text, String decision, String state) {}
}
