package ru.rshb.stroycontrol.banking;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Локальный HTTP-банк принимает только синтетические метаданные; порт открыт исключительно на
 * loopback.
 */
@Component
@RequiredArgsConstructor
public class StageBankMockApi {
    private final MockStageStore store;
    private final ObjectMapper mapper;
    private HttpServer server;

    @Value("${bank.stage.scenario:ACCEPTED}")
    private volatile String scenario;

    /**
     * Открывает локальный обработчик получения и проверки пакета.
     *
     * @throws IOException если loopback-порт недоступен
     */
    @PostConstruct
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/stage-packets/",
                exchange -> {
                    try {
                        if (scenario.equals("ERROR")) {
                            exchange.sendResponseHeaders(503, -1);
                            return;
                        }
                        var key =
                                UUID.fromString(
                                        exchange.getRequestURI()
                                                .getPath()
                                                .substring("/stage-packets/".length()));
                        BankStageGateway.Receipt receipt;
                        if (exchange.getRequestMethod().equals("POST")) {
                            receipt = receive(exchange, key);
                            if (scenario.equals("TIMEOUT_AFTER_RECEIPT")) return;
                        } else if (exchange.getRequestMethod().equals("GET")) {
                            receipt = store.poll(key);
                            if (receipt == null) {
                                exchange.sendResponseHeaders(404, -1);
                                return;
                            }
                        } else {
                            exchange.sendResponseHeaders(405, -1);
                            return;
                        }
                        byte[] bytes = mapper.writeValueAsBytes(receipt);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    } catch (IllegalArgumentException _) {
                        exchange.sendResponseHeaders(409, -1);
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
    }

    /** Проверяет ограниченный синтетический пакет перед сохранением квитанции. */
    private BankStageGateway.Receipt receive(HttpExchange exchange, UUID key) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(2097153);
        if (bytes.length > 2097152) throw new IllegalArgumentException("payload too large");
        Map<String, Object> body = mapper.readValue(bytes, new TypeReference<>() {});
        if (!Boolean.TRUE.equals(body.get("isDemo")))
            throw new IllegalArgumentException("demo only");
        return store.receive(key, body, scenario);
    }

    /**
     * Адрес изолированного адаптера.
     *
     * @return loopback URL
     */
    String url() {
        return "http://%s:%d/stage-packets"
                .formatted(
                        server.getAddress().getAddress() instanceof Inet6Address
                                ? "[::1]"
                                : "127.0.0.1",
                        server.getAddress().getPort());
    }

    /**
     * Меняет только синтетический сценарий локального стенда и тестов.
     *
     * @param value ACCEPTED, NEEDS_INFORMATION, ERROR либо TIMEOUT_AFTER_RECEIPT
     */
    public void scenario(String value) {
        if (!Set.of("ACCEPTED", "NEEDS_INFORMATION", "ERROR", "TIMEOUT_AFTER_RECEIPT")
                .contains(value)) throw new IllegalArgumentException("Unknown scenario");
        scenario = value;
    }

    /** Закрывает локальный сервер при остановке контекста. */
    @PreDestroy
    void close() {
        if (server != null) server.stop(0);
    }
}
