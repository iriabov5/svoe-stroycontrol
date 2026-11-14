package ru.rshb.stroycontrol.acceptance;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.rshb.stroycontrol.accounts.AccountView;

import java.util.Map;
import java.util.UUID;

/** REST приёмки внутри этапа; автор берётся только из сессии, CSRF проверяет общий фильтр. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/stages/{stageId}/acceptance")
@RequiredArgsConstructor
class AcceptanceController {
    private final AcceptanceService service;

    /**
     * Возвращает историю сдач доступного этапа.
     *
     * @param actor участник
     * @param projectId проект
     * @param stageId этап
     * @return сводка с историей
     */
    @GetMapping
    AcceptanceService.Page list(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId) {
        return service.list(actor, projectId, stageId);
    }

    /**
     * Передаёт завершённый этап на приёмку; идентичный повтор возвращает 200 вместо 201.
     *
     * @param actor сотрудник
     * @param projectId проект
     * @param stageId этап
     * @param body команда
     * @return сохранённая сдача
     */
    @PostMapping
    ResponseEntity<SubmissionView> submit(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId,
            @RequestBody Map<String, Object> body) {
        var result = service.submit(actor, projectId, stageId, body);
        return ResponseEntity.status(result.created() ? 201 : 200).body(result.submission());
    }

    /**
     * Принимает решение владельца по текущему циклу.
     *
     * @param actor владелец
     * @param projectId проект
     * @param stageId этап
     * @param submissionId цикл
     * @param body команда
     * @return решение
     */
    @PostMapping("/{submissionId}/decision")
    SubmissionView decide(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId,
            @PathVariable UUID submissionId,
            @RequestBody Map<String, Object> body) {
        return service.decide(actor, projectId, stageId, submissionId, body);
    }
}
