package ru.nordcodes.aqa;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.nordcodes.aqa.support.ApiTest;
import ru.nordcodes.aqa.support.AppFixture;

import java.util.Map;
import java.util.stream.Stream;

@Epic("API приложения")
@Feature("Доступ по API-ключу")
@DisplayName("Доступ по API-ключу")
class ApiKeyIT extends ApiTest {
    static Stream<Arguments> invalidKeys() {
        return Stream.of("LOGIN", "ACTION", "LOGOUT").flatMap(action -> Stream.of(
                Arguments.of(action, "ключ отсутствует", null),
                Arguments.of(action, "пустой ключ", ""),
                Arguments.of(action, "неверный ключ", "wrong-key"),
                Arguments.of(action, "другой регистр", AppFixture.API_KEY.toUpperCase(java.util.Locale.ROOT)),
                Arguments.of(action, "ключ из примера вместо настроенного", "qazWSXedc")));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidKeys")
    @DisplayName("Недействительный API-ключ запрещает операцию")
    @Story("Все операции требуют ключ")
    void rejectsMissingOrIncorrectKey(String action, String label, String key) throws Exception {
        successfulServices();
        String token = token();
        if (!action.equals("LOGIN")) {
            login(token);
            clearRequests();
        }
        api().form(Map.of("token", token, "action", action), key).isError(401);
        noCalls();
    }

    @Test
    @DisplayName("Регистр имени HTTP-заголовка не влияет на проверку ключа")
    void acceptsCaseInsensitiveHeaderName() throws Exception {
        successfulServices();
        String token = token();
        api().send("action=LOGIN&token=" + token, AppFixture.API_KEY, "x-api-key").isOk();
        verifyCall("/auth", token);
    }

    @Test
    @DisplayName("Запрос без ключа не создаёт авторизацию")
    void rejectedLoginDoesNotAuthorizeToken() throws Exception {
        successfulServices();
        String token = token();
        api().form(Map.of("token", token, "action", "LOGIN"), null).isError(401);
        api().call(token, "ACTION").isError(403);
        noCalls();
    }

    @Test
    @DisplayName("Запрос выхода с неверным ключом не завершает сессию")
    void rejectedLogoutDoesNotChangeSession() throws Exception {
        successfulServices();
        String token = token();
        login(token);
        clearRequests();
        api().form(Map.of("token", token, "action", "LOGOUT"), "wrong-key").isError(401);
        noCalls();
        api().call(token, "ACTION").isOk();
        verifyCall("/doAction", token);
        noCall("/auth");
    }
}
