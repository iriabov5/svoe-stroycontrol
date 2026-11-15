package ru.rshb.stroycontrol.common;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Записывает один итог HTTP-запроса, включая отказы безопасности и error dispatch. Тела, query,
 * заголовки и произвольные URI в журнал не копируются.
 */
@Slf4j
class RequestLoggingFilter extends OncePerRequestFilter {
    static final String ERROR_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".error";
    private static final String CONTEXT_ATTRIBUTE =
            RequestLoggingFilter.class.getName() + ".context";
    private static final String MDC_KEY = "requestId";
    private static final Set<String> METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> PUBLIC_ROUTES =
            Set.of(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/csrf",
                    "/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness");

    /** {@inheritDoc} */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected void doFilterNestedErrorDispatch(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        doFilterInternal(request, response, chain);
    }

    /**
     * Восстанавливает тот же контекст при error dispatch и освобождает MDC при любом исходе.
     * Необработанное исключение передаётся контейнеру, который устанавливает итоговый статус.
     *
     * @param request запрос браузера
     * @param response ответ сервера
     * @param chain последующая цепочка фильтров
     * @throws ServletException при ошибке обработки
     * @throws IOException при ошибке соединения
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var context = (Context) request.getAttribute(CONTEXT_ATTRIBUTE);
        if (context == null) {
            context = new Context();
            context.method = METHODS.contains(request.getMethod()) ? request.getMethod() : "OTHER";
            context.route =
                    PUBLIC_ROUTES.contains(request.getRequestURI())
                            ? request.getRequestURI()
                            : "маршрут не определён";
            request.setAttribute(CONTEXT_ATTRIBUTE, context);
        }
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, context.id);
        if (request.getDispatcherType() == DispatcherType.ERROR
                && context.failure == null
                && request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)
                        instanceof Throwable failure) {
            context.failure = failure;
        }
        boolean completedNormally = false;
        try {
            chain.doFilter(request, response);
            completedNormally = true;
        } catch (IOException | ServletException | RuntimeException exception) {
            context.failure = exception;
            throw exception;
        } finally {
            if (request.getDispatcherType() != DispatcherType.ERROR) {
                var pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                if (pattern != null) context.route = pattern.toString();
            }
            try {
                if (completedNormally || response.isCommitted()) {
                    complete(request, response, context);
                }
            } finally {
                if (previous == null) MDC.remove(MDC_KEY);
                else MDC.put(MDC_KEY, previous);
            }
        }
    }

    /**
     * Выбирает уровень по фактическому статусу и исключает повтор итога вложенным error dispatch.
     *
     * @param request запрос с публичным кодом ошибки
     * @param response окончательный ответ
     * @param context накопленные безопасные сведения
     */
    private void complete(
            HttpServletRequest request, HttpServletResponse response, Context context) {
        if (context.completed) return;
        context.completed = true;
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - context.started);
        int status = response.getStatus();
        Object code = request.getAttribute(ERROR_ATTRIBUTE);
        var level = status >= 500 ? Level.ERROR : Level.INFO;
        if (status < 400
                && (context.route.startsWith("/actuator/health")
                        || context.route.equals("/api/v1/auth/csrf"))) {
            level = Level.DEBUG;
        }
        log.atLevel(level)
                .log(
                        "HTTP-запрос завершён — метод={}, маршрут={}, статус={}, времяМс={}, код={}",
                        context.method,
                        context.route,
                        status,
                        elapsed,
                        code == null ? "—" : code);
        if (context.failure != null) {
            log.error(
                    "Не удалось обработать HTTP-запрос",
                    SafeDiagnostics.exception(context.failure));
        }
    }

    /** Хранит только технические сведения в пределах одной servlet-обработки. */
    private static final class Context {
        private final String id = UUID.randomUUID().toString();
        private final long started = System.nanoTime();
        private String method;
        private String route;
        private Throwable failure;
        private boolean completed;
    }
}
