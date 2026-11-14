package ru.rshb.stroycontrol.stages;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.rshb.stroycontrol.accounts.AccountView;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Предоставляет HTTP-операции плана и выполнения этапов строительства.
 *
 * <p>Аутентифицированный аккаунт берётся из сессии; права на конкретную карточку проверяет сервис.
 * Изменяющие запросы дополнительно защищены общим CSRF-фильтром.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/stages")
@RequiredArgsConstructor
class StageController {
    private final StageService stages;

    record CreateStage(
            String name, StageWorkType workType, String plannedStart, String plannedFinish) {
        StagePlan plan() {
            return StagePlan.parse(name, workType, plannedStart, plannedFinish);
        }
    }

    record UpdateStage(
            Long version,
            String name,
            StageWorkType workType,
            String plannedStart,
            String plannedFinish) {
        StagePlan plan() {
            return StagePlan.parse(name, workType, plannedStart, plannedFinish);
        }
    }

    record Command(Long version) {}

    record Order(List<StageVersion> stages) {}

    /**
     * Возвращает план этапов доступной карточки в заданном порядке.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @return элементы в порядке, определённом контрактом операции
     */
    @GetMapping
    List<StageView> list(@AuthenticationPrincipal AccountView actor, @PathVariable UUID projectId) {
        return stages.list(actor, projectId);
    }

    /**
     * Добавляет этап в план LEGACY-карточки от имени владельца.
     *
     * <p>Ответ 201 содержит Location созданного этапа.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param input входные поля операции по её REST-контракту
     * @return этап с актуальной версией, датами и вычисленными просрочками
     */
    @PostMapping
    ResponseEntity<StageView> create(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @RequestBody CreateStage input) {
        var stage = stages.create(actor, projectId, input.plan());
        return ResponseEntity.created(
                        URI.create(
                                "/api/v1/projects/%s/stages/%s".formatted(projectId, stage.id())))
                .body(stage);
    }

    /**
     * Возвращает этап с фактическими датами и вычисленными просрочками.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа в указанной карточке
     * @return этап с актуальной версией, датами и вычисленными просрочками
     */
    @GetMapping("/{stageId}")
    StageView get(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId) {
        return stages.get(actor, projectId, stageId);
    }

    /**
     * Сохраняет плановые поля LEGACY-этапа с проверкой версии.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа в указанной карточке
     * @param input входные поля операции по её REST-контракту
     * @return этап с актуальной версией, датами и вычисленными просрочками
     */
    @PutMapping("/{stageId}")
    StageView update(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId,
            @RequestBody UpdateStage input) {
        return stages.update(actor, projectId, stageId, input.version(), input.plan());
    }

    /**
     * Атомарно меняет полный порядок LEGACY-плана владельцем карточки.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param input входные поля операции по её REST-контракту
     * @return элементы в порядке, определённом контрактом операции
     */
    @PutMapping("/order")
    List<StageView> reorder(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @RequestBody Order input) {
        return stages.reorder(actor, projectId, input.stages());
    }

    /**
     * Фиксирует начало работ подрядчиком после проверки сметы и договора.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа в указанной карточке
     * @param input входные поля операции по её REST-контракту
     * @return этап с актуальной версией, датами и вычисленными просрочками
     */
    @PostMapping("/{stageId}/start")
    StageView start(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId,
            @RequestBody Command input) {
        return stages.start(actor, projectId, stageId, input.version());
    }

    /**
     * Фиксирует завершение начатого этапа подрядчиком.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param stageId идентификатор этапа в указанной карточке
     * @param input входные поля операции по её REST-контракту
     * @return этап с актуальной версией, датами и вычисленными просрочками
     */
    @PostMapping("/{stageId}/complete")
    StageView complete(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId,
            @RequestBody Command input) {
        return stages.complete(actor, projectId, stageId, input.version());
    }
}
