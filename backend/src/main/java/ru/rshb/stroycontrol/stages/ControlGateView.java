package ru.rshb.stroycontrol.stages;

import ru.rshb.stroycontrol.accounts.AccountRole;

import java.util.UUID;

/**
 * Read-only подсказка участнику перед необратимым действием на стройке.
 *
 * <p>Карточка не подтверждает соответствие работ проекту или нормам: она только показывает
 * ответственную сторону, следующий шаг и существующий экран, где участник может проверить
 * доказательства или продолжить процесс.
 *
 * @param key стабильный ключ контрольной точки
 * @param status вычисленное состояние
 * @param responsibleRole роль участника, от которого ожидается следующее действие
 * @param title короткое назначение контрольной точки
 * @param action понятное следующее действие
 * @param detail причина текущего состояния
 * @param stageId связанный этап либо {@code null}, если его нет в плане
 */
record ControlGateView(
        String key,
        ControlGateStatus status,
        AccountRole responsibleRole,
        String title,
        String action,
        String detail,
        UUID stageId) {}
