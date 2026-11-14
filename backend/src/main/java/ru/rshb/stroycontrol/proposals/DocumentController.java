package ru.rshb.stroycontrol.proposals;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.rshb.stroycontrol.accounts.AccountView;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Предоставляет HTTP-операции версий сметы и договора и решений по ним.
 *
 * <p>Аутентифицированный аккаунт берётся из сессии; права на конкретную карточку проверяет сервис.
 * Изменяющие запросы дополнительно защищены общим CSRF-фильтром.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/{collection:proposals|contracts}")
@RequiredArgsConstructor
class DocumentController {
    private final DocumentService documents;

    /**
     * Определяет вид документа по коллекции, уже ограниченной REST-маршрутом.
     *
     * @param collection proposals для смет или contracts для договоров
     * @return true для коллекции contracts
     */
    private boolean contract(String collection) {
        return collection.equals("contracts");
    }

    /**
     * Возвращает историю смет или договоров, видимых текущему участнику.
     *
     * <p>Черновики подрядчика скрыты от заказчика.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param collection proposals для смет или contracts для договоров
     * @return элементы в порядке, определённом контрактом операции
     */
    @GetMapping
    List<DocumentView> list(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable String collection) {
        return documents.list(actor, projectId, contract(collection));
    }

    /**
     * Создаёт черновик новой версии сметы или договора подрядчиком.
     *
     * <p>Повтор возвращает существующий черновик.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param collection proposals для смет или contracts для договоров
     * @return черновик новой или уже существующей версии документа
     */
    @PostMapping
    DocumentView create(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable String collection) {
        return documents.create(actor, projectId, contract(collection));
    }

    /**
     * Возвращает видимую участнику версию документа с решением заказчика.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param collection proposals для смет или contracts для договоров
     * @param documentId идентификатор версии сметы или договора
     * @return версия документа и решение заказчика, если оно уже есть
     */
    @GetMapping("/{documentId}")
    DocumentView get(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable String collection,
            @PathVariable UUID documentId) {
        return documents.get(actor, projectId, documentId, contract(collection));
    }

    /**
     * Сохраняет черновик документа с проверкой версии и назначенного подрядчика.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param collection proposals для смет или contracts для договоров
     * @param documentId идентификатор версии сметы или договора
     * @param body поля черновика и ожидаемая версия
     * @return сохранённый черновик с новой версией
     */
    @PutMapping("/{documentId}")
    DocumentView save(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable String collection,
            @PathVariable UUID documentId,
            @RequestBody Map<String, Object> body) {
        return documents.save(actor, projectId, documentId, contract(collection), body);
    }

    /**
     * Передаёт отправку, согласование или возврат документа на уточнение сервису.
     *
     * <p>Код действия ограничен маршрутом; роль и переход проверяются на backend.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param collection proposals для смет или contracts для договоров
     * @param documentId идентификатор версии сметы или договора
     * @param action submit, approve, request-changes или reject по разрешённому маршруту
     * @param body ожидаемая версия и для отклонения комментарий
     * @return документ после выполненного перехода
     */
    @PostMapping("/{documentId}/{action:submit|approve|request-changes}")
    DocumentView command(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable String collection,
            @PathVariable UUID documentId,
            @PathVariable String action,
            @RequestBody Map<String, Object> body) {
        return documents.command(actor, projectId, documentId, contract(collection), action, body);
    }
}
