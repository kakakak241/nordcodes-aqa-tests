package ru.nordcodes.aqa.support;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public abstract class ApiTest {
    @RegisterExtension
    protected static final AppFixture APP = new AppFixture();

    protected ApiClient api() { return APP.client(); }
    protected String token() { return TestData.token(); }

    protected void stub(String path, int status) {
        stub(path, aResponse().withStatus(status).withBody("External response body is not JSON"));
    }

    protected void stub(String path, ResponseDefinitionBuilder response) {
        APP.mock().stubFor(post(urlEqualTo(path)).willReturn(response));
    }

    protected void successfulServices() {
        stub("/auth", 200);
        stub("/doAction", 200);
    }

    protected void login(String token) throws Exception {
        Allure.step("Авторизовать тестовый токен", () -> api().call(token, "LOGIN").isOk());
    }

    protected void clearRequests() {
        APP.mock().resetRequests();
    }

    protected void verifyCall(String path, String token) {
        Allure.step("Проверить единственный POST " + path + " с нужным токеном и заголовками", () ->
                APP.mock().verify(1, postRequestedFor(urlEqualTo(path))
                        .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                        .withHeader("Accept", containing("application/json"))
                        .withRequestBody(equalTo("token=" + token))));
    }

    protected void noCalls() {
        Allure.step("Убедиться, что приложение не обращалось к внешнему сервису", () ->
                APP.mock().verify(0, anyRequestedFor(anyUrl())));
    }

    protected void noCall(String path) {
        Allure.step("Убедиться, что приложение не обращалось к " + path, () ->
                APP.mock().verify(0, anyRequestedFor(urlEqualTo(path))));
    }
}
