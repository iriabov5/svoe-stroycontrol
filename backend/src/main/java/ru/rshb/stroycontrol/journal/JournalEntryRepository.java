package ru.rshb.stroycontrol.journal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Хранит публикации этапа. Доступ и смену состояния проверяет сервис журнала. */
interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    /**
     * Ищет публикацию внутри указанных карточки и этапа.
     *
     * @param id идентификатор публикации
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа этой карточки
     * @return публикация либо пустой результат, если она принадлежит другому этапу
     */
    Optional<JournalEntry> findByIdAndProjectIdAndStageId(UUID id, UUID projectId, UUID stageId);

    /**
     * Читает до запрошенного предела публикаций в устойчивом обратном порядке.
     *
     * <p>Дополнительная запись сверх страницы показывает, что продолжение есть. При пустой границе
     * выбирается первая страница. Значения связаны параметрами.
     *
     * @param project идентификатор карточки дома
     * @param stage идентификатор этапа этой карточки
     * @param awaiting true, чтобы оставить только записи со статусом PENDING
     * @param at время границы страницы или null для первой страницы
     * @param boundary идентификатор публикации на границе или null для первой страницы
     * @param pageable предел выборки без отдельной сортировки
     * @return публикации от новых к старым в пределах страницы
     */
    @Query(
            """
            select e from JournalEntry e
            where e.projectId = :project and e.stageId = :stage
              and (:awaiting = false or e.status = 'PENDING')
              and (:boundary is null or e.createdAt < :at or (e.createdAt = :at and e.id < :boundary))
            order by e.createdAt desc, e.id desc
            """)
    List<JournalEntry> page(
            UUID project,
            UUID stage,
            boolean awaiting,
            Instant at,
            UUID boundary,
            Pageable pageable);

    /**
     * Считает все ожидающие ответа публикации указанного этапа.
     *
     * @param project идентификатор карточки дома
     * @param stage идентификатор этапа этой карточки
     * @return число публикаций со статусом PENDING в этом этапе
     */
    @Query(
            """
            select count(e) from JournalEntry e
            where e.projectId = :project and e.stageId = :stage and e.status = 'PENDING'
            """)
    long countAwaiting(UUID project, UUID stage);

    /**
     * Проверяет фото у опубликованных записей этапа без чтения содержимого журнала.
     *
     * @param project идентификатор карточки дома
     * @param stage идентификатор этапа
     * @return true, если существует опубликованная запись с фотографией
     */
    @Query(
            """
            select count(p) > 0 from JournalPhoto p, JournalEntry e
            where p.entryId = e.id and e.projectId = :project and e.stageId = :stage
              and e.status = 'PUBLISHED'
            """)
    boolean hasPublishedPhoto(UUID project, UUID stage);
}
