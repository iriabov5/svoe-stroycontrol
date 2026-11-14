package ru.rshb.stroycontrol.proposals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Хранит версии сметы. Права, переходы и версии проверяет сервис документов. */
interface ProposalRepository extends JpaRepository<ConstructionProposal, UUID> {
    /**
     * Возвращает редакции сметы от новой к старой.
     *
     * <p>Видимость черновика заказчику ограничивает сервис.
     *
     * @param projectId идентификатор карточки дома
     * @return версии сметы по убыванию номера редакции
     */
    List<ConstructionProposal> findByProjectIdOrderByRevisionNoDesc(UUID projectId);

    /**
     * Ищет версию сметы только внутри указанной карточки.
     *
     * @param id идентификатор версии сметы
     * @param projectId идентификатор карточки дома
     * @return версия сметы либо пустой результат
     */
    Optional<ConstructionProposal> findByIdAndProjectId(UUID id, UUID projectId);
}
