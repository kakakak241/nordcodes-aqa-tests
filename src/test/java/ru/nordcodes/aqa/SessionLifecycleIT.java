package ru.nordcodes.aqa;

import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.nordcodes.aqa.support.ApiTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Epic("API приложения")
@Feature("Жизненный цикл сессии")
@DisplayName("Жизненный цикл сессии")
class SessionLifecycleIT extends ApiTest {
    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Вход → действие → выход → запрет действия")
    void completesUserSession() throws Exception {
        successfulServices();
        String token = token();
        login(token);
        verifyCall("/auth", token);
        noCall("/doAction");
        clearRequests();
        api().call(token, "ACTION").isOk();
        verifyCall("/doAction", token);
        noCall("/auth");
        clearRequests();
        api().call(token, "LOGOUT").isOk();
        noCalls();
        api().call(token, "ACTION").isError(403);
        noCalls();
    }

    @Test
    @DisplayName("Действие без предварительного входа запрещено")
    void rejectsActionBeforeLogin() throws Exception {
        api().call(token(), "ACTION").isError(403);
        noCalls();
    }

    @Test
    @DisplayName("После выхода можно снова войти с тем же токеном")
    void allowsLoginAfterLogout() throws Exception {
        successfulServices();
        String token = token();
        login(token);
        api().call(token, "LOGOUT").isOk();
        clearRequests();
        login(token);
        verifyCall("/auth", token);
        api().call(token, "ACTION").isOk();
        verifyCall("/doAction", token);
    }

    @Test
    @DisplayName("Выполнение действия не завершает авторизацию")
    void allowsRepeatedActions() throws Exception {
        successfulServices();
        String token = token();
        login(token);
        for (int i = 0; i < 3; i++) {
            clearRequests();
            api().call(token, "ACTION").isOk();
            verifyCall("/doAction", token);
            noCall("/auth");
        }
    }

    @Test
    @DisplayName("Выход одного пользователя не завершает сессию другого")
    void keepsSessionsIndependent() throws Exception {
        successfulServices();
        String first = token();
        String second = token();
        login(first);
        login(second);
        clearRequests();
        api().call(first, "LOGOUT").isOk();
        api().call(first, "ACTION").isError(403);
        noCalls();
        api().call(second, "ACTION").isOk();
        verifyCall("/doAction", second);
        api().call(second, "LOGOUT").isOk();
    }

    @Test
    @DisplayName("Чужой неавторизованный токен не получает доступ к активной сессии")
    void doesNotShareAuthorization() throws Exception {
        successfulServices();
        String authorized = token();
        login(authorized);
        clearRequests();
        api().call(token(), "ACTION").isError(403);
        noCalls();
        api().call(authorized, "ACTION").isOk();
        verifyCall("/doAction", authorized);
    }

    @Test
    @DisplayName("Некорректное действие не завершает действующую сессию")
    void invalidActionDoesNotChangeSession() throws Exception {
        successfulServices();
        String token = token();
        login(token);
        clearRequests();
        api().call(token, "UNKNOWN").isError(400);
        noCalls();
        api().call(token, "ACTION").isOk();
        verifyCall("/doAction", token);
        noCall("/auth");
    }

    @Test
    @DisplayName("Сессии независимы при одновременных запросах разных пользователей")
    @Description("Небольшая проверка изоляции восьми сессий, не нагрузочный тест и без требований к времени ответа.")
    void handlesIndependentConcurrentSessions() throws Exception {
        successfulServices();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 8; i++) { tokens.add(token()); }
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> workflows = tokens.stream().<Callable<Void>>map(token -> () -> {
                api().call(token, "LOGIN").isOk();
                api().call(token, "ACTION").isOk();
                api().call(token, "LOGOUT").isOk();
                api().call(token, "ACTION").isError(403);
                return null;
            }).toList();
            for (var future : executor.invokeAll(workflows, 20, TimeUnit.SECONDS)) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        for (String token : tokens) {
            verifyCall("/auth", token);
            verifyCall("/doAction", token);
        }
    }
}
