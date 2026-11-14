package ru.rshb.stroycontrol.common;

import lombok.Getter;

import java.util.Map;

/** Ожидаемая ошибка операции, безопасная для передачи клиенту. */
@Getter
public final class ApiException extends RuntimeException {
    /** Публичная причина отклонения операции. */
    private final ApiError error;

    /** Ошибки полей без внутренних данных. */
    private final Map<String, String> fieldErrors;

    /**
     * Создаёт ожидаемую ошибку операции.
     *
     * @param error стабильный код и публичное описание ошибки
     */
    public ApiException(ApiError error) {
        super(error.title);
        this.error = error;
        this.fieldErrors = Map.of();
    }

    /**
     * Создаёт ошибку конкретного поля.
     *
     * @param field имя поля API
     * @param message подсказка для исправления
     */
    public ApiException(String field, String message) {
        super(message);
        this.error = ApiError.VALIDATION_ERROR;
        this.fieldErrors = Map.of(field, message);
    }
}
