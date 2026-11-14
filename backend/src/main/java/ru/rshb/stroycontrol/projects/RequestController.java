package ru.rshb.stroycontrol.projects;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.PreparationInput;

import java.util.Map;
import java.util.UUID;

/**
 * Предоставляет HTTP-операции черновика и отправки строительной заявки заказчика.
 *
 * <p>Аутентифицированный аккаунт берётся из сессии; права на конкретную карточку проверяет сервис.
 * Изменяющие запросы дополнительно защищены общим CSRF-фильтром.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/request")
@RequiredArgsConstructor
class RequestController {
    private final RequestService requests;

    /**
     * Возвращает видимые пожелания заказчика без закрытых кредитных данных.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @return видимые поля заявки без идентификатора кредитного снимка
     */
    @GetMapping
    RequestView get(@AuthenticationPrincipal AccountView actor, @PathVariable UUID projectId) {
        return requests.get(actor, projectId);
    }

    /**
     * Сохраняет поля черновика заявки с проверкой ожидаемой версии.
     *
     * <p>Отправленная заявка доступна только для чтения.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param body изменяемые поля черновика и ожидаемая версия
     * @return сохранённый черновик заявки
     */
    @PatchMapping
    RequestView patch(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @RequestBody Map<String, Object> body) {
        return requests.patch(actor, projectId, body);
    }

    /**
     * Отправляет проверенную заявку и открывает её назначенному подрядчику.
     *
     * <p>Повтор успешной отправки возвращает ранее отправленную заявку.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param body ожидаемая версия заявки
     * @return отправленная заявка
     */
    @PostMapping("/submit")
    RequestView submit(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @RequestBody Map<String, Object> body) {
        PreparationInput.keys(body, "version");
        return requests.submit(actor, projectId, body.get("version"));
    }
}
