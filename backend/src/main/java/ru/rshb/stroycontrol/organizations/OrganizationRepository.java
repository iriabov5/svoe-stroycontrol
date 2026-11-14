package ru.rshb.stroycontrol.organizations;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/** Хранит реквизиты и сериализует изменения команды с бизнес-командами проектов. */
interface OrganizationRepository extends JpaRepository<ContractorOrganization, UUID> {
    /**
     * @param id организация
     * @return организация под блокировкой до завершения транзакции
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ContractorOrganization o where o.id=:id")
    Optional<ContractorOrganization> lockById(UUID id);

    /**
     * @param ownerAccountId руководитель
     * @return его организация либо пустой результат
     */
    Optional<ContractorOrganization> findByOwnerAccountId(UUID ownerAccountId);

    /**
     * @param inn ИНН
     * @return существует ли профиль с таким ИНН
     */
    boolean existsByInn(String inn);
}
