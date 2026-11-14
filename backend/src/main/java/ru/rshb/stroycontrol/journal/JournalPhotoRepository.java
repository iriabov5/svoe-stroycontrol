package ru.rshb.stroycontrol.journal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Хранит фотографии публикации в порядке показа. Права проверяет сервис журнала. */
interface JournalPhotoRepository extends JpaRepository<JournalPhoto, UUID> {
    /**
     * Возвращает фотографии публикации по возрастанию позиции.
     *
     * @param entryId идентификатор публикации
     * @return фотографии в порядке показа
     */
    List<JournalPhoto> findByEntryIdOrderByPositionAsc(UUID entryId);
}
