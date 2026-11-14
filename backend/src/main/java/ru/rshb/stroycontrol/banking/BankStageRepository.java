package ru.rshb.stroycontrol.banking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Владеет отправками этапов; все изменения сериализуются блокировкой проекта. */
interface BankStageRepository extends JpaRepository<BankStageSubmission, UUID> {
    /**
     * Читает историю владельца по одному этапу.
     *
     * @param projectId проект
     * @param stageId этап
     * @return версии от последней к первой
     */
    List<BankStageSubmission> findByProjectIdAndStageIdOrderByRevisionDesc(
            UUID projectId, UUID stageId);

    /**
     * Ищет первоначальную команду отправки независимо от текущего состояния банка.
     *
     * @param actorId владелец
     * @param projectId проект
     * @param idempotencyKey ключ
     * @return прежняя отправка
     */
    Optional<BankStageSubmission> findByActorIdAndProjectIdAndIdempotencyKey(
            UUID actorId, UUID projectId, UUID idempotencyKey);

    /**
     * Читает маршрутизацию без помещения изменяемой сущности в контекст до блокировки проекта.
     *
     * @param id отправка
     * @return адресат блокировки
     */
    @Query(
"""
    select b.projectId as projectId,b.actorId as actorId
    from BankStageSubmission b
    where b.id=:id
""")
    Optional<Route> route(UUID id);

    /**
     * Выбирает ограниченную очередь незавершённых попыток.
     *
     * @param now время истечения аренды
     * @param pageable предел очереди
     * @return ID без сущностей
     */
    @Query(
"""
    select b.id
    from BankStageSubmission b
    where b.state in ('PENDING','UNKNOWN','RECEIVED')
    and b.autoAttempts<3
    and (b.leaseUntil is null or b.leaseUntil<:now)
    order by b.createdAt
""")
    List<UUID> due(Instant now, Pageable pageable);

    /** Адрес проекта и первоначально авторизованного владельца для фоновой доставки. */
    interface Route {
        UUID getProjectId();

        UUID getActorId();
    }
}
