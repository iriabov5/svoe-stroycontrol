package ru.rshb.stroycontrol.stages;

import ru.rshb.stroycontrol.accounts.AccountRole;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Вычисляет подсказки по существующему плану без сохранения дополнительного состояния. */
final class ControlGates {
    private ControlGates() {}

    /**
     * Формирует три контрольные точки первой итерации.
     *
     * <p>Тип этапа является безопасным и уже согласованным признаком плана. Поэтому расчёт не
     * угадывает качество работ по названию и не заменяет технический надзор.
     *
     * @param stages доступный участнику план
     * @return фундамент, скрытые сети и передача дома в постоянном порядке
     */
    static List<ControlGateView> from(List<StageView> stages) {
        return from(stages, ignored -> false);
    }

    /**
     * Формирует контрольные точки с учётом опубликованных фото-подтверждений.
     *
     * @param stages доступный участнику план
     * @param hasPublishedPhoto проверка подтверждения по идентификатору этапа
     * @return фундамент, скрытые сети и передача дома в постоянном порядке
     */
    static List<ControlGateView> from(List<StageView> stages, Predicate<UUID> hasPublishedPhoto) {
        return List.of(
                forStage(
                        "FOUNDATION",
                        "Перед заливкой фундамента",
                        StageWorkType.FOUNDATION,
                        "Проверьте фото армирования, опалубки и закладных до необратимых работ",
                        stages,
                        hasPublishedPhoto),
                forStage(
                        "UTILITIES",
                        "Перед закрытием инженерных сетей",
                        StageWorkType.UTILITIES,
                        "Проверьте трассы и испытания до зашивки стен или стяжки",
                        stages,
                        hasPublishedPhoto),
                handover(stages));
    }

    private static ControlGateView forStage(
            String key,
            String title,
            StageWorkType type,
            String completedDetail,
            List<StageView> stages,
            Predicate<UUID> hasPublishedPhoto) {
        var stage =
                stages.stream()
                        .filter(item -> item.workType() == type)
                        .min(Comparator.comparingInt(StageView::position));
        if (stage.isEmpty()) {
            return new ControlGateView(
                    key,
                    ControlGateStatus.BLOCKED,
                    AccountRole.CONTRACTOR,
                    title,
                    "Добавить этап в план",
                    "В плане пока нет подходящего этапа. Карточка появится после его добавления.",
                    null);
        }
        var item = stage.get();
        return switch (item.status()) {
            case PLANNED ->
                    new ControlGateView(
                            key,
                            ControlGateStatus.WAITING_CONTRACTOR,
                            AccountRole.CONTRACTOR,
                            title,
                            "Начать работы по этапу",
                            "Подрядчику нужно начать этап и опубликовать подтверждения до следующей работы.",
                            item.id());
            case IN_PROGRESS -> inProgressGate(key, title, item, hasPublishedPhoto.test(item.id()));
            case WORK_COMPLETED ->
                    new ControlGateView(
                            key,
                            ControlGateStatus.WAITING_CUSTOMER,
                            AccountRole.CUSTOMER,
                            title,
                            "Проверить завершённый этап",
                            completedDetail,
                            item.id());
        };
    }

    private static ControlGateView inProgressGate(
            String key, String title, StageView stage, boolean hasPublishedPhoto) {
        if (hasPublishedPhoto)
            return new ControlGateView(
                    key,
                    ControlGateStatus.COMPLETED,
                    AccountRole.CONTRACTOR,
                    title,
                    "Подтверждение добавлено",
                    "Фото-подтверждение опубликовано в журнале этапа.",
                    stage.id());
        return new ControlGateView(
                key,
                ControlGateStatus.WAITING_CONTRACTOR,
                AccountRole.CONTRACTOR,
                title,
                "Добавить подтверждения в журнал",
                "Опубликуйте фото и результат проверки до перехода к следующей работе.",
                stage.id());
    }

    private static ControlGateView handover(List<StageView> stages) {
        boolean complete =
                !stages.isEmpty()
                        && stages.stream()
                                .allMatch(stage -> stage.status() == StageStatus.WORK_COMPLETED);
        return new ControlGateView(
                "HANDOVER",
                complete ? ControlGateStatus.WAITING_CUSTOMER : ControlGateStatus.BLOCKED,
                complete ? AccountRole.CUSTOMER : AccountRole.CONTRACTOR,
                "Перед передачей дома",
                complete ? "Проверить завершённые этапы" : "Завершить все этапы",
                complete
                        ? "Проверьте подтверждения и примите этапы перед завершением строительства."
                        : "Перед передачей дома все этапы должны быть отмечены подрядчиком как завершённые.",
                lastStage(stages));
    }

    private static UUID lastStage(List<StageView> stages) {
        return stages.stream()
                .max(Comparator.comparingInt(StageView::position))
                .map(StageView::id)
                .orElse(null);
    }
}
