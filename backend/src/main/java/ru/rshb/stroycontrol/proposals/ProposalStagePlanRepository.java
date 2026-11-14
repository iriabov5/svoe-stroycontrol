package ru.rshb.stroycontrol.proposals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Хранит плановые этапы версии сметы. Исполнительный план создаёт модуль этапов. */
interface ProposalStagePlanRepository extends JpaRepository<ProposalStagePlan, UUID> {
    /**
     * Возвращает план версии сметы по возрастанию позиции.
     *
     * @param proposalId идентификатор версии сметы
     * @return плановые этапы в порядке выполнения
     */
    List<ProposalStagePlan> findByProposalIdOrderByPositionAsc(UUID proposalId);
}
