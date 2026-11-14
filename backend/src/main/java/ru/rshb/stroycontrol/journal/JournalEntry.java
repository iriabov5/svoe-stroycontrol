package ru.rshb.stroycontrol.journal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import ru.rshb.stroycontrol.common.ActorSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Хранит публикацию подрядчика и текущее состояние единственного ответа заказчика.
 *
 * <p>Текст и снимок автора после публикации не редактируются. Ссылка на исходную запись принадлежит
 * тому же этапу. Версию и состояние ответа изменяет сервис под блокировкой проекта.
 */
@Entity
@Table(name = "stage_journal_entries")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class JournalEntry {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "stage_id")
    private UUID stageId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "author_role")
    private String authorRole;

    @Column(name = "kind")
    private String kind;

    @Column(name = "status")
    private String status;

    @Column(name = "text")
    private String text;

    @Column(name = "source_entry_id")
    private UUID sourceEntryId;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "version")
    private long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "author_snapshot", columnDefinition = "jsonb")
    private ActorSnapshot authorSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachment_ids", columnDefinition = "jsonb")
    @Builder.Default
    private List<UUID> attachmentIds = List.of();
}
