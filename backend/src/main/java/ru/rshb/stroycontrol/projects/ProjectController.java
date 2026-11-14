package ru.rshb.stroycontrol.projects;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.rshb.stroycontrol.accounts.AccountView;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Предоставляет HTTP-операции карточек домов: создание, список, просмотр и назначение подрядчика.
 *
 * <p>Аутентифицированный аккаунт берётся из сессии; права на конкретную карточку проверяет сервис.
 * Изменяющие запросы дополнительно защищены общим CSRF-фильтром.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
class ProjectController {
    private final ProjectService projects;

    /**
     * Поля новой карточки дома.
     *
     * @param name название проекта
     * @param address адрес строительного участка
     */
    record CreateProject(String name, String address) {}

    /**
     * Адресат назначения подрядчика.
     *
     * @param email email зарегистрированного подрядчика
     */
    record AssignContractor(String email) {}

    /**
     * Возвращает карточки текущего аккаунта.
     *
     * <p>Подрядчику не показываются ещё не отправленные заявки.
     *
     * @param account аккаунт текущей аутентифицированной сессии
     * @return элементы в порядке, определённом контрактом операции
     */
    @GetMapping
    List<ProjectView> list(@AuthenticationPrincipal AccountView account) {
        return projects.list(account);
    }

    /**
     * Создаёт карточку и черновик заявки заказчика.
     *
     * <p>Ответ 201 содержит Location новой карточки.
     *
     * @param account аккаунт текущей аутентифицированной сессии
     * @param input входные поля операции по её REST-контракту
     * @return HTTP 201 с карточкой дома и Location
     */
    @PostMapping
    ResponseEntity<ProjectView> create(
            @AuthenticationPrincipal AccountView account, @RequestBody CreateProject input) {
        var project = projects.create(account, input.name(), input.address());
        return ResponseEntity.created(URI.create("/api/v1/projects/%s".formatted(project.id())))
                .body(project);
    }

    /**
     * Возвращает доступную текущему аккаунту карточку.
     *
     * @param account аккаунт текущей аутентифицированной сессии
     * @param id идентификатор карточки дома
     * @return общая модель карточки без закрытых банковских сведений
     */
    @GetMapping("/{id}")
    ProjectView get(@AuthenticationPrincipal AccountView account, @PathVariable UUID id) {
        return projects.get(account, id);
    }

    /**
     * Назначает подрядчика по email от имени владельца карточки.
     *
     * <p>Повтор того же назначения не меняет участника.
     *
     * @param account аккаунт текущей аутентифицированной сессии
     * @param id идентификатор карточки дома
     * @param input email назначаемого подрядчика
     * @return карточка с назначенным подрядчиком
     */
    @PutMapping("/{id}/contractor")
    ProjectView assign(
            @AuthenticationPrincipal AccountView account,
            @PathVariable UUID id,
            @RequestBody AssignContractor input) {
        return projects.assign(account, id, input.email());
    }
}
