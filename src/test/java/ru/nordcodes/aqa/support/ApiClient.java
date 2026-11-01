package ru.nordcodes.aqa.support;

import io.qameta.allure.Allure;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public final class ApiClient {
    private final URI endpoint;
    private final String apiKey;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public ApiClient(URI baseUrl, String apiKey) {
        this.endpoint = baseUrl.resolve("/endpoint");
        this.apiKey = apiKey;
    }

    public ApiResponse call(String token, String action) throws Exception {
        String step = switch (action) {
            case "LOGIN" -> "Войти в систему";
            case "ACTION" -> "Выполнить действие пользователя";
            case "LOGOUT" -> "Выйти из системы";
            default -> "Передать неподходящее название действия";
        };
        return Allure.step(step, () -> form(Map.of("token", token, "action", action), apiKey));
    }

    public ApiResponse form(Map<String, String> fields, String key) throws Exception {
        String body = fields.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        return send(body, key, "X-Api-Key");
    }

    public ApiResponse send(String formBody, String key, String keyHeader) throws Exception {
        return Allure.step("Передать данные приложению и получить ответ", () -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8));
            if (key != null) {
                builder.header(keyHeader, key);
            }
            Allure.addAttachment("Запрос к приложению", "text/plain",
                    "POST " + endpoint + "\nContent-Type: application/x-www-form-urlencoded"
                            + "\nAccept: application/json\n" + keyHeader + ": "
                            + (key == null ? "[отсутствует]" : "[тестовый ключ, длина " + key.length() + "]")
                            + "\n\n" + formBody, ".txt");
            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            Allure.addAttachment("Ответ приложения", "text/plain",
                    "HTTP " + response.statusCode() + "\nContent-Type: " + contentType
                            + "\n\n" + response.body(), ".txt");
            return new ApiResponse(response.statusCode(), contentType, response.body());
        });
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
