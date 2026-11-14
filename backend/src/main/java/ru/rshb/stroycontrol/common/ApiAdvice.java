package ru.rshb.stroycontrol.common;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import ru.rshb.stroycontrol.common.ApiProblems.Problem;

/** Преобразует ожидаемые ошибки операций и форматов запроса в REST-контракт. */
@RestControllerAdvice
@RequiredArgsConstructor
class ApiAdvice {
    private final ApiProblems problems;

    /**
     * Возвращает понятный предел файла, когда multipart отклонён до контроллера.
     *
     * @return HTTP 413 без внутренних данных
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Problem> tooLarge() {
        return response(ApiError.ATTACHMENT_TOO_LARGE);
    }

    /**
     * Передаёт ожидаемую ошибку операции в формате problem+json.
     *
     * <p>Внутренний стек и детали хранения не попадают в ответ.
     *
     * @param exception перехваченная ошибка обработки запроса
     * @return problem+json с HTTP-статусом операции
     */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Problem> operation(ApiException exception) {
        var error = exception.getError();
        return ResponseEntity.status(error.status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problems.body(exception));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    /**
     * Возвращает общую ошибку валидации для нечитаемого тела или неверного типа параметра.
     *
     * @param exception перехваченная ошибка обработки запроса
     * @return HTTP 400 с кодом VALIDATION_ERROR
     */
    ResponseEntity<Problem> invalidRequest(Exception exception) {
        return response(ApiError.VALIDATION_ERROR);
    }

    /**
     * Отклоняет запрос с форматом тела, отличным от поддерживаемого JSON.
     *
     * @return HTTP 415 с кодом UNSUPPORTED_MEDIA_TYPE
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Problem> unsupportedMediaType() {
        return response(ApiError.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * Преобразует конфликт оптимистической блокировки этапа в публичный код версии.
     *
     * @return HTTP 409 с кодом STALE_STAGE_VERSION
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Problem> staleVersion() {
        return response(ApiError.STALE_STAGE_VERSION);
    }

    /**
     * Формирует problem+json с HTTP-статусом стабильной публичной ошибки.
     *
     * @param error стабильный публичный код и HTTP-статус ошибки
     * @return HTTP-ответ problem+json без внутренних данных исключения
     */
    private ResponseEntity<Problem> response(ApiError error) {
        return ResponseEntity.status(error.status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problems.body(error));
    }
}
