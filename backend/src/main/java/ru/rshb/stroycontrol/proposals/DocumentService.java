package ru.rshb.stroycontrol.proposals;

import static ru.rshb.stroycontrol.common.PreparationInput.date;
import static ru.rshb.stroycontrol.common.PreparationInput.dates;
import static ru.rshb.stroycontrol.common.PreparationInput.field;
import static ru.rshb.stroycontrol.common.PreparationInput.image;
import static ru.rshb.stroycontrol.common.PreparationInput.keys;
import static ru.rshb.stroycontrol.common.PreparationInput.number;
import static ru.rshb.stroycontrol.common.PreparationInput.require;
import static ru.rshb.stroycontrol.common.PreparationInput.text;
import static ru.rshb.stroycontrol.common.PreparationInput.version;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.accounts.AccountRole;
import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.ActorSnapshot;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.organizations.Organizations;
import ru.rshb.stroycontrol.projects.ProjectAccess;
import ru.rshb.stroycontrol.projects.Projects;
import ru.rshb.stroycontrol.stages.StageWorkType;
import ru.rshb.stroycontrol.stages.Stages;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Управляет версиями сметы и договора; решения фиксируются атомарно с рабочим планом. */
@Service
@RequiredArgsConstructor
class DocumentService implements AgreedDocuments {
    private static final String STATE_DRAFT = "DRAFT";
    private static final String STATE_CHANGES_REQUESTED = "CHANGES_REQUESTED";
    private static final String FIELD_WORK_TYPE = "workType";
    private static final String FIELD_EXPECTED_RESULT = "expectedResult";
    private static final String FIELD_PLANNED_START = "plannedStart";
    private static final String FIELD_PLANNED_FINISH = "plannedFinish";
    private static final String FIELD_CAPTION = "caption";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_ESTIMATE_TEXT = "estimateText";
    private static final String FIELD_TERMS_TEXT = "termsText";
    private static final String FIELD_TOTAL_PRICE = "totalPrice";
    private static final String FIELD_IMAGES = "images";
    private static final String FIELD_STAGES = "stages";
    private static final String FIELD_CONTRACT_TEXT = "contractText";
    private static final String FIELD_COMMENT = "comment";
    private static final String FIELD_CONTRACTOR_CONFIRMED = "contractorConfirmed";
    private static final String STATE_APPROVED = "APPROVED";
    private static final String STATE_REJECTED = "REJECTED";
    private static final String STAGE_FIELD_PREFIX = "stages.";

    private final ProposalRepository proposals;
    private final ContractRepository contracts;
    private final ProposalImageRepository images;
    private final ProposalStagePlanRepository plans;
    private final ProposalDecisionRepository proposalDecisions;
    private final ContractDecisionRepository contractDecisions;
    private final Projects projects;
    private final Stages stages;
    private final Clock clock;
    private final Organizations organizations;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public AgreedDocuments.Snapshot agreed(UUID projectId, UUID ownerId) {
        var access = projects.requireParticipant(projectId, ownerId);
        if (!access.owner()) throw new ApiException(ApiError.PROJECT_NOT_FOUND);
        var proposal =
                access.approvedProposalId() == null
                        ? null
                        : proposals.findById(access.approvedProposalId()).orElseThrow();
        var contract =
                access.approvedContractId() == null
                        ? null
                        : contracts.findById(access.approvedContractId()).orElseThrow();
        return new AgreedDocuments.Snapshot(
                access.approvedProposalId(),
                access.approvedContractId(),
                proposal == null ? null : proposal.getEstimateText(),
                contract == null ? null : contract.getContractText(),
                proposal == null ? null : proposal.getTotalPrice());
    }

    /**
     * Выбирает публичную ошибку недопустимого состояния для сметы или договора.
     *
     * @param contract true для договора, false для сметы
     * @return ошибка INVALID_CONTRACT_STATE или INVALID_PROPOSAL_STATE
     */
    private ApiException state(boolean contract) {
        return new ApiException(
                contract ? ApiError.INVALID_CONTRACT_STATE : ApiError.INVALID_PROPOSAL_STATE);
    }

    /**
     * Выбирает публичную ошибку отсутствия для сметы или договора.
     *
     * @param contract true для договора, false для сметы
     * @return ошибка CONTRACT_NOT_FOUND или PROPOSAL_NOT_FOUND
     */
    private ApiException missing(boolean contract) {
        return new ApiException(
                contract ? ApiError.CONTRACT_NOT_FOUND : ApiError.PROPOSAL_NOT_FOUND);
    }

    /**
     * Находит версию сметы только внутри указанной карточки.
     *
     * @param projectId идентификатор карточки дома
     * @param id идентификатор версии сметы
     * @return версия сметы этой карточки
     * @throws ApiException с PROPOSAL_NOT_FOUND
     */
    private ConstructionProposal proposal(UUID projectId, UUID id) {
        return proposals.findByIdAndProjectId(id, projectId).orElseThrow(() -> missing(false));
    }

    /**
     * Находит версию договора только внутри указанной карточки.
     *
     * @param projectId идентификатор карточки дома
     * @param id идентификатор версии договора
     * @return версия договора этой карточки
     * @throws ApiException с CONTRACT_NOT_FOUND
     */
    private ConstructionContract contract(UUID projectId, UUID id) {
        return contracts.findByIdAndProjectId(id, projectId).orElseThrow(() -> missing(true));
    }

    /**
     * Читает идентификатор планового этапа без передачи внутренних ошибок разбора клиенту.
     *
     * @param value входное значение идентификатора
     * @return разобранный идентификатор планового этапа
     * @throws ApiException с VALIDATION_ERROR при неверном идентификаторе
     */
    private UUID uid(Object value) {
        try {
            return UUID.fromString(value.toString());
        } catch (RuntimeException _) {
            throw new ApiException(ApiError.VALIDATION_ERROR);
        }
    }

    /**
     * Скрывает черновик подрядчика от владельца карточки.
     *
     * @param access права текущего участника карточки
     * @param documentState текущее состояние версии
     * @param contract true для договора, false для сметы
     * @throws ApiException с кодом отсутствующего документа при просмотре черновика заказчиком
     */
    private void visible(ProjectAccess access, String documentState, boolean contract) {
        if (!access.canPrepareDocuments() && STATE_DRAFT.equals(documentState))
            throw missing(contract);
    }

    /**
     * Собирает ответ по смете из версии, иллюстраций, плана и решения.
     *
     * @param entity версия сметы текущего модуля
     * @return модель сметы для REST-контракта
     */
    private ProposalView view(ConstructionProposal entity) {
        return new ProposalView(
                entity.getId(),
                entity.getProjectId(),
                entity.getRevisionNo(),
                entity.getState(),
                entity.getVersion(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getSubmittedAt(),
                entity.getCreatedBySnapshot() == null
                        ? ActorSnapshot.legacy(entity.getCreatedBy(), AccountRole.CONTRACTOR)
                        : entity.getCreatedBySnapshot(),
                entity.getSubmittedById(),
                entity.getSubmittedBySnapshot(),
                entity.getEstimateText(),
                entity.getTermsText(),
                entity.getTotalPrice(),
                images.findByProposalIdOrderByPositionAsc(entity.getId()).stream()
                        .map(image -> new ProposalImageView(image.getUrl(), image.getCaption()))
                        .toList(),
                plans.findByProposalIdOrderByPositionAsc(entity.getId()).stream()
                        .map(plan ->
                                new ProposalStageView(
                                        plan.getId(),
                                        plan.getName(),
                                        plan.getWorkType(),
                                        plan.getExpectedResult(),
                                        plan.getPlannedStart(),
                                        plan.getPlannedFinish()))
                        .toList(),
                "RUB",
                proposalDecisions
                        .findByProposalId(entity.getId())
                        .map(decision ->
                                new DecisionView(
                                        decision.getActorId(),
                                        decision.getDecision(),
                                        decision.getComment(),
                                        decision.getDecidedAt(),
                                        decision.getActorSnapshot() == null
                                                ? ActorSnapshot.legacy(
                                                        decision.getActorId(), AccountRole.CUSTOMER)
                                                : decision.getActorSnapshot()))
                        .orElse(null));
    }

    /**
     * Собирает ответ по договору из версии и решения заказчика.
     *
     * @param entity версия договора текущего модуля
     * @return модель договора для REST-контракта
     */
    private ContractView view(ConstructionContract entity) {
        return new ContractView(
                entity.getId(),
                entity.getProjectId(),
                entity.getRevisionNo(),
                entity.getState(),
                entity.getVersion(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getSubmittedAt(),
                entity.getCreatedBySnapshot() == null
                        ? ActorSnapshot.legacy(entity.getCreatedBy(), AccountRole.CONTRACTOR)
                        : entity.getCreatedBySnapshot(),
                entity.getSubmittedById(),
                entity.getSubmittedBySnapshot(),
                entity.getProposalId(),
                entity.getContractText(),
                contractDecisions
                        .findByContractId(entity.getId())
                        .map(decision ->
                                new DecisionView(
                                        decision.getActorId(),
                                        decision.getDecision(),
                                        decision.getComment(),
                                        decision.getDecidedAt(),
                                        decision.getActorSnapshot() == null
                                                ? ActorSnapshot.legacy(
                                                        decision.getActorId(), AccountRole.CUSTOMER)
                                                : decision.getActorSnapshot()))
                        .orElse(null));
    }

    /**
     * Возвращает историю версий сметы или договора, доступных участнику.
     *
     * <p>Черновики скрыты от заказчика; порядок — от новой редакции к старой.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param contract true для договора, false для сметы
     * @return видимые версии от новой редакции к старой
     * @throws ApiException с PROJECT_NOT_FOUND для чужой или отсутствующей карточки
     */
    @Transactional(readOnly = true)
    List<DocumentView> list(AccountView actor, UUID projectId, boolean contract) {
        var access = projects.requireParticipant(projectId, actor.id());
        if (contract) {
            return contracts.findByProjectIdOrderByRevisionNoDesc(projectId).stream()
                    .filter(entity ->
                            access.canPrepareDocuments() || !STATE_DRAFT.equals(entity.getState()))
                    .map(entity -> (DocumentView) view(entity))
                    .toList();
        }
        return proposals.findByProjectIdOrderByRevisionNoDesc(projectId).stream()
                .filter(entity ->
                        access.canPrepareDocuments() || !STATE_DRAFT.equals(entity.getState()))
                .map(entity -> (DocumentView) view(entity))
                .toList();
    }

    /**
     * Возвращает одну доступную версию документа с изображениями, планом и решением.
     *
     * <p>Черновик подрядчика для заказчика считается отсутствующим документом.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param id идентификатор версии сметы или договора
     * @param contract true для договора, false для сметы
     * @return содержимое доступной версии документа
     * @throws ApiException с PROJECT_NOT_FOUND, PROPOSAL_NOT_FOUND или CONTRACT_NOT_FOUND
     */
    @Transactional(readOnly = true)
    DocumentView get(AccountView actor, UUID projectId, UUID id, boolean contract) {
        var access = projects.requireParticipant(projectId, actor.id());
        if (contract) {
            var entity = contract(projectId, id);
            visible(access, entity.getState(), true);
            return view(entity);
        }
        var entity = proposal(projectId, id);
        visible(access, entity.getState(), false);
        return view(entity);
    }

    /**
     * Создаёт черновик сметы или договора назначенным подрядчиком.
     *
     * <p>Первая смета опирается на отправленную заявку; договор — на согласованную смету. После
     * возврата на уточнение копируются поля предыдущей редакции, сохраняя историю. Операция
     * сериализована блокировкой проекта; повтор возвращает существующий черновик.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param contract true для договора, false для сметы
     * @return новый или уже существующий черновик
     * @throws ApiException с FORBIDDEN, PREPARATION_CLOSED или ошибкой состояния документа
     */
    @Transactional
    DocumentView create(AccountView actor, UUID projectId, boolean contract) {
        var access = projects.lockParticipant(projectId, actor.id());
        if (!access.canPrepareDocuments()) throw new ApiException(ApiError.FORBIDDEN);
        if (access.preparationClosed()) throw new ApiException(ApiError.PREPARATION_CLOSED);
        if ("LEGACY".equals(access.processMode())) throw state(contract);
        if (contract && access.approvedProposalId() == null) throw state(true);
        if ((contract ? access.approvedContractId() : access.approvedProposalId()) != null) {
            throw state(contract);
        }
        if (contract) return createContract(actor, projectId, access.approvedProposalId());
        return createProposal(actor, projectId);
    }

    /**
     * Создаёт редакцию договора или возвращает существующий черновик.
     *
     * <p>После возврата на уточнение в новый черновик переносится только текст предыдущей редакции.
     * Версия нового черновика остаётся начальной.
     *
     * @param actor подрядчик карточки
     * @param projectId идентификатор карточки дома
     * @param proposalId идентификатор согласованной сметы
     * @return черновик договора
     * @throws ApiException при недопустимом состоянии предыдущей редакции
     */
    private ContractView createContract(AccountView actor, UUID projectId, UUID proposalId) {
        var all = contracts.findByProjectIdOrderByRevisionNoDesc(projectId);
        ConstructionContract previous = all.isEmpty() ? null : all.getFirst();
        if (previous != null) {
            if (STATE_DRAFT.equals(previous.getState())) return view(previous);
            if (!STATE_CHANGES_REQUESTED.equals(previous.getState())) throw state(true);
        }
        var created =
                contracts.saveAndFlush(
                        ConstructionContract.builder()
                                .id(UUID.randomUUID())
                                .projectId(projectId)
                                .proposalId(proposalId)
                                .revisionNo(previous == null ? 1 : previous.getRevisionNo() + 1)
                                .createdBy(actor.id())
                                .createdBySnapshot(organizations.snapshot(actor.id()))
                                .build());
        if (previous != null) {
            created.setContractText(previous.getContractText());
            contracts.saveAndFlush(created);
        }
        return view(created);
    }

    /**
     * Создаёт редакцию сметы или возвращает существующий черновик.
     *
     * <p>После возврата на уточнение редактируемые поля копируются через сохранение черновика, а
     * плановые этапы получают новые идентификаторы.
     *
     * @param actor подрядчик карточки
     * @param projectId идентификатор карточки дома
     * @return черновик сметы
     * @throws ApiException при недопустимом состоянии предыдущей редакции
     */
    private ProposalView createProposal(AccountView actor, UUID projectId) {
        var all = proposals.findByProjectIdOrderByRevisionNoDesc(projectId);
        ConstructionProposal previous = all.isEmpty() ? null : all.getFirst();
        if (previous != null) {
            if (STATE_DRAFT.equals(previous.getState())) return view(previous);
            if (!STATE_CHANGES_REQUESTED.equals(previous.getState())) throw state(false);
        }
        UUID id = UUID.randomUUID();
        proposals.saveAndFlush(
                ConstructionProposal.builder()
                        .id(id)
                        .projectId(projectId)
                        .requestId(projects.submittedRequest(projectId, actor.id()))
                        .revisionNo(previous == null ? 1 : previous.getRevisionNo() + 1)
                        .createdBy(actor.id())
                        .createdBySnapshot(organizations.snapshot(actor.id()))
                        .build());
        if (previous != null) saveDocument(actor, projectId, id, false, editBody(view(previous)));
        return view(proposal(projectId, id));
    }

    /**
     * Выбирает только редактируемые поля сметы для новой версии после уточнения.
     *
     * <p>Решение, авторство и служебные поля предыдущей версии не копируются. Плановые этапы
     * получают новые идентификаторы, чтобы не занять строки предыдущей редакции.
     *
     * @param source предыдущая возвращённая смета
     * @return тело сохранения нового черновика с версией 0
     */
    private Map<String, Object> editBody(ProposalView source) {
        var planEntries = new ArrayList<Map<String, Object>>();
        for (var plan : source.stages()) {
            var row = new LinkedHashMap<String, Object>();
            row.put("id", UUID.randomUUID());
            row.put("name", plan.name());
            row.put(FIELD_WORK_TYPE, plan.workType());
            row.put(FIELD_EXPECTED_RESULT, plan.expectedResult());
            row.put(FIELD_PLANNED_START, plan.plannedStart());
            row.put(FIELD_PLANNED_FINISH, plan.plannedFinish());
            planEntries.add(row);
        }
        var pictures = new ArrayList<Map<String, Object>>();
        for (var picture : source.images()) {
            var row = new LinkedHashMap<String, Object>();
            row.put("url", picture.url());
            row.put(FIELD_CAPTION, picture.caption());
            pictures.add(row);
        }
        var body = new LinkedHashMap<String, Object>();
        body.put(FIELD_VERSION, 0);
        body.put(FIELD_ESTIMATE_TEXT, source.estimateText());
        body.put(FIELD_TERMS_TEXT, source.termsText());
        body.put(FIELD_TOTAL_PRICE, source.totalPrice());
        body.put(FIELD_IMAGES, pictures);
        body.put(FIELD_STAGES, planEntries);
        return body;
    }

    /**
     * Сохраняет полное содержимое черновика после проверки версии и автора.
     *
     * <p>Поля, фотографии и план меняются одной транзакцией под блокировкой проекта; ответ содержит
     * новую версию. Отправленные и согласованные документы не редактируются.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param id идентификатор версии сметы или договора
     * @param contract true для договора, false для сметы
     * @param body поля черновика и ожидаемая версия
     * @return сохранённый черновик с новой версией
     * @throws ApiException при отсутствии доступа, устаревшей версии, неверном состоянии или полях
     */
    @Transactional
    DocumentView save(
            AccountView actor,
            UUID projectId,
            UUID id,
            boolean contract,
            Map<String, Object> body) {
        return saveDocument(actor, projectId, id, contract, body);
    }

    /** Выполняет общую операцию внутри транзакции вызывающего сервиса. */
    private DocumentView saveDocument(
            AccountView actor,
            UUID projectId,
            UUID id,
            boolean contract,
            Map<String, Object> body) {
        var access = projects.lockParticipant(projectId, actor.id());
        if (!access.canPrepareDocuments()) throw new ApiException(ApiError.FORBIDDEN);
        if (contract) {
            var entity = contract(projectId, id);
            visible(access, entity.getState(), true);
            if (!access.canPrepareDocuments()) throw new ApiException(ApiError.FORBIDDEN);
            if (access.preparationClosed()) throw new ApiException(ApiError.PREPARATION_CLOSED);
            if (!STATE_DRAFT.equals(entity.getState())) throw state(true);
            checkVersion(entity.getVersion(), body.get(FIELD_VERSION), true);
            keys(body, FIELD_VERSION, FIELD_CONTRACT_TEXT);
            require(body.get(FIELD_CONTRACT_TEXT) instanceof String);
            String value = text(body.get(FIELD_CONTRACT_TEXT), 50000);
            entity.setContractText(value == null ? "" : value);
            entity.setVersion(entity.getVersion() + 1);
            contracts.saveAndFlush(entity);
            return view(entity);
        }
        var entity = proposal(projectId, id);
        visible(access, entity.getState(), false);
        if (!access.canPrepareDocuments()) throw new ApiException(ApiError.FORBIDDEN);
        if (access.preparationClosed()) throw new ApiException(ApiError.PREPARATION_CLOSED);
        if (!STATE_DRAFT.equals(entity.getState())) throw state(false);
        checkVersion(entity.getVersion(), body.get(FIELD_VERSION), false);
        keys(
                body,
                FIELD_VERSION,
                FIELD_ESTIMATE_TEXT,
                FIELD_TERMS_TEXT,
                FIELD_TOTAL_PRICE,
                FIELD_IMAGES,
                FIELD_STAGES);
        require(
                body.keySet()
                        .containsAll(
                                List.of(
                                        FIELD_ESTIMATE_TEXT,
                                        FIELD_TERMS_TEXT,
                                        FIELD_TOTAL_PRICE,
                                        FIELD_IMAGES,
                                        FIELD_STAGES)));
        require(body.get(FIELD_ESTIMATE_TEXT) instanceof String);
        String estimate = text(body.get(FIELD_ESTIMATE_TEXT), 30000);
        String terms = text(body.get(FIELD_TERMS_TEXT), 10000);
        var price = number(body.get(FIELD_TOTAL_PRICE), new BigDecimal("9999999999999.99"));
        replaceProposal(id, entity, estimate == null ? "" : estimate, terms, price, body);
        return view(entity);
    }

    /**
     * Заменяет текст, иллюстрации и план черновика сметы.
     *
     * <p>Удаления фиксируются до вставок, чтобы не нарушить уникальность позиций. Версия сметы
     * увеличивается в той же транзакции.
     *
     * @param id идентификатор версии сметы
     * @param entity черновик сметы под блокировкой проекта
     * @param estimate нормализованный текст работ и цен
     * @param terms дополнительные условия или null
     * @param price итог или null
     * @param body проверенное тело команды с изображениями и этапами
     */
    private void replaceProposal(
            UUID id,
            ConstructionProposal entity,
            String estimate,
            String terms,
            BigDecimal price,
            Map<String, Object> body) {
        var pictures = maps(body.get(FIELD_IMAGES), 10);
        var planEntries = maps(body.get(FIELD_STAGES), 100);
        var imageValues = new ArrayList<ProposalImage>();
        var planValues = new ArrayList<ProposalStagePlan>();
        int position = 1;
        for (var picture : pictures) {
            keys(picture, "url", FIELD_CAPTION);
            String url = image(picture.get("url"));
            String caption = text(picture.get(FIELD_CAPTION), 300);
            require(caption != null);
            imageValues.add(
                    ProposalImage.builder()
                            .proposalId(id)
                            .position(position++)
                            .url(url)
                            .caption(caption)
                            .build());
        }
        position = 1;
        var seen = new HashSet<UUID>();
        for (var plan : planEntries) {
            keys(
                    plan,
                    "id",
                    "name",
                    FIELD_WORK_TYPE,
                    FIELD_EXPECTED_RESULT,
                    FIELD_PLANNED_START,
                    FIELD_PLANNED_FINISH);
            UUID inputId = uid(plan.get("id"));
            require(seen.add(inputId));
            require(!planBelongsElsewhere(inputId, id));
            String name = text(plan.get("name"), 120);
            String result = text(plan.get(FIELD_EXPECTED_RESULT), 2000);
            String type = text(plan.get(FIELD_WORK_TYPE), 16);
            try {
                StageWorkType.valueOf(type);
            } catch (RuntimeException _) {
                throw new ApiException(ApiError.VALIDATION_ERROR);
            }
            var start = date(string(plan.get(FIELD_PLANNED_START)));
            var finish = date(string(plan.get(FIELD_PLANNED_FINISH)));
            dates(start, finish);
            require(name != null);
            planValues.add(
                    ProposalStagePlan.builder()
                            .proposalId(id)
                            .position(position++)
                            .name(name)
                            .workType(type)
                            .expectedResult(result == null ? "" : result)
                            .plannedStart(start)
                            .plannedFinish(finish)
                            .build());
        }
        entity.setEstimateText(estimate);
        entity.setTermsText(terms);
        entity.setTotalPrice(price);
        entity.setVersion(entity.getVersion() + 1);
        images.deleteAll(images.findByProposalIdOrderByPositionAsc(id));
        plans.deleteAll(plans.findByProposalIdOrderByPositionAsc(id));
        images.flush();
        images.saveAll(imageValues);
        plans.saveAll(planValues);
        proposals.saveAndFlush(entity);
    }

    /**
     * Проверяет, занят ли переданный идентификатор планом другой версии сметы.
     *
     * @param id идентификатор планового этапа из команды
     * @param proposalId идентификатор сохраняемой версии сметы
     * @return true, если идентификатор уже принадлежит плану другой версии
     */
    private boolean planBelongsElsewhere(UUID id, UUID proposalId) {
        return plans.findById(id)
                .filter(plan -> !proposalId.equals(plan.getProposalId()))
                .isPresent();
    }

    /**
     * Представляет необязательное значение даты строкой для общего валидатора.
     *
     * @param value входное значение даты или null
     * @return строка даты либо null
     */
    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Проверяет тип и предел размера массива объектов REST-контракта.
     *
     * @param raw значение входного JSON без неявного приведения типа
     * @param max максимально допустимое число элементов
     * @return элементы в исходном порядке
     * @throws ApiException с VALIDATION_ERROR при неверном составе
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object raw, int max) {
        require(raw instanceof List<?>);
        var list = (List<?>) raw;
        require(list.size() <= max && list.stream().allMatch(item -> item instanceof Map<?, ?>));
        return (List<Map<String, Object>>) raw;
    }

    /**
     * Фиксирует отправку документа или решение заказчика в общей транзакции.
     *
     * <p>Отправляет подрядчик, решение принимает владелец. Согласование сметы создаёт рабочий план,
     * отказ закрывает подготовку; все изменения выполняются под блокировкой проекта. Повтор
     * состоявшегося перехода возвращает результат; повтор отказа требует той же причины.
     *
     * @param actor аутентифицированный участник карточки
     * @param projectId идентификатор карточки дома
     * @param id идентификатор версии сметы или договора
     * @param contract true для договора, false для сметы
     * @param action submit, approve, request-changes или reject
     * @param body ожидаемая версия и для отклонения комментарий
     * @return версия документа после перехода либо результат повторного вызова
     * @throws ApiException при недопустимой роли, версии, состоянии, конфликте повтора или неполных
     *     полях
     */
    @Transactional
    DocumentView command(
            AccountView actor,
            UUID projectId,
            UUID id,
            boolean contract,
            String action,
            Map<String, Object> body) {
        if ("reject".equals(action)) {
            keys(body, FIELD_VERSION, FIELD_COMMENT);
            if (contract) throw state(true);
        } else {
            keys(body, FIELD_VERSION, FIELD_COMMENT, FIELD_CONTRACTOR_CONFIRMED);
        }
        long expected = version(body.get(FIELD_VERSION));
        var access = projects.lockParticipant(projectId, actor.id());
        boolean send = "submit".equals(action);
        if (send ? !access.canPrepareDocuments() : !access.owner())
            throw new ApiException(ApiError.FORBIDDEN);
        String documentState = documentState(projectId, id, contract);
        visible(access, documentState, contract);
        String target =
                switch (action) {
                    case "submit" -> "SUBMITTED";
                    case "approve" -> STATE_APPROVED;
                    case "request-changes" -> STATE_CHANGES_REQUESTED;
                    case "reject" -> STATE_REJECTED;
                    default -> throw new ApiException(ApiError.VALIDATION_ERROR);
                };
        if (target.equals(documentState))
            return repeatedDecision(projectId, id, contract, target, body);
        validateTransition(access, projectId, id, contract, target, send, expected);
        if (send) validateSending(projectId, id, contract, body);
        else applyDecision(actor, projectId, id, contract, target, body);

        return changeState(projectId, id, target, send, contract, actor.id());
    }

    /** Проверяет открытость подготовки, ожидаемую версию и допустимость перехода. */
    private void validateTransition(
            ProjectAccess access,
            UUID projectId,
            UUID id,
            boolean contract,
            String target,
            boolean send,
            long expected) {
        String documentState = documentState(projectId, id, contract);
        if (access.preparationClosed()) throw new ApiException(ApiError.PREPARATION_CLOSED);
        if (STATE_REJECTED.equals(target)
                && (!"PROPOSAL_REQUIRED".equals(access.processMode())
                        || access.approvedProposalId() != null)) {
            throw state(false);
        }
        long current =
                contract
                        ? contract(projectId, id).getVersion()
                        : proposal(projectId, id).getVersion();
        checkVersion(current, expected, contract);
        if (!(send ? STATE_DRAFT : "SUBMITTED").equals(documentState)) throw state(contract);
    }

    /** Повтор решения возвращает исходную версию; отказ дополнительно сверяет причину. */
    private DocumentView repeatedDecision(
            UUID projectId, UUID id, boolean contract, String target, Map<String, Object> body) {

        DocumentView result =
                contract ? view(contract(projectId, id)) : view(proposal(projectId, id));
        if (STATE_REJECTED.equals(target)) {
            var decision = ((ProposalView) result).decision();
            if (!Objects.equals(text(body.get(FIELD_COMMENT), 5000), decision.comment())) {
                throw new ApiException(ApiError.IDEMPOTENCY_CONFLICT);
            }
        }
        return result;
    }

    /** Проверяет полноту отправляемого документа до его публикации заказчику. */
    private void validateSending(
            UUID projectId, UUID id, boolean contract, Map<String, Object> body) {

        if (contract) {
            field(
                    text(contract(projectId, id).getContractText(), 50000) != null,
                    FIELD_CONTRACT_TEXT,
                    "Заполните текст договора");
            field(
                    Boolean.TRUE.equals(body.get(FIELD_CONTRACTOR_CONFIRMED)),
                    FIELD_CONTRACTOR_CONFIRMED,
                    "Подтвердите проверку условий договора");
        } else {
            var entity = proposal(projectId, id);
            field(
                    text(entity.getEstimateText(), 30000) != null,
                    FIELD_ESTIMATE_TEXT,
                    "Опишите работы, материалы и цены");
            field(entity.getTotalPrice() != null, FIELD_TOTAL_PRICE, "Укажите общую стоимость");
            var plan = plans.findByProposalIdOrderByPositionAsc(id);
            field(!plan.isEmpty(), FIELD_STAGES, "Добавьте хотя бы один этап");
            for (int i = 0; i < plan.size(); i++) {
                var stage = plan.get(i);
                field(
                        stage.getPlannedStart() != null,
                        STAGE_FIELD_PREFIX + i + ".plannedStart",
                        "Укажите плановое начало");
                field(
                        stage.getPlannedFinish() != null,
                        STAGE_FIELD_PREFIX + i + ".plannedFinish",
                        "Укажите плановое завершение");
                field(
                        text(stage.getExpectedResult(), 2000) != null,
                        STAGE_FIELD_PREFIX + i + ".expectedResult",
                        "Опишите ожидаемый результат этапа");
            }
        }
    }

    /** Сохраняет решение и согласованный план в транзакции команды под блокировкой проекта. */
    private void applyDecision(
            AccountView actor,
            UUID projectId,
            UUID id,
            boolean contract,
            String target,
            Map<String, Object> body) {

        String comment = text(body.get(FIELD_COMMENT), 5000);
        if (!STATE_APPROVED.equals(target)) {
            field(comment != null, FIELD_COMMENT, "Укажите причину или замечание");
        }
        if (STATE_REJECTED.equals(target)) projects.closePreparation(projectId, actor.id());
        decide(id, actor.id(), target, comment, contract);
        if (STATE_APPROVED.equals(target)) {
            if (contract) {
                projects.approveContract(projectId, actor.id(), id);
            } else {
                var plan =
                        plans.findByProposalIdOrderByPositionAsc(id).stream()
                                .map(stage ->
                                        new Stages.ApprovedStage(
                                                stage.getId(),
                                                stage.getName(),
                                                StageWorkType.valueOf(stage.getWorkType()),
                                                stage.getExpectedResult(),
                                                stage.getPlannedStart(),
                                                stage.getPlannedFinish()))
                                .toList();
                stages.install(projectId, plan);
                projects.approveProposal(projectId, actor.id(), id);
            }
        }
    }

    /**
     * Читает состояние версии после блокировки проекта.
     *
     * @param projectId идентификатор карточки дома
     * @param id идентификатор версии сметы или договора
     * @param contract true для договора, false для сметы
     * @return текущее состояние версии
     */
    private String documentState(UUID projectId, UUID id, boolean contract) {
        return contract ? contract(projectId, id).getState() : proposal(projectId, id).getState();
    }

    /**
     * Сохраняет единственное решение по версии документа.
     *
     * <p>Состояние документа меняет вызывающий метод в той же транзакции.
     *
     * @param id идентификатор версии сметы или договора
     * @param actor идентификатор владельца
     * @param decision код решения заказчика
     * @param comment пояснение или null, если оно необязательно
     * @param contract true для договора, false для сметы
     */
    private void decide(UUID id, UUID actor, String decision, String comment, boolean contract) {
        if (contract) {
            contractDecisions.save(
                    ContractDecision.builder()
                            .contractId(id)
                            .actorId(actor)
                            .actorSnapshot(organizations.snapshot(actor))
                            .decision(decision)
                            .comment(comment)
                            .decidedAt(clock.instant())
                            .build());
            return;
        }
        proposalDecisions.save(
                ProposalDecision.builder()
                        .proposalId(id)
                        .actorId(actor)
                        .actorSnapshot(organizations.snapshot(actor))
                        .decision(decision)
                        .comment(comment)
                        .decidedAt(clock.instant())
                        .build());
    }

    /**
     * Меняет состояние и версию документа, фиксируя дату первой отправки.
     *
     * @param projectId идентификатор карточки дома
     * @param id идентификатор версии сметы или договора
     * @param target новое состояние
     * @param actorId фактический отправитель
     * @param submitted true, если это первая отправка и нужна дата
     * @param contract true для договора, false для сметы
     * @return версия документа после перехода
     */
    private DocumentView changeState(
            UUID projectId,
            UUID id,
            String target,
            boolean submitted,
            boolean contract,
            UUID actorId) {
        if (contract) {
            var entity = contract(projectId, id);
            entity.setState(target);
            entity.setVersion(entity.getVersion() + 1);
            if (submitted) {
                entity.setSubmittedAt(clock.instant());
                entity.setSubmittedById(actorId);
                entity.setSubmittedBySnapshot(organizations.snapshot(actorId));
            }
            contracts.saveAndFlush(entity);
            return view(entity);
        }
        var entity = proposal(projectId, id);
        entity.setState(target);
        entity.setVersion(entity.getVersion() + 1);
        if (submitted) {
            entity.setSubmittedAt(clock.instant());
            entity.setSubmittedById(actorId);
            entity.setSubmittedBySnapshot(organizations.snapshot(actorId));
        }
        proposals.saveAndFlush(entity);
        return view(entity);
    }

    /**
     * Проверяет ожидаемую версию документа до применения команды.
     *
     * @param current версия, прочитанная под блокировкой
     * @param expected ожидаемая версия из команды
     * @param contract true для договора, false для сметы
     * @throws ApiException с STALE_PROPOSAL_VERSION или STALE_CONTRACT_VERSION
     */
    private void checkVersion(long current, Object expected, boolean contract) {
        if (current != version(expected)) {
            throw new ApiException(
                    contract ? ApiError.STALE_CONTRACT_VERSION : ApiError.STALE_PROPOSAL_VERSION);
        }
    }
}
