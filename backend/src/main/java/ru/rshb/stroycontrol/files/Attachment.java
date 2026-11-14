package ru.rshb.stroycontrol.files;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Хранит намерение загрузки и закрытый ключ объекта; ссылка из истории запрещает очистку. */
@Entity
@Table(name = "attachments")
@Getter
@Setter(AccessLevel.PACKAGE)
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class Attachment {
    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();
    private UUID projectId;
    private UUID stageId;
    private UUID actorId;
    private UUID idempotencyKey;
    private String purpose;
    private String requestHash;
    private String objectKey;
    private String name;
    private String contentType;
    private long size;
    private String sha256;
    private String caption;
    @Builder.Default
    private String state = "UPLOADING";
    @Builder.Default
    private Instant createdAt = Instant.now();
    private boolean retained;

    @Builder.Default
    private Instant uploadStartedAt = Instant.now();

    AttachmentView view() {
        return new AttachmentView(
                id,
                projectId,
                stageId,
                name,
                contentType,
                size,
                sha256,
                caption,
                "READY",
                createdAt,
                "/api/v1/projects/%s/attachments/%s/content".formatted(projectId, id));
    }
}
