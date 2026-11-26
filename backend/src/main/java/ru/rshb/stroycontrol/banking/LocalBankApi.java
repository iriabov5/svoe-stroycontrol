package ru.rshb.stroycontrol.banking;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Локальный HTTP Mock API: отдаёт только синтетическое решение и не обращается в банк. */
@Component
@RequiredArgsConstructor
public class LocalBankApi {
    private final ObjectMapper mapper;
    private HttpServer server;

    @Value("${bank.demo.scenario:FOUND}")
    private volatile String scenario;

    @Value("${bank.plot.scenario:NORMAL}")
    private volatile String plotScenario;

    private final AtomicLong plotRequests = new AtomicLong();

    /**
     * Открывает локальный HTTP Mock API после внедрения конфигурации Spring.
     *
     * <p>Свободный порт выбирается только на loopback-интерфейсе. Ответ сериализуется общим
     * JSON-маппером; приложение не обращается в банк и не использует его учётные данные. Ошибка
     * открытия порта прерывает создание bean, чтобы не публиковать неработающий адаптер.
     *
     * @throws IOException если локальный HTTP-порт не удалось открыть
     */
    @PostConstruct
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/approval",
                exchange -> {
                    String body;
                    int status = 200;
                    var today = LocalDate.now(ZoneId.of("Europe/Moscow"));
                    String currentScenario = scenario;
                    switch (currentScenario) {
                        case "NOT_FOUND" ->
                                body = mapper.writeValueAsString(Map.of("status", "NOT_FOUND"));
                        case "EXPIRED", "FOUND" -> {
                            boolean expired = currentScenario.equals("EXPIRED");
                            body =
                                    mapper.writeValueAsString(
                                            Map.of(
                                                    "status",
                                                    currentScenario,
                                                    "program",
                                                    "Сельская ипотека РСХБ",
                                                    "amount",
                                                    3000000,
                                                    "termMonths",
                                                    240,
                                                    "rate",
                                                    3,
                                                    "decisionDate",
                                                    today.minusDays(10),
                                                    "validUntil",
                                                    expired
                                                            ? today.minusDays(1)
                                                            : today.plusDays(80)));
                        }
                        default -> {
                            body = "{}";
                            status = 503;
                        }
                    }
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders()
                            .set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(status, bytes.length);
                    try (var stream = exchange.getResponseBody()) {
                        stream.write(bytes);
                    }
                });
        server.createContext("/plot-check", this::checkPlot);
        server.start();
    }

    /**
     * Обслуживает только синтетическую проверку участка на существующем loopback-сервере.
     *
     * @param exchange локальный HTTP-запрос проверки
     * @throws IOException при ошибке записи ответа
     */
    private void checkPlot(HttpExchange exchange) throws IOException {
        plotRequests.incrementAndGet();
        String currentScenario = plotScenario;
        if ("SLOW".equals(currentScenario)) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }
        int status = "ERROR".equals(currentScenario) ? 503 : 200;
        String query = exchange.getRequestURI().getRawQuery();
        String number =
                query != null && query.startsWith("number=")
                        ? URLDecoder.decode(query.substring(7), StandardCharsets.UTF_8)
                        : "";
        byte[] bytes =
                ("INVALID".equals(currentScenario)
                                ? "{}"
                                : mapper.writeValueAsString(PlotDemoData.result(number)))
                        .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    /**
     * Возвращает loopback-адрес локального Mock API.
     *
     * <p>Адрес учитывает IPv4 или IPv6 и случайный свободный порт; внешних запросов к банку нет.
     *
     * @return URL локального HTTP-адаптера
     */
    String url() {
        return "http://%s:%d/approval"
                .formatted(
                        server.getAddress().getAddress() instanceof Inet6Address
                                ? "[::1]"
                                : "127.0.0.1",
                        server.getAddress().getPort());
    }

    /**
     * Выбирает синтетический сценарий для локального стенда и интеграционных проверок.
     *
     * @param scenario FOUND, NOT_FOUND, EXPIRED или ERROR
     */
    public void scenario(String scenario) {
        this.scenario = scenario;
    }

    /**
     * Возвращает число обращений к демоисточнику для диагностики и проверки безопасного повтора.
     *
     * @return счётчик начатых HTTP-запросов проверки участка
     */
    public long plotRequests() {
        return plotRequests.get();
    }

    /**
     * Выбирает воспроизводимый сценарий проверки участка для стенда и тестов.
     *
     * @param scenario NORMAL, ERROR, INVALID либо SLOW
     */
    public void plotScenario(String scenario) {
        this.plotScenario = scenario;
    }

    /**
     * Возвращает локальный маршрут проверки участка на том же сервере.
     *
     * @return loopback URL без банковских учётных данных
     */
    String plotUrl() {
        return url().replace("/approval", "/plot-check");
    }

    /** Останавливает локальный HTTP-сервер при завершении Spring-контекста. */
    @PreDestroy
    void close() {
        if (server != null) server.stop(0);
    }
}
