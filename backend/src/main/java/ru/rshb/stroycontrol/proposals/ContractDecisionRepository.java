package ru.rshb.stroycontrol.proposals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Хранит единственное решение заказчика по версии договора. */
interface ContractDecisionRepository extends JpaRepository<ContractDecision, UUID> {
    /**
     * Находит решение по версии договора.
     *
     * @param contractId идентификатор версии договора
     * @return решение либо пустой результат, если заказчик ещё не решил
     */
    Optional<ContractDecision> findByContractId(UUID contractId);
}
