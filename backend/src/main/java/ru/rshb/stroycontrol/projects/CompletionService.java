package ru.rshb.stroycontrol.projects;

import static ru.rshb.stroycontrol.common.PreparationInput.keys;
import static ru.rshb.stroycontrol.common.PreparationInput.uuid;
import static ru.rshb.stroycontrol.common.PreparationInput.version;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.acceptance.Acceptance;
import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.common.CommandFingerprint;
import ru.rshb.stroycontrol.organizations.Organizations;
import ru.rshb.stroycontrol.stages.Stages;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/** Подтверждает завершение под той же блокировкой, которая сериализует план и решения приёмки. */
@Service
@RequiredArgsConstructor
class CompletionService {
    private final ProjectAuthorization authorization;
    private final Acceptance acceptance;
    private final Stages stages;
    private final Organizations organizations;
    private final Clock clock;
    private final CommandFingerprint fingerprint;

    /**
     * Возвращает сводку готовности для участника проекта.
     *
     * @param actor участник
     * @param project проект
     * @return готовность и сохранённый итог
     */
    @Transactional(readOnly = true)
    CompletionView get(AccountView actor, UUID project) {
        var p = authorization.project(project, actor.id(), false);
        return view(p, actor.id());
    }

    /**
     * Явно подтверждает завершение только полностью принятого непустого плана.
     *
     * @param actor владелец
     * @param project проект
     * @param body версия готовности и ключ
     * @return прежний либо созданный итог без изменения даты повтора
     */
    @Transactional
    CompletionView complete(AccountView actor, UUID project, Map<String, Object> body) {
        var p = authorization.project(project, actor.id(), true);
        if (!p.getCustomerId().equals(actor.id())) throw new ApiException(ApiError.FORBIDDEN);
        keys(body, "expectedVersion", "idempotencyKey");
        var key = uuid(body.get("idempotencyKey"));
        var hash = fingerprint.of(project.toString(), body);
        if (p.getCompletedAt() != null) {
            if (key.equals(p.getCompletionKey()) && !hash.equals(p.getCompletionHash()))
                throw new ApiException(ApiError.IDEMPOTENCY_CONFLICT);
            return view(p, actor.id());
        }
        if (p.getCompletionVersion() != version(body.get("expectedVersion")))
            throw new ApiException(ApiError.STALE_ACCEPTANCE_VERSION);
        var current = view(p, actor.id());
        if (!current.canComplete()) throw new ApiException(ApiError.CONSTRUCTION_NOT_READY);
        p.setCompletionKey(key);
        p.setCompletionHash(hash);
        p.setCompletedAt(clock.instant());
        p.setCompletedBySnapshot(organizations.snapshot(actor.id()));
        p.setCompletionVersion(p.getCompletionVersion() + 1);
        return view(p, actor.id());
    }

    private CompletionView view(HouseProject p, UUID actor) {
        var plan = stages.plan(p.getId(), actor);
        var accepted = acceptance.acceptedStages(p.getId());
        int count = (int) plan.stream().filter(s -> accepted.contains(s.id())).count();
        boolean ready = !plan.isEmpty() && count == plan.size();
        boolean owner = p.getCustomerId().equals(actor);
        return new CompletionView(
                p.getId(),
                plan.size(),
                count,
                owner && ready && p.getCompletedAt() == null,
                completionMessage(p, plan.isEmpty(), ready, owner),
                p.getCompletionVersion(),
                p.getCompletedAt(),
                p.getCompletedBySnapshot());
    }

    /** Поясняет следующее действие отдельно от расчёта прогресса. */
    private String completionMessage(
            HouseProject project, boolean empty, boolean ready, boolean owner) {
        if (project.getCompletedAt() != null) return "Строительство завершено";
        if (empty) return "Добавьте и согласуйте план строительства";
        if (!ready) return "Дождитесь приёмки всех этапов";
        if (!owner) return "Завершение подтверждает заказчик";
        return null;
    }
}
