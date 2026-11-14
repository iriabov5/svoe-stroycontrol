package ru.rshb.stroycontrol.proposals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Хранит версии договора. Права, переходы и версии проверяет сервис документов. */
interface ContractRepository extends JpaRepository<ConstructionContract, UUID> {
    /**
     * Возвращает редакции договора от новой к старой.
     *
     * @param projectId идентификатор карточки дома
     * @return версии договора по убыванию номера редакции
     */
    List<ConstructionContract> findByProjectIdOrderByRevisionNoDesc(UUID projectId);

    /**
     * Ищет версию договора только внутри указанной карточки.
     *
     * @param id идентификатор версии договора
     * @param projectId идентификатор карточки дома
     * @return версия договора либо пустой результат
     */
    Optional<ConstructionContract> findByIdAndProjectId(UUID id, UUID projectId);
}
