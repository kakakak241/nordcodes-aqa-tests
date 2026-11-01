package ru.nordcodes.aqa.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** A reader-facing view of actual Allure results, generated after test execution. */
public final class ReaderReport {
    private ReaderReport() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected: results directory and output HTML path");
        }
        List<JsonNode> results = new ArrayList<>();
        ObjectMapper json = new ObjectMapper();
        try (var files = Files.list(Path.of(args[0]))) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith("-result.json")).sorted().toList()) {
                JsonNode result = json.readTree(file.toFile());
                if (!Set.of("passed", "failed", "broken", "skipped", "unknown").contains(result.path("status").asText())) {
                    throw new IllegalStateException("Unrecognized result status: " + file);
                }
                results.add(result);
            }
        }
        if (results.isEmpty()) {
            throw new IllegalStateException("No test results: an empty run must not produce a success report");
        }
        Set<String> historyIds = new HashSet<>();
        for (JsonNode result : results) {
            String historyId = result.path("historyId").asText();
            if (!historyId.isBlank() && !historyIds.add(historyId)) {
                throw new IllegalStateException("Results contain repeated scenarios from earlier runs. Use clean verify.");
            }
        }
        results.sort(Comparator.comparing(r -> r.path("name").asText()));
        long passed = count(results, "passed"), failed = count(results, "failed");
        long unavailable = results.size() - passed - failed;
        List<JsonNode> known = results.stream().filter(r -> r.path("status").asText().equals("failed")
                && r.path("statusDetails").path("message").asText().contains("NC-001")).toList();
        long stopped = results.stream().mapToLong(r -> r.path("stop").asLong()).max().orElseThrow();
        String date = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("ru"))
                .withZone(ZoneId.of("Europe/Moscow")).format(Instant.ofEpochMilli(stopped));
        StringBuilder body = new StringBuilder();
        body.append("<header><p class='eyebrow'>NordCodes · проверка приложения</p><h1>Работают ли вход,<br>действия и выход?</h1><p class='intro'>Проверили, как приложение обрабатывает действия пользователей, защищает доступ и взаимодействует с внешней системой.</p><p class='muted'>Результаты запуска: ")
                .append(escape(date)).append(" · московское время</p></header>");
        body.append("<section class='stats' aria-label='Итоги проверки'>")
                .append(card(results.size(), "проверок в запуске", ""))
                .append(card(passed, "прошли успешно", "good"))
                .append(card(failed, "выявили расхождения", "bad"))
                .append(card(unavailable, "не дали результата", ""))
                .append("</section>");
        body.append("<section class='verdict'><h2>").append(failed + unavailable > 0
                ? "Есть результаты, требующие внимания" : "Все выполненные проверки успешны").append("</h2>");
        if (!known.isEmpty()) {
            body.append("<p><strong>В проверенных примерах пользователь не смог войти с допустимым кодом.</strong> Приложение отклонило приведённые ниже коды, хотя они соответствуют правилу из задания: заглавные латинские буквы и цифры.</p><p>")
                    .append("Проверок, выявивших эту ошибку: ").append(known.size())
                    .append(". Это примеры одного дефекта входа, а не разные дефекты.</p>");
        }
        if (failed > known.size()) {
            body.append("<p>Кроме известного дефекта входа есть другие расхождения. Их сценарии перечислены ниже; причины требуют отдельного разбора.</p>");
        }
        if (unavailable > 0) {
            body.append("<p>Для части сценариев проверка не завершилась либо была пропущена. Эти сценарии нельзя считать успешными.</p>");
        }
        if (failed + unavailable == 0) {
            body.append("<p>В этом запуске приложение выполнило ожидаемое поведение во всех проверенных сценариях.</p>");
        }
        body.append("</section><section><h2>Что именно проверили</h2><p class='muted'>В каждом разделе показаны реальные результаты этого запуска.</p>");
        Map<String, List<JsonNode>> groups = new LinkedHashMap<>();
        for (JsonNode result : results) { groups.computeIfAbsent(group(result), key -> new ArrayList<>()).add(result); }
        for (var group : groups.entrySet()) {
            long ok = count(group.getValue(), "passed");
            body.append("<details class='group'><summary><span>").append(escape(group.getKey()))
                    .append("</span><span class='group-count'>успешны ").append(ok).append(" из ")
                    .append(group.getValue().size()).append("</span></summary><ul class='cases'>");
            for (JsonNode result : group.getValue()) {
                String status = result.path("status").asText();
                String state = switch (status) {
                    case "passed" -> "Успешно";
                    case "failed" -> "Обнаружено расхождение";
                    case "skipped" -> "Пропущено";
                    default -> "Не удалось проверить";
                };
                body.append("<li><span class='state ").append(status.equals("passed") ? "good" : "bad").append("'>")
                        .append(state).append("</span><span>").append(escape(result.path("name").asText())).append("</span></li>");
            }
            body.append("</ul></details>");
        }
        body.append("</section>");
        List<JsonNode> problems = results.stream().filter(r -> !r.path("status").asText().equals("passed")).toList();
        if (!problems.isEmpty()) {
            body.append("<section><h2>Где результат отличается от ожидаемого</h2>");
            for (JsonNode problem : problems) {
                boolean nc001 = known.contains(problem);
                body.append("<article class='problem'><h3>").append(escape(problem.path("name").asText())).append("</h3>");
                if (nc001) {
                    body.append("<p><strong>Ожидали:</strong> пользователь с корректным кодом сможет войти в систему.</p><p><strong>Получили:</strong> приложение отказало во входе, посчитав допустимые символы неправильными.</p><p><strong>Последствие:</strong> пользователь с таким допустимым кодом не сможет перейти к выполнению действий.</p>");
                } else {
                    body.append("<p>Проверка не подтвердила ожидаемое поведение. Подробная причина и данные сохранены в отчёте Allure.</p>");
                }
                body.append("</article>");
            }
            body.append("</section>");
        }
        body.append("<section class='explanation'><h2>Как читать этот результат</h2><p><strong>Пользовательский код</strong> — строка из 32 заглавных латинских букв или цифр, по которой приложение различает пользователей. В техническом описании он называется токеном.</p><p><strong>Внешняя система</strong> проверяет вход и выполняет действие. В тестах её ответы задаются заранее: так можно повторить как успешный ответ, так и отказ или разрыв соединения.</p><p>Дополнительные правила, не уточнённые в задании, описаны как допущения в README проекта. Этот отчёт показывает результат выбранных сценариев, а не процент покрытия всего приложения или оценку тестового задания.</p></section>")
                .append("<footer><h2>Подробности для проверки и разбора</h2><p>В Allure сохранены шаги каждого сценария, переданные данные, ответы приложения и технические журналы.</p><a class='button' href='index.html'>Открыть подробный отчёт Allure →</a></footer>");
        String html = """
                <!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Результаты проверки приложения — NordCodes</title><style>
                *{box-sizing:border-box}body{margin:0;background:#f4f6f8;color:#182b35;font:16px/1.65 'Segoe UI',Arial,sans-serif}
                main{max-width:1060px;margin:auto;padding:48px 28px 70px}h1{font-size:46px;line-height:1.14;margin:12px 0 22px;letter-spacing:-1px}h2{font-size:26px;line-height:1.3;margin:0 0 16px}h3{font-size:18px;line-height:1.4;margin:0 0 12px}p{margin:10px 0}.eyebrow{color:#05786f;font-weight:650;letter-spacing:1px;font-size:13px;text-transform:uppercase}.intro{max-width:780px;font-size:20px}.muted{color:#536674;font-size:14px}
                section,footer{margin-top:30px}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}.card{background:#fff;border:1px solid #dce3e8;border-radius:14px;padding:20px}.number{font-size:40px;font-weight:700;line-height:1.2;display:block}.card p{font-size:14px;line-height:1.4}.good{color:#137149}.bad{color:#ab263c}.verdict{background:#fff;border-left:5px solid #d69b32;border-radius:8px;padding:25px 28px}
                .group{background:white;border:1px solid #dce3e8;border-radius:10px;margin:12px 0;overflow:hidden}summary{cursor:pointer;padding:18px 22px;display:flex;justify-content:space-between;gap:16px;font-weight:600}summary::before{content:'+';color:#05786f}details[open]>summary::before{content:'−'}.group-count{font-size:14px;color:#536674;white-space:nowrap;margin-left:auto}.cases{margin:0;padding:0 22px 12px;list-style:none}.cases li{padding:13px 0;border-top:1px solid #e8edf0;display:grid;grid-template-columns:155px 1fr;gap:15px;font-size:14px}.state{font-weight:600;font-size:12px}
                .problem{border:1px solid #e8c6cc;background:#fff9fa;border-radius:12px;padding:22px;margin:14px 0}.explanation{padding:25px 28px;background:#eaf2f2;border-radius:12px}footer{padding-top:24px;border-top:1px solid #cfd9df}.button{display:inline-block;margin-top:12px;padding:12px 18px;border-radius:8px;background:#076d66;color:white;text-decoration:none;font-weight:600}a:focus-visible,summary:focus-visible{outline:3px solid #d69b32;outline-offset:3px}strong{font-weight:650}
                @media(max-width:650px){main{padding:26px 18px}h1{font-size:34px}.intro{font-size:18px}.stats{grid-template-columns:repeat(2,1fr)}summary{padding:16px;flex-wrap:wrap}.group-count{margin-left:20px}.cases li{grid-template-columns:1fr;gap:4px}.verdict,.explanation{padding:20px}}
                @media print{body{background:white}main{padding:0}.button{display:none}details{break-inside:avoid}.stats{grid-template-columns:repeat(4,1fr)}}
                </style></head><body><main>
                """ + body + "</main></body></html>";
        Path output = Path.of(args[1]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, html, StandardCharsets.UTF_8);
        System.out.println("Reader report: " + output + "; results=" + results.size());
    }

    private static long count(List<JsonNode> results, String status) {
        return results.stream().filter(r -> r.path("status").asText().equals(status)).count();
    }

    private static String group(JsonNode result) {
        String name = result.path("fullName").asText();
        if (name.contains("ApiKeyIT")) return "Защита доступа: кто может выполнять операции";
        if (name.contains("TokenValidationIT")) return "Проверка пользовательского кода и введённых данных";
        if (name.contains("SessionLifecycleIT")) return "Вход, действия, выход и независимость пользователей";
        if (name.contains("ExternalServiceIT")) return "Работа с внешней системой и восстановление после отказа";
        if (name.contains("AdditionalBehaviorIT")) return "Дополнительные правила, принятые как допущения";
        return "Другие проверенные сценарии";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String card(long count, String label, String css) {
        return "<div class='card'><span class='number " + css + "'>" + count + "</span><p>" + label + "</p></div>";
    }
}
