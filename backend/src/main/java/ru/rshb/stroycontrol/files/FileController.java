package ru.rshb.stroycontrol.files;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Принимает multipart и выдаёт закрытые байты через ту же сессионную авторизацию, что карточка. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/attachments")
@RequiredArgsConstructor
class FileController {
    private final FileService service;

    /**
     * Загружает один файл до публикации записи; изменения защищены общим CSRF-фильтром.
     *
     * @param actor текущий аккаунт
     * @param projectId проект
     * @param stageId этап для JOURNAL
     * @param purpose JOURNAL либо BANK
     * @param key стабильный UUID повтора
     * @param caption подпись
     * @param file файл
     * @return готовые метаданные
     * @throws IOException при ошибке чтения multipart
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AttachmentView upload(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID stageId,
            @RequestParam String purpose,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestParam(defaultValue = "") String caption,
            @RequestParam MultipartFile file)
            throws IOException {
        if (file.getSize() > 20L * 1024 * 1024)
            throw new ApiException(ApiError.ATTACHMENT_TOO_LARGE);
        return service.upload(
                new UploadScope(actor.id(), projectId, stageId, purpose, key),
                file.getOriginalFilename(),
                caption,
                file.getBytes());
    }

    /**
     * Выдаёт файл без публичного адреса и кеширования персональных данных.
     *
     * @param actor текущий аккаунт
     * @param projectId проект
     * @param attachmentId файл
     * @return фото inline либо PDF attachment с nosniff
     */
    @GetMapping("/{attachmentId}/content")
    ResponseEntity<byte[]> download(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID attachmentId) {
        var d = service.download(actor.id(), projectId, attachmentId);
        var view = d.view();
        var disposition =
                view.contentType().startsWith("image/")
                        ? ContentDisposition.inline()
                        : ContentDisposition.attachment();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(view.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition
                                .filename(view.name(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(d.bytes());
    }
}
