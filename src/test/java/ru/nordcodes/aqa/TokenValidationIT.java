package ru.nordcodes.aqa;

import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.nordcodes.aqa.support.ApiTest;
import ru.nordcodes.aqa.support.AppFixture;

import java.util.Map;
import java.util.stream.Stream;

@Epic("API приложения")
@Feature("Проверка входных параметров")
@DisplayName("Проверка входных параметров")
class TokenValidationIT extends ApiTest {
    static Stream<Arguments> validTokens() {
        return Stream.of(
                Arguments.of("только цифры", "0".repeat(32)),
                Arguments.of("буква A", "A".repeat(32)),
                Arguments.of("буква F", "F".repeat(32)),
                Arguments.of("цифры и A-F", "0123456789ABCDEF".repeat(2)),
                Arguments.of("буква G", "G".repeat(32)),
                Arguments.of("буква Z", "Z".repeat(32)),
                Arguments.of("все допустимые буквы и цифры", "ABCDEFGHIJKLMNOPQRSTUVWXYZ012345"));
    }

    @ParameterizedTest(name = "Допустимый токен: {0}")
    @MethodSource("validTokens")
    @DisplayName("Допустимые токены A-Z0-9 проходят полный цикл сессии")
    @Story("Требование: 32 символа A-Z0-9")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Код пользователя должен содержать 32 символа: заглавные латинские буквы или цифры. Проверяется вход, действие и выход с таким кодом.")
    void acceptsTokensFromRequiredAlphabet(String label, String token) throws Exception {
        successfulServices();
        api().call(token, "LOGIN").isOk("NC-001: правильный пользовательский код должен позволять войти в систему");
        verifyCall("/auth", token);
        clearRequests();
        api().call(token, "ACTION").isOk();
        verifyCall("/doAction", token);
        clearRequests();
        api().call(token, "LOGOUT").isOk();
        noCalls();
    }

    static Stream<Arguments> invalidTokens() {
        String[][] cases = {
                {"пустой", ""},
                {"длина 1", "A"},
                {"длина 31", "A".repeat(31)},
                {"длина 33", "A".repeat(33)},
                {"длина 1024", "A".repeat(1024)},
                {"строчные буквы", "a".repeat(32)},
                {"одна строчная буква", "A".repeat(31) + "a"},
                {"кириллица", "А".repeat(32)},
                {"пробел внутри", "A".repeat(15) + " " + "A".repeat(16)},
                {"пробел перед токеном", " " + "A".repeat(31)},
                {"пробел после токена", "A".repeat(31) + " "},
                {"дефис", "A".repeat(31) + "-"},
                {"подчёркивание", "A".repeat(31) + "_"},
                {"плюс", "A".repeat(31) + "+"},
                {"процент", "A".repeat(31) + "%"},
                {"перевод строки", "A".repeat(31) + "\n"},
                {"нулевой символ", "A".repeat(31) + "\0"}
        };
        return Stream.of("LOGIN", "ACTION", "LOGOUT").flatMap(action ->
                Stream.of(cases).map(c -> Arguments.of(action, c[0], c[1])));
    }

    @ParameterizedTest(name = "{0}: недопустимый токен ({1})")
    @MethodSource("invalidTokens")
    @DisplayName("Некорректный токен отклоняется до внешнего запроса")
    @Story("Некорректные токены не уходят во внешний сервис")
    void rejectsInvalidTokens(String action, String label, String token) throws Exception {
        api().call(token, action).isError(400);
        noCalls();
    }

    @ParameterizedTest(name = "{0}: отсутствует token")
    @ValueSource(strings = {"LOGIN", "ACTION", "LOGOUT"})
    @DisplayName("Параметр token обязателен")
    void rejectsMissingToken(String action) throws Exception {
        api().form(Map.of("action", action), AppFixture.API_KEY).isError(400);
        noCalls();
    }

    @ParameterizedTest(name = "Отклонить action: «{0}»")
    @ValueSource(strings = {"", "login", "action", "logout", "UNKNOWN", "DELETE", "123"})
    @DisplayName("Пустое или неизвестное действие отклоняется")
    void rejectsInvalidAction(String action) throws Exception {
        api().call(token(), action).isError(400);
        noCalls();
    }

    @ParameterizedTest(name = "Отсутствует {0}")
    @ValueSource(strings = {"action", "action и token"})
    @DisplayName("Отсутствующие параметры отклоняются")
    void rejectsMissingParameters(String label) throws Exception {
        Map<String, String> fields = label.equals("action") ? Map.of("token", token()) : Map.of();
        api().form(fields, AppFixture.API_KEY).isError(400);
        noCalls();
    }
}
