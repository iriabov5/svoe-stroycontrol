package ru.rshb.stroycontrol.projects;

import lombok.RequiredArgsConstructor;

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

/** Предоставляет сводку принятого плана и отдельное подтверждение завершения строительства. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/completion")
@RequiredArgsConstructor
class CompletionController {
    private final CompletionService service;

    /**
     * Читает готовность доступного проекта.
     *
     * @param actor участник
     * @param projectId проект
     * @return сводка
     */
    @GetMapping
    CompletionView get(@AuthenticationPrincipal AccountView actor, @PathVariable UUID projectId) {
        return service.get(actor, projectId);
    }

    /**
     * Фиксирует явное подтверждение владельца с проверкой версии.
     *
     * @param actor владелец
     * @param projectId проект
     * @param body команда
     * @return итог
     */
    @PostMapping
    CompletionView complete(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @RequestBody Map<String, Object> body) {
        return service.complete(actor, projectId, body);
    }
}
