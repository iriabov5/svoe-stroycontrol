package ru.rshb.stroycontrol.projects;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
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
}
