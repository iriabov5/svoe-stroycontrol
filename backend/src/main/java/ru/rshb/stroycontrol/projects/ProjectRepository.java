package ru.rshb.stroycontrol.projects;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Хранит карточки дома и предоставляет общую блокировку записи для операций всех модулей. */
interface ProjectRepository extends JpaRepository<HouseProject, UUID> {
    /**
     * Возвращает карточки владельца от новых к старым с устойчивым порядком по ID.
     *
     * @param customerId идентификатор владельца карточек
     * @return элементы в порядке, определённом контрактом операции
     */
    List<HouseProject> findByCustomerIdOrderByCreatedAtDescIdDesc(UUID customerId);

    /**
     * Возвращает назначенные подрядчику карточки от новых к старым.
     *
     * <p>Окончательную видимость неотправленных заявок проверяет сервис проектов.
     *
     * @param contractorId идентификатор назначенного подрядчика
     * @return элементы в порядке, определённом контрактом операции
     */
    List<HouseProject> findByContractorIdOrderByCreatedAtDescIdDesc(UUID contractorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select p from HouseProject p
            where p.id = :id and (p.customerId = :accountId or p.contractorId = :accountId)
            """)
    /**
     * Блокирует карточку участника до завершения активной транзакции.
     *
     * <p>Пессимистическая блокировка сериализует изменения карточки и её дочерних объектов.
     * Проверка видимости для подрядчика до отправки заявки выполняется сервисом.
     *
     * @param id идентификатор карточки дома
     * @param accountId идентификатор аутентифицированного аккаунта
     * @return найденный объект либо пустой Optional
     */
    Optional<HouseProject> lockAccessible(UUID id, UUID accountId);

    /** Маршрут блокировки без помещения изменяемой карточки в persistence context. */
    interface Routing {
        /**
         * @return назначенная организация либо null
         */
        UUID getOrganizationId();

        /**
         * @return владелец карточки
         */
        UUID getCustomerId();

        /**
         * @return основной контакт либо null
         */
        UUID getContractorId();
    }

    /**
     * @param id карточка
     * @return связи для порядка блокировок
     */
    @Query(
"""
    select p.contractorOrganizationId as organizationId,p.customerId as customerId,p.contractorId as contractorId
    from HouseProject p
    where p.id=:id
""")
    Optional<Routing> routing(UUID id);

    /**
     * @param id карточка
     * @return карточка под общей блокировкой проекта
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from HouseProject p where p.id=:id")
    Optional<HouseProject> lockById(UUID id);

    /**
     * Возвращает кандидатов организации; фактический доступ проверяет сервис.
     *
     * @param organizationId текущая организация
     * @return кандидаты карточек
     */
    List<HouseProject> findByContractorOrganizationId(UUID organizationId);
}
