package ru.rshb.stroycontrol.banking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Хранит только собственные попытки банковского обмена. */
interface BankStageAttemptRepository extends JpaRepository<BankStageAttempt, UUID> {
    /**
     * Ищет сохранённое начало попытки для фиксации исхода.
     *
     * @param submissionId пакет
     * @param attemptNumber номер попытки
     * @return сохранённая попытка
     */
    Optional<BankStageAttempt> findBySubmissionIdAndAttemptNumber(
            UUID submissionId, int attemptNumber);
}
