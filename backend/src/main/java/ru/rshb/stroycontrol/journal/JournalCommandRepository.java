package ru.rshb.stroycontrol.journal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Хранит исходный результат команды журнала. Повтор и конфликт проверяет сервис. */
interface JournalCommandRepository extends JpaRepository<JournalCommand, UUID> {
    /**
     * Ищет сохранённую команду в области аккаунта, карточки и операции.
     *
     * @param actorId идентификатор автора команды
     * @param projectId идентификатор карточки дома
     * @param operation тип команды в области ключа повтора
     * @param idempotencyKey ключ повторной команды
     * @return сохранённая команда либо пустой результат
     */
    Optional<JournalCommand> findByActorIdAndProjectIdAndOperationAndIdempotencyKey(
            UUID actorId, UUID projectId, String operation, UUID idempotencyKey);
}
