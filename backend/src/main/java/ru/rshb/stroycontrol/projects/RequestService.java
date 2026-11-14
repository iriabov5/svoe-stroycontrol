package ru.rshb.stroycontrol.projects;

import static ru.rshb.stroycontrol.common.PreparationInput.date;
import static ru.rshb.stroycontrol.common.PreparationInput.field;
import static ru.rshb.stroycontrol.common.PreparationInput.fieldValue;
import static ru.rshb.stroycontrol.common.PreparationInput.keys;
import static ru.rshb.stroycontrol.common.PreparationInput.number;
import static ru.rshb.stroycontrol.common.PreparationInput.require;
import static ru.rshb.stroycontrol.common.PreparationInput.text;
import static ru.rshb.stroycontrol.common.PreparationInput.uuid;
import static ru.rshb.stroycontrol.common.PreparationInput.version;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.accounts.Accounts;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.common.CommandFingerprint;
import ru.rshb.stroycontrol.common.Input;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Управляет пожеланиями заказчика, отправкой заявки и созданием независимой копии.
 *
 * <p>Таблицы заявок и копирования принадлежат projects. Права проверяются по карточке; изменения
 * сериализованы её пессимистической блокировкой. Для банковского модуля публикуется узкий интерфейс
 * MortgageRequests без передачи сущностей.
 */
@Service
@RequiredArgsConstructor
class RequestService implements MortgageRequests {
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_TECHNOLOGY = "technology";
    private static final String FIELD_TECHNOLOGY_RECOMMENDATION = "technologyRecommendation";
    private static final String FIELD_MATERIALS = "materials";
    private static final String FIELD_MATERIALS_RECOMMENDATION = "materialsRecommendation";
    private static final String FIELD_LENGTH_M = "lengthM";
    private static final String FIELD_WIDTH_M = "widthM";
    private static final String FIELD_AREA_M2 = "areaM2";
    private static final String FIELD_FLOORS = "floors";
    private static final String FIELD_COMPLETION_SCOPE = "completionScope";
    private static final String FIELD_DESIRED_BUDGET = "desiredBudget";
    private static final String FIELD_CADASTRAL_NUMBER = "cadastralNumber";
    private static final String FIELD_DESIRED_START = "desiredStart";
    private static final String FIELD_DESIRED_FINISH = "desiredFinish";
    private static final String FIELD_FINANCING_SOURCE = "financingSource";
    private static final String STATE_RSHB_RURAL_MORTGAGE = "RSHB_RURAL_MORTGAGE";
    private static final String STATE_SUBMITTED = "SUBMITTED";
    private static final String FIELD_IDEMPOTENCY_KEY = "idempotencyKey";
    private static final String FIELD_CONTRACTOR_EMAIL = "contractorEmail";

    private final RequestRepository requests;
    private final RequestReissueRepository reissues;
    private final ProjectRepository projects;
    private final ProjectAuthorization authorization;
    private final Accounts accounts;
    private final CommandFingerprint fingerprint;

    /**
     * Возвращает выбранное финансирование для уже авторизованного участника своего модуля.
     *
     * @param projectId карточка
     * @return источник либо null для прежней карточки без заявки
     */
    String financing(UUID projectId) {
        return requests.findByProjectId(projectId)
                .map(ConstructionRequest::getFinancingSource)
                .orElse(null);
    }

    /**
     * Создаёт пустой черновик заявки при создании карточки дома.
     *
     * <p>Участвует в транзакции создания проекта; самостоятельного доступа извне не предоставляет.
     *
     * @param projectId идентификатор карточки дома
     * @param location адрес участка, перенесённый из карточки дома
     */
    void create(UUID projectId, String location) {
        var request = ConstructionRequest.builder().projectId(projectId).location(location).build();
        requests.saveAndFlush(request);
    }

    /**
     * Находит собственную заявку по карточке дома.
     *
     * <p>Проверку доступа выполняет вызывающая операция сервиса.
     *
     * @param projectId идентификатор карточки дома
     * @return сущность заявки текущего модуля
     * @throws ApiException с PROJECT_NOT_FOUND при отсутствии заявки
     */
    private ConstructionRequest request(UUID projectId) {
        return requests.findByProjectId(projectId)
                .orElseThrow(() -> new ApiException(ApiError.PROJECT_NOT_FOUND));
    }

    /**
     * Проверяет видимость карточки и при необходимости блокирует её для записи.
     *
     * <p>Блокировка требует активной транзакции и сохраняется до её завершения.
     *
     * @param id идентификатор карточки дома
     * @param actor идентификатор аутентифицированного аккаунта
     * @param lock true для блокировки на запись, false для обычного чтения
     * @return доступная карточка, при lock=true под блокировкой записи
     * @throws ApiException с PROJECT_NOT_FOUND для отсутствующей или недоступной карточки
     */
    private HouseProject project(UUID id, UUID actor, boolean lock) {
        return authorization.project(id, actor, lock);
    }

    /**
     * Возвращает заявку участнику доступной карточки.
     *
     * <p>Кредитная привязка исключена из результата; подрядчик видит заявку только после отправки.
     *
     * @param actor аутентифицированный участник карточки
     * @param id идентификатор карточки дома
     * @return пожелания заказчика без идентификатора кредитного снимка
     * @throws ApiException с PROJECT_NOT_FOUND для чужой или отсутствующей карточки
     */
    @Transactional(readOnly = true)
    RequestView get(AccountView actor, UUID id) {
        project(id, actor.id(), false);
        return read(id);
    }

    /**
     * Формирует общую модель заявки без закрытой привязки кредитного снимка.
     *
     * <p>Вызывается только после проверки доступа сервисом проектов.
     *
     * @param id идентификатор карточки дома
     * @return заявка без идентификатора кредитного снимка
     */
    private RequestView read(UUID id) {
        return request(id).view();
    }

    /**
     * Сохраняет переданные поля черновика заявки владельцем.
     *
     * <p>Полная проверка полей и версии выполняется под блокировкой проекта. При изменении
     * источника финансирования кредитная привязка сбрасывается.
     *
     * @param actor аутентифицированный участник карточки
     * @param id идентификатор карточки дома
     * @param body изменяемые поля черновика и ожидаемая версия
     * @return сохранённый черновик с новой версией
     * @throws ApiException с FORBIDDEN, REQUEST_LOCKED, STALE_REQUEST_VERSION или ошибкой поля
     */
    @Transactional
    RequestView patch(AccountView actor, UUID id, Map<String, Object> body) {
        var p = project(id, actor.id(), true);
        owner(p, actor.id());
        var old = read(id);
        draft(old);
        checkVersion(old, body.get("version"));
        var values = patchValues(old, body);
        var entity = request(id);
        entity.setDescription((String) values.get(FIELD_DESCRIPTION));
        entity.setTechnology((String) values.get(FIELD_TECHNOLOGY));
        entity.setTechnologyRecommendation((Boolean) values.get(FIELD_TECHNOLOGY_RECOMMENDATION));
        entity.setMaterials((String) values.get(FIELD_MATERIALS));
        entity.setMaterialsRecommendation((Boolean) values.get(FIELD_MATERIALS_RECOMMENDATION));
        entity.setLengthM((BigDecimal) values.get(FIELD_LENGTH_M));
        entity.setWidthM((BigDecimal) values.get(FIELD_WIDTH_M));
        entity.setAreaM2((BigDecimal) values.get(FIELD_AREA_M2));
        entity.setFloors((Integer) values.get(FIELD_FLOORS));
        entity.setCompletionScope((String) values.get(FIELD_COMPLETION_SCOPE));
        entity.setDesiredBudget((BigDecimal) values.get(FIELD_DESIRED_BUDGET));
        entity.setCadastralNumber((String) values.get(FIELD_CADASTRAL_NUMBER));
        entity.setDesiredStart((LocalDate) values.get(FIELD_DESIRED_START));
        entity.setDesiredFinish((LocalDate) values.get(FIELD_DESIRED_FINISH));
        entity.setFinancingSource((String) values.get(FIELD_FINANCING_SOURCE));
        entity.setVersion(entity.getVersion() + 1);
        if (!STATE_RSHB_RURAL_MORTGAGE.equals(values.get(FIELD_FINANCING_SOURCE)))
            entity.setMortgageSnapshotId(null);
        requests.flush();
        return read(id);
    }

    /** Объединяет частичный ввод с черновиком и проверяет только редактируемые пожелания. */
    private Map<String, Object> patchValues(RequestView old, Map<String, Object> body) {
        keys(
                body,
                "version",
                FIELD_DESCRIPTION,
                FIELD_TECHNOLOGY,
                FIELD_TECHNOLOGY_RECOMMENDATION,
                FIELD_MATERIALS,
                FIELD_MATERIALS_RECOMMENDATION,
                FIELD_LENGTH_M,
                FIELD_WIDTH_M,
                FIELD_AREA_M2,
                FIELD_FLOORS,
                FIELD_COMPLETION_SCOPE,
                FIELD_DESIRED_BUDGET,
                FIELD_CADASTRAL_NUMBER,
                FIELD_DESIRED_START,
                FIELD_DESIRED_FINISH,
                FIELD_FINANCING_SOURCE);
        var values = values(old);
        values.putAll(body);
        for (var f :
                List.of(
                        FIELD_DESCRIPTION,
                        FIELD_TECHNOLOGY,
                        FIELD_MATERIALS,
                        FIELD_COMPLETION_SCOPE,
                        FIELD_CADASTRAL_NUMBER))
            values.put(
                    f,
                    text(
                            values.get(f),
                            switch (f) {
                                case FIELD_DESCRIPTION -> 10000;
                                case FIELD_COMPLETION_SCOPE -> 1000;
                                case FIELD_CADASTRAL_NUMBER -> 50;
                                default -> 2000;
                            }));
        for (var f : List.of(FIELD_TECHNOLOGY_RECOMMENDATION, FIELD_MATERIALS_RECOMMENDATION)) {
            require(values.get(f) instanceof Boolean);
        }
        for (var f : List.of(FIELD_LENGTH_M, FIELD_WIDTH_M, FIELD_AREA_M2, FIELD_DESIRED_BUDGET)) {
            Object raw = values.get(f);
            var max =
                    switch (f) {
                        case FIELD_DESIRED_BUDGET -> new BigDecimal("9999999999999.99");
                        case FIELD_AREA_M2 -> new BigDecimal("99999999.99");
                        default -> new BigDecimal("999999.99");
                    };
            values.put(f, fieldValue(f, () -> number(raw, max)));
        }
        normalizeSchedule(values);
        var source = text(values.get(FIELD_FINANCING_SOURCE), 40);
        field(
                source == null || Set.of("OWN_FUNDS", STATE_RSHB_RURAL_MORTGAGE).contains(source),
                FIELD_FINANCING_SOURCE,
                "Выберите один из двух источников финансирования");
        values.put(FIELD_FINANCING_SOURCE, source);
        String cad = (String) values.get(FIELD_CADASTRAL_NUMBER);
        field(
                cad == null || cad.matches("\\d+:\\d+:\\d+:\\d+"),
                FIELD_CADASTRAL_NUMBER,
                "Используйте формат 50:12:1234567:89");
        require(
                !Boolean.TRUE.equals(values.get(FIELD_TECHNOLOGY_RECOMMENDATION))
                        || values.get(FIELD_TECHNOLOGY) == null);
        require(
                !Boolean.TRUE.equals(values.get(FIELD_MATERIALS_RECOMMENDATION))
                        || values.get(FIELD_MATERIALS) == null);
        return values;
    }

    /** Проверяет этажность и календарные границы, сохраняя даты без часового пояса. */
    private void normalizeSchedule(Map<String, Object> values) {
        Object floors = values.get(FIELD_FLOORS);
        if (floors != null) {
            field(
                    floors instanceof Integer count && count >= 1 && count <= 10,
                    FIELD_FLOORS,
                    "Укажите целое число этажей от 1 до 10");
        }
        var start =
                date(
                        values.get(FIELD_DESIRED_START) == null
                                ? null
                                : values.get(FIELD_DESIRED_START).toString());
        var finish =
                date(
                        values.get(FIELD_DESIRED_FINISH) == null
                                ? null
                                : values.get(FIELD_DESIRED_FINISH).toString());
        field(
                start == null || finish == null || !finish.isBefore(start),
                FIELD_DESIRED_FINISH,
                "Завершение не может быть раньше начала");
        values.put(FIELD_DESIRED_START, start);
        values.put(FIELD_DESIRED_FINISH, finish);
    }

    /**
     * Отправляет полную заявку и открывает её назначенному подрядчику.
     *
     * <p>Состояние заявки и видимость карточки меняются одной транзакцией. Повтор успешной отправки
     * не меняет дату и возвращает зафиксированные пожелания.
     *
     * @param actor аутентифицированный участник карточки
     * @param id идентификатор карточки дома
     * @param expected ожидаемая версия до изменения
     * @return зафиксированная отправленная заявка
     * @throws ApiException с FORBIDDEN, CONTRACTOR_REQUIRED, STALE_REQUEST_VERSION или ошибкой
     *     обязательного поля
     */
    @Transactional
    RequestView submit(AccountView actor, UUID id, Object expected) {
        var p = project(id, actor.id(), true);
        owner(p, actor.id());
        var r = read(id);
        if (STATE_SUBMITTED.equals(r.state())) return r;
        checkVersion(r, expected);
        if (p.getContractorId() == null) throw new ApiException(ApiError.CONTRACTOR_REQUIRED);
        field(r.description() != null, FIELD_DESCRIPTION, "Опишите желаемый дом");
        field(r.completionScope() != null, FIELD_COMPLETION_SCOPE, "Укажите комплектацию");
        field(r.desiredBudget() != null, FIELD_DESIRED_BUDGET, "Укажите желаемый бюджет");
        field(r.floors() != null, FIELD_FLOORS, "Укажите этажность");
        field(r.desiredStart() != null, FIELD_DESIRED_START, "Укажите желаемую дату начала");
        field(r.desiredFinish() != null, FIELD_DESIRED_FINISH, "Укажите желаемую дату завершения");
        field(
                r.financingSource() != null,
                FIELD_FINANCING_SOURCE,
                "Выберите источник финансирования");
        field(
                r.technology() != null || r.technologyRecommendation(),
                FIELD_TECHNOLOGY,
                "Укажите технологию или запросите рекомендацию");
        field(
                r.materials() != null || r.materialsRecommendation(),
                FIELD_MATERIALS,
                "Укажите материалы или запросите рекомендацию");
        field(
                r.areaM2() != null || (r.lengthM() != null && r.widthM() != null),
                FIELD_AREA_M2,
                "Укажите площадь или оба размера дома");
        field(
                (r.lengthM() == null) == (r.widthM() == null),
                FIELD_WIDTH_M,
                "Укажите оба размера дома или только площадь");
        var entity = request(id);
        entity.setState(STATE_SUBMITTED);
        entity.setSubmittedAt(Instant.now());
        entity.setVersion(entity.getVersion() + 1);
        requests.flush();
        p.submitRequest();
        return read(id);
    }

    /**
     * Проверяет владельца и актуальность ипотечного черновика под блокировкой проекта.
     *
     * <p>Требует активной транзакции вызывающего банковского адаптера; блокировка удерживается до
     * её завершения. Отправленная заявка и другой источник не допускаются.
     *
     * @param projectId идентификатор карточки дома
     * @param actorId идентификатор аутентифицированного аккаунта
     * @param expected ожидаемая версия до изменения
     * @return ID связанного снимка либо null, если он ещё не подключён
     * @throws ApiException с PROJECT_NOT_FOUND, REQUEST_LOCKED или STALE_REQUEST_VERSION
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID lockMortgage(UUID projectId, UUID actorId, long expected) {
        var p = project(projectId, actorId, true);
        if (!p.getCustomerId().equals(actorId)) throw new ApiException(ApiError.PROJECT_NOT_FOUND);
        var r = read(projectId);
        draft(r);
        checkVersion(r, expected);
        require(STATE_RSHB_RURAL_MORTGAGE.equals(r.financingSource()));
        return request(projectId).getMortgageSnapshotId();
    }

    /**
     * Привязывает сохранённый кредитный снимок и увеличивает версию заявки.
     *
     * <p>Требует активной транзакции и предшествующей проверки через lockMortgage. Снимок
     * принадлежит банковскому модулю и передаётся только идентификатором.
     *
     * @param id идентификатор карточки дома
     * @param snapshotId идентификатор уже сохранённого снимка банковского модуля
     * @throws IllegalTransactionStateException при отсутствии активной транзакции
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void attachMortgage(UUID id, UUID snapshotId) {
        var entity = request(id);
        entity.setMortgageSnapshotId(snapshotId);
        entity.setVersion(entity.getVersion() + 1);
        requests.flush();
    }

    /**
     * Возвращает ID привязанного снимка только владельцу карточки.
     *
     * <p>Не раскрывает привязку назначенному подрядчику.
     *
     * @param id идентификатор карточки дома
     * @param actorId идентификатор аутентифицированного аккаунта
     * @return идентификатор кредитного снимка либо null
     * @throws ApiException с PROJECT_NOT_FOUND для чужой карточки или другого участника
     */
    @Transactional(readOnly = true)
    public UUID mortgage(UUID id, UUID actorId) {
        return mortgageSnapshot(id, actorId);
    }

    /** Выполняет общую операцию внутри транзакции вызывающего сервиса. */
    private UUID mortgageSnapshot(UUID id, UUID actorId) {
        var p = project(id, actorId, false);
        if (!p.getCustomerId().equals(actorId)) throw new ApiException(ApiError.PROJECT_NOT_FOUND);
        return request(id).getMortgageSnapshotId();
    }

    /**
     * Проверяет выбор сельской ипотеки после проверки владельца карточки.
     *
     * @param id идентификатор карточки дома
     * @param actorId идентификатор аутентифицированного аккаунта
     * @return true при выбранном источнике RSHB_RURAL_MORTGAGE
     * @throws ApiException с PROJECT_NOT_FOUND для чужой карточки или другого участника
     */
    @Transactional(readOnly = true)
    public boolean mortgageSelected(UUID id, UUID actorId) {
        mortgageSnapshot(id, actorId);
        return STATE_RSHB_RURAL_MORTGAGE.equals(request(id).getFinancingSource());
    }

    /**
     * Результат копирования для HTTP-адаптера.
     *
     * @param project копия исходной карточки
     * @param created создана в текущем запросе
     */
    record Reissue(ProjectView project, boolean created) {}

    /**
     * Возвращает закрытую ссылку на единственную копию заявки владельцу.
     *
     * <p>Подрядчик не получает ни ссылку, ни сведения о наличии связанной карточки.
     *
     * @param actor аутентифицированный участник карточки
     * @param id идентификатор исходной карточки дома
     * @return ссылка на копию; targetProjectId равен null, пока копии нет
     * @throws ApiException с PROJECT_NOT_FOUND для другого участника или чужой карточки
     */
    @Transactional(readOnly = true)
    ReissueLink reissueLink(AccountView actor, UUID id) {
        var p = project(id, actor.id(), false);
        if (!p.getCustomerId().equals(actor.id()))
            throw new ApiException(ApiError.PROJECT_NOT_FOUND);
        return new ReissueLink(
                reissues.findBySourceProjectId(id)
                        .map(RequestReissue::getTargetProjectId)
                        .orElse(null));
    }

    /**
     * Создаёт независимый черновик заявки для другого зарегистрированного подрядчика.
     *
     * <p>Блокировка исходного проекта сериализует копирование. Карточка, пожелания и связь
     * сохраняются одной транзакцией; ошибка откатывает все три. Документы, этапы, лента, решения и
     * кредитный снимок не копируются. Идентичный повтор возвращает ту же карточку.
     *
     * @param actor аутентифицированный участник карточки
     * @param id идентификатор исходной карточки дома
     * @param body адрес подрядчика и ключ повтора
     * @return новая карточка и признак создания либо та же карточка при повторе
     * @throws ApiException при неверном адресате, уже созданной копии, незакрытой подготовке или
     *     конфликте ключа
     */
    @Transactional
    Reissue reissue(AccountView actor, UUID id, Map<String, Object> body) {
        var source = project(id, actor.id(), true);
        owner(source, actor.id());
        keys(body, FIELD_CONTRACTOR_EMAIL, FIELD_IDEMPOTENCY_KEY);
        UUID key = fieldValue(FIELD_IDEMPOTENCY_KEY, () -> uuid(body.get(FIELD_IDEMPOTENCY_KEY)));
        String email =
                fieldValue(
                        FIELD_CONTRACTOR_EMAIL,
                        () -> Input.email(text(body.get(FIELD_CONTRACTOR_EMAIL), 320)));
        String hash = fingerprint.of(id.toString(), body);
        var repeated = reissues.findByActorIdAndIdempotencyKey(actor.id(), key);
        if (repeated.isPresent()) {
            var previous = repeated.get();
            if (!hash.equals(previous.getRequestHash()))
                throw new ApiException(ApiError.IDEMPOTENCY_CONFLICT);
            return new Reissue(
                    authorization.view(
                            project(previous.getTargetProjectId(), actor.id(), false), actor.id()),
                    false);
        }
        if (!source.isPreparationClosed()
                || !"PROPOSAL_REQUIRED".equals(source.getProcessMode())
                || source.getApprovedProposalId() != null)
            throw new ApiException(ApiError.INVALID_PROPOSAL_STATE);
        if (reissues.findBySourceProjectId(id).isPresent())
            throw new ApiException(ApiError.REQUEST_ALREADY_REISSUED);
        var contractor =
                accounts.findContractor(email)
                        .orElseThrow(() -> new ApiException(ApiError.CONTRACTOR_NOT_FOUND));
        if (contractor.id().equals(source.getContractorId()))
            throw new ApiException(ApiError.INVALID_REISSUE_CONTRACTOR);
        var copy =
                HouseProject.builder()
                        .name(source.getName())
                        .address(source.getAddress())
                        .customerId(actor.id())
                        .build();
        var destinationMembership = authorization.organizations.membership(contractor.id());
        if (destinationMembership != null
                && destinationMembership
                        .organizationId()
                        .equals(source.getContractorOrganizationId()))
            throw new ApiException(ApiError.INVALID_REISSUE_CONTRACTOR);
        var org = authorization.organizations.assignable(contractor.id());
        if (org.id().equals(source.getContractorOrganizationId()))
            throw new ApiException(ApiError.INVALID_REISSUE_CONTRACTOR);
        copy.assign(contractor.id());
        copy.setContractorOrganizationId(org.id());
        projects.saveAndFlush(copy);
        var original = request(id);
        require(STATE_SUBMITTED.equals(original.getState()));
        var draft =
                ConstructionRequest.builder()
                        .projectId(copy.getId())
                        .location(original.getLocation())
                        .description(original.getDescription())
                        .technology(original.getTechnology())
                        .technologyRecommendation(original.isTechnologyRecommendation())
                        .materials(original.getMaterials())
                        .materialsRecommendation(original.isMaterialsRecommendation())
                        .lengthM(original.getLengthM())
                        .widthM(original.getWidthM())
                        .areaM2(original.getAreaM2())
                        .floors(original.getFloors())
                        .completionScope(original.getCompletionScope())
                        .desiredBudget(original.getDesiredBudget())
                        .cadastralNumber(original.getCadastralNumber())
                        .desiredStart(original.getDesiredStart())
                        .desiredFinish(original.getDesiredFinish())
                        .financingSource(original.getFinancingSource())
                        .build();
        requests.saveAndFlush(draft);
        var link =
                RequestReissue.builder()
                        .sourceProjectId(id)
                        .targetProjectId(copy.getId())
                        .actorId(actor.id())
                        .targetContractorId(contractor.id())
                        .idempotencyKey(key)
                        .requestHash(hash)
                        .build();
        reissues.saveAndFlush(link);
        return new Reissue(authorization.view(copy, actor.id()), true);
    }

    /**
     * Проверяет, что изменение заявки выполняет владелец карточки.
     *
     * @param p карточка, для которой уже проверен доступ
     * @param actor аутентифицированный участник карточки
     * @throws ApiException с FORBIDDEN для другого участника
     */
    private void owner(HouseProject p, UUID actor) {
        if (!p.getCustomerId().equals(actor)) throw new ApiException(ApiError.FORBIDDEN);
    }

    /**
     * Запрещает изменение уже отправленной заявки.
     *
     * @param request заявка, доступ к которой уже проверен
     * @throws ApiException с REQUEST_LOCKED для состояния, отличного от DRAFT
     */
    private void draft(RequestView request) {
        if (!"DRAFT".equals(request.state())) throw new ApiException(ApiError.REQUEST_LOCKED);
    }

    /**
     * Сверяет версию заявки с версией команды до изменения данных.
     *
     * @param request заявка, прочитанная под блокировкой или для чтения
     * @param expected ожидаемая версия до изменения
     * @throws ApiException с STALE_REQUEST_VERSION при несовпадении
     */
    private void checkVersion(RequestView request, Object expected) {
        if (request.version() != version(expected))
            throw new ApiException(ApiError.STALE_REQUEST_VERSION);
    }

    /**
     * Собирает редактируемые поля заявки для слияния с телом команды.
     *
     * <p>Тело JSON может заменить отдельные значения; типы уже сохранённых полей остаются прежними,
     * пока валидатор их не нормализует.
     *
     * @param current текущая заявка без кредитного снимка
     * @return изменяемая карта полей черновика
     */
    private LinkedHashMap<String, Object> values(RequestView current) {
        var values = new LinkedHashMap<String, Object>();
        values.put(FIELD_DESCRIPTION, current.description());
        values.put(FIELD_TECHNOLOGY, current.technology());
        values.put(FIELD_TECHNOLOGY_RECOMMENDATION, current.technologyRecommendation());
        values.put(FIELD_MATERIALS, current.materials());
        values.put(FIELD_MATERIALS_RECOMMENDATION, current.materialsRecommendation());
        values.put(FIELD_LENGTH_M, current.lengthM());
        values.put(FIELD_WIDTH_M, current.widthM());
        values.put(FIELD_AREA_M2, current.areaM2());
        values.put(FIELD_FLOORS, current.floors());
        values.put(FIELD_COMPLETION_SCOPE, current.completionScope());
        values.put(FIELD_DESIRED_BUDGET, current.desiredBudget());
        values.put(FIELD_CADASTRAL_NUMBER, current.cadastralNumber());
        values.put(FIELD_DESIRED_START, current.desiredStart());
        values.put(FIELD_DESIRED_FINISH, current.desiredFinish());
        values.put(FIELD_FINANCING_SOURCE, current.financingSource());
        return values;
    }

    /**
     * Возвращает ID отправленной заявки для внутреннего интерфейса проектов.
     *
     * <p>Проверку участника выполняет публичный интерфейс Projects до вызова.
     *
     * @param projectId идентификатор карточки дома
     * @return ID отправленной заявки
     * @throws ApiException с REQUEST_LOCKED, если заявка ещё не отправлена
     */
    UUID submittedRequest(UUID projectId) {
        var entity = request(projectId);
        if (!STATE_SUBMITTED.equals(entity.getState()))
            throw new ApiException(ApiError.REQUEST_LOCKED);
        return entity.getId();
    }
}
