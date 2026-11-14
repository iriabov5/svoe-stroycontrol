package ru.rshb.stroycontrol.projects;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;

import java.net.URI;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/**
 * Предоставляет HTTP-операции создания независимой копии заявки для другого подрядчика.
 *
 * <p>Аутентифицированный аккаунт берётся из сессии; права на конкретную карточку проверяет сервис.
 * Изменяющие запросы дополнительно защищены общим CSRF-фильтром.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/request/reissue")
@RequiredArgsConstructor
class ReissueController {
    private final RequestService requests;

    /**
     * Возвращает владельцу ссылку на созданную копию заявки либо null.
     *
     * <p>Подрядчикам связь между карточками недоступна.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор исходной карточки дома
     * @return идентификатор карточки-копии либо null, если копии ещё нет
     */
    @GetMapping
    ReissueLink link(@AuthenticationPrincipal AccountView actor, @PathVariable UUID projectId) {
        return requests.reissueLink(actor, projectId);
    }

    /**
     * Создаёт независимую копию отклонённой заявки для другого подрядчика.
     *
     * <p>Первый успешный запрос возвращает 201, идентичный повтор — 200 и тот же Location. Конфликт
     * уникального ключа преобразуется после отката транзакции сервиса.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param body адрес подрядчика и ключ повтора
     * @return HTTP 201 для новой копии или 200 при повторе с карточкой и Location
     */
    @PostMapping
    ResponseEntity<ProjectView> reissue(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @RequestBody Map<String, Object> body) {
        try {
            var result = requests.reissue(actor, projectId, body);
            return ResponseEntity.status(result.created() ? 201 : 200)
                    .location(URI.create("/api/v1/projects/%s".formatted(result.project().id())))
                    .body(result.project());
        } catch (DataIntegrityViolationException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause())
                if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState()))
                    throw new ApiException(ApiError.IDEMPOTENCY_CONFLICT);
            throw e;
        }
    }
}
