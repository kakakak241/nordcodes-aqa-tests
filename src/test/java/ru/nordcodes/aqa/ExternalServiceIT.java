package ru.nordcodes.aqa;

import com.github.tomakehurst.wiremock.http.Fault;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.nordcodes.aqa.support.ApiTest;

import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@Epic("API приложения")
@Feature("Интеграция с внешним сервисом")
@DisplayName("Интеграция с внешним сервисом")
class ExternalServiceIT extends ApiTest {
    static Stream<Arguments> successResponses() {
        return Stream.of("LOGIN", "ACTION").flatMap(action ->
                Stream.of(200, 201, 202, 204, 299).map(code -> Arguments.of(action, code)));
    }

    @ParameterizedTest(name = "{0}: внешний сервис вернул {1}")
    @MethodSource("successResponses")
    @DisplayName("Успешные коды внешнего сервиса принимаются")
    @Story("Допущение A2: ответы 2xx означают успех")
    void acceptsSuccessfulExternalStatus(String action, int externalStatus) throws Exception {
        successfulServices();
        String token = token();
        String path = action.equals("LOGIN") ? "/auth" : "/doAction";
        if (action.equals("ACTION")) { login(token); }
        APP.mock().removeStub(APP.mock().getStubMappings().stream()
                .filter(mapping -> path.equals(mapping.getRequest().getUrl())).findFirst().orElseThrow());
        stub(path, externalStatus);
        clearRequests();
        api().call(token, action).isOk();
        verifyCall(path, token);
        noCall(action.equals("LOGIN") ? "/doAction" : "/auth");
        if (action.equals("LOGIN")) {
            api().call(token, "ACTION").isOk();
            verifyCall("/doAction", token);
        }
    }

    static Stream<Arguments> errorResponses() {
        return Stream.of("LOGIN", "ACTION").flatMap(action ->
                Stream.of(400, 401, 403, 404, 409, 429, 500, 502, 503, 504)
                        .map(code -> Arguments.of(action, code)));
    }

    @ParameterizedTest(name = "{0}: внешний сервис вернул ошибку {1}")
    @MethodSource("errorResponses")
    @DisplayName("Внешняя ошибка обрабатывается, после восстановления работа продолжается")
    @Story("Допущение A3: ошибка внешнего сервиса → HTTP 500 / ERROR")
    void handlesExternalErrorsAndCanRecover(String action, int externalStatus) throws Exception {
        String token = token();
        stub("/auth", 200);
        stub("/doAction", action.equals("ACTION") ? externalStatus : 200);
        if (action.equals("ACTION")) {
            login(token);
        } else {
            APP.mock().resetMappings();
            stub("/auth", externalStatus);
            stub("/doAction", 200);
        }
        clearRequests();
        api().call(token, action).isError(500);
        String path = action.equals("LOGIN") ? "/auth" : "/doAction";
        verifyCall(path, token);
        noCall(action.equals("LOGIN") ? "/doAction" : "/auth");
        if (action.equals("LOGIN")) {
            clearRequests();
            api().call(token, "ACTION").isError(403);
            noCalls();
        }
        APP.mock().resetMappings();
        successfulServices();
        clearRequests();
        if (action.equals("LOGIN")) { login(token); }
        api().call(token, "ACTION").isOk();
        verifyCall("/doAction", token);
    }

    static Stream<Arguments> responseBodies() {
        return Stream.of("LOGIN", "ACTION").flatMap(action -> Stream.of(
                Arguments.of(action, "пустое тело", ""),
                Arguments.of(action, "обычный текст", "This is not JSON"),
                Arguments.of(action, "JSON с ERROR", "{\"result\":\"ERROR\"}"),
                Arguments.of(action, "повреждённый JSON", "{invalid"),
                Arguments.of(action, "HTML", "<html>Unavailable</html>")));
    }

    @ParameterizedTest(name = "{0}: {1} внешнего ответа не влияет на успех")
    @MethodSource("responseBodies")
    @DisplayName("Тело внешнего ответа не влияет на результат операции")
    @Story("Требование: тело внешнего ответа игнорируется")
    void ignoresExternalBody(String action, String label, String body) throws Exception {
        stub("/auth", 200);
        stub("/doAction", 200);
        String token = token();
        if (action.equals("ACTION")) { login(token); }
        APP.mock().resetMappings();
        stub("/auth", aResponse().withStatus(200).withBody(body));
        stub("/doAction", aResponse().withStatus(200).withBody(body));
        clearRequests();
        api().call(token, action).isOk();
        verifyCall(action.equals("LOGIN") ? "/auth" : "/doAction", token);
        if (action.equals("LOGIN")) {
            api().call(token, "ACTION").isOk();
            verifyCall("/doAction", token);
        }
    }

    @ParameterizedTest(name = "{0}: разрыв соединения с внешним сервисом")
    @ValueSource(strings = {"LOGIN", "ACTION"})
    @Story("Допущение A3: сетевая ошибка не превращается в успех")
    @DisplayName("После разрыва внешнего соединения приложение восстанавливает работу")
    void handlesConnectionReset(String action) throws Exception {
        successfulServices();
        String token = token();
        if (action.equals("ACTION")) { login(token); }
        APP.mock().resetMappings();
        stub("/auth", action.equals("LOGIN") ? aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER) : aResponse().withStatus(200));
        stub("/doAction", action.equals("ACTION") ? aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER) : aResponse().withStatus(200));
        clearRequests();
        api().call(token, action).isError(500);
        // Connection-level retries are unspecified; assert the target and the token, not the retry count.
        APP.mock().verify(moreThanOrExactly(1), postRequestedFor(urlEqualTo(action.equals("LOGIN") ? "/auth" : "/doAction"))
                .withRequestBody(equalTo("token=" + token)));
        if (action.equals("LOGIN")) {
            clearRequests();
            api().call(token, "ACTION").isError(403);
            noCalls();
        }
        APP.mock().resetMappings();
        successfulServices();
        clearRequests();
        if (action.equals("LOGIN")) { login(token); }
        api().call(token, "ACTION").isOk();
        verifyCall("/doAction", token);
    }

    @ParameterizedTest(name = "{0}: задержанный успешный ответ")
    @ValueSource(strings = {"LOGIN", "ACTION"})
    @DisplayName("Небольшая задержка не изменяет успешный результат")
    void acceptsDelayedSuccess(String action) throws Exception {
        successfulServices();
        String token = token();
        if (action.equals("ACTION")) { login(token); }
        APP.mock().resetMappings();
        stub("/auth", aResponse().withStatus(200).withFixedDelay(250));
        stub("/doAction", aResponse().withStatus(200).withFixedDelay(250));
        clearRequests();
        api().call(token, action).isOk();
        verifyCall(action.equals("LOGIN") ? "/auth" : "/doAction", token);
    }
}
