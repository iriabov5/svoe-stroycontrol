package ru.rshb.stroycontrol.projects;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Хранит назначения действующих членств в конкретные проекты. */
interface ProjectTeamRepository extends JpaRepository<ProjectTeamMember, UUID> {
    /**
     * @param projectId стройка
     * @param membershipId членство
     * @return назначение либо пустой результат
     */
    Optional<ProjectTeamMember> findByProjectIdAndMembershipId(UUID projectId, UUID membershipId);

    /**
     * @param projectId стройка
     * @return назначения только этой стройки
     */
    List<ProjectTeamMember> findByProjectId(UUID projectId);

    /**
     * @param membershipId членство
     * @return назначенные ему проекты без проверки их текущей видимости
     */
    List<ProjectTeamMember> findByMembershipId(UUID membershipId);
}
