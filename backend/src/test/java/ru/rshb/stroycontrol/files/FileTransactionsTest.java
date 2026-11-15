package ru.rshb.stroycontrol.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.rshb.stroycontrol.projects.MortgageRequests;
import ru.rshb.stroycontrol.projects.ProjectAccess;
import ru.rshb.stroycontrol.projects.Projects;
import ru.rshb.stroycontrol.proposals.ProposalFiles;
import ru.rshb.stroycontrol.stages.StageStatus;
import ru.rshb.stroycontrol.stages.StageView;
import ru.rshb.stroycontrol.stages.Stages;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

class FileTransactionsTest {
    @Test
    @DisplayName("Повтор старой загрузки продлевает срок и исключает объект из текущей уборки")
    void resumedUploadIsNotCleaned() {
        var now = Instant.parse("2026-11-14T09:00:00Z");
        var repository = mock(AttachmentRepository.class);
        var projects = mock(Projects.class);
        var stages = mock(Stages.class);
        var service =
                new FileTransactions(
                        repository,
                        projects,
                        stages,
                        mock(MortgageRequests.class),
                        Clock.fixed(now, ZoneOffset.UTC),
                        mock(ProposalFiles.class));
        var project = UUID.randomUUID();
        var actor = UUID.randomUUID();
        var stage = UUID.randomUUID();
        var key = UUID.randomUUID();
        var hash = "abc";
        var fingerprint =
                FileInput.sha256(
                        (hash + ":" + stage + ":JOURNAL:photo.png:Фото")
                                .getBytes(StandardCharsets.UTF_8));
        var file =
                Attachment.builder()
                        .projectId(project)
                        .actorId(actor)
                        .stageId(stage)
                        .idempotencyKey(key)
                        .name("photo.png")
                        .caption("Фото")
                        .requestHash(fingerprint)
                        .objectKey("private/temp")
                        .createdAt(now.minusSeconds(172800))
                        .uploadStartedAt(now.minusSeconds(172800))
                        .build();
        when(projects.lockParticipant(project, actor))
                .thenReturn(new ProjectAccess(project, false, true));
        var view = mock(StageView.class);
        when(view.status()).thenReturn(StageStatus.IN_PROGRESS);
        when(stages.requireParticipant(project, stage, actor)).thenReturn(view);
        when(repository.findByActorIdAndProjectIdAndIdempotencyKey(actor, project, key))
                .thenReturn(Optional.of(file));
        when(repository.lockById(file.getId())).thenReturn(Optional.of(file));
        service.prepare(
                new UploadScope(actor, project, stage, "JOURNAL", key),
                new FileTransactions.Metadata("photo.png", "Фото", "image/png", 10, hash));
        assertThat(file.getUploadStartedAt()).isEqualTo(now);
        assertThat(service.claimCleanup(file.getId())).isNull();
        assertThat(file.getState()).isEqualTo("UPLOADING");
    }
}
