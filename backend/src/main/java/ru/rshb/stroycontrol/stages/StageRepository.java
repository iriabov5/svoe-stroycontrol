package ru.rshb.stroycontrol.stages;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Хранит этапы своего модуля; принадлежность карточке проверяется вместе с ID этапа. */
interface StageRepository extends JpaRepository<ConstructionStage, UUID> {
    /**
     * Возвращает этапы карточки в плановом порядке.
     *
     * <p>Доступ к карточке проверяет вызывающий сервис.
     *
     * @param projectId идентификатор карточки дома
     * @return элементы в порядке, определённом контрактом операции
     */
    List<ConstructionStage> findByProjectIdOrderByPositionAsc(UUID projectId);

    /**
     * Ищет этап одновременно по собственному ID и карточке.
     *
     * <p>Не позволяет подставить этап из другого проекта.
     *
     * @param id идентификатор этапа
     * @param projectId идентификатор карточки дома
     * @return найденный объект либо пустой Optional
     */
    Optional<ConstructionStage> findByIdAndProjectId(UUID id, UUID projectId);

    /**
     * Читает планы набором без запроса для каждого этапа.
     *
     * @param projects авторизованные карточки
     * @return этапы в порядке плана
     */
    List<ConstructionStage> findByProjectIdInOrderByPositionAscIdAsc(Set<UUID> projects);
}
