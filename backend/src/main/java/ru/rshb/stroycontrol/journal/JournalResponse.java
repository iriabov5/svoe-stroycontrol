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
import java.util.UUID;

/**
 * Хранит единственный ответ или решение заказчика по публикации этапа.
 *
 * <p>Вопрос требует текста, отказ — пояснения. Ответ фиксируется одной транзакцией с новым
 * состоянием публикации; повторный независимый ответ запрещён.
 */
@Entity
@Table(name = "stage_journal_responses")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class JournalResponse {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "text")
    private String text;

    @Column(name = "decision")
    private String decision;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "author_snapshot", columnDefinition = "jsonb")
    private ActorSnapshot authorSnapshot;
}
