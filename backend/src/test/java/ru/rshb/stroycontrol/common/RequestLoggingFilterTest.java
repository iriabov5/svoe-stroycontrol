package ru.rshb.stroycontrol.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Проверяет уровни, конфиденциальность, error dispatch и изоляцию контекста журнала запросов. */
class RequestLoggingFilterTest {
    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> events =
            new ListAppender<>() {
                @Override
                protected void append(ILoggingEvent event) {
                    event.prepareForDeferredProcessing();
                    super.append(event);
                }
            };
    private Level previousLevel;

    /** Подключает локальный сборщик без изменения глобального уровня приложения. */
    @BeforeEach
    void capture() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        events.start();
        logger.addAppender(events);
    }

    /** Освобождает сборщик и проверяет отсутствие оставленного MDC. */
    @AfterEach
    void release() {
        logger.detachAppender(events);
        logger.setLevel(previousLevel);
        events.stop();
        assertThat(MDC.get("requestId")).isNull();
    }

    @ParameterizedTest(name = "{0} → {1}, уровень {2}")
    @CsvSource({
        "/api/v1/projects,200,INFO",
        "/api/v1/projects,201,INFO",
        "/api/v1/projects,400,INFO",
        "/api/v1/projects,401,INFO",
        "/api/v1/projects,403,INFO",
        "/api/v1/projects,409,INFO",
        "/api/v1/projects,503,ERROR",
        "/actuator/health,200,DEBUG",
        "/actuator/health,503,ERROR",
        "/api/v1/auth/csrf,200,DEBUG",
        "/api/v1/auth/csrf,403,INFO"
    })
    @DisplayName("Итог запроса использует согласованный уровень и фактический статус")
    void severity(String route, int status, String level) throws Exception {
        var request = new MockHttpServletRequest("GET", route);
        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (input, output) -> {
                    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, route);
                    ((MockHttpServletResponse) output).setStatus(status);
                });
        assertThat(events.list).hasSize(1);
        var event = events.list.getFirst();
        assertThat(event.getLevel()).hasToString(level);
        assertThat(event.getFormattedMessage()).contains("статус=" + status, "времяМс=");
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getMDCPropertyMap().get("requestId")).isNotBlank();
    }

    @Test
    @DisplayName("Отказ до MVC записывает публичный код и исключает URI, query, заголовки и тело")
    void rejectedRequestIsSafe() throws Exception {
        var request = new MockHttpServletRequest("POST", "/secret-user-data");
        request.setQueryString("token=secret-token");
        request.addHeader("Cookie", "session=secret-cookie");
        request.setContent("secret-password bank-amount file-name caption".getBytes());
        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (input, output) -> {
                    request.setAttribute(
                            RequestLoggingFilter.ERROR_ATTRIBUTE,
                            ApiError.INVALID_CSRF_TOKEN.name());
                    ((MockHttpServletResponse) output).setStatus(403);
                });
        var event = events.list.getFirst();
        assertThat(event.getFormattedMessage())
                .contains("INVALID_CSRF_TOKEN", "статус=403")
                .doesNotContain("secret", "password", "bank-amount", "file-name", "caption");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("Необработанная ошибка ждёт error dispatch и записывает единственный итог 500")
    void errorDispatchLogsFinalStatusOnce() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/projects/secret-id");
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() ->
                        filter.doFilter(
                                request,
                                response,
                                (input, output) -> {
                                    request.setAttribute(
                                            HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                                            "/api/v1/projects/{id}");
                                    throw new ServletException(
                                            "secret-password",
                                            new IllegalStateException("secret-token"));
                                }))
                .isInstanceOf(ServletException.class);
        assertThat(events.list).isEmpty();
        request.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(request, response, (input, output) -> response.setStatus(500));
        filter.doFilter(request, response, (input, output) -> response.setStatus(500));
        assertThat(events.list).hasSize(2);
        assertThat(events.list.getFirst().getFormattedMessage())
                .contains("статус=500", "/api/v1/projects/{id}")
                .doesNotContain("secret");
        assertThat(events.list.get(1).getThrowableProxy().getMessage())
                .isEqualTo(ServletException.class.getName());
        assertThat(events.list.get(1).getThrowableProxy().getCause().getMessage())
                .isEqualTo(IllegalStateException.class.getName());
    }

    @Test
    @DisplayName("Последовательные запросы в одном потоке имеют независимые идентификаторы")
    void contextDoesNotLeakBetweenRequests() throws Exception {
        for (int index = 0; index < 2; index++) {
            filter.doFilter(
                    new MockHttpServletRequest("GET", "/api/v1/projects"),
                    new MockHttpServletResponse(),
                    (input, output) -> assertThat(MDC.get("requestId")).isNotBlank());
            assertThat(MDC.get("requestId")).isNull();
        }
        assertThat(events.list.getFirst().getMDCPropertyMap())
                .doesNotContainEntry(
                        "requestId", events.list.get(1).getMDCPropertyMap().get("requestId"));
    }

    @Test
    @DisplayName("Диагностика сохраняет типы и кадры, исключая секреты из сообщений и suppressed")
    void diagnosticCopiesOnlyTypesAndFrames() {
        var cause = new IllegalArgumentException("secret-token");
        var failure = new IllegalStateException("secret-password", cause);
        failure.addSuppressed(new RuntimeException("secret-file-name"));
        var safe = SafeDiagnostics.exception(failure);
        var text = new StringWriter();
        safe.printStackTrace(new PrintWriter(text));
        assertThat(safe.getStackTrace()).containsExactly(failure.getStackTrace());
        assertThat(text.toString())
                .contains(
                        IllegalStateException.class.getName(),
                        IllegalArgumentException.class.getName())
                .doesNotContain("secret");
        assertThat(failure.getMessage()).isEqualTo("secret-password");
    }
}
