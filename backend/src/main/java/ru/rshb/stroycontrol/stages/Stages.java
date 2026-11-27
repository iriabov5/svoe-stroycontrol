package ru.rshb.stroycontrol.stages;

import org.springframework.transaction.IllegalTransactionStateException;

import ru.rshb.stroycontrol.common.ApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Публичная граница создания плана из согласованного предложения. */
public interface Stages {
    /**
     * Возвращает полный актуальный план для сводки приёмки.
     *
     * @param projectId проект
     * @param actorId участник
     * @return этапы по порядку
     */
    List<StageView> plan(UUID projectId, UUID actorId);

    /**
     * Создаёт исполнительные этапы из плана согласованной версии сметы.
     *
     * <p>Вызывающий модуль предварительно проверяет решение владельца и удерживает блокировку
     * проекта. План сохраняется одной транзакцией с решением по смете; частичная установка
     * невозможна. Повторная установка в непустой план отклоняется.
     *
     * @param projectId проект
     * @param plan полный проверенный план в порядке выполнения работ
     * @throws ApiException с INVALID_PROPOSAL_STATE, если исполнительный план уже существует
     * @throws IllegalTransactionStateException при отсутствии активной транзакции
     */
    void install(UUID projectId, List<ApprovedStage> plan);

    /**
     * Проверяет принадлежность этапа и доступ участника без выдачи сущности.
     *
     * @param projectId проект
     * @param stageId этап
     * @param actorId аккаунт
     * @return текущая модель этапа
     * @throws ApiException если этап или проект недоступны
     */
    StageView requireParticipant(UUID projectId, UUID stageId, UUID actorId);

    /**
     * Исходный этап согласованного пакета.
     *
     * @param id источник
     * @param name название
     * @param workType вид
     * @param result результат
     * @param start начало
     * @param finish окончание
     */
    record ApprovedStage(
            UUID id,
            String name,
            StageWorkType workType,
            String result,
            LocalDate start,
            LocalDate finish) {}

    /**
     * Читает планы уже авторизованного набора в активной транзакции.
     *
     * @param projects карточки, проверенные вызывающим модулем
     * @return этапы всех карточек, отсортированные внутри плана
     */
    List<StageView> plans(Set<UUID> projects);
}
