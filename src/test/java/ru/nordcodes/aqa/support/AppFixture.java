package ru.nordcodes.aqa.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owns only the application process and WireMock it started. No fixed ports or external services. */
public final class AppFixture implements BeforeAllCallback, AfterAllCallback, BeforeTestExecutionCallback,
        BeforeEachCallback, AfterEachCallback, TestExecutionExceptionHandler {
    public static final String API_KEY = "aqa-local-test-secret";
    private static final Pattern PORT = Pattern.compile("Tomcat started on port (\\d+)");
    private WireMockServer mock;
    private Process process;
    private Path logFile;
    private ApiClient client;
    private Thread shutdownHook;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Path root = Path.of(System.getProperty("project.basedir", ".")).toAbsolutePath().normalize();
        Path jar = Path.of(System.getProperty("sut.jar", root.resolve("app/internal-0.0.1-SNAPSHOT.jar").toString()))
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Не найден JAR приложения: " + jar
                    + ". Поместите предоставленное приложение в app/ или передайте -Dsut.jar=<путь>.");
        }
        Path logs = root.resolve("target/app-logs");
        Files.createDirectories(logs);
        logFile = logs.resolve(context.getRequiredTestClass().getSimpleName() + ".log");
        try {
            mock = new WireMockServer(wireMockConfig().bindAddress("127.0.0.1").dynamicPort());
            mock.start();
            String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            String java = System.getProperty("sut.java", Path.of(System.getProperty("java.home"), "bin", executable).toString());
            process = new ProcessBuilder(java, "-Dsecret=" + API_KEY,
                    "-Dmock=" + mock.baseUrl() + "/", "-Dserver.address=127.0.0.1", "-Dserver.port=0",
                    "-Dspring.main.banner-mode=off", "-Dfile.encoding=UTF-8", "-jar", jar.toString())
                    .directory(jar.getParent().toFile()).redirectErrorStream(true)
                    .redirectOutput(logFile.toFile()).start();
            shutdownHook = new Thread(this::close, "aqa-process-cleanup");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            long deadline = System.nanoTime() + Duration.ofSeconds(40).toNanos();
            while (System.nanoTime() < deadline) {
                String log = Files.readString(logFile);
                var matcher = PORT.matcher(log);
                if (matcher.find() && log.contains("Started AqaApplication")) {
                    client = new ApiClient(URI.create("http://127.0.0.1:" + matcher.group(1)), API_KEY);
                    writeEnvironment(root, jar);
                    return;
                }
                if (!process.isAlive()) {
                    throw new IllegalStateException("Приложение завершилось при запуске:\n" + log);
                }
                Thread.sleep(100); // Bounded readiness polling, not synchronization of test assertions.
            }
            throw new IllegalStateException("Приложение не запустилось за 40 секунд:\n" + Files.readString(logFile));
        } catch (Exception e) {
            close();
            throw e;
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        mock.resetAll();
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        Allure.getLifecycle().updateTestCase(result -> {
            String name = context.getDisplayName()
                    .replace("LOGIN", "вход").replace("ACTION", "действие").replace("LOGOUT", "выход")
                    .replace("API-ключ", "ключ доступа")
                    .replace("токенов", "пользовательских кодов")
                    .replace("токеном", "пользовательским кодом")
                    .replace("токену", "пользовательскому коду")
                    .replace("токена", "пользовательского кода")
                    .replace("токены", "пользовательские коды")
                    .replace("токен", "пользовательский код")
                    .replace("Отклонить action", "Отклонить неизвестное название действия");
            if (context.getRequiredTestMethod().getName().startsWith("rejectsMissing")) {
                name = name.replace("token", "код пользователя").replace("action", "название действия");
            }
            if (context.getRequiredTestMethod().getName().equals("acceptsSuccessfulExternalStatus")) {
                name = name.replace("внешний сервис вернул ", "внешний сервис подтвердил операцию (HTTP ") + ")";
            }
            if (context.getRequiredTestMethod().getName().equals("handlesExternalErrorsAndCanRecover")) {
                name = name.replace("внешний сервис вернул ошибку ", "внешний сервис вернул ошибку (HTTP ") + ")";
            }
            result.setName(Character.toUpperCase(name.charAt(0)) + name.substring(1));
            if (result.getDescription() == null || result.getDescription().isBlank()) {
                result.setDescription(switch (context.getRequiredTestClass().getSimpleName()) {
                    case "ApiKeyIT" -> "Для каждой операции нужен верный ключ доступа. При отказе состояние входа не должно меняться.";
                    case "TokenValidationIT" -> "Проверка ограничений на код пользователя и название действия. Недопустимые данные должны отклоняться без обращения к внешнему сервису.";
                    case "SessionLifecycleIT" -> "После входа пользователь может выполнять действия, после выхода доступ прекращается. Сессии разных пользователей независимы.";
                    case "ExternalServiceIT" -> "В тесте заранее задан ответ внешнего сервиса. Проверяется, как приложение его обработает.";
                    default -> "Ожидаемое поведение принято как допущение, поскольку в задании оно не описано. Подробности в README.";
                });
            }
        });
    }

    @Override
    public void afterEach(ExtensionContext context) {
        StringBuilder journal = new StringBuilder();
        mock.getAllServeEvents().forEach(event -> journal.append(event.getRequest().getMethod())
                .append(' ').append(event.getRequest().getUrl()).append('\n')
                .append(event.getRequest().getHeaders()).append('\n')
                .append(event.getRequest().getBodyAsString()).append('\n')
                .append("Ответ заглушки: HTTP ").append(event.getResponse().getStatus())
                .append("\nСовпала с правилом: ").append(event.getWasMatched()).append("\n\n"));
        Allure.addAttachment("Запросы приложения к внешнему сервису", "text/plain",
                journal.isEmpty() ? "Внешних запросов не было" : journal.toString(), ".txt");
        assertTrue(mock.findAllUnmatchedRequests().isEmpty(),
                "Приложение отправило запрос, для которого не было предусмотрено заглушки");
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        if (logFile != null && Files.exists(logFile)) {
            String log = Files.readString(logFile);
            Allure.addAttachment("Лог приложения при падении", "text/plain",
                    log.substring(Math.max(0, log.length() - 24000)), ".log");
        }
        throw throwable;
    }

    @Override
    public void afterAll(ExtensionContext context) {
        close();
    }

    public WireMockServer mock() { return mock; }
    public ApiClient client() { return client; }

    private synchronized void close() {
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        if (mock != null && mock.isRunning()) {
            mock.stop();
        }
        if (shutdownHook != null && Thread.currentThread() != shutdownHook) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // The JVM is already shutting down and will run the registered hook itself.
            }
            shutdownHook = null;
        }
    }

    private static void writeEnvironment(Path root, Path jar) throws Exception {
        Path results = Path.of(System.getProperty("allure.results.directory", root.resolve("target/allure-results").toString()));
        Files.createDirectories(results);
        try (var categories = AppFixture.class.getResourceAsStream("/categories.json")) {
            if (categories != null) {
                Files.copy(categories, results.resolve("categories.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Properties environment = new Properties();
        environment.setProperty("Java", System.getProperty("java.version"));
        environment.setProperty("OS", System.getProperty("os.name"));
        environment.setProperty("Application", jar.getFileName().toString());
        environment.setProperty("Application.SHA256", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
        environment.setProperty("WireMock", "3.13.2, loopback, dynamic ports");
        try (var writer = Files.newBufferedWriter(results.resolve("environment.properties"))) {
            environment.store(writer, "Test execution environment");
        }
    }
}
