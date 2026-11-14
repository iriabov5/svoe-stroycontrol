package ru.rshb.stroycontrol.stages;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.projects.ProjectAccess;
import ru.rshb.stroycontrol.projects.Projects;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Управляет планом и выполнением строительных этапов с проверкой участников карточки.
 *
 * <p>Ручное редактирование плана доступно владельцу только для LEGACY-процесса. Начало и завершение
 * выполняет подрядчик; в новом процессе обязательны согласованные смета и договор. Запись
 * координируется блокировкой проекта, а версия этапа защищает от устаревшего ввода.
 */
@Service
@RequiredArgsConstructor
class StageService implements Stages {
    private final StageRepository repository;
    private final Projects projects;
    private final Clock clock;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<StageView> plan(UUID projectId, UUID actorId) {
        projects.requireParticipant(projectId, actorId);
        return views(repository.findByProjectIdOrderByPositionAsc(projectId));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public StageView requireParticipant(UUID projectId, UUID stageId, UUID actorId) {
        projects.requireParticipant(projectId, actorId);
        return stage(projectId, stageId).view(today());
    }

    /**
     * Возвращает этапы доступной карточки в плановом порядке.
     *
     * <p>Просрочки рассчитываются на текущую дату общих часов приложения.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @return элементы в порядке, определённом контрактом операции
     * @throws ApiException с PROJECT_NOT_FOUND для недоступной карточки
     */
    @Transactional(readOnly = true)
    List<StageView> list(AccountView actor, UUID projectId) {
        projects.requireParticipant(projectId, actor.id());
        return views(repository.findByProjectIdOrderByPositionAsc(projectId));
    }

    /**
     * Возвращает этап, принадлежащий доступной карточке.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа в указанной карточке
     * @return этап с актуальной версией, датами и вычисленными просрочками
     * @throws ApiException с PROJECT_NOT_FOUND или STAGE_NOT_FOUND
     */
    @Transactional(readOnly = true)
    StageView get(AccountView actor, UUID projectId, UUID stageId) {
        projects.requireParticipant(projectId, actor.id());
        return stage(projectId, stageId).view(today());
    }

    /**
     * Добавляет плановый этап в конец плана LEGACY-карточки.
     *
     * <p>Разрешено владельцу; позиция вычисляется под блокировкой проекта.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param plan проверенные название, вид работ и плановые даты этапа
     * @return этап с актуальной версией, датами и вычисленными просрочками
     * @throws ApiException с FORBIDDEN или APPROVED_PLAN_LOCKED
     */
    @Transactional
    StageView create(AccountView actor, UUID projectId, StagePlan plan) {
        var access = projects.lockParticipant(projectId, actor.id());
        projects.requireOpen(projectId, actor.id());
        requireLegacy(access);
        requireOwner(access.owner());
        projects.touchCompletion(projectId, actor.id());
        var position = repository.findByProjectIdOrderByPositionAsc(projectId).size() + 1;
        return repository
                .saveAndFlush(ConstructionStage.create(projectId, position, plan))
                .view(today());
    }

    /**
     * Изменяет плановые поля этапа LEGACY-карточки.
     *
     * <p>Фактические даты сохраняются. Версия проверяется под блокировкой проекта до изменения;
     * ответ содержит уже увеличенную версию.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа в указанной карточке
     * @param version ожидаемая версия до изменения
     * @param plan проверенные название, вид работ и плановые даты этапа
     * @return этап с актуальной версией, датами и вычисленными просрочками
     * @throws ApiException с STALE_STAGE_VERSION, FORBIDDEN или APPROVED_PLAN_LOCKED
     */
    @Transactional
    StageView update(
            AccountView actor, UUID projectId, UUID stageId, Long version, StagePlan plan) {
        var access = projects.lockParticipant(projectId, actor.id());
        var stage = stage(projectId, stageId);
        projects.requireOpen(projectId, actor.id());
        requireLegacy(access);
        requireOwner(access.owner());
        stage.requireVersion(version);
        stage.updatePlan(plan);
        repository.flush();
        return stage.view(today());
    }

    /**
     * Атомарно переставляет весь план LEGACY-карточки.
     *
     * <p>Состав, уникальность ID и все версии проверяются до первой перестановки. Блокировка
     * проекта исключает конкурентное изменение порядка.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param order полный порядок этапов с ожидаемыми версиями
     * @return элементы в порядке, определённом контрактом операции
     * @throws ApiException при неполном порядке, чужом этапе или устаревшей версии
     */
    @Transactional
    List<StageView> reorder(AccountView actor, UUID projectId, List<StageVersion> order) {
        var access = projects.lockParticipant(projectId, actor.id());
        projects.requireOpen(projectId, actor.id());
        requireLegacy(access);
        requireOwner(access.owner());
        var stages = repository.findByProjectIdOrderByPositionAsc(projectId);
        if (order == null || order.size() != stages.size())
            throw new ApiException(ApiError.VALIDATION_ERROR);
        var byId =
                stages.stream()
                        .collect(Collectors.toMap(ConstructionStage::getId, Function.identity()));
        var seen = new HashSet<UUID>();
        for (var entry : order) {
            if (entry == null || !byId.containsKey(entry.id()) || !seen.add(entry.id())) {
                throw new ApiException(ApiError.VALIDATION_ERROR);
            }
            byId.get(entry.id()).requireVersion(entry.version());
        }
        var result = new ArrayList<ConstructionStage>();
        for (int index = 0; index < order.size(); index++) {
            var stage = byId.get(order.get(index).id());
            stage.moveTo(index + 1);
            result.add(stage);
        }
        repository.flush();
        return views(result);
    }

    /**
     * Фиксирует фактическое начало работ назначенным подрядчиком.
     *
     * <p>В новом процессе обязательны согласованные смета и договор. Повтор после успешного старта
     * возвращает тот же результат без смены даты.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа в указанной карточке
     * @param version ожидаемая версия до изменения
     * @return этап с актуальной версией, датами и вычисленными просрочками
     * @throws ApiException с CONTRACT_REQUIRED, FORBIDDEN или STALE_STAGE_VERSION
     */
    @Transactional
    StageView start(AccountView actor, UUID projectId, UUID stageId, Long version) {
        return transition(actor, projectId, stageId, version, false);
    }

    /**
     * Фиксирует завершение начатого этапа назначенным подрядчиком.
     *
     * <p>Дата завершения не предшествует началу. Повтор успешного завершения не меняет фактические
     * даты.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа в указанной карточке
     * @param version ожидаемая версия до изменения
     * @return этап с актуальной версией, датами и вычисленными просрочками
     * @throws ApiException с INVALID_STAGE_TRANSITION, FORBIDDEN или STALE_STAGE_VERSION
     */
    @Transactional
    StageView complete(AccountView actor, UUID projectId, UUID stageId, Long version) {
        return transition(actor, projectId, stageId, version, true);
    }

    /**
     * Выполняет переход этапа под общей с журналом блокировкой проекта.
     *
     * <p>Проверки доступа, договора и версии предшествуют изменению фактических дат.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа в указанной карточке
     * @param version ожидаемая версия до изменения
     * @param complete true для завершения, false для начала работ
     * @return этап с актуальной версией, датами и вычисленными просрочками
     */
    private StageView transition(
            AccountView actor, UUID projectId, UUID stageId, Long version, boolean complete) {
        var access = projects.lockParticipant(projectId, actor.id());
        var stage = stage(projectId, stageId);
        if ("PROPOSAL_REQUIRED".equals(access.processMode())
                && (access.approvedProposalId() == null || access.approvedContractId() == null))
            throw new ApiException(ApiError.CONTRACT_REQUIRED);
        if (!access.canRecordWork()) throw new ApiException(ApiError.FORBIDDEN);
        if (version == null || version < 0) throw new ApiException(ApiError.VALIDATION_ERROR);
        if (complete) stage.complete(version, today());
        else stage.start(version, today());
        repository.flush();
        return stage.view(today());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void install(UUID projectId, List<ApprovedStage> plan) {
        if (!repository.findByProjectIdOrderByPositionAsc(projectId).isEmpty())
            throw new ApiException(ApiError.INVALID_PROPOSAL_STATE);
        int position = 1;
        for (var p : plan) {
            var stage =
                    ConstructionStage.create(
                            projectId,
                            position++,
                            new StagePlan(p.name(), p.workType(), p.start(), p.finish()));
            stage.source(p.id(), p.result());
            repository.save(stage);
        }
        repository.flush();
    }

    /**
     * Запрещает ручное редактирование плана нового процесса согласования.
     *
     * @param access права текущего участника карточки
     * @throws ApiException с APPROVED_PLAN_LOCKED для нового процесса
     */
    private void requireLegacy(ProjectAccess access) {
        if (!"LEGACY".equals(access.processMode()))
            throw new ApiException(ApiError.APPROVED_PLAN_LOCKED);
    }

    /**
     * Находит этап с одновременной проверкой принадлежности карточке.
     *
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа в указанной карточке
     * @return этап указанной карточки
     * @throws ApiException с STAGE_NOT_FOUND при чужом или отсутствующем этапе
     */
    private ConstructionStage stage(UUID projectId, UUID stageId) {
        return repository
                .findByIdAndProjectId(stageId, projectId)
                .orElseThrow(() -> new ApiException(ApiError.STAGE_NOT_FOUND));
    }

    /**
     * Проверяет право владельца изменять план LEGACY-карточки.
     *
     * @param owner true, если текущий участник является владельцем карточки
     * @throws ApiException с FORBIDDEN для другого участника
     */
    private void requireOwner(boolean owner) {
        if (!owner) throw new ApiException(ApiError.FORBIDDEN);
    }

    /**
     * Получает сегодняшнюю дату из общих часов приложения.
     *
     * @return сегодняшняя дата в часовом поясе общих часов
     */
    private LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Формирует модели всех этапов относительно одной и той же текущей даты.
     *
     * @param stages этапы в порядке показа
     * @return элементы в порядке, определённом контрактом операции
     */
    private List<StageView> views(List<ConstructionStage> stages) {
        var today = today();
        return stages.stream().map(stage -> stage.view(today)).toList();
    }
}
