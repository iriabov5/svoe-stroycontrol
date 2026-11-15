package ru.rshb.stroycontrol.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rshb.stroycontrol.acceptance.Acceptance;
import ru.rshb.stroycontrol.common.CommandFingerprint;
import ru.rshb.stroycontrol.files.Files;
import ru.rshb.stroycontrol.projects.MortgageRequests;
import ru.rshb.stroycontrol.projects.Projects;
import ru.rshb.stroycontrol.proposals.AgreedDocuments;
import ru.rshb.stroycontrol.stages.Stages;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Проверяет неизвестный исход доставки без HTTP и прикладной базы данных. */
class BankStageDeliveryTest {
    @ParameterizedTest(name = "Ответ банка: {0}")
    @NullSource
    @ValueSource(strings = {"BROKEN", "THROWS"})
    @DisplayName("Пустая квитанция или технический сбой оставляют пакет для безопасного повтора")
    void invalidReceiptRemainsUnknown(String state) {
        var repository = mock(BankStageRepository.class);
        var attempts = mock(BankStageAttemptRepository.class);
        var gateway = mock(BankStageGateway.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var transaction = new TransactionTemplate(manager);
        var clock = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC);
        var row =
                BankStageSubmission.builder()
                        .projectId(UUID.randomUUID())
                        .actorId(UUID.randomUUID())
                        .build();
        var route = mock(BankStageRepository.Route.class);
        when(route.getProjectId()).thenReturn(row.getProjectId());
        when(route.getActorId()).thenReturn(row.getActorId());
        when(repository.due(any(), any())).thenReturn(List.of(row.getId()));
        when(repository.route(row.getId())).thenReturn(Optional.of(route));
        when(repository.findById(row.getId())).thenReturn(Optional.of(row));
        var attempt = BankStageAttempt.builder().submissionId(row.getId()).attemptNumber(1).build();
        when(attempts.findBySubmissionIdAndAttemptNumber(row.getId(), 1))
                .thenReturn(Optional.of(attempt));
        BankStageGateway.Receipt receipt =
                state == null ? null : new BankStageGateway.Receipt(state, "invalid", null);
        if ("THROWS".equals(state)) {
            when(gateway.deliver(row.getBankKey(), 0, null))
                    .thenThrow(new IllegalStateException("secret-bank-response"));
        } else {
            when(gateway.deliver(row.getBankKey(), 0, null)).thenReturn(receipt);
        }
        var service =
                new BankStageService(
                        repository,
                        attempts,
                        mock(Projects.class),
                        mock(Stages.class),
                        mock(Acceptance.class),
                        mock(AgreedDocuments.class),
                        mock(MortgageRequests.class),
                        mock(ConstructionBankCases.class),
                        mock(Files.class),
                        gateway,
                        mock(CommandFingerprint.class),
                        transaction,
                        clock);
        service.work();
        assertThat(row.getState()).isEqualTo("UNKNOWN");
        assertThat(row.getBankReference()).isNull();
        assertThat(row.getLeaseToken()).isNull();
        assertThat(row.getAttemptCount()).isEqualTo(1);
        assertThat(attempt.getOutcome()).isEqualTo("UNKNOWN");
    }
}
