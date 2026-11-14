package ru.rshb.stroycontrol.projects;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Хранит закрытые связи исходных заявок и их копий; доступ разрешает сервис проектов. */
interface RequestReissueRepository extends JpaRepository<RequestReissue, UUID> {
    /**
     * Возвращает единственную связь копирования исходной карточки.
     *
     * <p>Ссылка на копию выдаётся только владельцу.
     *
     * @param projectId идентификатор карточки дома
     * @return найденный объект либо пустой Optional
     */
    Optional<RequestReissue> findBySourceProjectId(UUID projectId);

    /**
     * Ищет команду копирования по владельцу и глобальному для него ключу повтора.
     *
     * <p>При повторе сервис дополнительно сравнивает отпечаток исходной карточки и тела.
     *
     * @param actorId идентификатор аутентифицированного аккаунта
     * @param key идентификатор повторной команды
     * @return найденный объект либо пустой Optional
     */
    Optional<RequestReissue> findByActorIdAndIdempotencyKey(UUID actorId, UUID key);
}
