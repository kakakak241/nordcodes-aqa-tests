package ru.nordcodes.aqa.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;

import static org.junit.jupiter.api.Assertions.*;

public record ApiResponse(int status, String contentType, String body) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public void isOk() {
        isOk("Операция должна завершиться успешно");
    }

    public void isOk(String reason) {
        Allure.step(reason, () -> {
            if (status != 200) {
                fail(reason + ". Ожидалось: операция выполнена. Получено: " + outcome(status)
                        + ". Запрос и ответ сохранены во вложениях.");
            }
            JsonNode json = json();
            assertEquals("OK", json.path("result").asText(), "Поле result");
            assertFalse(json.has("message"), "Успешный ответ не должен содержать сообщение об ошибке");
        });
    }

    public void isError(int expectedStatus) {
        Allure.step("Проверить ожидаемый отказ: " + outcome(expectedStatus), () -> {
            if (status != expectedStatus) {
                fail("Ожидалось: " + outcome(expectedStatus) + ". Получено: " + outcome(status)
                        + ". Технические подробности сохранены во вложениях.");
            }
            JsonNode json = json();
            assertEquals("ERROR", json.path("result").asText(), "Поле result");
            assertTrue(json.path("message").isTextual(), "message должен быть строкой");
            assertFalse(json.path("message").asText().isBlank(), "Причина ошибки не должна быть пустой");
        });
    }

    private static String outcome(int status) {
        return switch (status) {
            case 200 -> "операция выполнена";
            case 400 -> "приложение отклонило переданные данные";
            case 401 -> "доступ запрещён из-за отсутствующего или неверного ключа";
            case 403 -> "операция недоступна без активного входа";
            case 409 -> "повторный вход отклонён";
            case 500 -> "приложение сообщило о невозможности выполнить операцию";
            default -> "получен непредусмотренный ответ приложения (код " + status + ")";
        };
    }

    private JsonNode json() throws Exception {
        assertTrue(contentType.toLowerCase(java.util.Locale.ROOT).startsWith("application/json"),
                "Ожидался JSON Content-Type, получен: " + contentType);
        JsonNode result = JSON.readTree(body);
        assertNotNull(result, "Тело ответа не должно быть пустым");
        assertTrue(result.isObject(), "Ожидался JSON-объект");
        assertTrue(result.path("result").isTextual(), "result должен быть строкой");
        return result;
    }
}
