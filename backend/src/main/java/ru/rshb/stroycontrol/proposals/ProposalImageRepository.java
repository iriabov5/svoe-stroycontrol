package ru.rshb.stroycontrol.proposals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Хранит иллюстрации версии сметы в заданном порядке. */
interface ProposalImageRepository extends JpaRepository<ProposalImage, UUID> {
    /**
     * Возвращает иллюстрации версии сметы по возрастанию позиции.
     *
     * @param proposalId идентификатор версии сметы
     * @return ссылки и подписи в порядке показа
     */
    List<ProposalImage> findByProposalIdOrderByPositionAsc(UUID proposalId);
}
