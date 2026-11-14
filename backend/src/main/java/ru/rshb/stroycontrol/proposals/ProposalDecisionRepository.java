package ru.rshb.stroycontrol.proposals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Хранит единственное решение заказчика по версии сметы. */
interface ProposalDecisionRepository extends JpaRepository<ProposalDecision, UUID> {
    /**
     * Находит решение по версии сметы.
     *
     * @param proposalId идентификатор версии сметы
     * @return решение либо пустой результат, если заказчик ещё не решил
     */
    Optional<ProposalDecision> findByProposalId(UUID proposalId);
}
