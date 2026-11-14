package ru.rshb.stroycontrol.projects;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.rshb.stroycontrol.accounts.AccountView;

import java.util.Map;
import java.util.UUID;

/** HTTP-команды состава стройки; профиль и права сверяются сервисом, изменения защищены CSRF. */
@RestController
@RequestMapping("/api/v1/projects/{id}")
@RequiredArgsConstructor
class ProjectTeamController {
    private final ProjectTeamService team;

    /**
     * @param actor участник
     * @param id проект
     * @return состав и версия команды
     */
    @GetMapping("/contractor-team")
    ProjectTeamService.Team get(@AuthenticationPrincipal AccountView actor, @PathVariable UUID id) {
        return team.get(actor, id);
    }

    /**
     * @param actor руководитель
     * @param id проект
     * @param body полный состав
     * @return новая версия команды
     */
    @PutMapping("/contractor-team")
    ProjectTeamService.Team save(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return team.save(actor, id, body);
    }

    /**
     * @param actor основной контакт
     * @param id прежний проект
     * @param body собственная организация
     * @return привязанная карточка
     */
    @PostMapping("/contractor-organization")
    ProjectView connect(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return team.connect(actor, id, body);
    }

    /**
     * @param actor заказчик
     * @param id его карточка
     * @param email основной контакт подрядчика
     * @return краткий предпросмотр назначения
     */
    @GetMapping("/contractor-preview")
    ProjectTeamService.Preview preview(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID id,
            @RequestParam String email) {
        return team.preview(actor, id, email);
    }
}
