package ru.rshb.stroycontrol.proposals;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Хранит иллюстрации версии сметы в заданном порядке. */
interface ProposalImageRepository extends JpaRepository<ProposalImage, UUID> {
    /**
     * Проверяет наличие связи с доступной читателю версией, включая исторические отправки.
     *
     * @param projectId доступный проект
     * @param attachmentId закрытый файл
     * @param includeDraft разрешено чтение черновиков
     * @return есть ли доступная связь
     */
    @Query(
            """
            select count(i) > 0 from ProposalImage i, ConstructionProposal p
            where i.proposalId = p.id and p.projectId = :projectId
              and i.attachmentId = :attachmentId
              and (:includeDraft = true or p.state <> 'DRAFT')
            """)
    boolean hasVisibleImage(
            @Param("projectId") UUID projectId,
            @Param("attachmentId") UUID attachmentId,
            @Param("includeDraft") boolean includeDraft);

    /**
     * Возвращает иллюстрации версии сметы по возрастанию позиции.
     *
     * @param proposalId идентификатор версии сметы
     * @return ссылки и подписи в порядке показа
     */
    List<ProposalImage> findByProposalIdOrderByPositionAsc(UUID proposalId);
}
