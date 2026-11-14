package ru.rshb.stroycontrol.files;

import static ru.rshb.stroycontrol.common.PreparationInput.field;

import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;

import java.util.UUID;

/**
 * Координирует метаданные и S3 короткими транзакциями; внешние вызовы не удерживают блокировки БД.
 */
@Service
@RequiredArgsConstructor
class FileService {
    private final FileTransactions transactions;
    private final ObjectStorage storage;

    /**
     * Загружает проверенный файл, восстанавливая результат по ключу и хешу.
     *
     * @param scope доступ и ключ повтора
     * @param name имя файла
     * @param caption подпись
     * @param bytes содержимое
     * @return готовые метаданные
     * @throws ApiException при неверных правах, содержимом, конфликте ключа или сбое S3
     */
    AttachmentView upload(UploadScope scope, String name, String caption, byte[] bytes) {
        var type = FileInput.contentType(bytes);
        var hash = FileInput.sha256(bytes);
        String normalized = caption == null ? "" : caption.strip();
        field(normalized.length() <= 300, "caption", "Подпись — до 300 символов");
        field(
                !type.startsWith("image/") || !normalized.isEmpty(),
                "caption",
                "Подпишите фотографию");
        var attempt =
                transactions.prepare(
                        scope,
                        new FileTransactions.Metadata(
                                FileInput.name(name), normalized, type, bytes.length, hash));
        if (attempt.state().equals("READY")) return attempt.view();
        try {
            if (!storage.matches(attempt.key(), hash))
                storage.put(attempt.key(), type, hash, bytes);
        } catch (RuntimeException _) {
            transactions.failed(attempt.id());
            throw new ApiException(ApiError.STORAGE_UNAVAILABLE);
        }
        return transactions.complete(scope.actor(), scope.project(), attempt.id());
    }

    /**
     * Выдаёт байты только после проверки актуального участия; внешний объект не имеет публичного
     * URL.
     *
     * @param actor читатель
     * @param project проект
     * @param id файл
     * @return метаданные и содержимое
     */
    Download download(UUID actor, UUID project, UUID id) {
        var file = transactions.read(actor, project, id);
        try {
            return new Download(file.view(), storage.get(file.key()));
        } catch (RuntimeException _) {
            throw new ApiException(ApiError.STORAGE_UNAVAILABLE);
        }
    }

    /**
     * Результат чтения не хранит байты в прикладной БД.
     *
     * @param view метаданные
     * @param bytes содержимое
     */
    @Value
    @Accessors(fluent = true)
    @ToString(exclude = "bytes")
    static class Download {
        AttachmentView view;
        byte[] bytes;
    }

    /**
     * Убирает только несвязанные загрузки старше суток; неизвестный исход удаления безопасно
     * повторяется.
     */
    @Scheduled(
            fixedDelayString = "${storage.cleanup-delay-ms:3600000}",
            initialDelayString = "${storage.cleanup-delay-ms:3600000}")
    void cleanup() {
        for (var id : transactions.candidates()) {
            var key = transactions.claimCleanup(id);
            if (key == null) continue;
            try {
                storage.delete(key);
                transactions.deleted(id);
            } catch (RuntimeException _) {
                /* DELETING остаётся кандидатом следующего прохода. */
            }
        }
    }
}
