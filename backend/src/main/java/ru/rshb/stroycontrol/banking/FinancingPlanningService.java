package ru.rshb.stroycontrol.banking;

import static ru.rshb.stroycontrol.common.PreparationInput.fieldValue;
import static ru.rshb.stroycontrol.common.PreparationInput.keys;
import static ru.rshb.stroycontrol.common.PreparationInput.uuid;
import static ru.rshb.stroycontrol.common.PreparationInput.version;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.common.CommandFingerprint;
import ru.rshb.stroycontrol.projects.FinancingRequests;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Хранит личные проверки и расчёты владельца отдельно от строительной заявки и одобрения. HTTP
 * выполняется вне транзакции; фиксация сериализована общей блокировкой карточки.
 */
@Service
@RequiredArgsConstructor
class FinancingPlanningService {
    private static final String FIELD_REQUEST_VERSION = "requestVersion";
    private static final String FIELD_PLANNING_VERSION = "planningVersion";
    private static final String FIELD_IDEMPOTENCY_KEY = "idempotencyKey";
    private static final String FIELD_PLOT_CHECK_ID = "plotCheckId";
    private static final String FIELD_JOINT = "joint";

    private final FinancingRequests requests;
    private final ProjectFinancingPlanRepository plans;
    private final ProjectPlotCheckRepository checks;
    private final ProjectMortgageCalculationRepository calculations;
    private final FinancingPlanningCommandRepository commands;
    private final CommandFingerprint fingerprint;
    private final TransactionTemplate tx;
    private final RestClient client;
    private final LocalBankApi mock;
    private final Clock clock;

    /**
     * Читает личный план без создания строк и без вызова Mock API.
     *
     * @param project карточка
     * @param owner текущий аккаунт; права проверяются по проекту
     * @return результаты и признаки актуальности, отсутствие результатов выражено null
     */
    @Transactional(readOnly = true)
    FinancingPlanningView get(UUID project, UUID owner) {
        return view(
                requests.financingContext(project, owner), plans.findById(project).orElse(null));
    }

    /**
     * Проверяет сохранённый номер через локальный источник и фиксирует результат атомарно.
     *
     * @param project карточка
     * @param owner аккаунт владельца
     * @param body версии и ключ повторной команды; номер из тела не принимается
     * @return результат команды, одинаковый при повторе того же тела
     * @throws ApiException с BANK_DEMO_UNAVAILABLE при ошибке источника либо конфликтом версий
     */
    FinancingPlanningView check(UUID project, UUID owner, Map<String, Object> body) {
        requests.financingContext(project, owner);
        keys(body, FIELD_REQUEST_VERSION, FIELD_PLANNING_VERSION, FIELD_IDEMPOTENCY_KEY);
        Command command = command("CHECK", body);
        Start start = tx.execute(status -> start(project, owner, command));
        if (start.cached() != null) return start.cached();
        String number = PlotDemoData.number(start.context().cadastralNumber());
        PlotDemoResponse response;
        try {
            response =
                    client.get()
                            .uri(mock.plotUrl() + "?number={number}", number)
                            .retrieve()
                            .body(PlotDemoResponse.class);
            if (!PlotDemoData.result(number).equals(response))
                throw new ApiException(ApiError.BANK_DEMO_UNAVAILABLE);
        } catch (RuntimeException _) {
            throw new ApiException(ApiError.BANK_DEMO_UNAVAILABLE);
        }
        return tx.execute(status -> {
            Start current = start(project, owner, command);
            if (current.cached() != null) return current.cached();
            if (!number.equals(PlotDemoData.number(current.context().cadastralNumber()))) {
                throw new ApiException(ApiError.STALE_REQUEST_VERSION);
            }
            var check =
                    ProjectPlotCheck.builder()
                            .projectId(project)
                            .ownerId(owner)
                            .cadastralNumber(number)
                            .requestVersion(command.requestVersion())
                            .status(response.status())
                            .address(response.address())
                            .region(response.region())
                            .landCategory(response.landCategory())
                            .permittedUse(response.permittedUse())
                            .reason(response.reason())
                            .rate(response.rate())
                            .termsVersion(response.termsVersion())
                            .checkedAt(clock.instant())
                            .build();
            checks.saveAndFlush(check);
            var plan = plan(project);
            plan.setLatestPlotCheckId(check.getId());
            return finish(project, owner, command, current.context(), plan);
        });
    }

    /**
     * Считает и сохраняет предварительную ипотеку под блокировкой карточки.
     *
     * @param project карточка
     * @param owner аккаунт владельца
     * @param body параметры калькулятора и основание расчёта
     * @return сохранённый серверный результат, не изменяющий финансирование и одобрение
     */
    FinancingPlanningView calculate(UUID project, UUID owner, Map<String, Object> body) {
        requests.financingContext(project, owner);
        keys(
                body,
                FIELD_REQUEST_VERSION,
                FIELD_PLANNING_VERSION,
                FIELD_IDEMPOTENCY_KEY,
                FIELD_PLOT_CHECK_ID,
                "cost",
                "depositPercent",
                "years",
                FIELD_JOINT);
        Command command = command("CALCULATE", body);
        UUID checkId = fieldValue(FIELD_PLOT_CHECK_ID, () -> uuid(body.get(FIELD_PLOT_CHECK_ID)));
        BigDecimal cost = decimal(body.get("cost"));
        int deposit = whole(body, "depositPercent");
        int years = whole(body, "years");
        if (!(body.get(FIELD_JOINT) instanceof Boolean))
            throw new ApiException(FIELD_JOINT, "Укажите совместное оформление");
        boolean joint = (Boolean) body.get(FIELD_JOINT);
        MortgageMath.validate(cost, deposit, years);
        return tx.execute(status -> {
            Start current = start(project, owner, command);
            if (current.cached() != null) return current.cached();
            var plan = plan(project);
            if (!checkId.equals(plan.getLatestPlotCheckId()))
                throw new ApiException(ApiError.STALE_FINANCING_PLAN);
            var check =
                    checks.findById(checkId)
                            .orElseThrow(() -> new ApiException(ApiError.STALE_FINANCING_PLAN));
            if (!check.getProjectId().equals(project)
                    || !check.getOwnerId().equals(owner)
                    || !"ELIGIBLE".equals(check.getStatus())
                    || !Objects.equals(
                            check.getCadastralNumber(),
                            normalized(current.context().cadastralNumber()))
                    || !PlotDemoData.TERMS_VERSION.equals(check.getTermsVersion())) {
                throw new ApiException(ApiError.STALE_FINANCING_PLAN);
            }
            BigDecimal down = cost.multiply(BigDecimal.valueOf(deposit)).movePointLeft(2);
            BigDecimal principal = cost.subtract(down);
            BigDecimal limit = BigDecimal.valueOf(joint ? 12000000 : 6000000);
            var calculation =
                    ProjectMortgageCalculation.builder()
                            .projectId(project)
                            .ownerId(owner)
                            .plotCheckId(checkId)
                            .cost(cost)
                            .depositPercent(deposit)
                            .depositAmount(down)
                            .years(years)
                            .joint(joint)
                            .principal(principal)
                            .rate(check.getRate())
                            .creditLimit(limit)
                            .monthlyPayment(MortgageMath.payment(principal, check.getRate(), years))
                            .withinLimit(principal.compareTo(limit) <= 0)
                            .termsVersion(check.getTermsVersion())
                            .calculatedAt(clock.instant())
                            .build();
            calculations.saveAndFlush(calculation);
            plan.setLatestCalculationId(calculation.getId());
            return finish(project, owner, command, current.context(), plan);
        });
    }

    /**
     * Читает контекст под общей блокировкой; успешный повтор проверяется до устаревших версий.
     *
     * @param project карточка
     * @param owner владелец
     * @param command нормализованные реквизиты команды
     * @return контекст и сохранённый повтор, если он есть
     */
    private Start start(UUID project, UUID owner, Command command) {
        var context = requests.lockFinancingContext(project, owner);
        var old =
                commands.findByProjectIdAndOwnerIdAndIdempotencyKey(project, owner, command.key());
        if (old.isPresent()) {
            if (!old.get().getRequestHash().equals(command.hash())
                    || !old.get().getOperation().equals(command.operation())) {
                throw new ApiException(ApiError.IDEMPOTENCY_CONFLICT);
            }
            return new Start(context, old.get().getResult());
        }
        if (!context.editable()) throw new ApiException(ApiError.REQUEST_LOCKED);
        if (context.requestVersion() != command.requestVersion())
            throw new ApiException(ApiError.STALE_REQUEST_VERSION);
        long actual = plans.findById(project).map(ProjectFinancingPlan::getVersion).orElse(0L);
        if (actual != command.planningVersion())
            throw new ApiException(ApiError.STALE_FINANCING_PLAN);
        return new Start(context, null);
    }

    /**
     * Возвращает существующий либо новый указатель; вызывается только под блокировкой проекта.
     *
     * @param project карточка
     * @return управляемая либо новая JPA-сущность своего модуля
     */
    private ProjectFinancingPlan plan(UUID project) {
        return plans.findById(project)
                .orElseGet(() -> ProjectFinancingPlan.builder().projectId(project).build());
    }

    /**
     * Фиксирует результат и ключ в одной транзакции без изменения строительной заявки.
     *
     * @param project карточка
     * @param owner владелец
     * @param command команда
     * @param context актуальная заявка
     * @param plan изменённые указатели
     * @return итоговый ответ для повторного использования
     */
    private FinancingPlanningView finish(
            UUID project,
            UUID owner,
            Command command,
            FinancingRequests.Context context,
            ProjectFinancingPlan plan) {
        plan.setVersion(plan.getVersion() + 1);
        plans.saveAndFlush(plan);
        FinancingPlanningView result = view(context, plan);
        commands.saveAndFlush(
                FinancingPlanningCommand.builder()
                        .projectId(project)
                        .ownerId(owner)
                        .idempotencyKey(command.key())
                        .operation(command.operation())
                        .requestHash(command.hash())
                        .result(result)
                        .build());
        return result;
    }

    /**
     * Формирует ответ и сравнивает основание расчёта с текущим номером без изменения истории.
     *
     * @param context текущая заявка
     * @param plan указатели либо null
     * @return личные результаты и признаки актуальности
     */
    private FinancingPlanningView view(
            FinancingRequests.Context context, ProjectFinancingPlan plan) {
        ProjectPlotCheckView check =
                plan == null || plan.getLatestPlotCheckId() == null
                        ? null
                        : checks.findById(plan.getLatestPlotCheckId()).orElseThrow().view();
        ProjectMortgageCalculationView calculation =
                plan == null || plan.getLatestCalculationId() == null
                        ? null
                        : calculations.findById(plan.getLatestCalculationId()).orElseThrow().view();
        boolean checkCurrent =
                check != null
                        && check.cadastralNumber().equals(normalized(context.cadastralNumber()));
        boolean calculationCurrent =
                checkCurrent
                        && "ELIGIBLE".equals(check.status())
                        && calculation != null
                        && calculation.plotCheckId().equals(check.id())
                        && PlotDemoData.TERMS_VERSION.equals(calculation.termsVersion());
        return new FinancingPlanningView(
                plan == null ? 0 : plan.getVersion(),
                context.editable(),
                check,
                calculation,
                checkCurrent,
                calculationCurrent);
    }

    /**
     * Разбирает обязательные реквизиты и сохраняет нормализованный отпечаток тела.
     *
     * @param operation CHECK либо CALCULATE
     * @param body тело без неизвестных полей
     * @return неизменяемые реквизиты для обеих коротких транзакций
     */
    private Command command(String operation, Map<String, Object> body) {
        return new Command(
                operation,
                fieldValue(FIELD_REQUEST_VERSION, () -> version(body.get(FIELD_REQUEST_VERSION))),
                fieldValue(FIELD_PLANNING_VERSION, () -> version(body.get(FIELD_PLANNING_VERSION))),
                fieldValue(FIELD_IDEMPOTENCY_KEY, () -> uuid(body.get(FIELD_IDEMPOTENCY_KEY))),
                fingerprint.of(operation, body));
    }

    /**
     * Читает стоимость без принятия строк и нечисловых JSON-значений.
     *
     * @param value значение JSON
     * @return стоимость, проверяемая бизнес-правилами
     */
    private BigDecimal decimal(Object value) {
        if (!(value instanceof Number))
            throw new ApiException("cost", "Укажите стоимость в рублях");
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException _) {
            throw new ApiException("cost", "Укажите стоимость в рублях");
        }
    }

    /**
     * Читает целый параметр без усечения дробной части и переполнения.
     *
     * @param body тело команды
     * @param field имя параметра
     * @return целое значение
     */
    private int whole(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof Integer)) throw new ApiException(field, "Укажите целое число");
        return (Integer) value;
    }

    /**
     * Нормализует только краевые пробелы; старые номера заявок не отвергаются при чтении.
     *
     * @param number сохранённый номер либо null
     * @return номер либо null
     */
    private String normalized(String number) {
        return number == null ? null : number.strip();
    }

    /**
     * Реквизиты повторяемой команды без объектов JPA.
     *
     * @param operation тип команды
     * @param requestVersion версия заявки
     * @param planningVersion версия плана
     * @param key ключ повтора
     * @param hash отпечаток нормализованного тела
     */
    private record Command(
            String operation, long requestVersion, long planningVersion, UUID key, String hash) {}

    /**
     * Снимок между короткой транзакцией и HTTP без удержания блокировки.
     *
     * @param context сведения заявки
     * @param cached сохранённый ответ либо null
     */
    private record Start(FinancingRequests.Context context, FinancingPlanningView cached) {}
}
