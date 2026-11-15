package ru.rshb.stroycontrol.common;

/**
 * Формирует диагностику исключений без сообщений, содержащих пользовательские или внешние данные.
 */
public final class SafeDiagnostics {
    /** Закрывает создание статического помощника. */
    private SafeDiagnostics() {}

    /**
     * Копирует типы и кадры стека, исключая сообщения и suppressed-исключения. Глубина причин
     * ограничена восемью для защиты от циклических цепочек.
     *
     * @param exception исходное техническое исключение
     * @return безопасная диагностическая копия для SLF4J
     */
    public static Throwable exception(Throwable exception) {
        return copy(exception, 0);
    }

    /**
     * Ограничивает обход причин и сохраняет место возникновения сбоя.
     *
     * @param source исходная причина
     * @param depth текущая глубина
     * @return копия с безопасным сообщением, содержащим только имя типа
     */
    private static Throwable copy(Throwable source, int depth) {
        var result = new Throwable(source.getClass().getName());
        result.setStackTrace(source.getStackTrace());
        if (source.getCause() != null && depth < 7) {
            result.initCause(copy(source.getCause(), depth + 1));
        }
        return result;
    }
}
