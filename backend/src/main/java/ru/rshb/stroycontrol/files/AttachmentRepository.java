package ru.rshb.stroycontrol.files;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Хранит собственные метаданные файлов и сериализует удержание с очисткой. */
interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    /**
     * Находит прежнюю попытку загрузки по стабильному ключу автора и проекта.
     *
     * @param actorId автор
     * @param projectId проект
     * @param idempotencyKey ключ
     * @return прежняя попытка
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Attachment> findByActorIdAndProjectIdAndIdempotencyKey(
            UUID actorId, UUID projectId, UUID idempotencyKey);

    /**
     * Блокирует один объект в активной транзакции.
     *
     * @param id файл
     * @return объект либо отсутствие
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attachment a where a.id=:id")
    Optional<Attachment> lockById(UUID id);

    /**
     * Выбирает кандидатов очистки; перед удалением состояние проверяется повторно под блокировкой.
     *
     * @param before время истечения временной загрузки
     * @param pageable размер очередной порции
     * @return временные объекты
     */
    @Query(
"""
    select a.id
    from Attachment a
    where a.retained=false
    and a.uploadStartedAt<:before
    and a.state <> 'DELETED'
    order by a.createdAt
""")
    List<UUID> cleanupCandidates(Instant before, Pageable pageable);
}
