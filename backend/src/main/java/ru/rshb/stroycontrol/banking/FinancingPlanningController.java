package ru.rshb.stroycontrol.banking;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
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

/** Сессионный API личного планирования; права проверяет сервис, CSRF — общий фильтр. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/financing-planning")
@RequiredArgsConstructor
class FinancingPlanningController {
    private final FinancingPlanningService service;

    /**
     * Читает закрытый результат без кэширования браузером.
     *
     * @param actor текущий аккаунт
     * @param projectId карточка
     * @return сохранённые результаты либо пустой план
     */
    @GetMapping
    ResponseEntity<FinancingPlanningView> get(
            @AuthenticationPrincipal AccountView actor, @PathVariable UUID projectId) {
        return response(service.get(projectId, actor.id()));
    }

    /**
     * Проверяет номер сохранённой заявки с безопасным повтором.
     *
     * @param actor текущий аккаунт
     * @param projectId карточка
     * @param body версии и ключ команды
     * @return сохранённый результат проверки
     */
    @PostMapping("/plot-checks")
    ResponseEntity<FinancingPlanningView> check(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @RequestBody Map<String, Object> body) {
        return response(service.check(projectId, actor.id(), body));
    }

    /**
     * Сохраняет предварительный расчёт по синтетическим условиям.
     *
     * @param actor текущий аккаунт
     * @param projectId карточка
     * @param body параметры, версии и ключ команды
     * @return серверный расчёт, не являющийся одобрением
     */
    @PostMapping("/calculations")
    ResponseEntity<FinancingPlanningView> calculate(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @RequestBody Map<String, Object> body) {
        return response(service.calculate(projectId, actor.id(), body));
    }

    /**
     * Исключает сохранение личных банковских оценок в HTTP-кэше.
     *
     * @param view ответ владельцу
     * @return ответ с no-store
     */
    private ResponseEntity<FinancingPlanningView> response(FinancingPlanningView view) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view);
    }
}
