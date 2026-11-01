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
            assertEquals(200, status, reason + "; тело ответа: " + body);
            JsonNode json = json();
            assertEquals("OK", json.path("result").asText(), "Поле result");
            assertFalse(json.has("message"), "Успешный ответ не должен содержать сообщение об ошибке");
        });
    }

    public void isError(int expectedStatus) {
        Allure.step("Получить ошибку HTTP " + expectedStatus + " с описанием причины", () -> {
            assertEquals(expectedStatus, status, "HTTP-код; тело ответа: " + body);
            JsonNode json = json();
            assertEquals("ERROR", json.path("result").asText(), "Поле result");
            assertTrue(json.path("message").isTextual(), "message должен быть строкой");
            assertFalse(json.path("message").asText().isBlank(), "Причина ошибки не должна быть пустой");
        });
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
