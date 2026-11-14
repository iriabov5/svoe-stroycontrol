package ru.rshb.stroycontrol.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

/** Формирует единый problem+json для MVC и фильтров безопасности. */
@Component
@RequiredArgsConstructor
public class ApiProblems {
    /**
     * Поля problem+json, обязательные по OpenAPI.
     *
     * @param type тип ошибки, в текущем контракте {@code about:blank}
     * @param title безопасное описание для пользователя
     * @param status HTTP-статус ответа
     * @param fieldErrors сообщения по полям, отсутствуют у ошибок без привязки к полю
     * @param code стабильный код для клиента
     */
    public record Problem(
            String type,
            String title,
            int status,
            String code,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fieldErrors) {
        /**
         * Создаёт ошибку без уточнений по полям.
         *
         * @param type тип ошибки
         * @param title описание
         * @param status HTTP статус
         * @param code стабильный код
         */
        public Problem(String type, String title, int status, String code) {
            this(type, title, status, code, Map.of());
        }
    }

    /**
     * Описывает ошибку поля для клиента.
     *
     * @param exception публичная ошибка
     * @return безопасное описание и ошибки полей
     */
    public Problem body(ApiException exception) {
        var e = exception.getError();
        return new Problem("about:blank", e.title, e.status, e.name(), exception.getFieldErrors());
    }

    private final ObjectMapper mapper;

    /**
     * Создаёт описание ожидаемой ошибки без внутренних данных.
     *
     * @param error публичная ошибка
     * @return problem с кодом и HTTP-статусом
     */
    public Problem body(ApiError error) {
        return new Problem("about:blank", error.title, error.status, error.name());
    }

    /**
     * Записывает ошибку из фильтра до передачи управления MVC.
     *
     * @param response ответ браузеру
     * @param error публичная ошибка
     * @throws IOException при недоступности выходного потока
     */
    public void write(HttpServletResponse response, ApiError error) throws IOException {
        response.setStatus(error.status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), body(error));
    }
}
