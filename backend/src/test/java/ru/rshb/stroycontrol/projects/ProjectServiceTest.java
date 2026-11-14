package ru.rshb.stroycontrol.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.rshb.stroycontrol.accounts.AccountRole;
import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.accounts.Accounts;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.organizations.OrganizationView;
import ru.rshb.stroycontrol.organizations.Organizations;

import java.util.Optional;
import java.util.UUID;

class ProjectServiceTest {
    final ProjectRepository repository = mock(ProjectRepository.class);
    final Accounts accounts = mock(Accounts.class);
    final Organizations organizations = mock(Organizations.class);
    final ProjectAuthorization authorization =
            new ProjectAuthorization(
                    repository, accounts, organizations, mock(ProjectTeamRepository.class));
    final ProjectService service = new ProjectService(authorization, mock(RequestService.class));
    final AccountView owner =
            new AccountView(UUID.randomUUID(), "owner@example.test", AccountRole.CUSTOMER);
    final AccountView builder =
            new AccountView(UUID.randomUUID(), "builder@example.test", AccountRole.CONTRACTOR);
    HouseProject project;

    @BeforeEach
    void setUp() {
        project = new HouseProject("Дом", "Адрес", owner.id());
        var route = mock(ProjectRepository.Routing.class);
        when(route.getCustomerId()).thenReturn(owner.id());
        when(route.getOrganizationId()).thenAnswer(call -> project.getContractorOrganizationId());
        when(route.getContractorId()).thenAnswer(call -> project.getContractorId());
        when(repository.routing(project.getId())).thenReturn(Optional.of(route));
        when(repository.findById(project.getId())).thenReturn(Optional.of(project));
        when(repository.lockById(project.getId())).thenReturn(Optional.of(project));
        when(organizations.assignable(builder.id()))
                .thenReturn(
                        new OrganizationView(
                                UUID.randomUUID(),
                                builder.id(),
                                "LEGAL_ENTITY",
                                "Компания",
                                "Компания",
                                "9000000018",
                                "Адрес",
                                null,
                                0));
    }

    @Test
    @DisplayName("Подрядчик не может создать карточку и репозиторий не вызывается")
    void contractorCannotCreate() {
        assertThatThrownBy(() -> service.create(builder, "Дом", "Адрес"))
                .isInstanceOf(ApiException.class)
                .extracting("error")
                .isEqualTo(ApiError.FORBIDDEN);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Чужая карточка скрывается до поиска подрядчика")
    void inaccessibleAssignment() {
        when(repository.routing(project.getId())).thenReturn(Optional.empty());
        assertAssignmentFails(builder, owner)
                .isInstanceOf(ApiException.class)
                .extracting("error")
                .isEqualTo(ApiError.PROJECT_NOT_FOUND);
        verifyNoInteractions(accounts);
    }

    @Test
    @DisplayName("Назначенный подрядчик получает 403 при попытке изменить назначение")
    void contractorCannotAssign() {
        project.assign(builder.id());
        project.submitRequest();
        when(repository.lockAccessible(project.getId(), builder.id()))
                .thenReturn(Optional.of(project));
        assertAssignmentFails(builder, owner)
                .isInstanceOf(ApiException.class)
                .extracting("error")
                .isEqualTo(ApiError.FORBIDDEN);
        verifyNoInteractions(accounts);
    }

    @Test
    @DisplayName("Повтор назначения сохраняет подрядчика, другой подрядчик отклоняется")
    void idempotentAssignment() {
        when(repository.lockAccessible(project.getId(), owner.id()))
                .thenReturn(Optional.of(project));
        when(accounts.findContractor(builder.email())).thenReturn(Optional.of(builder));
        assertThat(service.assign(owner, project.getId(), builder.email()).contractorId())
                .isEqualTo(builder.id());
        assertThat(service.assign(owner, project.getId(), builder.email()).contractorId())
                .isEqualTo(builder.id());
        var second =
                new AccountView(UUID.randomUUID(), "second@example.test", AccountRole.CONTRACTOR);
        when(accounts.findContractor(second.email())).thenReturn(Optional.of(second));
        assertAssignmentFails(owner, second)
                .isInstanceOf(ApiException.class)
                .extracting("error")
                .isEqualTo(ApiError.CONTRACTOR_ALREADY_ASSIGNED);
        assertThat(project.getContractorId()).isEqualTo(builder.id());
    }

    @Test
    @DisplayName("Отсутствующий подрядчик не меняет назначение")
    void missingContractor() {
        when(repository.lockAccessible(project.getId(), owner.id()))
                .thenReturn(Optional.of(project));
        when(accounts.findContractor(builder.email())).thenReturn(Optional.empty());
        assertAssignmentFails(owner, builder)
                .isInstanceOf(ApiException.class)
                .extracting("error")
                .isEqualTo(ApiError.CONTRACTOR_NOT_FOUND);
        assertThat(project.getContractorId()).isNull();
    }

    /** Изолирует изменение назначения от вычисления аргументов проверяемого вызова. */
    private AbstractThrowableAssert<?, ? extends Throwable> assertAssignmentFails(
            AccountView actor, AccountView target) {
        var projectId = project.getId();
        var email = target.email();
        return assertThatThrownBy(() -> service.assign(actor, projectId, email));
    }
}
