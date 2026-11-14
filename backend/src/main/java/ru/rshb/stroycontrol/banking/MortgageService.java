package ru.rshb.stroycontrol.banking;

import static ru.rshb.stroycontrol.common.PreparationInput.keys;
import static ru.rshb.stroycontrol.common.PreparationInput.require;
import static ru.rshb.stroycontrol.common.PreparationInput.version;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.projects.MortgageRequests;
import ru.rshb.stroycontrol.projects.Projects;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Предоставляет владельцу карточки демонстрационное одобрение сельской ипотеки.
 *
 * <p>Банковские данные доступны только заказчику. HTTP-вызов локального Mock API выполняется без
 * транзакции БД; перед сохранением повторно проверяются владелец, версия заявки и ключ повтора под
 * блокировкой проекта. Снимок и результат импорта фиксируются атомарно.
 */
@Service
@RequiredArgsConstructor
class MortgageService {
    private static final String STATE_FOUND = "FOUND";
    private static final String FIELD_STATUS = "status";
    private static final String STATE_NOT_FOUND = "NOT_FOUND";
    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_TERM_MONTHS = "termMonths";

    private final MortgageRequests requests;
    private final Projects projects;
    private final MortgageSnapshotRepository snapshots;
    private final MortgageImportRepository imports;
    private final LocalBankApi mock;
    private final TransactionTemplate tx;
    private final RestClient client;
    private final Clock clock;

    /**
     * Возвращает состояние демонстрационного одобрения только владельцу карточки.
     *
     * <p>Не обращается к Mock API. Срок действия снимка проверяется по московской дате; при другом
     * источнике финансирования возвращается NOT_CONNECTED без банковских данных.
     *
     * @param project идентификатор карточки дома
     * @param owner идентификатор владельца карточки
     * @return состояние подключения и синтетический снимок либо null
     * @throws ApiException с PROJECT_NOT_FOUND для недоступной карточки или другого участника
     */
    @Transactional(readOnly = true)
    MortgageApproval get(UUID project, UUID owner) {
        UUID id = requests.mortgage(project, owner);
        if (!requests.mortgageSelected(project, owner)) return result("NOT_CONNECTED", null);
        var recent =
                imports.findFirstByProjectIdAndOwnerIdOrderByCreatedAtDescIdDesc(project, owner);
        String state =
                recent.map(MortgageImport::getResultStatus)
                        .orElseGet(() -> id == null ? "NOT_CONNECTED" : status(id));
        if (STATE_FOUND.equals(state)) state = status(id);
        return result(state, id);
    }

    /**
     * Определяет актуальность сохранённого одобрения по московской дате.
     *
     * @param id идентификатор сохранённого кредитного снимка
     * @return EXPIRED при истёкшем сроке, иначе FOUND
     */
    private String status(UUID id) {
        return snapshots
                        .findById(id)
                        .orElseThrow()
                        .getValidUntil()
                        .isBefore(LocalDate.now(ZoneId.of("Europe/Moscow")))
                ? "EXPIRED"
                : STATE_FOUND;
    }

    /**
     * Импортирует синтетическое одобрение после явного согласия владельца.
     *
     * <p>Перед HTTP-вызовом проверяет и блокирует ипотечный черновик в короткой транзакции. После
     * вызова повторно проверяет доступ, версию и ключ под блокировкой; снимок, привязка и результат
     * импорта фиксируются атомарно. Уже зафиксированный повтор не вызывает Mock API.
     *
     * @param project идентификатор карточки дома
     * @param owner идентификатор владельца карточки
     * @param body версия заявки, согласие на демонстрационный импорт и ключ повтора
     * @return зафиксированный результат импорта либо результат идентичного повтора
     * @throws ApiException с BANK_DEMO_UNAVAILABLE при сбое Mock API, STALE_REQUEST_VERSION при
     *     изменении заявки или IDEMPOTENCY_CONFLICT
     */
    MortgageApproval load(UUID project, UUID owner, Map<String, Object> body) {
        keys(body, "version", "demoConsent", "idempotencyKey");
        long version = version(body.get("version"));
        require(Boolean.TRUE.equals(body.get("demoConsent")));
        UUID key;
        try {
            key = UUID.fromString((String) body.get("idempotencyKey"));
        } catch (RuntimeException _) {
            throw new ApiException(ApiError.VALIDATION_ERROR);
        }
        requests.mortgage(project, owner);
        require(requests.mortgageSelected(project, owner));
        var cached = cached(project, owner, key, version);
        if (cached != null) return cached;
        tx.executeWithoutResult(status -> requests.lockMortgage(project, owner, version));
        Map<?, ?> response;
        try {
            response = client.get().uri(mock.url()).retrieve().body(Map.class);
            validate(response);
        } catch (RuntimeException _) {
            throw new ApiException(ApiError.BANK_DEMO_UNAVAILABLE);
        }
        final var bank = response;
        return tx.execute(status -> {
            projects.lockParticipant(project, owner);
            var previous = cached(project, owner, key, version);
            if (previous != null) return previous;
            UUID snapshot = requests.lockMortgage(project, owner, version);
            String state = (String) bank.get(FIELD_STATUS);
            if (!STATE_NOT_FOUND.equals(state)) {
                snapshot = UUID.randomUUID();
                var entity =
                        MortgageSnapshot.builder()
                                .id(snapshot)
                                .projectId(project)
                                .ownerId(owner)
                                .program((String) bank.get("program"))
                                .amount(new BigDecimal(bank.get(FIELD_AMOUNT).toString()))
                                .termMonths(((Number) bank.get(FIELD_TERM_MONTHS)).intValue())
                                .rate(new BigDecimal(bank.get("rate").toString()))
                                .decisionDate(LocalDate.parse((String) bank.get("decisionDate")))
                                .validUntil(LocalDate.parse((String) bank.get("validUntil")))
                                .fetchedAt(clock.instant())
                                .build();
                snapshots.saveAndFlush(entity);
                requests.attachMortgage(project, snapshot);
            }
            var imported =
                    MortgageImport.builder()
                            .ownerId(owner)
                            .projectId(project)
                            .idempotencyKey(key)
                            .requestVersion(version)
                            .resultStatus(state)
                            .snapshotId(snapshot)
                            .build();
            imports.saveAndFlush(imported);
            return result(state, snapshot);
        });
    }

    /**
     * Возвращает сохранённый результат импорта с проверкой исходной версии заявки.
     *
     * <p>Ключ связан с владельцем и карточкой; повтор с другой версией не импортирует новый снимок.
     *
     * @param project идентификатор карточки дома
     * @param owner идентификатор владельца карточки
     * @param key идентификатор повторной команды
     * @param version ожидаемая версия до изменения
     * @return результат импорта либо null, если ключ ещё не использовался
     * @throws ApiException с IDEMPOTENCY_CONFLICT при другой версии
     */
    private MortgageApproval cached(UUID project, UUID owner, UUID key, long version) {
        var found = imports.findByOwnerIdAndProjectIdAndIdempotencyKey(owner, project, key);
        if (found.isEmpty()) return null;
        var imported = found.get();
        if (imported.getRequestVersion() != version)
            throw new ApiException(ApiError.IDEMPOTENCY_CONFLICT);
        return result(imported.getResultStatus(), imported.getSnapshotId());
    }

    /**
     * Формирует закрытый ответ ипотечного адаптера с необязательным снимком.
     *
     * @param status состояние ипотечного подключения FOUND, EXPIRED, NOT_FOUND или NOT_CONNECTED
     * @param id идентификатор снимка или null, если снимок не подключён
     * @return состояние и снимок для REST-контракта
     */
    private MortgageApproval result(String status, UUID id) {
        return new MortgageApproval(status, id == null ? null : snapshotView(id));
    }

    /**
     * Исключает внутренние связи проекта и владельца из кредитного снимка.
     *
     * <p>Доступ владельца проверяется до вызова.
     *
     * @param id идентификатор сохранённого кредитного снимка
     * @return снимок без связей с карточкой и владельцем
     */
    private MortgageSnapshotView snapshotView(UUID id) {
        return snapshots.findById(id).orElseThrow().view();
    }

    /**
     * Проверяет структуру синтетического ответа до сохранения банковского снимка.
     *
     * <p>Найденное одобрение требует положительных суммы и срока, неотрицательной ставки и
     * упорядоченных дат; NOT_FOUND не требует полей снимка.
     *
     * @param r модель документа или заявки текущей операции
     * @throws ApiException при неполном или недопустимом банковском ответе
     */
    private void validate(Map<?, ?> r) {
        if (r == null) throw new ApiException(ApiError.BANK_DEMO_UNAVAILABLE);
        require(Set.of(STATE_FOUND, STATE_NOT_FOUND, "EXPIRED").contains(r.get(FIELD_STATUS)));
        if (STATE_NOT_FOUND.equals(r.get(FIELD_STATUS))) return;
        require(
                r.get("program") instanceof String
                        && r.get(FIELD_AMOUNT) instanceof Number
                        && ((Number) r.get(FIELD_AMOUNT)).doubleValue() > 0
                        && r.get(FIELD_TERM_MONTHS) instanceof Number
                        && ((Number) r.get(FIELD_TERM_MONTHS)).intValue() > 0
                        && r.get("rate") instanceof Number
                        && ((Number) r.get("rate")).doubleValue() >= 0);
        var start = LocalDate.parse((String) r.get("decisionDate"));
        var until = LocalDate.parse((String) r.get("validUntil"));
        require(!until.isBefore(start));
    }
}
