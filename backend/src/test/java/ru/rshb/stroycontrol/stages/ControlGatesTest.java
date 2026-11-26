package ru.rshb.stroycontrol.stages;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class ControlGatesTest {
    @Test
    @DisplayName("Незавершённый фундамент ждёт подтверждений подрядчика")
    void foundationInProgressWaitsForContractor() {
        var foundation = stage(StageWorkType.FOUNDATION, StageStatus.IN_PROGRESS, 1);

        var gate = ControlGates.from(List.of(foundation)).getFirst();

        assertThat(gate.key()).isEqualTo("FOUNDATION");
        assertThat(gate.status()).isEqualTo(ControlGateStatus.WAITING_CONTRACTOR);
        assertThat(gate.stageId()).isEqualTo(foundation.id());
    }

    @Test
    @DisplayName("После публикации фото действие подрядчика по этапу исчезает")
    void publishedPhotoCompletesContractorAction() {
        var utilities = stage(StageWorkType.UTILITIES, StageStatus.IN_PROGRESS, 1);

        var gate = ControlGates.from(List.of(utilities), id -> id.equals(utilities.id())).get(1);

        assertThat(gate.status()).isEqualTo(ControlGateStatus.COMPLETED);
        assertThat(gate.action()).isEqualTo("Подтверждение добавлено");
    }

    @Test
    @DisplayName("После завершения полного плана передача дома ждёт заказчика")
    void completedPlanWaitsForCustomerAtHandover() {
        var foundation = stage(StageWorkType.FOUNDATION, StageStatus.WORK_COMPLETED, 1);
        var utilities = stage(StageWorkType.UTILITIES, StageStatus.WORK_COMPLETED, 2);

        var gate = ControlGates.from(List.of(foundation, utilities)).getLast();

        assertThat(gate.key()).isEqualTo("HANDOVER");
        assertThat(gate.status()).isEqualTo(ControlGateStatus.WAITING_CUSTOMER);
        assertThat(gate.stageId()).isEqualTo(utilities.id());
    }

    private StageView stage(StageWorkType type, StageStatus status, int position) {
        return new StageView(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                position,
                type.name(),
                type,
                status,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0);
    }
}
