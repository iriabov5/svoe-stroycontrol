package ru.rshb.stroycontrol.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;

import java.time.LocalDate;
import java.util.UUID;

class ConstructionStageTest {
    final LocalDate today = LocalDate.of(2026, 11, 13);

    ConstructionStage stage(LocalDate start, LocalDate finish) {
        return ConstructionStage.create(
                UUID.randomUUID(),
                1,
                new StagePlan("Фундамент", StageWorkType.FOUNDATION, start, finish));
    }

    @Test
    @DisplayName("Новый этап запланирован, название обрезано, фактические даты пусты")
    void plannedStage() {
        var stage =
                ConstructionStage.create(
                        UUID.randomUUID(),
                        1,
                        StagePlan.parse(" Фундамент ", StageWorkType.FOUNDATION, null, null));
        assertThat(stage.getName()).isEqualTo("Фундамент");
        assertThat(stage.getStatus()).isEqualTo(StageStatus.PLANNED);
        assertThat(stage.getActualStart()).isNull();
        assertThat(stage.getActualFinish()).isNull();
        assertThat(stage.view(today).finishOverdueDays()).isZero();
    }

    @ParameterizedTest(name = "дата = {0}")
    @ValueSource(
            strings = {
                "2026-02-29",
                "2026-13-01",
                "0000-01-01",
                "10000-01-01",
                "2026-1-01",
                "2026-01-01T00:00:00Z",
                ""
            })
    @DisplayName("Некорректная календарная дата отклоняется")
    void invalidDates(String date) {
        assertThatThrownBy(() -> StagePlan.parse("Дом", StageWorkType.OTHER, date, null))
                .isInstanceOf(ApiException.class)
                .extracting("error")
                .isEqualTo(ApiError.INVALID_STAGE_DATES);
    }

    @Test
    @DisplayName("Частичный план и високосная дата допустимы, обратный диапазон отклоняется")
    void dateRange() {
        assertThatCode(() -> StagePlan.parse("Дом", StageWorkType.OTHER, "2024-02-29", null))
                .doesNotThrowAnyException();
        assertThatCode(() -> StagePlan.parse("Дом", StageWorkType.OTHER, null, "9999-12-31"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() ->
                        StagePlan.parse("Дом", StageWorkType.OTHER, "2026-11-14", "2026-11-13"))
                .isInstanceOf(ApiException.class)
                .extracting("error")
                .isEqualTo(ApiError.INVALID_STAGE_DATES);
    }

    @Test
    @DisplayName("Пустое название и неизвестный вид работ отклоняются")
    void invalidPlan() {
        assertThatThrownBy(() -> StagePlan.parse("  ", StageWorkType.OTHER, null, null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> StagePlan.parse("Дом", null, null, null))
                .isInstanceOf(ApiException.class);
    }

    @ParameterizedTest(name = "смещение плана = {0}, просрочка = {1}")
    @CsvSource({"-1,1", "0,0", "1,0"})
    @DisplayName("Просрочка возникает только после плановой даты")
    void deadlineBoundary(int shift, long overdue) {
        var view = stage(today.plusDays(shift), today.plusDays(shift)).view(today);
        assertThat(view.startOverdueDays()).isEqualTo(overdue);
        assertThat(view.finishOverdueDays()).isEqualTo(overdue);
        assertThat(view.startLateDays()).isZero();
    }

    @Test
    @DisplayName("Начало и завершение фиксируют даты, повторы с исходной версией не меняют факт")
    void transitionsAndReplay() {
        var stage = stage(today.minusDays(2), today.minusDays(1));
        stage.start(0L, today);
        assertThat(stage.getStatus()).isEqualTo(StageStatus.IN_PROGRESS);
        stage.start(99L, today.plusDays(1));
        assertThat(stage.getActualStart()).isEqualTo(today);
        stage.complete(0L, today.plusDays(1));
        stage.complete(99L, today.plusDays(2));
        stage.start(99L, today.plusDays(3));
        assertThat(stage.getStatus()).isEqualTo(StageStatus.WORK_COMPLETED);
        assertThat(stage.getActualFinish()).isEqualTo(today.plusDays(1));
        var view = stage.view(today.plusDays(3));
        assertThat(view.startOverdueDays()).isZero();
        assertThat(view.finishOverdueDays()).isZero();
        assertThat(view.startLateDays()).isEqualTo(2);
        assertThat(view.finishLateDays()).isEqualTo(2);
    }

    @Test
    @DisplayName("Завершение до начала и перевод часов назад не меняют состояние")
    void invalidTransition() {
        var stage = stage(null, null);
        assertThatThrownBy(() -> stage.complete(0L, today))
                .isInstanceOf(ApiException.class)
                .extracting("error")
                .isEqualTo(ApiError.INVALID_STAGE_TRANSITION);
        assertThat(stage.getStatus()).isEqualTo(StageStatus.PLANNED);
        stage.start(0L, today);
        var beforeStart = today.minusDays(1);
        assertThatThrownBy(() -> stage.complete(0L, beforeStart)).isInstanceOf(ApiException.class);
        assertThat(stage.getActualFinish()).isNull();
    }

    @Test
    @DisplayName("Устаревшая версия отклоняется, правка плана сохраняет фактические даты")
    void versionAndPlan() {
        var stage = stage(null, null);
        assertThatThrownBy(() -> stage.start(1L, today))
                .isInstanceOf(ApiException.class)
                .extracting("error")
                .isEqualTo(ApiError.STALE_STAGE_VERSION);
        stage.start(0L, today);
        stage.updatePlan(StagePlan.parse("Новое имя", StageWorkType.OTHER, "2026-11-10", null));
        assertThat(stage.getActualStart()).isEqualTo(today);
        assertThat(stage.getStatus()).isEqualTo(StageStatus.IN_PROGRESS);
        assertThat(stage.getName()).isEqualTo("Новое имя");
    }
}
