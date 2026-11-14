package ru.rshb.stroycontrol.banking;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
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

/** REST банковских подтверждений только владельца; краткий статус подрядчику выдаёт приёмка. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
class BankStageController {
    private final BankStageService service;
    private final ConstructionBankCases cases;

    /**
     * Показывает состав и историю без автоматической отправки.
     *
     * @param actor владелец
     * @param projectId проект
     * @param stageId этап
     * @return банковский блок
     */
    @GetMapping("/stages/{stageId}/bank-confirmation")
    BankStageService.Confirmation get(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId) {
        return service.get(actor.id(), projectId, stageId);
    }

    /**
     * Явно отправляет принятую сдачу; сетевой сбой отражается состоянием пакета.
     *
     * @param actor владелец
     * @param projectId проект
     * @param stageId этап
     * @param body команда
     * @return принятая в обработку отправка
     */
    @PostMapping("/stages/{stageId}/bank-confirmations")
    ResponseEntity<BankStageView> send(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId,
            @RequestBody Map<String, Object> body) {
        var result = service.send(actor.id(), projectId, stageId, body);
        return ResponseEntity.status(result.created() ? 202 : 200).body(result.submission());
    }

    /**
     * Повторяет ту же версию после проверки текущих прав и версии.
     *
     * @param actor владелец
     * @param projectId проект
     * @param stageId этап
     * @param submissionId отправка
     * @param body версия
     * @return актуальный результат
     */
    @PostMapping("/stages/{stageId}/bank-confirmations/{submissionId}/retry")
    BankStageView retry(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId,
            @PathVariable UUID submissionId,
            @RequestBody Map<String, Object> body) {
        return service.retry(actor.id(), projectId, stageId, submissionId, body);
    }

    /**
     * Подключает синтетическое дело с отдельным согласием, не меняя отправленную заявку.
     *
     * @param actor владелец
     * @param projectId проект
     * @param body согласие и ключ
     * @return демо-дело
     */
    @PostMapping("/bank-case")
    ConstructionBankCases.CaseView connect(
            @AuthenticationPrincipal AccountView actor,
            @PathVariable UUID projectId,
            @RequestBody Map<String, Object> body) {
        return cases.connect(projectId, actor.id(), body);
    }
}
