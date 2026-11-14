package ru.rshb.stroycontrol.organizations;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP-граница реквизитов, личных приглашений и команды; права проверяет сервис, записи защищены
 * CSRF.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class OrganizationController {
    private final OrganizationService organizations;

    /**
     * @param actor личный аккаунт
     * @return профиль своей организации либо null
     */
    @GetMapping("/organizations/me")
    OrganizationService.Mine mine(@AuthenticationPrincipal AccountView actor) {
        return organizations.mine(actor);
    }

    /**
     * @param actor руководитель
     * @param body реквизиты
     * @return 201 при создании либо 200 при повторе
     */
    @PostMapping("/organizations")
    ResponseEntity<OrganizationView> create(
            @AuthenticationPrincipal AccountView actor, @RequestBody Map<String, Object> body) {
        try {
            var result = organizations.create(actor, body);
            return ResponseEntity.status(result.created() ? 201 : 200)
                    .location(URI.create("/api/v1/organizations/" + result.organization().id()))
                    .body(result.organization());
        } catch (DataIntegrityViolationException _) {
            throw new ApiException(ApiError.ORGANIZATION_ALREADY_EXISTS);
        }
    }

    /**
     * @param actor руководитель
     * @param id организация
     * @param body версия и реквизиты
     * @return актуальный профиль
     */
    @PatchMapping("/organizations/{id}")
    OrganizationView edit(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return organizations.edit(actor, id, body);
    }

    /**
     * @param actor сотрудник
     * @param id своя организация
     * @return её действующие участники
     */
    @GetMapping("/organizations/{id}/members")
    List<Organizations.Member> members(
            @AuthenticationPrincipal AccountView actor, @PathVariable UUID id) {
        return organizations.visibleMembers(actor, id);
    }

    /**
     * @param actor руководитель
     * @param id организация
     * @param body адресат и должность
     * @return приглашение, без отправки email
     */
    @PostMapping("/organizations/{id}/invitations")
    ResponseEntity<OrganizationService.InvitationView> invite(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        var result = organizations.invite(actor, id, body);
        return ResponseEntity.status(result.created() ? 201 : 200).body(result.invitation());
    }

    /**
     * @param actor руководитель
     * @param id организация
     * @return приглашения организации
     */
    @GetMapping("/organizations/{id}/invitations")
    List<OrganizationService.InvitationView> invitations(
            @AuthenticationPrincipal AccountView actor, @PathVariable UUID id) {
        return organizations.organizationInvitations(actor, id);
    }

    /**
     * @param actor адресат
     * @return только его открытые приглашения
     */
    @GetMapping("/auth/invitations")
    List<OrganizationService.InvitationView> personal(@AuthenticationPrincipal AccountView actor) {
        return organizations.personalInvitations(actor);
    }

    /**
     * @param actor адресат либо руководитель
     * @param id приглашение
     * @param action решение
     * @param body версия
     * @return сохранённое решение
     */
    @PostMapping("/organizations/invitations/{id}/{action:accept|decline|cancel}")
    OrganizationService.InvitationView decide(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID id,
            @PathVariable String action,
            @RequestBody Map<String, Object> body) {
        return organizations.decide(actor, id, action, body);
    }

    /**
     * @param actor руководитель
     * @param id организация
     * @param membershipId членство
     * @param body версия и должность
     * @return обновлённый сотрудник
     */
    @PatchMapping("/organizations/{id}/members/{membershipId}")
    Organizations.Member editMember(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID id,
            @PathVariable UUID membershipId,
            @RequestBody Map<String, Object> body) {
        return organizations.editMember(actor, id, membershipId, body);
    }

    /**
     * @param actor руководитель
     * @param id организация
     * @param membershipId членство
     * @param expectedVersion версия
     * @return 204 при исключении и его повторе
     */
    @DeleteMapping("/organizations/{id}/members/{membershipId}")
    ResponseEntity<Void> remove(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID id,
            @PathVariable UUID membershipId,
            @RequestParam long expectedVersion) {
        organizations.remove(actor, id, membershipId, expectedVersion);
        return ResponseEntity.noContent().build();
    }
}
