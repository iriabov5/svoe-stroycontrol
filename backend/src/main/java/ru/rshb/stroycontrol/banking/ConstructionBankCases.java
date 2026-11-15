package ru.rshb.stroycontrol.banking;

import static ru.rshb.stroycontrol.common.PreparationInput.field;
import static ru.rshb.stroycontrol.common.PreparationInput.keys;
import static ru.rshb.stroycontrol.common.PreparationInput.uuid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.common.SafeDiagnostics;
import ru.rshb.stroycontrol.projects.MortgageRequests;
import ru.rshb.stroycontrol.projects.Projects;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Подключает дело с явным согласием; внешний Mock API вызывается вне транзакции и проектной
 * блокировки.
 */
@Service
@Slf4j
@RequiredArgsConstructor
class ConstructionBankCases {
    private static final String FIELD_CONSENT = "consent";

    private final ConstructionBankCaseRepository repository;
    private final MortgageRequests requests;
    private final Projects projects;
    private final LocalBankApi mock;
    private final RestClient client;
    private final TransactionTemplate tx;
    private final Clock clock;

    /**
     * Возвращает привязку только владельцу, включая исходный кредитный снимок без проверки его
     * давности.
     *
     * @param project карточка
     * @param owner владелец
     * @return ID синтетического дела либо null
     */
    @Transactional(readOnly = true)
    UUID connected(UUID project, UUID owner) {
        return findConnectedCase(project, owner);
    }

    /** Выполняет общую операцию внутри транзакции вызывающего сервиса. */
    private UUID findConnectedCase(UUID project, UUID owner) {
        var snapshot = requests.mortgage(project, owner);
        return snapshot != null
                ? snapshot
                : repository.findById(project).map(ConstructionBankCase::getCaseId).orElse(null);
    }

    /**
     * Подключает синтетическое дело после согласия, не изменяя заявку и исходное одобрение.
     *
     * @param project проект
     * @param owner владелец
     * @param body согласие и ключ
     * @return существующее либо созданное дело
     */
    CaseView connect(UUID project, UUID owner, Map<String, Object> body) {
        keys(body, FIELD_CONSENT, "idempotencyKey");
        field(
                Boolean.TRUE.equals(body.get(FIELD_CONSENT)),
                FIELD_CONSENT,
                "Подтвердите получение демонстрационных сведений");
        var key = uuid(body.get("idempotencyKey"));
        var old =
                tx.execute(status -> {
                    authorize(project, owner);
                    return Optional.ofNullable(findConnectedCase(project, owner));
                });
        if (old.isPresent()) return new CaseView(old.get(), true);
        String approval;
        try {
            var response = client.get().uri(mock.url()).retrieve().body(Map.class);
            approval = response == null ? "NOT_FOUND" : String.valueOf(response.get("status"));
        } catch (RuntimeException exception) {
            log.error(
                    "Не удалось получить демонстрационные сведения банка — проект={}",
                    project,
                    SafeDiagnostics.exception(exception));
            throw new ApiException(ApiError.BANK_DEMO_UNAVAILABLE);
        }
        if (!Set.of("FOUND", "EXPIRED").contains(approval))
            throw new ApiException(ApiError.BANK_CASE_REQUIRED);
        return tx.execute(status -> {
            authorize(project, owner);
            var existing = findConnectedCase(project, owner);
            if (existing != null) return new CaseView(existing, true);
            var saved =
                    repository.saveAndFlush(
                            ConstructionBankCase.builder()
                                    .projectId(project)
                                    .ownerId(owner)
                                    .idempotencyKey(key)
                                    .approvalStatus(approval)
                                    .createdAt(clock.instant())
                                    .build());
            return new CaseView(saved.getCaseId(), true);
        });
    }

    /**
     * Публичный итог без реальных банковских данных.
     *
     * @param id синтетическое дело
     * @param isDemo всегда true
     */
    record CaseView(UUID id, boolean isDemo) {}

    private void authorize(UUID project, UUID owner) {
        var access = projects.lockParticipant(project, owner);
        if (!access.owner()) throw new ApiException(ApiError.PROJECT_NOT_FOUND);
        if (!requests.mortgageSelected(project, owner))
            throw new ApiException(ApiError.INVALID_BANK_SUBMISSION_STATE);
    }
}
