package ru.rshb.stroycontrol.banking;

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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/** Локальный HTTP Mock API: отдаёт только синтетическое решение и не обращается в банк. */
@Component
@RequiredArgsConstructor
public class LocalBankApi {
    private final ObjectMapper mapper;
    private HttpServer server;

    @Value("${bank.demo.scenario:FOUND}")
    private volatile String scenario;

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
                        case "ERROR" -> {
                            body = "{}";
                            status = 503;
                        }
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
        server.start();
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

    /** Останавливает локальный HTTP-сервер при завершении Spring-контекста. */
    @PreDestroy
    void close() {
        if (server != null) server.stop(0);
    }
}
