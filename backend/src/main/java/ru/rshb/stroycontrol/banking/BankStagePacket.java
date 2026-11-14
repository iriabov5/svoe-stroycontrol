package ru.rshb.stroycontrol.banking;

import ru.rshb.stroycontrol.acceptance.SubmissionView;
import ru.rshb.stroycontrol.files.AttachmentView;
import ru.rshb.stroycontrol.projects.Projects;
import ru.rshb.stroycontrol.proposals.AgreedDocuments;
import ru.rshb.stroycontrol.stages.StageView;

import java.util.List;
import java.util.UUID;

/**
 * Неизменяемый пакет синтетического подтверждения; байты файлов остаются в закрытом бакете.
 *
 * @param project сведения о доме
 * @param documents согласованные смета и договор
 * @param stage плановые и фактические даты этапа
 * @param acceptance принятое решение с подтверждениями
 * @param creditCaseId синтетическое дело
 * @param additionalFiles накопленные банковские дополнения
 * @param clarifications история пояснений владельца
 */
public record BankStagePacket(
        Projects.Info project,
        AgreedDocuments.Snapshot documents,
        StageView stage,
        SubmissionView acceptance,
        UUID creditCaseId,
        List<AttachmentView> additionalFiles,
        List<String> clarifications) {}
