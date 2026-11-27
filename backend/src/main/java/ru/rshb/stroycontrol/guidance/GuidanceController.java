package ru.rshb.stroycontrol.guidance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.common.SafeDiagnostics;

import java.util.UUID;
import java.util.function.Supplier;

/** Закрытые read-only операции навигатора внутри существующего backend. */
@RestController
@RequiredArgsConstructor
@Slf4j
class GuidanceController {
    private final GuidanceService service;

    /**
     * Возвращает точки только текущему участнику.
     *
     * @param actor сессия
     * @param projectId карточка
     * @return персональный ответ без HTTP-кеша
     */
    @GetMapping("/api/v1/projects/{projectId}/control-gates")
    ResponseEntity<GuidanceView> gates(
            @AuthenticationPrincipal AccountView actor, @PathVariable UUID projectId) {
        return read(() -> service.gates(projectId, actor.id()));
    }

    /**
     * Возвращает число объектов с доступными аккаунту действиями.
     *
     * @param actor сессия
     * @return согласованная сводка
     */
    @GetMapping("/api/v1/construction-actions")
    ResponseEntity<ConstructionActionsView> actions(@AuthenticationPrincipal AccountView actor) {
        return read(() -> service.actions(actor));
    }

    /**
     * Возвращает паспорт без личных ипотечных сведений.
     *
     * @param actor сессия
     * @param projectId карточка
     * @return read-only характеристики
     */
    @GetMapping("/api/v1/projects/{projectId}/construction-profile")
    ResponseEntity<ConstructionProfileView> profile(
            @AuthenticationPrincipal AccountView actor, @PathVariable UUID projectId) {
        return read(() -> service.profile(projectId, actor.id()));
    }

    /**
     * Не превращает сбой источника в пустой успех; сохраняет существующие коды доступа.
     *
     * @param operation чтение через транзакционный proxy, включая его завершение
     * @param <T> тип ответа
     * @return успешный ответ либо существующий Problem
     */
    private <T> ResponseEntity<T> read(Supplier<T> operation) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(operation.get());
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Не удалось прочитать навигатор строительства", SafeDiagnostics.exception(e));
            throw new ApiException(ApiError.GUIDANCE_READ_FAILED);
        }
    }
}
