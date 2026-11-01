package ru.nordcodes.aqa;

import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.nordcodes.aqa.support.ApiTest;
import ru.nordcodes.aqa.support.AppFixture;

import java.util.Map;

@Epic("API приложения")
@Feature("Дополнительные сценарии и допущения")
@Tag("assumption")
@DisplayName("Дополнительные сценарии и допущения")
class AdditionalBehaviorIT extends ApiTest {
    @Test
    @Story("Допущение A4: повторный вход → HTTP 409")
    @DisplayName("Повторный вход отклоняется и не создаёт новый внешний запрос")
    void rejectsDuplicateLoginAndKeepsSession() throws Exception {
        successfulServices();
        String token = token();
        login(token);
        clearRequests();
        api().call(token, "LOGIN").isError(409);
        noCalls();
        api().call(token, "ACTION").isOk();
        verifyCall("/doAction", token);
    }

    @Test
    @Story("Допущение A4: неизвестный токен при выходе → HTTP 403")
    @DisplayName("Выход без существующей сессии отклоняется локально")
    void rejectsLogoutOfUnknownToken() throws Exception {
        api().call(token(), "LOGOUT").isError(403);
        noCalls();
    }

    @Test
    @Story("Допущение A4: повторный выход → HTTP 403")
    @DisplayName("Повторный выход не обращается к внешнему сервису")
    void rejectsRepeatedLogout() throws Exception {
        successfulServices();
        String token = token();
        login(token);
        api().call(token, "LOGOUT").isOk();
        clearRequests();
        api().call(token, "LOGOUT").isError(403);
        noCalls();
    }

    @ParameterizedTest(name = "Допущение A5: действие «{0}» нормализуется")
    @ValueSource(strings = {" LOGIN", "LOGIN ", " LOGIN "})
    @DisplayName("Пробелы по краям действия удаляются")
    void trimsAction(String action) throws Exception {
        successfulServices();
        String token = token();
        api().call(token, action).isOk();
        verifyCall("/auth", token);
    }

    @Test
    @Story("Допущение A5: неизвестные поля игнорируются")
    @DisplayName("Дополнительное поле не мешает обработать корректный запрос")
    void ignoresAdditionalField() throws Exception {
        successfulServices();
        String token = token();
        api().form(Map.of("token", token, "action", "LOGIN", "extra", "value"), AppFixture.API_KEY).isOk();
        verifyCall("/auth", token);
    }
}
