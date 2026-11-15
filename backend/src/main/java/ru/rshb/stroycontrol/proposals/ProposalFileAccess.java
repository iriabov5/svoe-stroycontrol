package ru.rshb.stroycontrol.proposals;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.projects.Projects;

import java.util.UUID;

/**
 * Читает только права и связи версий; не зависит от файлового сервиса и не создаёт цикл модулей.
 */
@Service
@RequiredArgsConstructor
class ProposalFileAccess implements ProposalFiles {
    private final Projects projects;
    private final ProposalRepository proposals;
    private final ProposalImageRepository images;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public void requireUpload(UUID projectId, UUID actorId) {
        var access = projects.requireParticipant(projectId, actorId);
        if (!access.canPrepareDocuments()) throw new ApiException(ApiError.FORBIDDEN);
        if (access.preparationClosed()) throw new ApiException(ApiError.PREPARATION_CLOSED);
        if (!proposals.existsByProjectIdAndState(projectId, "DRAFT")) {
            throw new ApiException(ApiError.INVALID_PROPOSAL_STATE);
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public boolean canRead(UUID projectId, UUID actorId, UUID attachmentId, UUID uploaderId) {
        var access = projects.requireParticipant(projectId, actorId);
        return images.hasVisibleImage(projectId, attachmentId, access.canPrepareDocuments())
                || (access.canPrepareDocuments() && actorId.equals(uploaderId));
    }
}
