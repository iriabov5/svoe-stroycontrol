package ru.rshb.stroycontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ru.rshb.stroycontrol.banking.LocalBankApi;
import ru.rshb.stroycontrol.banking.StageBankMockApi;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.7");

    @Container
    static final GenericContainer<?> S3 =
            new GenericContainer<>("chrislusf/seaweedfs:4.52")
                    .withCommand("mini", "-dir=/data")
                    .withEnv("AWS_ACCESS_KEY_ID", "integration-access")
                    .withEnv("AWS_SECRET_ACCESS_KEY", "integration-secret-password")
                    .withEnv("S3_BUCKET", "stroycontrol-private")
                    .withExposedPorts(8333)
                    .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("bank.stage.worker-delay-ms", () -> "3600000");
        registry.add(
                "storage.endpoint", () -> "http://" + S3.getHost() + ":" + S3.getMappedPort(8333));
        registry.add("storage.access-key", () -> "integration-access");
        registry.add("storage.secret-key", () -> "integration-secret-password");
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    StageBankMockApi stageBank;

    @AfterEach
    void resetStageBankScenario() {
        stageBank.scenario("ACCEPTED");
    }

    @LocalServerPort
    int port;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    JdbcTemplate jdbc;
    static final AtomicLong ORGANIZATION_SEQUENCE = new AtomicLong();
    static final String PASSWORD = "example-password-123";

    class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        String token;

        HttpResponse<String> upload(
                String project,
                String stage,
                UUID key,
                byte[] data,
                String name,
                String caption,
                boolean withCsrf)
                throws Exception {
            return uploadFor(project, stage, key, data, name, caption, withCsrf, "JOURNAL");
        }

        HttpResponse<String> uploadFor(
                String project,
                String stage,
                UUID key,
                byte[] data,
                String name,
                String caption,
                boolean withCsrf,
                String purpose)
                throws Exception {
            String boundary = "stroy-" + UUID.randomUUID();
            var output = new ByteArrayOutputStream();
            writeField(output, boundary, "purpose", purpose);
            if (stage != null) writeField(output, boundary, "stageId", stage);
            writeField(output, boundary, "caption", caption);
            output.writeBytes(
                    String.join(
                                    "\r\n",
                                    "--" + boundary,
                                    "Content-Disposition: form-data; name=\"file\"; filename=\""
                                            + name
                                            + "\"",
                                    "Content-Type: application/octet-stream",
                                    "",
                                    "")
                            .getBytes(StandardCharsets.UTF_8));
            output.writeBytes(data);
            output.writeBytes(
                    String.join("\r\n", "", "--" + boundary + "--", "")
                            .getBytes(StandardCharsets.UTF_8));
            var builder =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            "http://localhost:"
                                                    + port
                                                    + "/api/v1/projects/"
                                                    + project
                                                    + "/attachments"))
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .header("Idempotency-Key", key.toString());
            if (withCsrf) builder.header("X-CSRF-TOKEN", token);
            return client.send(
                    builder.POST(HttpRequest.BodyPublishers.ofByteArray(output.toByteArray()))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        /** Записывает поле multipart с обязательными CRLF, а не разделителем строк платформы. */
        private void writeField(
                ByteArrayOutputStream output, String boundary, String name, String value) {
            output.writeBytes(
                    String.join(
                                    "\r\n",
                                    "--" + boundary,
                                    "Content-Disposition: form-data; name=\"" + name + "\"",
                                    "",
                                    value,
                                    "")
                            .getBytes(StandardCharsets.UTF_8));
        }

        HttpResponse<String> request(String method, String path, Object body, boolean csrf)
                throws Exception {
            var builder =
                    HttpRequest.newBuilder(
                                    URI.create("http://localhost:" + port + "/api/v1" + path))
                            .header("Content-Type", "application/json");
            if (csrf) builder.header("X-CSRF-TOKEN", token);
            builder.method(
                    method,
                    body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        void csrf() throws Exception {
            var response = request("GET", "/auth/csrf", null, false);
            assertThat(response.statusCode()).isEqualTo(200);
            token = json(response).path("token").asText();
        }

        String email;

        Browser account(String role) throws Exception {
            csrf();
            email = UUID.randomUUID() + "@example.test";
            assertThat(register(email, role).statusCode()).isEqualTo(201);
            login();
            if (role.equals("CONTRACTOR")) {
                long seed = 900000000L + ORGANIZATION_SEQUENCE.incrementAndGet();
                String digits = Long.toString(seed);
                int[] weights = {2, 4, 10, 3, 5, 9, 4, 6, 8};
                int sum = 0;
                for (int i = 0; i < 9; i++) sum += (digits.charAt(i) - '0') * weights[i];
                String inn = digits + (sum % 11 % 10);
                var organization =
                        Map.of(
                                "type",
                                "LEGAL_ENTITY",
                                "displayName",
                                "Тестовый подрядчик",
                                "legalName",
                                "Тестовая строительная организация",
                                "inn",
                                inn,
                                "address",
                                "Тестовый участок",
                                "idempotencyKey",
                                UUID.randomUUID());
                assertThat(request("POST", "/organizations", organization, true).statusCode())
                        .isEqualTo(201);
            }
            return this;
        }

        HttpResponse<String> register(String email, String role) throws Exception {
            return request(
                    "POST",
                    "/auth/register",
                    Map.of(
                            "email",
                            email,
                            "password",
                            PASSWORD,
                            "role",
                            role,
                            "displayName",
                            role.equals("CUSTOMER") ? "Иван" : "Сергей Иванов"),
                    true);
        }

        void login() throws Exception {
            assertThat(
                            request(
                                            "POST",
                                            "/auth/login",
                                            Map.of("email", email, "password", PASSWORD),
                                            true)
                                    .statusCode())
                    .isEqualTo(200);
            csrf();
        }

        String project() throws Exception {
            var response =
                    request(
                            "POST",
                            "/projects",
                            Map.of("name", " Дом в Берёзовке ", "address", " Улица, 14 "),
                            true);
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(response.headers().firstValue("Location"))
                    .contains("/api/v1/projects/" + json(response).path("id").asText());
            assertThat(json(response).path("name").asText()).isEqualTo("Дом в Берёзовке");
            assertThat(json(response).path("contractorId").isNull()).isTrue();
            var id = json(response).path("id").asText();
            // Исторический режим V2: в продукте его выставляет только миграция Flyway.
            jdbc.update(
                    "update house_projects set process_mode = 'LEGACY' where id = ?",
                    UUID.fromString(id));
            return id;
        }

        HttpResponse<String> assign(String id, String email) throws Exception {
            return request("PUT", "/projects/" + id + "/contractor", Map.of("email", email), true);
        }
    }

    JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }

    Map<String, Object> stagePlan(String name, String start, String finish) {
        var plan = new HashMap<String, Object>();
        plan.put("name", name);
        plan.put("workType", "FOUNDATION");
        plan.put("plannedStart", start);
        plan.put("plannedFinish", finish);
        return plan;
    }

    JsonNode createStage(Browser owner, String projectId, String name) throws Exception {
        var response =
                owner.request(
                        "POST",
                        "/projects/" + projectId + "/stages",
                        stagePlan(name, null, null),
                        true);
        assertThat(response.statusCode()).isEqualTo(201);
        var stage = json(response);
        assertThat(response.headers().firstValue("Location"))
                .contains("/api/v1/projects/" + projectId + "/stages/" + stage.path("id").asText());
        return stage;
    }

    @Test
    @DisplayName("План этапов доступен участникам, чужая карточка и чужой этап скрыты")
    void stageIsolation() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var builder = new Browser().account("CONTRACTOR");
        var outsider = new Browser().account("CUSTOMER");
        var projectId = owner.project();
        owner.assign(projectId, builder.email);
        var stage = createStage(owner, projectId, "Фундамент");
        var path = "/projects/" + projectId + "/stages";
        assertThat(json(builder.request("GET", path, null, false)).size()).isEqualTo(1);
        error(outsider.request("GET", path, null, false), 404, "PROJECT_NOT_FOUND");
        error(
                outsider.request("GET", path + "/" + stage.path("id").asText(), null, false),
                404,
                "PROJECT_NOT_FOUND");
        var otherId = owner.project();
        error(
                owner.request(
                        "GET",
                        "/projects/" + otherId + "/stages/" + stage.path("id").asText(),
                        null,
                        false),
                404,
                "STAGE_NOT_FOUND");
        error(
                builder.request("POST", path, stagePlan("Чужой план", null, null), true),
                403,
                "FORBIDDEN");
        error(
                owner.request(
                        "POST",
                        path + "/" + stage.path("id").asText() + "/start",
                        Map.of("version", 0),
                        true),
                403,
                "FORBIDDEN");
        error(new Browser().request("GET", path, null, false), 401, "AUTH_REQUIRED");
        error(
                owner.request("POST", path, stagePlan("Дом", null, null), false),
                403,
                "INVALID_CSRF_TOKEN");
    }

    @Test
    @DisplayName("Подрядчик начинает и завершает этап, повторы сохраняют исходные даты и версии")
    void stageLifecycle() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var builder = new Browser().account("CONTRACTOR");
        var projectId = owner.project();
        owner.assign(projectId, builder.email);
        var stage = createStage(owner, projectId, "Фундамент");
        var path = "/projects/" + projectId + "/stages/" + stage.path("id").asText();
        error(
                builder.request("POST", path + "/complete", Map.of("version", 0), true),
                409,
                "INVALID_STAGE_TRANSITION");
        var started = builder.request("POST", path + "/start", Map.of("version", 0), true);
        assertThat(started.statusCode()).isEqualTo(200);
        assertThat(json(started).path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(json(started).path("actualStart").asText())
                .isEqualTo(LocalDate.now(ZoneId.of("Europe/Moscow")).toString());
        assertThat(json(started).path("version").asLong()).isEqualTo(1);
        assertThat(json(builder.request("POST", path + "/start", Map.of("version", 0), true)))
                .isEqualTo(json(started));
        var completed = builder.request("POST", path + "/complete", Map.of("version", 1), true);
        assertThat(completed.statusCode()).isEqualTo(200);
        assertThat(json(completed).path("status").asText()).isEqualTo("WORK_COMPLETED");
        assertThat(json(completed).path("version").asLong()).isEqualTo(2);
        assertThat(json(builder.request("POST", path + "/complete", Map.of("version", 1), true)))
                .isEqualTo(json(completed));
        assertThat(json(builder.request("POST", path + "/start", Map.of("version", 0), true)))
                .isEqualTo(json(completed));
    }

    @Test
    @DisplayName("Неверные даты и устаревшая версия не меняют план этапа")
    void stagePlanValidation() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var projectId = owner.project();
        var stage = createStage(owner, projectId, "Фундамент");
        var path = "/projects/" + projectId + "/stages/" + stage.path("id").asText();
        var invalid = stagePlan("Новое имя", "2026-11-14", "2026-11-13");
        invalid.put("version", 0);
        var wrongVersion = stagePlan("Новое имя", null, null);
        wrongVersion.put("version", "0");
        error(owner.request("PUT", path, wrongVersion, true), 400, "VALIDATION_ERROR");
        wrongVersion.put("version", 0.5);
        error(owner.request("PUT", path, wrongVersion, true), 400, "VALIDATION_ERROR");
        error(owner.request("PUT", path, invalid, true), 400, "INVALID_STAGE_DATES");
        var valid = stagePlan("Обновлённый фундамент", "2026-11-13", "2026-11-13");
        valid.put("version", 0);
        var updated = owner.request("PUT", path, valid, true);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(json(updated).path("version").asLong()).isEqualTo(1);
        valid.put("name", "Устаревшее имя");
        error(owner.request("PUT", path, valid, true), 409, "STALE_STAGE_VERSION");
        assertThat(json(owner.request("GET", path, null, false)).path("name").asText())
                .isEqualTo("Обновлённый фундамент");
        var extra = stagePlan("Дом", null, null);
        extra.put("actualStart", "2026-01-01");
        error(
                owner.request("POST", "/projects/" + projectId + "/stages", extra, true),
                400,
                "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("Полная перестановка атомарна, дубли и пропуски не меняют порядок")
    void stageOrder() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var projectId = owner.project();
        var first = createStage(owner, projectId, "Фундамент");
        var second = createStage(owner, projectId, "Стены");
        var path = "/projects/" + projectId + "/stages";
        var a = Map.of("id", first.path("id").asText(), "version", 0);
        var b = Map.of("id", second.path("id").asText(), "version", 0);
        error(
                owner.request("PUT", path + "/order", Map.of("stages", List.of(a, a)), true),
                400,
                "VALIDATION_ERROR");
        error(
                owner.request("PUT", path + "/order", Map.of("stages", List.of(a)), true),
                400,
                "VALIDATION_ERROR");
        var response = owner.request("PUT", path + "/order", Map.of("stages", List.of(b, a)), true);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get(0).path("name").asText()).isEqualTo("Стены");
        assertThat(json(response).get(1).path("position").asInt()).isEqualTo(2);
        error(
                owner.request("PUT", path + "/order", Map.of("stages", List.of(a, b)), true),
                409,
                "STALE_STAGE_VERSION");
        assertThat(json(owner.request("GET", path, null, false)).get(0).path("name").asText())
                .isEqualTo("Стены");
    }

    @Test
    @DisplayName("Конкурентное редактирование этапа не теряет сохранённое изменение")
    void concurrentStageEdit() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var projectId = owner.project();
        var stage = createStage(owner, projectId, "Фундамент");
        var other = new Browser();
        other.csrf();
        other.email = owner.email;
        other.login();
        var path = "/projects/" + projectId + "/stages/" + stage.path("id").asText();
        var first = stagePlan("Первое имя", null, null);
        first.put("version", 0);
        var second = stagePlan("Второе имя", null, null);
        second.put("version", 0);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a =
                    pool.submit(() -> {
                        start.await();
                        return owner.request("PUT", path, first, true);
                    });
            var b =
                    pool.submit(() -> {
                        start.await();
                        return other.request("PUT", path, second, true);
                    });
            start.countDown();
            assertThat(
                            List.of(
                                    a.get(20, TimeUnit.SECONDS).statusCode(),
                                    b.get(20, TimeUnit.SECONDS).statusCode()))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(json(owner.request("GET", path, null, false)).path("version").asLong())
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Конкурентное создание этапов получает разные последовательные позиции")
    void concurrentStageCreation() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var projectId = owner.project();
        var other = new Browser();
        other.csrf();
        other.email = owner.email;
        other.login();
        var path = "/projects/" + projectId + "/stages";
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a =
                    pool.submit(() -> {
                        start.await();
                        return owner.request("POST", path, stagePlan("Первый", null, null), true);
                    });
            var b =
                    pool.submit(() -> {
                        start.await();
                        return other.request("POST", path, stagePlan("Второй", null, null), true);
                    });
            start.countDown();
            assertThat(
                            List.of(
                                    a.get(20, TimeUnit.SECONDS).statusCode(),
                                    b.get(20, TimeUnit.SECONDS).statusCode()))
                    .containsOnly(201);
            var list = json(owner.request("GET", path, null, false));
            assertThat(list.size()).isEqualTo(2);
            assertThat(
                            List.of(
                                    list.get(0).path("position").asInt(),
                                    list.get(1).path("position").asInt()))
                    .containsExactly(1, 2);
        }
    }

    @Test
    @DisplayName("Ограничения БД запрещают невозможный статус и завершение раньше начала")
    void stageDatabaseConstraints() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var projectId = owner.project();
        var stage = createStage(owner, projectId, "Фундамент");
        var id = UUID.fromString(stage.path("id").asText());
        assertThatThrownBy(() ->
                        jdbc.update(
                                "update construction_stages set status = 'WORK_COMPLETED' where id"
                                        + " = ?",
                                id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
                        jdbc.update(
                                """
                                update construction_stages
                                   set planned_start = '2026-11-14', planned_finish = '2026-11-13'
                                 where id = ?
                                """,
                                id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
                        jdbc.update(
                                """
                                update construction_stages
                                   set status = 'WORK_COMPLETED',
                                       actual_start = '2026-11-14',
                                       actual_finish = '2026-11-13'
                                 where id = ?
                                """,
                                id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    void error(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/problem+json");
        assertThat(json(response).path("code").asText()).isEqualTo(code);
        assertThat(json(response).path("status").asInt()).isEqualTo(status);
        assertThat(json(response).path("type").asText()).isEqualTo("about:blank");
    }

    @Test
    @DisplayName("Регистрация нормализует email, сохраняет Argon2id и не открывает сессию")
    void registration() throws Exception {
        var browser = new Browser();
        browser.csrf();
        var email = UUID.randomUUID() + "@example.test";
        var response = browser.register(" " + email.toUpperCase(Locale.ROOT) + " ", "CUSTOMER");
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(json(response).path("email").asText()).isEqualTo(email);
        assertThat(json(response).propertyNames())
                .containsExactlyInAnyOrder(
                        "id", "email", "role", "displayName", "profileVersion", "membership");
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        error(browser.request("GET", "/auth/me", null, false), 401, "AUTH_REQUIRED");
        var hash =
                jdbc.queryForObject(
                        "select password_hash from accounts where email = ?", String.class, email);
        assertThat(hash).startsWith("$argon2id$").doesNotContain(PASSWORD);
        error(browser.register(email, "CONTRACTOR"), 409, "EMAIL_ALREADY_EXISTS");
        assertThat(
                        jdbc.queryForObject(
                                "select role from accounts where email = ?", String.class, email))
                .isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("Вход меняет ID сессии и CSRF, выход закрывает доступ по прежней cookie")
    void sessionLifecycle() throws Exception {
        var browser = new Browser();
        browser.csrf();
        browser.email = UUID.randomUUID() + "@example.test";
        browser.register(browser.email, "CUSTOMER");
        var oldCookie = browser.cookies.getCookieStore().getCookies().getFirst().getValue();
        var oldToken = browser.token;
        browser.login();
        assertThat(browser.cookies.getCookieStore().getCookies().getFirst().getValue())
                .isNotEqualTo(oldCookie);
        var profile = browser.request("GET", "/auth/me", null, false);
        assertThat(profile.statusCode()).isEqualTo(200);
        browser.token = oldToken;
        error(
                browser.request(
                        "POST", "/projects", Map.of("name", "Дом", "address", "Адрес"), true),
                403,
                "INVALID_CSRF_TOKEN");
        browser.csrf();
        var authenticatedCookie =
                browser.cookies.getCookieStore().getCookies().getFirst().getValue();
        assertThat(browser.request("POST", "/auth/logout", null, true).statusCode()).isEqualTo(204);
        error(browser.request("GET", "/auth/me", null, false), 401, "AUTH_REQUIRED");
        var stale =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:"
                                                                + port
                                                                + "/api/v1/auth/me"))
                                        .header("Cookie", "JSESSIONID=" + authenticatedCookie)
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        error(stale, 401, "AUTH_REQUIRED");
    }

    @ParameterizedTest(name = "операция = {0}")
    @ValueSource(strings = {"/auth/register", "/auth/login", "/auth/logout", "/projects"})
    @DisplayName("Изменяющая операция без CSRF отклоняется до аутентификации")
    void csrfRequired(String path) throws Exception {
        error(new Browser().request("POST", path, Map.of(), false), 403, "INVALID_CSRF_TOKEN");
    }

    @Test
    @DisplayName("Неизвестный email и неверный пароль получают одинаковый ответ входа")
    void indistinguishableCredentials() throws Exception {
        var browser = new Browser().account("CUSTOMER");
        var wrong =
                browser.request(
                        "POST",
                        "/auth/login",
                        Map.of("email", browser.email, "password", "wrong-password-123"),
                        true);
        var missing =
                browser.request(
                        "POST",
                        "/auth/login",
                        Map.of("email", UUID.randomUUID() + "@example.test", "password", PASSWORD),
                        true);
        error(wrong, 401, "INVALID_CREDENTIALS");
        error(missing, 401, "INVALID_CREDENTIALS");
        assertThat(json(wrong)).isEqualTo(json(missing));
    }

    @Test
    @DisplayName("Неизвестные поля, роль, пустой текст и неверный UUID отклоняются по контракту")
    void validation() throws Exception {
        var browser = new Browser().account("CUSTOMER");
        error(browser.register("invalid", "CUSTOMER"), 400, "VALIDATION_ERROR");
        error(
                browser.register(UUID.randomUUID() + "@example.test", "ADMIN"),
                400,
                "VALIDATION_ERROR");
        error(
                browser.request(
                        "POST",
                        "/auth/register",
                        Map.of(
                                "email",
                                UUID.randomUUID() + "@example.test",
                                "password",
                                PASSWORD,
                                "role",
                                0),
                        true),
                400,
                "VALIDATION_ERROR");
        error(
                browser.request("POST", "/projects", Map.of("name", 123, "address", "Адрес"), true),
                400,
                "VALIDATION_ERROR");
        error(
                browser.request("POST", "/projects", Map.of("name", " ", "address", "Адрес"), true),
                400,
                "VALIDATION_ERROR");
        error(
                browser.request(
                        "POST",
                        "/projects",
                        Map.of("name", "Дом", "address", "Адрес", "customerId", UUID.randomUUID()),
                        true),
                400,
                "VALIDATION_ERROR");
        error(browser.request("GET", "/projects/not-a-uuid", null, false), 400, "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("Доступ к карточке изолирован, подрядчик получает только назначенные объекты")
    void isolation() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var builder = new Browser().account("CONTRACTOR");
        var outsider = new Browser().account("CUSTOMER");
        var id = owner.project();
        assertThat(json(builder.request("GET", "/projects", null, false)).isEmpty()).isTrue();
        error(
                builder.request(
                        "POST", "/projects", Map.of("name", "Дом", "address", "Адрес"), true),
                403,
                "FORBIDDEN");
        var foreign = outsider.request("GET", "/projects/" + id, null, false);
        var missing = outsider.request("GET", "/projects/" + UUID.randomUUID(), null, false);
        error(foreign, 404, "PROJECT_NOT_FOUND");
        assertThat(json(foreign)).isEqualTo(json(missing));
        error(outsider.assign(id, builder.email), 404, "PROJECT_NOT_FOUND");
        error(owner.assign(id, owner.email), 422, "CONTRACTOR_NOT_FOUND");
        var assigned = owner.assign(id, builder.email);
        assertThat(assigned.statusCode()).isEqualTo(200);
        assertThat(json(owner.assign(id, " " + builder.email.toUpperCase(Locale.ROOT) + " ")))
                .isEqualTo(json(assigned));
        assertThat(builder.request("GET", "/projects/" + id, null, false).statusCode())
                .isEqualTo(200);
        assertThat(json(builder.request("GET", "/projects", null, false)).size()).isEqualTo(1);
        error(builder.assign(id, builder.email), 403, "FORBIDDEN");
        assertThat(json(outsider.request("GET", "/projects", null, false)).isEmpty()).isTrue();
        error(new Browser().request("GET", "/projects", null, false), 401, "AUTH_REQUIRED");
    }

    @Test
    @DisplayName("Два конкурентных назначения дают один успех и один конфликт")
    void concurrentAssignment() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var first = new Browser().account("CONTRACTOR");
        var second = new Browser().account("CONTRACTOR");
        var id = owner.project();
        var otherSession = new Browser();
        otherSession.csrf();
        otherSession.email = owner.email;
        otherSession.login();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a =
                    pool.submit(() -> {
                        start.await();
                        return owner.assign(id, first.email);
                    });
            var b =
                    pool.submit(() -> {
                        start.await();
                        return otherSession.assign(id, second.email);
                    });
            start.countDown();
            var responses = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertThat(responses.stream().map(HttpResponse::statusCode))
                    .containsExactlyInAnyOrder(200, 409);
            var access =
                    List.of(
                            first.request("GET", "/projects/" + id, null, false).statusCode(),
                            second.request("GET", "/projects/" + id, null, false).statusCode());
            assertThat(access).containsExactlyInAnyOrder(200, 404);
        }
    }

    @Test
    @DisplayName("Конкурентная регистрация одного email создаёт только один аккаунт")
    void concurrentRegistration() throws Exception {
        var first = new Browser();
        first.csrf();
        var second = new Browser();
        second.csrf();
        var email = UUID.randomUUID() + "@example.test";
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a =
                    pool.submit(() -> {
                        start.await();
                        return first.register(email, "CUSTOMER");
                    });
            var b =
                    pool.submit(() -> {
                        start.await();
                        return second.register(
                                " " + email.toUpperCase(Locale.ROOT) + " ", "CONTRACTOR");
                    });
            start.countDown();
            assertThat(
                            List.of(
                                    a.get(20, TimeUnit.SECONDS).statusCode(),
                                    b.get(20, TimeUnit.SECONDS).statusCode()))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from accounts where email = ?",
                                    Integer.class,
                                    email))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Одинаковые названия допустимы, при равном времени список сортируется по ID")
    void stableProjectOrder() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var first = owner.project();
        var second = owner.project();
        jdbc.update(
                "update house_projects set created_at = '2026-01-01T00:00:00Z' where id in (?, ?)",
                UUID.fromString(first),
                UUID.fromString(second));
        var list = json(owner.request("GET", "/projects", null, false));
        var expected = new ArrayList<>(List.of(first, second));
        expected.sort(Comparator.reverseOrder());
        assertThat(List.of(list.get(0).path("id").asText(), list.get(1).path("id").asText()))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("Неверный Content-Type и повреждённый JSON получают контрактные ошибки")
    void malformedBody() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var base = URI.create("http://localhost:" + port + "/api/v1/projects");
        var wrongType =
                owner.client.send(
                        HttpRequest.newBuilder(base)
                                .header("X-CSRF-TOKEN", owner.token)
                                .header("Content-Type", "text/plain")
                                .POST(HttpRequest.BodyPublishers.ofString("text"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        error(wrongType, 415, "UNSUPPORTED_MEDIA_TYPE");
        var malformed =
                owner.client.send(
                        HttpRequest.newBuilder(base)
                                .header("X-CSRF-TOKEN", owner.token)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("{"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        error(malformed, 400, "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("База обеспечивает внешние ключи и запрещает пустые имена карточек")
    void databaseConstraints() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var accountId =
                UUID.fromString(
                        json(owner.request("GET", "/auth/me", null, false)).path("id").asText());
        var constraintId1 = UUID.randomUUID();
        var constraintId2 = UUID.randomUUID();
        var constraintId3 = UUID.randomUUID();
        assertThatThrownBy(() ->
                        jdbc.update(
                                """
                                insert into house_projects
                                    (id, name, address, customer_id, contractor_id, created_at)
                                values (?, ?, ?, ?, null, now())
                                """,
                                constraintId1,
                                "Дом",
                                "Адрес",
                                constraintId2))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
                        jdbc.update(
                                """
                                insert into house_projects
                                    (id, name, address, customer_id, contractor_id, created_at)
                                values (?, ?, ?, ?, null, now())
                                """,
                                constraintId3,
                                "",
                                "Адрес",
                                accountId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    String preparationProject(Browser owner) throws Exception {
        var response =
                owner.request(
                        "POST",
                        "/projects",
                        Map.of("name", "Каркасный дом", "address", "Тестовый участок"),
                        true);
        assertThat(response.statusCode()).isEqualTo(201);
        return json(response).path("id").asText();
    }

    JsonNode preparationRequest(Browser owner, String id, String source) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("version", 0);
        body.put("description", "Дом для семьи");
        body.put("technology", "Каркас");
        body.put("materialsRecommendation", true);
        body.put("areaM2", 80);
        body.put("floors", 1);
        body.put("completionScope", "Тёплый контур");
        body.put("desiredBudget", 3000000);
        body.put("desiredStart", "2026-12-01");
        body.put("desiredFinish", "2027-03-01");
        body.put("financingSource", source);
        var response = owner.request("PATCH", "/projects/" + id + "/request", body, true);
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response);
    }

    JsonNode proposal(Browser builder, String id) throws Exception {
        var response = builder.request("POST", "/projects/" + id + "/proposals", null, true);
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response);
    }

    JsonNode saveProposal(Browser builder, String id, JsonNode draft) throws Exception {
        return saveProposalImages(builder, id, draft, List.of());
    }

    /** Сохраняет проверяемый набор изображений в полноценном черновике с планом. */
    JsonNode saveProposalImages(
            Browser builder, String id, JsonNode draft, List<Map<String, Object>> pictures)
            throws Exception {
        var plan =
                Map.of(
                        "id",
                        UUID.randomUUID(),
                        "name",
                        "Фундамент",
                        "workType",
                        "FOUNDATION",
                        "expectedResult",
                        "Готовое основание",
                        "plannedStart",
                        "2026-12-01",
                        "plannedFinish",
                        "2026-12-10");
        var body = new HashMap<String, Object>();
        body.put("version", draft.path("version").asLong());
        body.put("estimateText", "Работы 100000, материалы 200000");
        body.put("termsText", null);
        body.put("totalPrice", 300000);
        body.put("images", pictures);
        body.put("stages", List.of(plan));
        var response =
                builder.request(
                        "PUT",
                        "/projects/" + id + "/proposals/" + draft.path("id").asText(),
                        body,
                        true);
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response);
    }

    JsonNode documentCommand(Browser browser, String path, String action, JsonNode doc)
            throws Exception {
        var response =
                browser.request(
                        "POST",
                        path + "/" + action,
                        Map.of(
                                "version",
                                doc.path("version").asLong(),
                                "contractorConfirmed",
                                true),
                        true);
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response);
    }

    @Test
    @DisplayName("Новый проект проходит заявку, возврат сметы и договор до начала работ")
    void preparationLifecycle() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var builder = new Browser().account("CONTRACTOR");
        var id = preparationProject(owner);
        var root = "/projects/" + id;
        owner.assign(id, builder.email);
        error(builder.request("GET", root, null, false), 404, "PROJECT_NOT_FOUND");
        var r = preparationRequest(owner, id, "OWN_FUNDS");
        error(
                owner.request("POST", root + "/stages", stagePlan("Обход", null, null), true),
                409,
                "APPROVED_PLAN_LOCKED");
        assertThat(
                        owner.request(
                                        "POST",
                                        root + "/request/submit",
                                        Map.of("version", r.path("version").asLong()),
                                        true)
                                .statusCode())
                .isEqualTo(200);
        assertThat(builder.request("GET", root, null, false).statusCode()).isEqualTo(200);
        var p = saveProposal(builder, id, proposal(builder, id));
        var path = root + "/proposals/" + p.path("id").asText();
        error(owner.request("GET", path, null, false), 404, "PROPOSAL_NOT_FOUND");
        p = documentCommand(builder, path, "submit", p);
        var returned =
                owner.request(
                        "POST",
                        path + "/request-changes",
                        Map.of(
                                "version",
                                p.path("version").asLong(),
                                "comment",
                                "Уточнить материалы"),
                        true);
        assertThat(returned.statusCode()).isEqualTo(200);
        var nextDraft = proposal(builder, id);
        p = saveProposal(builder, id, nextDraft);
        path = root + "/proposals/" + p.path("id").asText();
        p = documentCommand(builder, path, "submit", p);
        p = documentCommand(owner, path, "approve", p);
        documentCommand(owner, path, "approve", p);
        var stages = json(owner.request("GET", root + "/stages", null, false));
        assertThat(stages.size()).isEqualTo(1);
        var stagePath = root + "/stages/" + stages.get(0).path("id").asText();
        error(
                builder.request("POST", stagePath + "/start", Map.of("version", 0), true),
                409,
                "CONTRACT_REQUIRED");
        approveContractAndStart(owner, builder, root, stagePath);
    }

    @Test
    @DisplayName("Изображение сметы скрыто в черновике, публикуется и сохраняется в новой версии")
    void proposalImagePublicationLifecycle() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var builder = new Browser().account("CONTRACTOR");
        var stranger = new Browser().account("CUSTOMER");
        var id = preparationProject(owner);
        var root = "/projects/" + id;
        owner.assign(id, builder.email);
        var request = preparationRequest(owner, id, "OWN_FUNDS");
        assertThat(
                        owner.request(
                                        "POST",
                                        root + "/request/submit",
                                        Map.of("version", request.path("version").asLong()),
                                        true)
                                .statusCode())
                .isEqualTo(200);
        var proposal = saveProposal(builder, id, proposal(builder, id));
        proposal = attachProposalImage(builder, owner, stranger, id, proposal);
        var pictureId = proposal.path("images").get(0).path("attachmentId").asText();
        var imageContent = root + "/attachments/" + pictureId + "/content";
        var path = root + "/proposals/" + proposal.path("id").asText();
        error(stranger.request("GET", path, null, false), 404, "PROJECT_NOT_FOUND");
        proposal = documentCommand(builder, path, "submit", proposal);
        assertThat(owner.request("GET", imageContent, null, false).statusCode()).isEqualTo(200);
        var anonymous =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://"
                                                                + S3.getHost()
                                                                + ":"
                                                                + S3.getMappedPort(8333)
                                                                + "/stroycontrol-private"))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.discarding());
        assertThat(anonymous.statusCode()).isIn(401, 403);

        assertThat(
                        owner.request(
                                        "POST",
                                        path + "/request-changes",
                                        Map.of(
                                                "version",
                                                proposal.path("version").asLong(),
                                                "comment",
                                                "Уточнить материалы"),
                                        true)
                                .statusCode())
                .isEqualTo(200);
        var nextDraft = proposal(builder, id);
        assertThat(nextDraft.path("images").get(0).path("attachmentId").asText())
                .isEqualTo(pictureId);
        saveProposal(builder, id, nextDraft);
        assertThat(owner.request("GET", imageContent, null, false).statusCode()).isEqualTo(200);
    }

    /**
     * Загружает изображение сметы и проверяет закрытый черновик, повтор загрузки и формат файла.
     *
     * @param builder подрядчик, редактирующий предложение
     * @param owner заказчик карточки
     * @param stranger посторонний аккаунт
     * @param id карточка дома
     * @param p черновик предложения
     * @return черновик с проверенным вложением
     * @throws Exception при ошибке HTTP-обмена
     */
    private JsonNode attachProposalImage(
            Browser builder, Browser owner, Browser stranger, String id, JsonNode p)
            throws Exception {
        var root = "/projects/" + id;
        var uploadKey = UUID.randomUUID();
        var picture =
                json(
                        builder.uploadFor(
                                id,
                                null,
                                uploadKey,
                                demoPng(),
                                "plan.png",
                                "План дома",
                                true,
                                "PROPOSAL"));
        assertThat(
                        json(
                                builder.uploadFor(
                                        id,
                                        null,
                                        uploadKey,
                                        demoPng(),
                                        "plan.png",
                                        "План дома",
                                        true,
                                        "PROPOSAL")))
                .isEqualTo(picture);
        var pictureId = picture.path("id").asText();
        var imageContent = root + "/attachments/" + pictureId + "/content";
        p =
                saveProposalImages(
                        builder,
                        id,
                        p,
                        List.of(
                                Map.of(
                                        "attachmentId",
                                        pictureId,
                                        "caption",
                                        "План первого этажа")));
        assertThat(p.path("images").get(0).path("attachmentId").asText()).isEqualTo(pictureId);
        error(owner.request("GET", imageContent, null, false), 404, "ATTACHMENT_NOT_FOUND");
        error(stranger.request("GET", imageContent, null, false), 404, "PROJECT_NOT_FOUND");
        error(
                builder.uploadFor(
                        id,
                        null,
                        UUID.randomUUID(),
                        "%PDF-1.4\n%%EOF\n".getBytes(),
                        "plan.pdf",
                        "PDF",
                        true,
                        "PROPOSAL"),
                400,
                "VALIDATION_ERROR");
        return p;
    }

    @Test
    @DisplayName(
            "Демо-ипотека доступна владельцу, повтор не дублируется, смена источника снимает"
                    + " привязку")
    void mortgageImportIsolation() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var builder = new Browser().account("CONTRACTOR");
        var id = preparationProject(owner);
        owner.assign(id, builder.email);
        var r = preparationRequest(owner, id, "RSHB_RURAL_MORTGAGE");
        var path = "/projects/" + id + "/mortgage-approval";
        var command =
                Map.of(
                        "version",
                        r.path("version").asLong(),
                        "demoConsent",
                        true,
                        "idempotencyKey",
                        UUID.randomUUID());
        var response = owner.request("POST", path + "/import", command, true);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).path("status").asText()).isEqualTo("FOUND");
        assertThat(json(response).path("snapshot").path("isDemo").asBoolean()).isTrue();
        assertThat(json(owner.request("POST", path + "/import", command, true)))
                .isEqualTo(json(response));
        error(builder.request("GET", path, null, false), 404, "PROJECT_NOT_FOUND");
        r = json(owner.request("GET", "/projects/" + id + "/request", null, false));
        assertThat(r.has("mortgageSnapshotId")).isFalse();
        assertThat(
                        owner.request(
                                        "PATCH",
                                        "/projects/" + id + "/request",
                                        Map.of(
                                                "version",
                                                r.path("version").asLong(),
                                                "financingSource",
                                                "OWN_FUNDS"),
                                        true)
                                .statusCode())
                .isEqualTo(200);
        assertThat(json(owner.request("GET", path, null, false)).path("snapshot").isNull())
                .isTrue();
    }

    @Test
    @DisplayName("Некорректные URL и устаревшие изменения не перезаписывают предложение")
    void proposalValidation() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var builder = new Browser().account("CONTRACTOR");
        var id = preparationProject(owner);
        owner.assign(id, builder.email);
        var r = preparationRequest(owner, id, "OWN_FUNDS");
        owner.request(
                "POST",
                "/projects/" + id + "/request/submit",
                Map.of("version", r.path("version").asLong()),
                true);
        var p = saveProposal(builder, id, proposal(builder, id));
        var path = "/projects/" + id + "/proposals/" + p.path("id").asText();
        var body = new HashMap<String, Object>();
        body.put("version", p.path("version").asLong());
        body.put("estimateText", "Изменение");
        body.put("termsText", null);
        body.put("totalPrice", 100);
        body.put("images", List.of(Map.of("url", "javascript:alert(1)", "caption", "План")));
        body.put("stages", List.of());
        error(builder.request("PUT", path, body, true), 400, "VALIDATION_ERROR");
        assertThat(json(builder.request("GET", path, null, false)).path("estimateText").asText())
                .contains("Работы");
        body.put("images", List.of());
        body.put("version", 0);
        error(builder.request("PUT", path, body, true), 409, "STALE_PROPOSAL_VERSION");
    }

    @Autowired
    LocalBankApi bank;

    @Test
    @DisplayName("Одновременные согласование и возврат фиксируют только одно решение")
    void concurrentProposalDecision() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var builder = new Browser().account("CONTRACTOR");
        var id = preparationProject(owner);
        owner.assign(id, builder.email);
        var req = preparationRequest(owner, id, "OWN_FUNDS");
        owner.request(
                "POST",
                "/projects/" + id + "/request/submit",
                Map.of("version", req.path("version").asLong()),
                true);
        var doc = saveProposal(builder, id, proposal(builder, id));
        var path = "/projects/" + id + "/proposals/" + doc.path("id").asText();
        doc = documentCommand(builder, path, "submit", doc);
        long version = doc.path("version").asLong();
        var other = new Browser();
        other.csrf();
        other.email = owner.email;
        other.login();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var a =
                    pool.submit(() -> {
                        gate.await();
                        return owner.request(
                                "POST", path + "/approve", Map.of("version", version), true);
                    });
            var b =
                    pool.submit(() -> {
                        gate.await();
                        return other.request(
                                "POST",
                                path + "/request-changes",
                                Map.of("version", version, "comment", "Уточнить"),
                                true);
                    });
            gate.countDown();
            assertThat(
                            List.of(
                                    a.get(20, TimeUnit.SECONDS).statusCode(),
                                    b.get(20, TimeUnit.SECONDS).statusCode()))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from proposal_decisions where proposal_id=?",
                                Integer.class,
                                UUID.fromString(doc.path("id").asText())))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Ошибка создания этапов откатывает согласование и все его записи")
    void approvalRollback() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var builder = new Browser().account("CONTRACTOR");
        var id = preparationProject(owner);
        owner.assign(id, builder.email);
        var req = preparationRequest(owner, id, "OWN_FUNDS");
        owner.request(
                "POST",
                "/projects/" + id + "/request/submit",
                Map.of("version", req.path("version").asLong()),
                true);
        var doc = saveProposal(builder, id, proposal(builder, id));
        var path = "/projects/" + id + "/proposals/" + doc.path("id").asText();
        doc = documentCommand(builder, path, "submit", doc);
        jdbc.execute(
                jdbc.queryForObject(
"""
select format(
    'alter table construction_stages add constraint test_install_failure check (project_id <> %L::uuid)',
    cast(? as text))
""",
                        String.class, id));
        try {
            assertThat(
                            owner.request(
                                            "POST",
                                            path + "/approve",
                                            Map.of("version", doc.path("version").asLong()),
                                            true)
                                    .statusCode())
                    .isEqualTo(500);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from proposal_decisions where proposal_id=?",
                                    Integer.class,
                                    UUID.fromString(doc.path("id").asText())))
                    .isZero();
            assertThat(json(owner.request("GET", path, null, false)).path("state").asText())
                    .isEqualTo("SUBMITTED");
            assertThat(
                            json(owner.request("GET", "/projects/" + id, null, false))
                                    .path("approvedProposalId")
                                    .isNull())
                    .isTrue();
            assertThat(
                            json(owner.request("GET", "/projects/" + id + "/stages", null, false))
                                    .isEmpty())
                    .isTrue();
        } finally {
            jdbc.execute("alter table construction_stages drop constraint test_install_failure");
        }
    }

    @Test
    @DisplayName(
            "Демо-банк показывает отсутствие и истечение, а сбой не стирает сохранённые данные")
    void mortgageAlternatives() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var id = preparationProject(owner);
        var root = "/projects/" + id;
        preparationRequest(owner, id, "RSHB_RURAL_MORTGAGE");
        try {
            for (var scenario : List.of("NOT_FOUND", "EXPIRED", "FOUND")) {
                bank.scenario(scenario);
                long version =
                        json(owner.request("GET", root + "/request", null, false))
                                .path("version")
                                .asLong();
                var response =
                        owner.request(
                                "POST",
                                root + "/mortgage-approval/import",
                                Map.of(
                                        "version",
                                        version,
                                        "demoConsent",
                                        true,
                                        "idempotencyKey",
                                        UUID.randomUUID()),
                                true);
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(json(response).path("status").asText()).isEqualTo(scenario);
            }
            var saved =
                    json(owner.request("GET", root + "/mortgage-approval", null, false))
                            .path("snapshot");
            bank.scenario("ERROR");
            long version =
                    json(owner.request("GET", root + "/request", null, false))
                            .path("version")
                            .asLong();
            error(
                    owner.request(
                            "POST",
                            root + "/mortgage-approval/import",
                            Map.of(
                                    "version",
                                    version,
                                    "demoConsent",
                                    true,
                                    "idempotencyKey",
                                    UUID.randomUUID()),
                            true),
                    503,
                    "BANK_DEMO_UNAVAILABLE");
            assertThat(
                            json(owner.request("GET", root + "/mortgage-approval", null, false))
                                    .path("snapshot"))
                    .isEqualTo(saved);
        } finally {
            bank.scenario("FOUND");
        }
    }

    @Test
    @DisplayName("Flyway сохраняет начатые этапы при переходе со схемы V2 к подготовке стройки")
    void migrationFromV2() throws Exception {
        String schema = "before_preparation";
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target("2")
                .load()
                .migrate();
        try (var connection =
                DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.setSchema(schema);
            var owner = UUID.randomUUID();
            var project = UUID.randomUUID();
            var stage = UUID.randomUUID();
            try (var insertAccount =
                    connection.prepareStatement(
                            """
                            insert into accounts (id, email, role, password_hash, created_at)
                            values (cast(? as uuid), 'migration@example.test', 'CUSTOMER', 'hash', now())
                            """)) {
                insertAccount.setString(1, owner.toString());
                insertAccount.executeUpdate();
            }
            try (var insertProject =
                    connection.prepareStatement(
                            """
                            insert into house_projects
                                (id, name, address, customer_id, contractor_id, created_at)
                            values (cast(? as uuid), 'Дом', 'Адрес', cast(? as uuid), null, now())
                            """)) {
                insertProject.setString(1, project.toString());
                insertProject.setString(2, owner.toString());
                insertProject.executeUpdate();
            }
            try (var insertStage =
                    connection.prepareStatement(
                            """
                            insert into construction_stages
                                (id, project_id, name, work_type, position, status, actual_start)
                            values (
                                cast(? as uuid), cast(? as uuid), 'Фундамент', 'FOUNDATION', 1,
                                'IN_PROGRESS', '2026-11-01')
                            """)) {
                insertStage.setString(1, stage.toString());
                insertStage.setString(2, project.toString());
                insertStage.executeUpdate();
            }
            Flyway.configure()
                    .dataSource(
                            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema)
                    .defaultSchema(schema)
                    .load()
                    .migrate();
            try (var query = connection.createStatement();
                    var result =
                            query.executeQuery(
                                    """
                                    select p.process_mode, s.status, s.actual_start
                                      from house_projects p
                                      join construction_stages s on p.id = s.project_id
                                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("LEGACY");
                assertThat(result.getString(2)).isEqualTo("IN_PROGRESS");
                assertThat(result.getDate(3).toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            }
        }
    }

    @Test
    @DisplayName("Конкурентный повтор импорта сохраняет один кредитный снимок")
    void concurrentMortgageRepeat() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var id = preparationProject(owner);
        var req = preparationRequest(owner, id, "RSHB_RURAL_MORTGAGE");
        var other = new Browser();
        other.csrf();
        other.email = owner.email;
        other.login();
        String path = "/projects/" + id + "/mortgage-approval/import";
        var command =
                Map.of(
                        "version",
                        req.path("version").asLong(),
                        "demoConsent",
                        true,
                        "idempotencyKey",
                        UUID.randomUUID());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var a =
                    pool.submit(() -> {
                        gate.await();
                        return owner.request("POST", path, command, true);
                    });
            var b =
                    pool.submit(() -> {
                        gate.await();
                        return other.request("POST", path, command, true);
                    });
            gate.countDown();
            var first = a.get(20, TimeUnit.SECONDS);
            var second = b.get(20, TimeUnit.SECONDS);
            assertThat(first.statusCode()).isEqualTo(200);
            assertThat(second.statusCode()).isEqualTo(200);
            assertThat(json(first)).isEqualTo(json(second));
        }
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from mortgage_approval_snapshots where"
                                        + " project_id=?",
                                Integer.class,
                                UUID.fromString(id)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Отправка неполной заявки указывает поле, старый импорт не меняет обновлённый источник")
    void requestFieldsAndStaleMortgage() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var builder = new Browser().account("CONTRACTOR");
        var id = preparationProject(owner);
        owner.assign(id, builder.email);
        var response =
                owner.request(
                        "POST", "/projects/" + id + "/request/submit", Map.of("version", 0), true);
        error(response, 400, "VALIDATION_ERROR");
        assertThat(json(response).path("fieldErrors").path("description").asText()).isNotBlank();
        var req = preparationRequest(owner, id, "RSHB_RURAL_MORTGAGE");
        long version = req.path("version").asLong();
        owner.request(
                "PATCH",
                "/projects/" + id + "/request",
                Map.of("version", version, "description", "Уточнено"),
                true);
        error(
                owner.request(
                        "POST",
                        "/projects/" + id + "/mortgage-approval/import",
                        Map.of(
                                "version",
                                version,
                                "demoConsent",
                                true,
                                "idempotencyKey",
                                UUID.randomUUID()),
                        true),
                409,
                "STALE_REQUEST_VERSION");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from mortgage_approval_snapshots where"
                                        + " project_id=?",
                                Integer.class,
                                UUID.fromString(id)))
                .isZero();
    }

    record Work(
            Browser owner,
            Browser builder,
            Browser outsider,
            String project,
            String stage,
            String root) {}

    Work journalWork(boolean start) throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var builder = new Browser().account("CONTRACTOR");
        var outsider = new Browser().account("CUSTOMER");
        String project = owner.project();
        owner.assign(project, builder.email);
        var stage = createStage(owner, project, "Фундамент");
        String id = stage.path("id").asText();
        if (start)
            assertThat(
                            builder.request(
                                            "POST",
                                            "/projects/" + project + "/stages/" + id + "/start",
                                            Map.of("version", 0),
                                            true)
                                    .statusCode())
                    .isEqualTo(200);
        return new Work(
                owner,
                builder,
                outsider,
                project,
                id,
                "/projects/" + project + "/stages/" + id + "/journal");
    }

    Map<String, Object> journalBody(String kind, String text) {
        var body = new HashMap<String, Object>();
        body.put("kind", kind);
        body.put("text", text);
        body.put("photos", List.of());
        body.put("sourceEntryId", null);
        body.put("idempotencyKey", UUID.randomUUID());
        return body;
    }

    @Test
    @DisplayName("Фото с подписями, вопрос и ответ сохраняются, повтор не дублирует результат")
    void journalLifecycle() throws Exception {
        var w = journalWork(true);
        var body = journalBody("NOTE", "Установлена опалубка");
        body.put(
                "photos",
                List.of(
                        Map.of("url", "https://example.test/a.png", "caption", "Опалубка"),
                        Map.of("url", "https://example.test/b.png", "caption", "Армирование")));
        var first = w.builder.request("POST", w.root, body, true);
        assertThat(first.statusCode()).isEqualTo(201);
        var entry = json(first);
        assertThat(entry.path("photos").size()).isEqualTo(2);
        assertThat(entry.path("author").path("email").asText()).isEqualTo(w.builder.email);
        assertThat(entry.has("authorEmail")).isFalse();
        var duplicate = w.builder.request("POST", w.root, body, true);
        assertThat(duplicate.statusCode()).isEqualTo(200);
        assertThat(json(duplicate)).isEqualTo(entry);
        body.put("text", "Иное тело");
        error(w.builder.request("POST", w.root, body, true), 409, "IDEMPOTENCY_CONFLICT");
        var questionBody = journalBody("QUESTION", "Какой цвет фасада выбрать?");
        var question = json(w.builder.request("POST", w.root, questionBody, true));
        String responsePath = w.root + "/" + question.path("id").asText() + "/response";
        var answer = new HashMap<String, Object>();
        answer.put("version", 0);
        answer.put("idempotencyKey", UUID.randomUUID());
        answer.put("text", "Белый");
        answer.put("decision", null);
        var response = w.owner.request("POST", responsePath, answer, true);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).path("status").asText()).isEqualTo("ANSWERED");
        assertThat(json(w.owner.request("POST", responsePath, answer, true)))
                .isEqualTo(json(response));
        assertThat(json(w.builder.request("POST", w.root, questionBody, true))).isEqualTo(question);
        var page = json(w.owner.request("GET", w.root, null, false));
        assertThat(page.path("items").size()).isEqualTo(2);
        assertThat(page.path("awaitingCount").asInt()).isZero();
        assertThat(page.path("items").get(0).path("response").path("text").asText())
                .isEqualTo("Белый");
    }

    @Test
    @DisplayName("Лента изолирована по проекту, этапу и роли; неверное фото откатывает публикацию")
    void journalIsolation() throws Exception {
        var w = journalWork(true);
        error(
                w.owner.request("POST", w.root, journalBody("NOTE", "Не моя роль"), true),
                403,
                "FORBIDDEN");
        error(w.outsider.request("GET", w.root, null, false), 404, "PROJECT_NOT_FOUND");
        error(
                w.builder.request("POST", w.root, journalBody("NOTE", "Без CSRF"), false),
                403,
                "INVALID_CSRF_TOKEN");
        var body = journalBody("NOTE", "Фото");
        body.put("photos", List.of(Map.of("url", "javascript:alert(1)", "caption", "Подпись")));
        error(w.builder.request("POST", w.root, body, true), 400, "VALIDATION_ERROR");
        assertThat(json(w.owner.request("GET", w.root, null, false)).path("items").isEmpty())
                .isTrue();
        var entry =
                json(w.builder.request("POST", w.root, journalBody("QUESTION", "Вопрос"), true));
        String path = w.root + "/" + entry.path("id").asText();
        error(w.outsider.request("GET", path, null, false), 404, "PROJECT_NOT_FOUND");
        var other = journalWork(true);
        error(
                other.owner.request(
                        "GET", other.root + "/" + entry.path("id").asText(), null, false),
                404,
                "JOURNAL_ENTRY_NOT_FOUND");
        var reply = new HashMap<String, Object>();
        reply.put("version", 0);
        reply.put("idempotencyKey", UUID.randomUUID());
        reply.put("text", "Ответ");
        reply.put("decision", null);
        error(w.builder.request("POST", path + "/response", reply, true), 403, "FORBIDDEN");
    }

    @Test
    @DisplayName(
            "До начала публикации закрыты, после завершения разрешены отчёт и ответ на старый"
                    + " вопрос")
    void journalStageStates() throws Exception {
        var w = journalWork(false);
        error(
                w.builder.request("POST", w.root, journalBody("NOTE", "Рано"), true),
                409,
                "JOURNAL_STAGE_STATE");
        w.builder.request(
                "POST",
                "/projects/" + w.project + "/stages/" + w.stage + "/start",
                Map.of("version", 0),
                true);
        var q =
                json(
                        w.builder.request(
                                "POST", w.root, journalBody("QUESTION", "Открытый вопрос"), true));
        w.builder.request(
                "POST",
                "/projects/" + w.project + "/stages/" + w.stage + "/complete",
                Map.of("version", 1),
                true);
        error(
                w.builder.request("POST", w.root, journalBody("APPROVAL", "Новый вопрос"), true),
                409,
                "JOURNAL_STAGE_STATE");
        assertThat(
                        w.builder
                                .request("POST", w.root, journalBody("NOTE", "Итоги"), true)
                                .statusCode())
                .isEqualTo(201);
        var answer = new HashMap<String, Object>();
        answer.put("version", 0);
        answer.put("idempotencyKey", UUID.randomUUID());
        answer.put("text", "Ответ после завершения");
        answer.put("decision", null);
        assertThat(
                        w.owner
                                .request(
                                        "POST",
                                        w.root + "/" + q.path("id").asText() + "/response",
                                        answer,
                                        true)
                                .statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName(
            "Согласование в ленте не меняет сроки этапа и требует причину отрицательного решения")
    void journalApproval() throws Exception {
        var w = journalWork(true);
        var before =
                json(
                        w.owner.request(
                                "GET",
                                "/projects/" + w.project + "/stages/" + w.stage,
                                null,
                                false));
        var entry =
                json(
                        w.builder.request(
                                "POST",
                                w.root,
                                journalBody("APPROVAL", "Согласовать образец"),
                                true));
        String path = w.root + "/" + entry.path("id").asText() + "/response";
        var answer = new HashMap<String, Object>();
        answer.put("version", 0);
        answer.put("idempotencyKey", UUID.randomUUID());
        answer.put("text", null);
        answer.put("decision", "DECLINED");
        error(w.owner.request("POST", path, answer, true), 400, "VALIDATION_ERROR");
        answer.put("text", "Нужен другой образец");
        var response = w.owner.request("POST", path, answer, true);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).path("status").asText()).isEqualTo("DECLINED");
        assertThat(
                        json(
                                w.owner.request(
                                        "GET",
                                        "/projects/" + w.project + "/stages/" + w.stage,
                                        null,
                                        false)))
                .isEqualTo(before);
    }

    @Test
    @DisplayName("Два конкурентных ответа сохраняют один результат без перезаписи")
    void concurrentJournalReplies() throws Exception {
        var w = journalWork(true);
        var q = json(w.builder.request("POST", w.root, journalBody("QUESTION", "Вопрос"), true));
        var other = new Browser();
        other.csrf();
        other.email = w.owner.email;
        other.login();
        var a = new HashMap<String, Object>();
        a.put("version", 0);
        a.put("idempotencyKey", UUID.randomUUID());
        a.put("text", "Первый");
        a.put("decision", null);
        var b = new HashMap<>(a);
        b.put("idempotencyKey", UUID.randomUUID());
        b.put("text", "Второй");
        String path = w.root + "/" + q.path("id").asText() + "/response";
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first =
                    pool.submit(() -> {
                        start.await();
                        return w.owner.request("POST", path, a, true);
                    });
            var second =
                    pool.submit(() -> {
                        start.await();
                        return other.request("POST", path, b, true);
                    });
            start.countDown();
            assertThat(
                            List.of(
                                    first.get(20, TimeUnit.SECONDS).statusCode(),
                                    second.get(20, TimeUnit.SECONDS).statusCode()))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from stage_journal_responses where entry_id=?",
                                Integer.class,
                                UUID.fromString(q.path("id").asText())))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Пагинация ленты устойчива, фильтр и полный счётчик ожиданий не зависят от страницы")
    void journalPagination() throws Exception {
        var w = journalWork(true);
        for (int i = 0; i < 23; i++)
            assertThat(
                            w.builder
                                    .request(
                                            "POST",
                                            w.root,
                                            journalBody(
                                                    i % 2 == 0 ? "QUESTION" : "NOTE",
                                                    "Запись " + i),
                                            true)
                                    .statusCode())
                    .isEqualTo(201);
        var first = json(w.owner.request("GET", w.root, null, false));
        assertThat(first.path("items").size()).isEqualTo(20);
        assertThat(first.path("awaitingCount").asInt()).isEqualTo(12);
        String cursor = first.path("nextCursor").asText();
        var next = json(w.owner.request("GET", w.root + "?cursor=" + cursor, null, false));
        assertThat(next.path("items").size()).isEqualTo(3);
        assertThat(next.path("nextCursor").isNull()).isTrue();
        var ids = new HashSet<String>();
        for (var e : first.path("items")) ids.add(e.path("id").asText());
        for (var e : next.path("items")) assertThat(ids.add(e.path("id").asText())).isTrue();
        var pending = json(w.owner.request("GET", w.root + "?awaitingOnly=true", null, false));
        assertThat(pending.path("items").size()).isEqualTo(12);
        error(
                w.owner.request("GET", w.root + "?cursor=invalid", null, false),
                400,
                "VALIDATION_ERROR");
    }

    @Test
    @DisplayName(
            "Отказ закрывает предложение, копия переносит только пожелания и изолирует подрядчиков")
    void proposalReissue() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var first = new Browser().account("CONTRACTOR");
        var second = new Browser().account("CONTRACTOR");
        String id = preparationProject(owner);
        owner.assign(id, first.email);
        var r = preparationRequest(owner, id, "RSHB_RURAL_MORTGAGE");
        owner.request(
                "POST",
                "/projects/" + id + "/request/submit",
                Map.of("version", r.path("version").asLong()),
                true);
        var p = saveProposal(first, id, proposal(first, id));
        String proposalPath = "/projects/" + id + "/proposals/" + p.path("id").asText();
        p = documentCommand(first, proposalPath, "submit", p);
        var rejection =
                Map.of("version", p.path("version").asLong(), "comment", "Не подходит стоимость");
        var rejected = owner.request("POST", proposalPath + "/reject", rejection, true);
        assertThat(rejected.statusCode()).isEqualTo(200);
        assertThat(json(rejected).path("state").asText()).isEqualTo("REJECTED");
        assertThat(json(owner.request("POST", proposalPath + "/reject", rejection, true)))
                .isEqualTo(json(rejected));
        error(
                first.request("POST", "/projects/" + id + "/proposals", null, true),
                409,
                "PREPARATION_CLOSED");
        String path = "/projects/" + id + "/request/reissue";
        error(first.request("GET", path, null, false), 404, "PROJECT_NOT_FOUND");
        error(
                owner.request(
                        "POST",
                        path,
                        Map.of("contractorEmail", first.email, "idempotencyKey", UUID.randomUUID()),
                        true),
                422,
                "INVALID_REISSUE_CONTRACTOR");
        var body = Map.of("contractorEmail", second.email, "idempotencyKey", UUID.randomUUID());
        var response = owner.request("POST", path, body, true);
        assertThat(response.statusCode()).isEqualTo(201);
        var copy = json(response);
        String target = copy.path("id").asText();
        assertThat(target).isNotEqualTo(id);
        assertThat(json(owner.request("POST", path, body, true))).isEqualTo(copy);
        assertCopiedWishes(owner, target);
        error(second.request("GET", "/projects/" + target, null, false), 404, "PROJECT_NOT_FOUND");
        owner.request(
                "POST", "/projects/" + target + "/request/submit", Map.of("version", 0), true);
        assertThat(second.request("GET", "/projects/" + target, null, false).statusCode())
                .isEqualTo(200);
        error(first.request("GET", "/projects/" + target, null, false), 404, "PROJECT_NOT_FOUND");
        error(second.request("GET", proposalPath, null, false), 404, "PROJECT_NOT_FOUND");
        assertThat(
                        json(owner.request(
                                        "GET", "/projects/" + target + "/proposals", null, false))
                                .isEmpty())
                .isTrue();
        assertThat(json(owner.request("GET", path, null, false)).path("targetProjectId").asText())
                .isEqualTo(target);
    }

    @FunctionalInterface
    interface ConcurrentRequest {
        HttpResponse<String> execute() throws Exception;
    }

    List<HttpResponse<String>> race(ConcurrentRequest first, ConcurrentRequest second)
            throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a =
                    pool.submit(() -> {
                        start.await();
                        return first.execute();
                    });
            var b =
                    pool.submit(() -> {
                        start.await();
                        return second.execute();
                    });
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("Завершение этапа и вопрос упорядочены без частичной публикации")
    void completionRacesQuestion() throws Exception {
        var w = journalWork(true);
        var second = new Browser();
        second.csrf();
        second.email = w.builder.email;
        second.login();
        var result =
                race(
                        () ->
                                w.builder.request(
                                        "POST",
                                        w.root,
                                        journalBody("QUESTION", "Вопрос во время работ"),
                                        true),
                        () ->
                                second.request(
                                        "POST",
                                        "/projects/"
                                                + w.project
                                                + "/stages/"
                                                + w.stage
                                                + "/complete",
                                        Map.of("version", 1),
                                        true));
        assertThat(result.get(1).statusCode()).isEqualTo(200);
        assertThat(result.get(0).statusCode()).isIn(201, 409);
        assertThat(json(w.owner.request("GET", w.root, null, false)).path("items").size())
                .isEqualTo(result.get(0).statusCode() == 201 ? 1 : 0);
    }

    @Test
    @DisplayName("Отказ и согласование сметы конкурируют за одно решение без смешанного состояния")
    void rejectionRacesApproval() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var builder = new Browser().account("CONTRACTOR");
        var id = preparationProject(owner);
        owner.assign(id, builder.email);
        var request = preparationRequest(owner, id, "OWN_FUNDS");
        owner.request(
                "POST",
                "/projects/" + id + "/request/submit",
                Map.of("version", request.path("version").asLong()),
                true);
        var doc = saveProposal(builder, id, proposal(builder, id));
        String path = "/projects/" + id + "/proposals/" + doc.path("id").asText();
        var sent = documentCommand(builder, path, "submit", doc);
        var second = new Browser();
        second.csrf();
        second.email = owner.email;
        second.login();
        var result =
                race(
                        () ->
                                owner.request(
                                        "POST",
                                        path + "/reject",
                                        Map.of(
                                                "version",
                                                sent.path("version").asLong(),
                                                "comment",
                                                "Не подходит"),
                                        true),
                        () ->
                                second.request(
                                        "POST",
                                        path + "/approve",
                                        Map.of("version", sent.path("version").asLong()),
                                        true));
        assertThat(result.stream().map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(200, 409);
        var project = json(owner.request("GET", "/projects/" + id, null, false));
        boolean rejected = result.get(0).statusCode() == 200;
        assertThat(project.path("preparationClosed").asBoolean()).isEqualTo(rejected);
        assertThat(project.path("approvedProposalId").isNull()).isEqualTo(rejected);
        assertThat(json(owner.request("GET", "/projects/" + id + "/stages", null, false)).size())
                .isEqualTo(rejected ? 0 : 1);
    }

    @Test
    @DisplayName(
            "Конкурентное копирование создаёт одну карточку, ошибка адресата не оставляет копию")
    void reissueRaceAndRollback() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var builder = new Browser().account("CONTRACTOR");
        var next = new Browser().account("CONTRACTOR");
        var id = preparationProject(owner);
        owner.assign(id, builder.email);
        var request = preparationRequest(owner, id, "OWN_FUNDS");
        owner.request(
                "POST",
                "/projects/" + id + "/request/submit",
                Map.of("version", request.path("version").asLong()),
                true);
        var doc = saveProposal(builder, id, proposal(builder, id));
        String path = "/projects/" + id + "/proposals/" + doc.path("id").asText();
        var sent = documentCommand(builder, path, "submit", doc);
        owner.request(
                "POST",
                path + "/reject",
                Map.of("version", sent.path("version").asLong(), "comment", "Другой подрядчик"),
                true);
        String reissue = "/projects/" + id + "/request/reissue";
        int before = json(owner.request("GET", "/projects", null, false)).size();
        error(
                owner.request(
                        "POST",
                        reissue,
                        Map.of("contractorEmail", owner.email, "idempotencyKey", UUID.randomUUID()),
                        true),
                422,
                "CONTRACTOR_NOT_FOUND");
        assertThat(json(owner.request("GET", "/projects", null, false)).size()).isEqualTo(before);
        var second = new Browser();
        second.csrf();
        second.email = owner.email;
        second.login();
        var body =
                Map.of(
                        "contractorEmail",
                        next.email.toUpperCase(Locale.ROOT),
                        "idempotencyKey",
                        UUID.randomUUID());
        var result =
                race(
                        () -> owner.request("POST", reissue, body, true),
                        () -> second.request("POST", reissue, body, true));
        assertThat(result.stream().map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(201, 200);
        assertThat(json(result.get(0))).isEqualTo(json(result.get(1)));
        assertThat(json(owner.request("GET", "/projects", null, false)).size())
                .isEqualTo(before + 1);
        var changed = new HashMap<String, Object>(body);
        changed.put("contractorEmail", builder.email);
        error(owner.request("POST", reissue, changed, true), 409, "IDEMPOTENCY_CONFLICT");
    }

    @ParameterizedTest(name = "Версия схемы: {0}")
    @ValueSource(strings = {"4", "5"})
    @DisplayName("Миграция заполненной прежней схемы сохраняет документы, этап и журнал")
    void migrationFromFilledV4(String previousVersion) throws Exception {
        String schema = "v" + previousVersion + "_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target(previousVersion)
                .load()
                .migrate();
        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            statement.execute(
"""
insert into accounts values
  ('00000000-0000-0000-0000-000000000001','v4owner@example.test','CUSTOMER','hash',now()),
  ('00000000-0000-0000-0000-000000000002','v4builder@example.test','CONTRACTOR','hash',now());
insert into house_projects(id,name,address,customer_id,contractor_id,created_at)
  values('00000000-0000-0000-0000-000000000003','Дом','Участок','00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',now());
insert into construction_requests(id,project_id,location,state)
  values('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000003','Участок','DRAFT');
insert into construction_proposals(id,project_id,request_id,revision_no,state,estimate_text,total_price,created_by,created_at,submitted_at)
  values('00000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000004',1,'APPROVED','Сохранённая смета',300000,'00000000-0000-0000-0000-000000000002',now(),now());
insert into construction_contracts(id,project_id,proposal_id,revision_no,state,contract_text,created_by,created_at,submitted_at)
  values('00000000-0000-0000-0000-000000000006','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000005',1,'APPROVED','Сохранённый договор','00000000-0000-0000-0000-000000000002',now(),now());
insert into construction_stages(id,project_id,name,work_type,position,status,actual_start)
  values('00000000-0000-0000-0000-000000000007','00000000-0000-0000-0000-000000000003','Фундамент','FOUNDATION',1,'IN_PROGRESS','2026-11-01');
""");
            if (previousVersion.equals("5")) {
                statement.execute(
"""
insert into stage_journal_entries(id,project_id,stage_id,author_id,author_email,author_role,kind,status,text,created_at)
values('00000000-0000-0000-0000-000000000008','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000007','00000000-0000-0000-0000-000000000002','v4builder@example.test','CONTRACTOR','NOTE','PUBLISHED','Прежний отчёт',now());
insert into journal_commands(id,actor_id,project_id,operation,idempotency_key,request_hash,entry_id,result_json,created_at)
values('00000000-0000-0000-0000-000000000009','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003','PUBLISH','00000000-0000-0000-0000-000000000010',repeat('a',64),'00000000-0000-0000-0000-000000000008','{"legacy":"unchanged"}',now());
""");
            }
            Flyway.configure()
                    .dataSource(
                            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema)
                    .defaultSchema(schema)
                    .load()
                    .migrate();
            try (var rows =
                    statement.executeQuery(
"""
select p.preparation_closed,s.status,s.actual_start,c.contract_text,d.estimate_text
from house_projects p join construction_stages s on s.project_id=p.id
join construction_contracts c on c.project_id=p.id join construction_proposals d on d.project_id=p.id
""")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isFalse();
                assertThat(rows.getString(2)).isEqualTo("IN_PROGRESS");
                assertThat(rows.getDate(3).toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 1));
                assertThat(rows.getString(4)).isEqualTo("Сохранённый договор");
                assertThat(rows.getString(5)).isEqualTo("Сохранённая смета");
            }
            if (previousVersion.equals("5")) {
                try (var rows =
                        statement.executeQuery(
                                "select e.text,e.author_snapshot,c.result_json->>'legacy' from"
                                        + " stage_journal_entries e join journal_commands c on"
                                        + " c.entry_id=e.id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("Прежний отчёт");
                    assertThat(rows.getObject(2)).isNull();
                    assertThat(rows.getString(3)).isEqualTo("unchanged");
                }
            }
        } finally {
            jdbc.execute("drop schema " + schema + " cascade");
        }
    }

    Browser employee(String name) throws Exception {
        var employee = new Browser();
        employee.csrf();
        employee.email = UUID.randomUUID() + "@example.test";
        assertThat(
                        employee.request(
                                        "POST",
                                        "/auth/register",
                                        Map.of(
                                                "email",
                                                employee.email,
                                                "password",
                                                PASSWORD,
                                                "role",
                                                "CONTRACTOR",
                                                "displayName",
                                                name),
                                        true)
                                .statusCode())
                .isEqualTo(201);
        employee.login();
        return employee;
    }

    String organizationId(Browser director) throws Exception {
        return json(director.request("GET", "/organizations/me", null, false))
                .path("organization")
                .path("id")
                .asText();
    }

    JsonNode invite(Browser director, Browser employee, String position) throws Exception {
        var response =
                director.request(
                        "POST",
                        "/organizations/" + organizationId(director) + "/invitations",
                        Map.of(
                                "recipientEmail",
                                employee.email,
                                "position",
                                position,
                                "idempotencyKey",
                                UUID.randomUUID()),
                        true);
        assertThat(response.statusCode()).isEqualTo(201);
        return json(response);
    }

    JsonNode join(Browser director, Browser employee, String position) throws Exception {
        var invitation = invite(director, employee, position);
        assertThat(
                        employee.request(
                                        "POST",
                                        "/organizations/invitations/"
                                                + invitation.path("id").asText()
                                                + "/accept",
                                        Map.of("version", 0),
                                        true)
                                .statusCode())
                .isEqualTo(200);
        return json(employee.request("GET", "/organizations/me", null, false)).path("membership");
    }

    void team(Browser director, String project, List<Map<String, Object>> members, long version)
            throws Exception {
        var response =
                director.request(
                        "PUT",
                        "/projects/" + project + "/contractor-team",
                        Map.of("version", version, "members", members),
                        true);
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
    }

    Map<String, Object> grant(JsonNode member, boolean documents, boolean works) {
        return Map.of(
                "membershipId", member.path("id").asText(), "documents", documents, "works", works);
    }

    @Test
    @DisplayName(
            "Прежний аккаунт читает проекты, заполняет имя перед записью и получает актуальный"
                    + " профиль")
    void legacyProfileCompletion() throws Exception {
        var actor = new Browser();
        actor.csrf();
        actor.email = UUID.randomUUID() + "@example.test";
        var registered =
                actor.request(
                        "POST",
                        "/auth/register",
                        Map.of("email", actor.email, "password", PASSWORD, "role", "CUSTOMER"),
                        true);
        assertThat(registered.statusCode()).isEqualTo(201);
        actor.login();
        assertThat(actor.request("GET", "/projects", null, false).statusCode()).isEqualTo(200);
        error(
                actor.request(
                        "POST", "/projects", Map.of("name", "Дом", "address", "Участок"), true),
                409,
                "PROFILE_REQUIRED");
        error(
                actor.request(
                        "PATCH", "/auth/profile", Map.of("version", 0, "displayName", " "), true),
                400,
                "VALIDATION_ERROR");
        var named =
                actor.request(
                        "PATCH",
                        "/auth/profile",
                        Map.of("version", 0, "displayName", "Иван"),
                        true);
        assertThat(named.statusCode()).isEqualTo(200);
        assertThat(json(actor.request("GET", "/auth/me", null, false)).path("displayName").asText())
                .isEqualTo("Иван");
        error(
                actor.request(
                        "PATCH",
                        "/auth/profile",
                        Map.of("version", 0, "displayName", "Другое имя"),
                        true),
                409,
                "STALE_PROFILE_VERSION");
        error(
                actor.request(
                        "PATCH",
                        "/auth/profile",
                        Map.of("version", 1, "displayName", "Иван", "role", "CONTRACTOR"),
                        true),
                400,
                "VALIDATION_ERROR");
        assertThat(
                        actor.request(
                                        "POST",
                                        "/projects",
                                        Map.of("name", "Дом", "address", "Участок"),
                                        true)
                                .statusCode())
                .isEqualTo(201);
    }

    @Test
    @DisplayName(
            "Приглашение принимает только адресат; повтор не дублирует членство, должность не даёт"
                    + " прав на проекты")
    void organizationInvitationsAndIsolation() throws Exception {
        var director = new Browser().account("CONTRACTOR");
        var worker = employee("Алексей Орлов");
        var stranger = employee("Посторонний");
        var invitation = invite(director, worker, "Прораб");
        String path = "/organizations/invitations/" + invitation.path("id").asText() + "/accept";
        error(
                stranger.request("POST", path, Map.of("version", 0), true),
                404,
                "INVITATION_NOT_FOUND");
        assertThat(
                        json(worker.request("GET", "/organizations/me", null, false))
                                .path("membership")
                                .isNull())
                .isTrue();
        var accepted = worker.request("POST", path, Map.of("version", 0), true);
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(json(worker.request("POST", path, Map.of("version", 0), true)))
                .isEqualTo(json(accepted));
        var org = organizationId(director);
        error(
                worker.request(
                        "PATCH",
                        "/organizations/" + org,
                        Map.of(
                                "version",
                                0,
                                "displayName",
                                "Подмена",
                                "legalName",
                                "Подмена",
                                "address",
                                "Адрес"),
                        true),
                403,
                "FORBIDDEN");
        var owner = new Browser().account("CUSTOMER");
        var project = owner.project();
        owner.assign(project, director.email);
        error(worker.request("GET", "/projects/" + project, null, false), 404, "PROJECT_NOT_FOUND");
        assertThat(json(worker.request("GET", "/projects", null, false)).isEmpty()).isTrue();
    }

    @Test
    @DisplayName(
            "Юрист отправляет документ, прораб ведёт этап; чужая стройка и ипотека остаются"
                    + " закрыты")
    void projectTeamPermissionsAndAttribution() throws Exception {
        var owner = new Browser().account("CUSTOMER");
        var director = new Browser().account("CONTRACTOR");
        var lawyer = employee("Мария Соколова");
        var foreman = employee("Алексей Орлов");
        var lawMember = join(director, lawyer, "Юрист");
        var workMember = join(director, foreman, "Прораб");
        var project = preparationProject(owner);
        owner.assign(project, director.email);
        var request = preparationRequest(owner, project, "RSHB_RURAL_MORTGAGE");
        owner.request(
                "POST",
                "/projects/" + project + "/request/submit",
                Map.of("version", request.path("version").asLong()),
                true);
        team(
                director,
                project,
                List.of(grant(lawMember, true, false), grant(workMember, false, true)),
                0);
        String root = "/projects/" + project;
        var draft = saveProposal(director, project, proposal(director, project));
        String proposalPath = root + "/proposals/" + draft.path("id").asText();
        error(
                foreman.request(
                        "POST",
                        proposalPath + "/submit",
                        Map.of("version", draft.path("version").asLong()),
                        true),
                403,
                "FORBIDDEN");
        var sent = documentCommand(lawyer, proposalPath, "submit", draft);
        assertThat(sent.path("createdBySnapshot").path("displayName").asText())
                .isEqualTo("Сергей Иванов");
        assertThat(sent.path("submittedBySnapshot").path("displayName").asText())
                .isEqualTo("Мария Соколова");
        documentCommand(owner, proposalPath, "approve", sent);
        String stagePath = startStageWithTeam(owner, director, lawyer, foreman, root);
        assertJournalAttribution(owner, lawyer, foreman, stagePath);
        var other = owner.project();
        owner.assign(other, director.email);
        error(foreman.request("GET", "/projects/" + other, null, false), 404, "PROJECT_NOT_FOUND");
        error(
                foreman.request("GET", root + "/mortgage-approval", null, false),
                404,
                "PROJECT_NOT_FOUND");
        team(director, project, List.of(grant(lawMember, true, false)), 1);
        error(
                foreman.request(
                        "POST", stagePath + "/journal", journalBody("NOTE", "После отзыва"), true),
                404,
                "PROJECT_NOT_FOUND");
    }

    @Test
    @DisplayName(
            "Исключение отзывает открытую сессию, повторное вступление не восстанавливает"
                    + " назначения")
    void removalAndRejoinDoNotRestoreProjects() throws Exception {
        var director = new Browser().account("CONTRACTOR");
        var worker = employee("Алексей Орлов");
        var member = join(director, worker, "Прораб");
        var owner = new Browser().account("CUSTOMER");
        String project = owner.project();
        owner.assign(project, director.email);
        team(director, project, List.of(grant(member, false, true)), 0);
        assertThat(worker.request("GET", "/projects/" + project, null, false).statusCode())
                .isEqualTo(200);
        String org = organizationId(director),
                path =
                        "/organizations/"
                                + org
                                + "/members/"
                                + member.path("id").asText()
                                + "?expectedVersion=0";
        assertThat(director.request("DELETE", path, null, true).statusCode()).isEqualTo(204);
        assertThat(director.request("DELETE", path, null, true).statusCode()).isEqualTo(204);
        error(worker.request("GET", "/projects/" + project, null, false), 404, "PROJECT_NOT_FOUND");
        var replacement = join(director, worker, "Прораб");
        assertThat(replacement.path("id").asText()).isNotEqualTo(member.path("id").asText());
        error(worker.request("GET", "/projects/" + project, null, false), 404, "PROJECT_NOT_FOUND");
    }

    @Test
    @DisplayName("Одновременное принятие двух приглашений создаёт одно активное членство")
    void concurrentOrganizationJoining() throws Exception {
        var first = new Browser().account("CONTRACTOR");
        var second = new Browser().account("CONTRACTOR");
        var worker = employee("Мария Соколова");
        var a = invite(first, worker, "Юрист");
        var b = invite(second, worker, "Юрист");
        var another = new Browser();
        another.csrf();
        another.email = worker.email;
        another.login();
        var result =
                race(
                        () ->
                                worker.request(
                                        "POST",
                                        "/organizations/invitations/"
                                                + a.path("id").asText()
                                                + "/accept",
                                        Map.of("version", 0),
                                        true),
                        () ->
                                another.request(
                                        "POST",
                                        "/organizations/invitations/"
                                                + b.path("id").asText()
                                                + "/accept",
                                        Map.of("version", 0),
                                        true));
        assertThat(result.stream().map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(200, 409);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from contractor_memberships where account_id=? and"
                                        + " state='ACTIVE'",
                                Integer.class,
                                UUID.fromString(a.path("recipientAccountId").asText())))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Дополнительный ключ ожидающего приглашения сохраняет результат после принятия")
    void invitationAliasSurvivesAcceptance() throws Exception {
        var director = new Browser().account("CONTRACTOR");
        var worker = employee("Алексей Орлов");
        var initial = invite(director, worker, "Прораб");
        var path = "/organizations/" + organizationId(director) + "/invitations";
        var body = new HashMap<String, Object>();
        body.put("recipientEmail", worker.email);
        body.put("position", "Прораб");
        body.put("idempotencyKey", UUID.randomUUID().toString());
        var alias = director.request("POST", path, body, true);
        assertThat(alias.statusCode()).isEqualTo(200);
        assertThat(json(alias).path("id")).isEqualTo(initial.path("id"));
        assertThat(
                        worker.request(
                                        "POST",
                                        "/organizations/invitations/"
                                                + initial.path("id").asText()
                                                + "/accept",
                                        Map.of("version", 0),
                                        true)
                                .statusCode())
                .isEqualTo(200);
        var repeated = director.request("POST", path, body, true);
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(json(repeated).path("id")).isEqualTo(initial.path("id"));
        body.put("position", "Юрист");
        error(director.request("POST", path, body, true), 409, "IDEMPOTENCY_CONFLICT");
    }

    @Test
    @DisplayName("Исключение сотрудника и публикация имеют единый порядок без частичной записи")
    void removalRacesPublication() throws Exception {
        var w = journalWork(true);
        var worker = employee("Алексей Орлов");
        var member = join(w.builder, worker, "Прораб");
        team(w.builder, w.project, List.of(grant(member, false, true)), 0);
        var result =
                race(
                        () ->
                                worker.request(
                                        "POST",
                                        w.root,
                                        journalBody("NOTE", "Отчёт во время отзыва"),
                                        true),
                        () ->
                                w.builder.request(
                                        "DELETE",
                                        "/organizations/"
                                                + organizationId(w.builder)
                                                + "/members/"
                                                + member.path("id").asText()
                                                + "?expectedVersion=0",
                                        null,
                                        true));
        assertThat(result.get(1).statusCode()).isEqualTo(204);
        assertThat(result.get(0).statusCode()).isIn(201, 404);
        assertThat(json(w.owner.request("GET", w.root, null, false)).path("items").size())
                .isEqualTo(result.get(0).statusCode() == 201 ? 1 : 0);
        error(
                worker.request("POST", w.root, journalBody("NOTE", "После отзыва"), true),
                404,
                "PROJECT_NOT_FOUND");
    }

    byte[] demoPng() throws Exception {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    @Test
    @DisplayName(
            "Загруженные фото и PDF публикуются в одной ленте; повторы и закрытый доступ"
                    + " сохраняются")
    void privateAttachmentsLifecycle() throws Exception {
        var w = journalWork(true);
        var png = demoPng();
        var key = UUID.randomUUID();
        var uploaded =
                w.builder.upload(
                        w.project,
                        w.stage,
                        key,
                        png,
                        "foundation.png",
                        "Армирование фундамента",
                        true);
        assertThat(uploaded.statusCode()).isEqualTo(200);
        var file = json(uploaded);
        assertThat(file.path("contentType").asText()).isEqualTo("image/png");
        assertThat(
                        json(
                                w.builder.upload(
                                        w.project,
                                        w.stage,
                                        key,
                                        png,
                                        "foundation.png",
                                        "Армирование фундамента",
                                        true)))
                .isEqualTo(file);
        error(
                w.builder.upload(
                        w.project,
                        w.stage,
                        key,
                        png,
                        "changed.png",
                        "Армирование фундамента",
                        true),
                409,
                "IDEMPOTENCY_CONFLICT");
        String content =
                "/projects/" + w.project + "/attachments/" + file.path("id").asText() + "/content";
        error(w.owner.request("GET", content, null, false), 404, "ATTACHMENT_NOT_FOUND");
        var pdf =
                json(
                        w.builder.upload(
                                w.project,
                                w.stage,
                                UUID.randomUUID(),
                                "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF\n".getBytes(),
                                "passport.pdf",
                                "Паспорт материала",
                                true));
        var body = journalBody("NOTE", "Армирование проверено, приложен паспорт материала");
        body.put("attachmentIds", List.of(file.path("id").asText(), pdf.path("id").asText()));
        var entry = w.builder.request("POST", w.root, body, true);
        assertThat(entry.statusCode()).isEqualTo(201);
        assertThat(json(entry).path("attachments").size()).isEqualTo(2);
        assertThat(json(w.builder.request("POST", w.root, body, true))).isEqualTo(json(entry));
        var download = w.owner.request("GET", content, null, false);
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.headers().firstValue("content-type").orElseThrow())
                .isEqualTo("image/png");
        assertThat(download.headers().firstValue("x-content-type-options").orElseThrow())
                .isEqualTo("nosniff");
        error(w.outsider.request("GET", content, null, false), 404, "PROJECT_NOT_FOUND");
        assertThat(
                        jdbc.queryForObject(
                                "select retained from attachments where id=?",
                                Boolean.class,
                                UUID.fromString(file.path("id").asText())))
                .isTrue();
    }

    @Test
    @DisplayName("Неверное содержимое, CSRF и чужие файлы не создают частичную публикацию")
    void attachmentValidationAndIsolation() throws Exception {
        var w = journalWork(true);
        var other = journalWork(true);
        error(
                w.builder.upload(
                        w.project,
                        w.stage,
                        UUID.randomUUID(),
                        "<script>alert(1)</script>".getBytes(),
                        "photo.png",
                        "Подмена",
                        true),
                400,
                "VALIDATION_ERROR");
        error(
                w.builder.upload(
                        w.project,
                        w.stage,
                        UUID.randomUUID(),
                        demoPng(),
                        "photo.png",
                        "Фото",
                        false),
                403,
                "INVALID_CSRF_TOKEN");
        var file =
                json(
                        other.builder.upload(
                                other.project,
                                other.stage,
                                UUID.randomUUID(),
                                demoPng(),
                                "photo.png",
                                "Чужой файл",
                                true));
        var body = journalBody("NOTE", "Недопустимый файл");
        body.put("attachmentIds", List.of(file.path("id").asText()));
        error(w.builder.request("POST", w.root, body, true), 404, "ATTACHMENT_NOT_FOUND");
        assertThat(json(w.owner.request("GET", w.root, null, false)).path("items").isEmpty())
                .isTrue();
    }

    record Ready(Work work, JsonNode entry, JsonNode stage) {}

    Ready readyForAcceptance(boolean mortgage) throws Exception {
        var w = journalWork(true);
        if (mortgage) preparationRequest(w.owner, w.project, "RSHB_RURAL_MORTGAGE");
        var file =
                json(
                        w.builder.upload(
                                w.project,
                                w.stage,
                                UUID.randomUUID(),
                                demoPng(),
                                "foundation.png",
                                "Армирование",
                                true));
        var body = journalBody("NOTE", "Армирование проверено");
        body.put("attachmentIds", List.of(file.path("id").asText()));
        var entry = json(w.builder.request("POST", w.root, body, true));
        var stage =
                w.builder.request(
                        "POST",
                        "/projects/" + w.project + "/stages/" + w.stage + "/complete",
                        Map.of("version", 1),
                        true);
        assertThat(stage.statusCode()).isEqualTo(200);
        return new Ready(w, entry, json(stage));
    }

    Map<String, Object> submissionBody(Ready fixture, String summary, JsonNode entry) {
        return Map.of(
                "expectedStageVersion",
                fixture.stage.path("version").asLong(),
                "summary",
                summary,
                "entryIds",
                List.of(entry.path("id").asText()),
                "idempotencyKey",
                UUID.randomUUID());
    }

    JsonNode submitStage(Ready fixture, String summary) throws Exception {
        var w = fixture.work;
        var response =
                w.builder.request(
                        "POST",
                        w.root.replace("/journal", "/acceptance"),
                        submissionBody(fixture, summary, fixture.entry),
                        true);
        assertThat(response.statusCode()).isEqualTo(201);
        return json(response);
    }

    JsonNode decideStage(Ready fixture, JsonNode submission, String decision, String comment)
            throws Exception {
        var w = fixture.work;
        var body = new HashMap<String, Object>();
        body.put("expectedVersion", submission.path("version").asLong());
        body.put("decision", decision);
        body.put("idempotencyKey", UUID.randomUUID());
        if (comment != null) body.put("comment", comment);
        var response =
                w.owner.request(
                        "POST",
                        w.root.replace("/journal", "/acceptance/")
                                + submission.path("id").asText()
                                + "/decision",
                        body,
                        true);
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response);
    }

    JsonNode acceptStage(Ready fixture) throws Exception {
        return decideStage(fixture, submitStage(fixture, "Этап выполнен"), "ACCEPT", null);
    }

    JsonNode connectCase(Ready fixture) throws Exception {
        var w = fixture.work;
        var response =
                w.owner.request(
                        "POST",
                        "/projects/" + w.project + "/bank-case",
                        Map.of("consent", true, "idempotencyKey", UUID.randomUUID()),
                        true);
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response);
    }

    @Test
    @DisplayName("Возврат и повторная сдача сохраняют фото, исходные даты и персональную историю")
    void acceptanceCorrectionAndSnapshots() throws Exception {
        var f = readyForAcceptance(false);
        var w = f.work;
        String root = w.root.replace("/journal", "/acceptance");
        var body = submissionBody(f, "Первичная сдача", f.entry);
        var first = w.builder.request("POST", root, body, true);
        assertThat(first.statusCode()).isEqualTo(201);
        var submission = json(first);
        assertThat(json(w.builder.request("POST", root, body, true))).isEqualTo(submission);
        error(
                w.builder.request(
                        "POST",
                        root + "/" + submission.path("id").asText() + "/decision",
                        Map.of(
                                "expectedVersion",
                                0,
                                "decision",
                                "ACCEPT",
                                "idempotencyKey",
                                UUID.randomUUID()),
                        true),
                403,
                "FORBIDDEN");
        var empty =
                w.owner.request(
                        "POST",
                        root + "/" + submission.path("id").asText() + "/decision",
                        Map.of(
                                "expectedVersion",
                                0,
                                "decision",
                                "RETURN",
                                "comment",
                                "   ",
                                "idempotencyKey",
                                UUID.randomUUID()),
                        true);
        error(empty, 400, "VALIDATION_ERROR");
        assertThat(json(empty).path("fieldErrors").has("comment")).isTrue();
        decideStage(f, submission, "RETURN", "Добавьте фото исправления армирования");
        var fixedFile =
                json(
                        w.builder.upload(
                                w.project,
                                w.stage,
                                UUID.randomUUID(),
                                demoPng(),
                                "fixed.png",
                                "Армирование исправлено",
                                true));
        var note = journalBody("NOTE", "Армирование исправлено по замечанию");
        note.put("attachmentIds", List.of(fixedFile.path("id").asText()));
        var fixedEntry = json(w.builder.request("POST", w.root, note, true));
        var second =
                w.builder.request(
                        "POST", root, submissionBody(f, "Исправления выполнены", fixedEntry), true);
        assertThat(second.statusCode()).isEqualTo(201);
        decideStage(f, json(second), "ACCEPT", null);
        var page = json(w.owner.request("GET", root, null, false));
        assertThat(page.path("state").asText()).isEqualTo("ACCEPTED");
        assertThat(page.path("submissions").size()).isEqualTo(2);
        var stage =
                json(
                        w.owner.request(
                                "GET",
                                "/projects/" + w.project + "/stages/" + w.stage,
                                null,
                                false));
        assertThat(stage.path("actualFinish")).isEqualTo(f.stage.path("actualFinish"));
        assertThat(
                        w.builder
                                .request(
                                        "PATCH",
                                        "/auth/profile",
                                        Map.of("version", 0, "displayName", "Новое имя"),
                                        true)
                                .statusCode())
                .isEqualTo(200);
        assertThat(json(w.builder.request("POST", root, body, true))).isEqualTo(submission);
    }

    @Test
    @DisplayName("Сдача требует реальные фото своего этапа; посторонний и заказчик не обходят роли")
    void acceptanceEvidenceAndAccess() throws Exception {
        var f = readyForAcceptance(false);
        var w = f.work;
        var other = readyForAcceptance(false);
        String root = w.root.replace("/journal", "/acceptance");
        error(
                w.builder.request(
                        "POST", root, submissionBody(f, "Чужое подтверждение", other.entry), true),
                404,
                "JOURNAL_ENTRY_NOT_FOUND");
        var plain =
                json(
                        w.builder.request(
                                "POST",
                                w.root,
                                journalBody("NOTE", "Только текст без фотографии"),
                                true));
        error(
                w.builder.request("POST", root, submissionBody(f, "Без фото", plain), true),
                400,
                "VALIDATION_ERROR");
        error(
                w.owner.request(
                        "POST", root, submissionBody(f, "Сам себе подрядчик", f.entry), true),
                403,
                "FORBIDDEN");
        error(w.outsider.request("GET", root, null, false), 404, "PROJECT_NOT_FOUND");
        assertThat(json(w.owner.request("GET", root, null, false)).path("submissions").isEmpty())
                .isTrue();
    }

    @Test
    @DisplayName(
            "Конкурентные повторы создают одну сдачу, конкурирующие решения фиксируют один"
                    + " результат")
    void acceptanceConcurrency() throws Exception {
        var f = readyForAcceptance(false);
        var w = f.work;
        String root = w.root.replace("/journal", "/acceptance");
        var another = new Browser();
        another.csrf();
        another.email = w.builder.email;
        another.login();
        var body = submissionBody(f, "Одновременная сдача", f.entry);
        var submissions =
                race(
                        () -> w.builder.request("POST", root, body, true),
                        () -> another.request("POST", root, body, true));
        assertThat(submissions.stream().map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(200, 201);
        assertThat(json(submissions.get(0))).isEqualTo(json(submissions.get(1)));
        var submission = json(submissions.get(0));
        var ownerTwo = new Browser();
        ownerTwo.csrf();
        ownerTwo.email = w.owner.email;
        ownerTwo.login();
        String decision = root + "/" + submission.path("id").asText() + "/decision";
        var decisions =
                race(
                        () ->
                                w.owner.request(
                                        "POST",
                                        decision,
                                        Map.of(
                                                "expectedVersion",
                                                0,
                                                "decision",
                                                "ACCEPT",
                                                "idempotencyKey",
                                                UUID.randomUUID()),
                                        true),
                        () ->
                                ownerTwo.request(
                                        "POST",
                                        decision,
                                        Map.of(
                                                "expectedVersion",
                                                0,
                                                "decision",
                                                "RETURN",
                                                "comment",
                                                "Исправить",
                                                "idempotencyKey",
                                                UUID.randomUUID()),
                                        true));
        assertThat(decisions.stream().map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(200, 409);
    }

    @Test
    @DisplayName(
            "Завершение требует приёмки всех этапов, сохраняет дату повтора и закрывает новые"
                    + " работы")
    void constructionCompletion() throws Exception {
        var f = readyForAcceptance(false);
        var w = f.work;
        String root = "/projects/" + w.project + "/completion";
        var before = json(w.owner.request("GET", root, null, false));
        error(
                w.owner.request(
                        "POST",
                        root,
                        Map.of(
                                "expectedVersion",
                                before.path("version").asLong(),
                                "idempotencyKey",
                                UUID.randomUUID()),
                        true),
                409,
                "CONSTRUCTION_NOT_READY");
        acceptStage(f);
        var ready = json(w.owner.request("GET", root, null, false));
        assertThat(ready.path("canComplete").asBoolean()).isTrue();
        error(
                w.owner.request(
                        "POST",
                        root,
                        Map.of(
                                "expectedVersion",
                                before.path("version").asLong(),
                                "idempotencyKey",
                                UUID.randomUUID()),
                        true),
                409,
                "STALE_ACCEPTANCE_VERSION");
        var body = new HashMap<String, Object>();
        body.put("expectedVersion", ready.path("version").asLong());
        body.put("idempotencyKey", UUID.randomUUID());
        var complete = w.owner.request("POST", root, body, true);
        assertThat(complete.statusCode()).isEqualTo(200);
        assertThat(json(complete).path("completedAt").isNull()).isFalse();
        assertThat(json(w.owner.request("POST", root, body, true))).isEqualTo(json(complete));
        body.put("expectedVersion", 0);
        error(w.owner.request("POST", root, body, true), 409, "IDEMPOTENCY_CONFLICT");
        error(
                w.builder.request("POST", w.root, journalBody("NOTE", "После завершения"), true),
                409,
                "CONSTRUCTION_COMPLETED");
        error(
                w.owner.request(
                        "POST",
                        "/projects/" + w.project + "/stages",
                        Map.of("name", "Новый этап", "workType", "OTHER"),
                        true),
                409,
                "CONSTRUCTION_COMPLETED");
        var empty = w.owner.project();
        var emptyView =
                json(w.owner.request("GET", "/projects/" + empty + "/completion", null, false));
        assertThat(emptyView.path("canComplete").asBoolean()).isFalse();
    }

    @Test
    @DisplayName(
            "Позднее демо-дело не меняет заявку; дополнения в банк доступны после завершения"
                    + " стройки")
    void bankStageClarificationAfterCompletion() throws Exception {
        var f = readyForAcceptance(true);
        var w = f.work;
        var accepted = acceptStage(f);
        String root = w.root.replace("/journal", "");
        var original =
                json(w.owner.request("GET", "/projects/" + w.project + "/request", null, false));
        var body = new HashMap<String, Object>();
        body.put("acceptanceId", accepted.path("id").asText());
        body.put("expectedVersion", 0);
        body.put("idempotencyKey", UUID.randomUUID());
        error(
                w.owner.request("POST", root + "/bank-confirmations", body, true),
                409,
                "BANK_CASE_REQUIRED");
        connectCase(f);
        assertThat(json(w.owner.request("GET", "/projects/" + w.project + "/request", null, false)))
                .isEqualTo(original);
        stageBank.scenario("NEEDS_INFORMATION");
        var sent = w.owner.request("POST", root + "/bank-confirmations", body, true);
        assertThat(sent.statusCode()).isEqualTo(202);
        assertThat(json(sent).path("state").asText()).isEqualTo("RECEIVED");
        var repeated = w.owner.request("POST", root + "/bank-confirmations", body, true);
        assertThat(repeated.statusCode()).isEqualTo(200);
        var needs = json(repeated);
        assertThat(needs.path("state").asText()).isEqualTo("NEEDS_INFORMATION");
        var completion =
                json(w.owner.request("GET", "/projects/" + w.project + "/completion", null, false));
        assertThat(
                        w.owner
                                .request(
                                        "POST",
                                        "/projects/" + w.project + "/completion",
                                        Map.of(
                                                "expectedVersion",
                                                completion.path("version").asLong(),
                                                "idempotencyKey",
                                                UUID.randomUUID()),
                                        true)
                                .statusCode())
                .isEqualTo(200);
        var supplement =
                w.owner.uploadFor(
                        w.project,
                        null,
                        UUID.randomUUID(),
                        "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(),
                        "clarification.pdf",
                        "Дополнение",
                        true,
                        "BANK");
        assertThat(supplement.statusCode()).isEqualTo(200);
        body.put("expectedVersion", needs.path("version").asLong());
        body.put("previousId", needs.path("id").asText());
        body.put("idempotencyKey", UUID.randomUUID());
        body.put("note", "Документ и пояснение добавлены");
        body.put("attachmentIds", List.of(json(supplement).path("id").asText()));
        var second = w.owner.request("POST", root + "/bank-confirmations", body, true);
        assertThat(second.statusCode()).isEqualTo(202);
        var current = json(second);
        var checked =
                w.owner.request(
                        "POST",
                        root + "/bank-confirmations/" + current.path("id").asText() + "/retry",
                        Map.of("expectedVersion", current.path("version").asLong()),
                        true);
        assertThat(checked.statusCode()).isEqualTo(200);
        assertThat(json(checked).path("state").asText()).isEqualTo("ACCEPTED");
        var history = json(w.owner.request("GET", root + "/bank-confirmation", null, false));
        assertThat(history.path("submissions").size()).isEqualTo(2);
        assertThat(history.path("submissions").get(0).path("packet").path("additionalFiles").size())
                .isEqualTo(1);
        error(
                w.builder.request("GET", root + "/bank-confirmation", null, false),
                404,
                "PROJECT_NOT_FOUND");
        String content =
                "/projects/"
                        + w.project
                        + "/attachments/"
                        + json(supplement).path("id").asText()
                        + "/content";
        error(w.builder.request("GET", content, null, false), 404, "ATTACHMENT_NOT_FOUND");
    }

    @Test
    @DisplayName("Потерянный после получения ответ восстанавливается без второй квитанции банка")
    void bankStageLostResponse() throws Exception {
        var f = readyForAcceptance(true);
        var w = f.work;
        var accepted = acceptStage(f);
        connectCase(f);
        stageBank.scenario("TIMEOUT_AFTER_RECEIPT");
        String root = w.root.replace("/journal", "");
        var body =
                Map.of(
                        "acceptanceId",
                        accepted.path("id").asText(),
                        "expectedVersion",
                        0,
                        "idempotencyKey",
                        UUID.randomUUID());
        var sent = w.owner.request("POST", root + "/bank-confirmations", body, true);
        assertThat(sent.statusCode()).isEqualTo(202);
        var unknown = json(sent);
        assertThat(unknown.path("state").asText()).isEqualTo("UNKNOWN");
        var restored =
                w.owner.request(
                        "POST",
                        root + "/bank-confirmations/" + unknown.path("id").asText() + "/retry",
                        Map.of("expectedVersion", unknown.path("version").asLong()),
                        true);
        assertThat(restored.statusCode()).isEqualTo(200);
        assertThat(json(restored).path("state").asText()).isEqualTo("ACCEPTED");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from bank_mock_stage_receipts where id=(select"
                                        + " bank_key from bank_stage_submissions where id=?)",
                                Integer.class,
                                UUID.fromString(unknown.path("id").asText())))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from bank_stage_attempts where submission_id=?",
                                Integer.class,
                                UUID.fromString(unknown.path("id").asText())))
                .isEqualTo(2);
        var compact = json(w.builder.request("GET", root + "/acceptance", null, false));
        assertThat(compact.path("bankStatus").asText()).isEqualTo("ACCEPTED");
        assertThat(compact.has("creditCaseId")).isFalse();
        assertThat(compact.has("reason")).isFalse();
    }

    @Test
    @DisplayName("Истёкший исходный снимок не запрещает отчёт о принятом этапе уже начатой стройки")
    void expiredApprovalAllowsStageReport() throws Exception {
        var f = readyForAcceptance(true);
        var w = f.work;
        var req = json(w.owner.request("GET", "/projects/" + w.project + "/request", null, false));
        bank.scenario("EXPIRED");
        try {
            var imported =
                    w.owner.request(
                            "POST",
                            "/projects/" + w.project + "/mortgage-approval/import",
                            Map.of(
                                    "version",
                                    req.path("version").asLong(),
                                    "demoConsent",
                                    true,
                                    "idempotencyKey",
                                    UUID.randomUUID()),
                            true);
            assertThat(imported.statusCode()).isEqualTo(200);
        } finally {
            bank.scenario("FOUND");
        }
        var accepted = acceptStage(f);
        String root = w.root.replace("/journal", "");
        var preview = json(w.owner.request("GET", root + "/bank-confirmation", null, false));
        assertThat(preview.path("connected").asBoolean()).isTrue();
        assertThat(preview.path("canSend").asBoolean()).isTrue();
        assertThat(
                        w.owner
                                .request(
                                        "POST",
                                        root + "/bank-confirmations",
                                        Map.of(
                                                "acceptanceId",
                                                accepted.path("id").asText(),
                                                "expectedVersion",
                                                0,
                                                "idempotencyKey",
                                                UUID.randomUUID()),
                                        true)
                                .statusCode())
                .isEqualTo(202);
    }

    /** Проверяет возврат договора, его новую версию и допуск к началу согласованного этапа. */
    private void approveContractAndStart(
            Browser owner, Browser builder, String root, String stagePath) throws Exception {
        var contract = json(builder.request("POST", root + "/contracts", null, true));
        var cp = root + "/contracts/" + contract.path("id").asText();
        error(owner.request("GET", cp, null, false), 404, "CONTRACT_NOT_FOUND");
        contract =
                json(
                        builder.request(
                                "PUT",
                                cp,
                                Map.of(
                                        "version",
                                        contract.path("version").asLong(),
                                        "contractText",
                                        "Стороны, предмет, цена, сроки и порядок оплаты"),
                                true));
        contract = documentCommand(builder, cp, "submit", contract);
        var back =
                owner.request(
                        "POST",
                        cp + "/request-changes",
                        Map.of(
                                "version",
                                contract.path("version").asLong(),
                                "comment",
                                "Уточнить порядок оплаты"),
                        true);
        assertThat(back.statusCode()).isEqualTo(200);
        contract = json(builder.request("POST", root + "/contracts", null, true));
        cp = root + "/contracts/" + contract.path("id").asText();
        contract = documentCommand(builder, cp, "submit", contract);
        contract = documentCommand(owner, cp, "approve", contract);
        documentCommand(owner, cp, "approve", contract);
        var started = builder.request("POST", stagePath + "/start", Map.of("version", 0), true);
        assertThat(started.statusCode()).isEqualTo(200);
        assertThat(json(started).path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(json(owner.request("GET", root + "/proposals", null, false)).size())
                .isEqualTo(2);
        assertThat(json(owner.request("GET", root + "/contracts", null, false)).size())
                .isEqualTo(2);
    }

    /** Копия переносит пожелания и источник средств, но не персональный кредитный снимок. */
    private void assertCopiedWishes(Browser owner, String target) throws Exception {
        var copiedRequest =
                json(owner.request("GET", "/projects/" + target + "/request", null, false));
        assertThat(copiedRequest.path("state").asText()).isEqualTo("DRAFT");
        assertThat(copiedRequest.path("description").asText()).isEqualTo("Дом для семьи");
        assertThat(copiedRequest.path("financingSource").asText()).isEqualTo("RSHB_RURAL_MORTGAGE");
        assertThat(
                        json(owner.request(
                                        "GET",
                                        "/projects/" + target + "/mortgage-approval",
                                        null,
                                        false))
                                .path("snapshot")
                                .isNull())
                .isTrue();
    }

    /** Подписывает договор юристом и проверяет, что начало работ разрешено только прорабу. */
    private String startStageWithTeam(
            Browser owner, Browser director, Browser lawyer, Browser foreman, String root)
            throws Exception {
        var contractResponse = director.request("POST", root + "/contracts", null, true);
        var contract = json(contractResponse);
        String contractPath = root + "/contracts/" + contract.path("id").asText();
        contract =
                json(
                        lawyer.request(
                                "PUT",
                                contractPath,
                                Map.of("version", 0, "contractText", "Договор подряда"),
                                true));
        contract = documentCommand(lawyer, contractPath, "submit", contract);
        documentCommand(owner, contractPath, "approve", contract);
        var stage = json(owner.request("GET", root + "/stages", null, false)).get(0);
        String stagePath = root + "/stages/" + stage.path("id").asText();
        error(
                lawyer.request("POST", stagePath + "/start", Map.of("version", 0), true),
                403,
                "FORBIDDEN");
        assertThat(
                        foreman.request("POST", stagePath + "/start", Map.of("version", 0), true)
                                .statusCode())
                .isEqualTo(200);
        return stagePath;
    }

    /**
     * Проверяет персональные снимки, ответ владельца и неизменность автора при повторе после
     * переименования.
     */
    private void assertJournalAttribution(
            Browser owner, Browser lawyer, Browser foreman, String stagePath) throws Exception {
        var questionBody = journalBody("QUESTION", "Где оставить проход?");
        var question = json(foreman.request("POST", stagePath + "/journal", questionBody, true));
        assertThat(question.path("authorSnapshot").path("displayName").asText())
                .isEqualTo("Алексей Орлов");
        assertThat(question.path("authorSnapshot").path("position").asText()).isEqualTo("Прораб");
        error(
                lawyer.request("POST", stagePath + "/journal", journalBody("NOTE", "Нельзя"), true),
                403,
                "FORBIDDEN");
        var answer = new HashMap<String, Object>();
        answer.put("version", 0);
        answer.put("text", "Со стороны дороги");
        answer.put("decision", null);
        answer.put("idempotencyKey", UUID.randomUUID());
        var answered =
                json(
                        owner.request(
                                "POST",
                                stagePath
                                        + "/journal/"
                                        + question.path("id").asText()
                                        + "/response",
                                answer,
                                true));
        assertThat(answered.path("response").path("authorSnapshot").path("displayName").asText())
                .isEqualTo("Иван");
        foreman.request(
                "PATCH",
                "/auth/profile",
                Map.of("version", 0, "displayName", "Александр Орлов"),
                true);
        assertThat(json(foreman.request("POST", stagePath + "/journal", questionBody, true)))
                .isEqualTo(question);
        assertThat(
                        json(owner.request(
                                        "GET",
                                        stagePath + "/journal/" + question.path("id").asText(),
                                        null,
                                        false))
                                .path("authorSnapshot")
                                .path("displayName")
                                .asText())
                .isEqualTo("Алексей Орлов");
    }
}
