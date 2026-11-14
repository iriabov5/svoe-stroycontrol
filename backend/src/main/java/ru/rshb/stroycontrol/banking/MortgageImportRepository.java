package ru.rshb.stroycontrol.banking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Хранит результаты импорта и ключи повторов отдельно от неизменяемых снимков. */
interface MortgageImportRepository extends JpaRepository<MortgageImport, UUID> {
    /**
     * Возвращает последнюю попытку импорта одобрения конкретного владельца.
     *
     * <p>Время и ID обеспечивают устойчивый порядок при совпадении дат попыток.
     *
     * @param projectId идентификатор карточки дома
     * @param ownerId идентификатор владельца ипотечной заявки
     * @return найденный объект либо пустой Optional
     */
    Optional<MortgageImport> findFirstByProjectIdAndOwnerIdOrderByCreatedAtDescIdDesc(
            UUID projectId, UUID ownerId);

    /**
     * Находит прежний результат импорта по владельцу, карточке и ключу команды.
     *
     * <p>Версию исходной заявки дополнительно проверяет банковский сервис.
     *
     * @param ownerId идентификатор владельца ипотечной заявки
     * @param projectId идентификатор карточки дома
     * @param key идентификатор повторной команды
     * @return найденный объект либо пустой Optional
     */
    Optional<MortgageImport> findByOwnerIdAndProjectIdAndIdempotencyKey(
            UUID ownerId, UUID projectId, UUID key);
}
