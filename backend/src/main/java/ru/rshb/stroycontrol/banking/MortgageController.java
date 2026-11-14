package ru.rshb.stroycontrol.banking;

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

/**
 * Предоставляет HTTP-операции закрытого демонстрационного одобрения сельской ипотеки владельца.
 *
 * <p>Аутентифицированный аккаунт берётся из сессии; права на конкретную карточку проверяет сервис.
 * Изменяющие запросы дополнительно защищены общим CSRF-фильтром.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/mortgage-approval")
@RequiredArgsConstructor
class MortgageController {
    private final MortgageService mortgages;

    /**
     * Возвращает владельцу состояние демонстрационного ипотечного одобрения.
     *
     * <p>Доступ подрядчика к банковским сведениям скрывается как отсутствие карточки.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @return состояние одобрения и синтетический снимок либо null
     */
    @GetMapping
    MortgageApproval get(@AuthenticationPrincipal AccountView actor, @PathVariable UUID projectId) {
        return mortgages.get(projectId, actor.id());
    }

    /**
     * Запускает демонстрационный импорт после согласия владельца.
     *
     * <p>Повторный ключ возвращает прежний результат без нового HTTP-вызова к Mock API.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param body версия заявки, согласие на демонстрационный импорт и ключ повтора
     * @return зафиксированный результат импорта либо результат идентичного повтора
     */
    @PostMapping("/import")
    MortgageApproval load(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @RequestBody Map<String, Object> body) {
        return mortgages.load(projectId, actor.id(), body);
    }
}
