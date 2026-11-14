package ru.rshb.stroycontrol.proposals;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.rshb.stroycontrol.accounts.AccountView;

import java.util.Map;
import java.util.UUID;

/**
 * Предоставляет HTTP-операции окончательного отказа от несогласованной сметы с обязательной
 * причиной.
 *
 * <p>Аутентифицированный аккаунт берётся из сессии; права на конкретную карточку проверяет сервис.
 * Изменяющие запросы дополнительно защищены общим CSRF-фильтром.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/proposals/{documentId}/reject")
@RequiredArgsConstructor
class ProposalRejectionController {
    private final DocumentService documents;

    /**
     * Окончательно отклоняет отправленную смету с сохранением причины.
     *
     * <p>Разрешено владельцу до согласования; подготовка закрывается атомарно с решением.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param documentId идентификатор отклоняемой версии сметы
     * @param body ожидаемая версия и комментарий отклонения
     * @return отклонённая смета с сохранённым решением
     */
    @PostMapping
    DocumentView reject(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID documentId,
            @RequestBody Map<String, Object> body) {
        return documents.command(actor, projectId, documentId, false, "reject", body);
    }
}
