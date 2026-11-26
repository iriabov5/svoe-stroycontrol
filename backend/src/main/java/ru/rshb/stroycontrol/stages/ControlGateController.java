package ru.rshb.stroycontrol.stages;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.rshb.stroycontrol.accounts.AccountView;

import java.util.List;
import java.util.UUID;

/** Предоставляет участнику read-only подсказки перед контрольными точками строительства. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/control-gates")
@RequiredArgsConstructor
class ControlGateController {
    private final StageService stages;

    /**
     * Возвращает контрольные точки доступного проекта.
     *
     * <p>Карточки не меняют план и не являются заключением строительного контроля. Доступ к
     * карточке проверяется до чтения этапов.
     *
     * @param actor аутентифицированный участник проекта
     * @param projectId идентификатор карточки дома
     * @return подсказки в постоянном порядке
     */
    @GetMapping
    List<ControlGateView> list(
            @AuthenticationPrincipal AccountView actor, @PathVariable UUID projectId) {
        return stages.controlGates(actor, projectId);
    }
}
