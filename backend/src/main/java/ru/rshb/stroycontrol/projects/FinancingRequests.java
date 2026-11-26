package ru.rshb.stroycontrol.projects;

import ru.rshb.stroycontrol.common.ApiException;

import java.util.UUID;

/** Открывает банковскому планированию сведения своей заявки без передачи сущностей JPA. */
public interface FinancingRequests {
    /**
     * Читает сведения только владельцу; не создаёт отсутствующий черновик LEGACY.
     *
     * @param projectId карточка
     * @param actorId текущий аккаунт
     * @return номер, версия и доступность редактирования
     * @throws ApiException с PROJECT_NOT_FOUND для другого аккаунта
     */
    Context financingContext(UUID projectId, UUID actorId);

    /**
     * Блокирует проект в активной транзакции и повторно проверяет владельца.
     *
     * @param projectId карточка
     * @param actorId текущий аккаунт
     * @return актуальные сведения под общей блокировкой до commit
     */
    Context lockFinancingContext(UUID projectId, UUID actorId);

    /**
     * Снимок заявки для планирования; номер nullable, если не задан.
     *
     * @param cadastralNumber сохранённый номер
     * @param requestVersion версия заявки, 0 при её отсутствии
     * @param editable есть открытый черновик незавершённого проекта
     */
    record Context(String cadastralNumber, long requestVersion, boolean editable) {}
}
