package ru.rshb.stroycontrol.projects;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Хранит заявку внутри модуля проектов; доступ и блокировку проверяет RequestService. */
interface RequestRepository extends JpaRepository<ConstructionRequest, UUID> {
    /**
     * Возвращает единственную заявку карточки дома.
     *
     * <p>Доступ и блокировку карточки проверяет сервис проектов.
     *
     * @param projectId идентификатор карточки дома
     * @return найденный объект либо пустой Optional
     */
    Optional<ConstructionRequest> findByProjectId(UUID projectId);

    /**
     * Читает заявки набора уже авторизованных карточек.
     *
     * @param projectIds проверенные карточки
     * @return существующие заявки
     */
    List<ConstructionRequest> findByProjectIdIn(Set<UUID> projectIds);
}
