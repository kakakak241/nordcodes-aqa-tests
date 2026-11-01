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
        body.append("<header><h1>Результаты тестирования</h1><p class='meta'>NordCodes · ")
                .append(escape(date)).append(" МСК · <a href='index.html'>Подробный отчёт Allure</a></p></header>");
        body.append("<p class='totals' data-total='").append(results.size()).append("' data-passed='")
                .append(passed).append("' data-failed='").append(failed).append("' data-unavailable='")
                .append(unavailable).append("'>Проверок: ").append(results.size())
                .append(". Успешно: ").append(passed).append(". Не прошли: ").append(failed)
                .append(". Без результата: ").append(unavailable).append(".</p>");
        Map<String, List<JsonNode>> groups = new LinkedHashMap<>();
        List.of("Доступ по ключу", "Проверка входных данных", "Вход, действия и выход", "Внешний сервис", "Допущения")
                .forEach(name -> groups.put(name, new ArrayList<>()));
        for (JsonNode result : results) { groups.computeIfAbsent(group(result), key -> new ArrayList<>()).add(result); }
        groups.values().removeIf(List::isEmpty);
        body.append("<table class='overview'><caption>Сводка по разделам</caption><thead><tr><th scope='col'>Раздел</th><th scope='col'>Всего</th><th scope='col'>Успешно</th><th scope='col'>Не прошли</th><th scope='col'>Нет результата</th></tr></thead><tbody>");
        for (var entry : groups.entrySet()) {
            long ok = count(entry.getValue(), "passed"), errors = count(entry.getValue(), "failed");
            body.append("<tr><th scope='row'>").append(escape(entry.getKey())).append("</th><td>")
                    .append(entry.getValue().size()).append("</td><td>").append(ok)
                    .append("</td><td class='").append(errors > 0 ? "failed" : "").append("'>").append(errors)
                    .append("</td><td>").append(entry.getValue().size() - ok - errors).append("</td></tr>");
        }
        body.append("</tbody></table>");
        if (!known.isEmpty()) {
            body.append("<section class='finding'><h2>NC-001. Отказ во входе с допустимым кодом</h2>")
                    .append("<p>По заданию код пользователя может содержать заглавные латинские буквы A–Z и цифры. Приложение отклонило следующие допустимые варианты:</p><ul class='examples'>");
            for (JsonNode result : known) {
                body.append("<li>").append(escape(result.path("name").asText()
                        .replaceFirst("^Допустимый пользовательский код: ", ""))).append("</li>");
            }
            body.append("</ul><p>Ожидался успешный вход. Получен отказ: приложение посчитало символы неправильными. Это один дефект, воспроизведённый в ")
                    .append(known.size()).append(" проверках.</p></section>");
        }
        List<JsonNode> other = results.stream().filter(r -> !r.path("status").asText().equals("passed") && !known.contains(r)).toList();
        if (!other.isEmpty()) {
            body.append("<section><h2>Другие незавершённые и неуспешные проверки</h2><ul>");
            for (JsonNode result : other) {
                body.append("<li>").append(escape(result.path("name").asText())).append(": ")
                        .append(state(result)).append(".</li>");
            }
            body.append("</ul><p>Причины и данные запросов сохранены в Allure.</p></section>");
        } else if (known.isEmpty()) {
            body.append("<p>Все проверки этого запуска прошли успешно.</p>");
        }
        body.append("<section><h2>Сценарии</h2>");
        for (var entry : groups.entrySet()) {
            body.append("<details class='group'><summary>").append(escape(entry.getKey())).append("</summary><ul class='cases'>");
            for (JsonNode result : entry.getValue()) {
                body.append("<li><span class='state ").append(result.path("status").asText()).append("'>")
                        .append(state(result)).append("</span><span>").append(escape(result.path("name").asText())).append("</span></li>");
            }
            body.append("</ul></details>");
        }
        body.append("</section><footer><p>Код пользователя (токен) — строка из 32 букв или цифр. Он используется при входе, выполнении действий и выходе.</p><p>Дополнительные правила, отсутствующие в задании, перечислены в README проекта. В таблице им соответствует раздел «Допущения».</p></footer>");
        String html = """
                <!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Результаты проверки приложения — NordCodes</title><style>
                *{box-sizing:border-box}body{margin:0;background:#fff;color:#222;font:14px/1.5 Arial,sans-serif}
                main{max-width:940px;margin:32px auto;padding:0 24px 32px}h1{font-size:24px;line-height:1.25;margin:0 0 6px}h2{font-size:17px;margin:26px 0 10px}p{margin:10px 0;max-width:76ch}.meta,footer{color:#555;font-size:13px}.totals{margin:22px 0 18px;font-weight:600}a{color:#155a9c;text-underline-offset:2px}a:hover{color:#103e6a}a:focus-visible,summary:focus-visible{outline:2px solid #155a9c;outline-offset:3px}::selection{background:#dbe8f4;color:#111}
                table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}caption{text-align:left;font-weight:600;margin-bottom:8px}th,td{padding:8px 10px;border-bottom:1px solid #ddd;text-align:right;font-weight:400}thead th{background:#f4f4f4;font-weight:600;border-bottom-color:#aaa}th:first-child{text-align:left;width:48%}.failed,.broken,.unknown{color:#a32929}.finding{margin-top:24px}.finding h2{color:#8e2525}.examples{padding-left:22px;margin:10px 0}.examples li{margin:4px 0}
                .group{border-top:1px solid #ddd}summary{cursor:pointer;padding:10px 0;font-weight:600}summary:hover{color:#155a9c}.cases{list-style:none;margin:0;padding:0 0 10px}.cases li{display:grid;grid-template-columns:110px 1fr;gap:14px;padding:7px 0;border-top:1px solid #eee}.state{font-size:13px}.passed{color:#356044}.skipped{color:#665724}footer{margin-top:26px;padding-top:12px;border-top:1px solid #aaa}
                @media(max-width:600px){main{margin:20px auto;padding:0 16px 24px}h1{font-size:22px}.overview{font-size:12px}th,td{padding:7px 4px}th:first-child{width:42%}.cases li{grid-template-columns:1fr;gap:0}summary{padding:12px 0}}
                @media print{main{max-width:none;margin:0;padding:0}body{font-size:11pt}details{break-inside:avoid}a{color:#222}thead th{background:none}}
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
        if (name.contains("ApiKeyIT")) return "Доступ по ключу";
        if (name.contains("TokenValidationIT")) return "Проверка входных данных";
        if (name.contains("SessionLifecycleIT")) return "Вход, действия и выход";
        if (name.contains("ExternalServiceIT")) return "Внешний сервис";
        if (name.contains("AdditionalBehaviorIT")) return "Допущения";
        return "Другие сценарии";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String state(JsonNode result) {
        return switch (result.path("status").asText()) {
            case "passed" -> "Успешно";
            case "failed" -> "Не пройден";
            case "skipped" -> "Пропущено";
            default -> "Нет результата";
        };
    }
}
