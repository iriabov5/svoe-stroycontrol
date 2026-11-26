package ru.rshb.stroycontrol.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import ru.rshb.stroycontrol.accounts.AccountRole;
import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.journal.Journal;
import ru.rshb.stroycontrol.projects.ProjectAccess;
import ru.rshb.stroycontrol.projects.Projects;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class StageServiceTest {
    final StageRepository repository = mock(StageRepository.class);
    final Projects projects = mock(Projects.class);
    final Journal journal = mock(Journal.class);
    final ObjectProvider<Journal> journals = mock(ObjectProvider.class);
    final Clock clock =
            Clock.fixed(Instant.parse("2026-11-12T22:30:00Z"), ZoneId.of("Europe/Moscow"));
    final StageService service = new StageService(repository, projects, journals, clock);
    final AccountView actor =
            new AccountView(UUID.randomUUID(), "actor@example.test", AccountRole.CUSTOMER);
    final UUID projectId = UUID.randomUUID();
    final StagePlan plan = StagePlan.parse("Дом", StageWorkType.OTHER, null, null);

    @Test
    @DisplayName("Подрядчик не может добавлять этапы в доступный проект")
    void contractorCannotCreate() {
        when(projects.lockParticipant(projectId, actor.id()))
                .thenReturn(new ProjectAccess(projectId, false, true));
        assertThatThrownBy(() -> service.create(actor, projectId, plan))
                .isInstanceOf(ApiException.class)
                .extracting("error")
                .isEqualTo(ApiError.FORBIDDEN);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Проверка участия предшествует чтению этапов")
    void accessBeforeData() {
        when(projects.requireParticipant(projectId, actor.id()))
                .thenThrow(new ApiException(ApiError.PROJECT_NOT_FOUND));
        assertThatThrownBy(() -> service.list(actor, projectId)).isInstanceOf(ApiException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Заказчик не выполняет команды подрядчика даже при повторе")
    void ownerCannotStart() {
        when(projects.lockParticipant(projectId, actor.id()))
                .thenReturn(new ProjectAccess(projectId, true, false));
        var stage = ConstructionStage.create(projectId, 1, plan);
        stage.start(0L, LocalDate.now(clock));
        when(repository.findByIdAndProjectId(stage.getId(), projectId))
                .thenReturn(Optional.of(stage));
        var stageId = stage.getId();
        assertThatThrownBy(() -> service.start(actor, projectId, stageId, 0L))
                .isInstanceOf(ApiException.class)
                .extracting("error")
                .isEqualTo(ApiError.FORBIDDEN);
        verify(repository, never()).flush();
    }

    @Test
    @DisplayName("Дата начала берётся из внедрённых часов в зоне Москвы")
    void injectedClock() {
        when(projects.lockParticipant(projectId, actor.id()))
                .thenReturn(new ProjectAccess(projectId, false, true));
        var stage = ConstructionStage.create(projectId, 1, plan);
        when(repository.findByIdAndProjectId(stage.getId(), projectId))
                .thenReturn(Optional.of(stage));
        assertThat(service.start(actor, projectId, stage.getId(), 0L).actualStart())
                .isEqualTo(LocalDate.of(2026, 11, 13));
    }

    @Test
    @DisplayName("Повтор ID в перестановке отклоняется до изменения позиций")
    void invalidOrder() {
        when(projects.lockParticipant(projectId, actor.id()))
                .thenReturn(new ProjectAccess(projectId, true, false));
        var first = ConstructionStage.create(projectId, 1, plan);
        var second = ConstructionStage.create(projectId, 2, plan);
        when(repository.findByProjectIdOrderByPositionAsc(projectId))
                .thenReturn(List.of(first, second));
        var duplicatePlan =
                List.of(new StageVersion(first.getId(), 0L), new StageVersion(first.getId(), 0L));
        assertThatThrownBy(() -> service.reorder(actor, projectId, duplicatePlan))
                .isInstanceOf(ApiException.class)
                .extracting("error")
                .isEqualTo(ApiError.VALIDATION_ERROR);
        assertThat(first.getPosition()).isEqualTo(1);
        assertThat(second.getPosition()).isEqualTo(2);
        verify(repository, never()).flush();
    }
}
