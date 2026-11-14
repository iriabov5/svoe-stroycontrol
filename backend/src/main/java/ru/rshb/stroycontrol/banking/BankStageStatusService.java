package ru.rshb.stroycontrol.banking;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Даёт приёмке краткую сводку без зависимости от координации отправки и без кредитных сведений. */
@Service
@RequiredArgsConstructor
class BankStageStatusService implements BankStageStatus {
    private final BankStageRepository repository;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public String latest(UUID projectId, UUID stageId) {
        return repository.findByProjectIdAndStageIdOrderByRevisionDesc(projectId, stageId).stream()
                .findFirst()
                .map(BankStageSubmission::getState)
                .orElse(null);
    }
}
