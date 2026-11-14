package ru.rshb.stroycontrol.journal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Хранит единственный ответ на публикацию. Уникальность ответа обеспечивает база. */
interface JournalResponseRepository extends JpaRepository<JournalResponse, UUID> {
    /**
     * Находит ответ на публикацию.
     *
     * @param entryId идентификатор публикации
     * @return ответ либо пустой результат, если заказчик ещё не ответил
     */
    Optional<JournalResponse> findByEntryId(UUID entryId);
}
