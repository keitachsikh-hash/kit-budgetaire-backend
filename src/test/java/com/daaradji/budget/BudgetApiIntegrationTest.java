package com.daaradji.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

// Clé vidée : le test ne doit jamais appeler l'API Anthropic, même si la variable existe sur la machine.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ANTHROPIC_API_KEY=")
@ActiveProfiles({ "dev", "test" })
class BudgetApiIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Test
    void parcoursComplet() throws Exception {
        assertThat(get("/api/accounts", null).statusCode()).isEqualTo(401);

        HttpResponse<String> register = post("/api/auth/register",
                "{\"fullName\":\"Fatou Sall\",\"email\":\"fatou@test.sn\",\"password\":\"secret12\"}", null);
        assertThat(register.statusCode()).isEqualTo(201);
        String token = extract(register.body(), "\"token\":\"([^\"]+)\"");

        HttpResponse<String> duplicate = post("/api/auth/register",
                "{\"fullName\":\"Fatou\",\"email\":\"FATOU@test.sn\",\"password\":\"secret12\"}", null);
        assertThat(duplicate.statusCode()).isEqualTo(400);

        String accounts = get("/api/accounts", token).body();
        String accountId = extract(accounts, "\"id\":(\\d+)");
        String expenseCats = get("/api/categories?type=EXPENSE", token).body();
        String categoryId = extract(expenseCats, "\"id\":(\\d+)");

        String today = LocalDate.now().toString();
        HttpResponse<String> tx = post("/api/transactions", """
                {"type":"EXPENSE","amount":5000,"date":"%s","accountId":%s,"categoryId":%s}
                """.formatted(today, accountId, categoryId), token);
        assertThat(tx.statusCode()).isEqualTo(201);

        HttpResponse<String> wrongType = post("/api/transactions", """
                {"type":"INCOME","amount":5000,"date":"%s","accountId":%s,"categoryId":%s}
                """.formatted(today, accountId, categoryId), token);
        assertThat(wrongType.statusCode()).isEqualTo(400);

        HttpResponse<String> budget = post("/api/budgets", """
                {"categoryId":%s,"period":"%s","limitAmount":4000}
                """.formatted(categoryId, today.substring(0, 7)), token);
        assertThat(budget.statusCode()).isEqualTo(201);

        String overview = get("/api/budgets", token).body();
        assertThat(overview).contains("\"status\":\"EXCEEDED\"").contains("\"percent\":125");

        String dashboard = get("/api/dashboard", token).body();
        assertThat(dashboard).contains("\"expense\":5000").contains("\"totalBalance\":-5000");

        // Saisie multiple : tout ou rien
        String incomeCategoryId = extract(get("/api/categories?type=INCOME", token).body(), "\"id\":(\\d+)");
        HttpResponse<String> badBatch = post("/api/transactions/batch", """
                {"items":[{"type":"EXPENSE","amount":1000,"date":"%s","accountId":%s,"categoryId":%s},
                          {"type":"INCOME","amount":2000,"date":"%s","accountId":%s,"categoryId":%s}]}
                """.formatted(today, accountId, categoryId, today, accountId, categoryId), token);
        assertThat(badBatch.statusCode()).isEqualTo(400);
        assertThat(badBatch.body()).contains("Ligne 2");
        assertThat(get("/api/transactions", token).body()).contains("\"totalElements\":1");

        HttpResponse<String> batch = post("/api/transactions/batch", """
                {"items":[{"type":"EXPENSE","amount":1000,"date":"%s","accountId":%s,"categoryId":%s},
                          {"type":"INCOME","amount":2000,"date":"%s","accountId":%s,"categoryId":%s}]}
                """.formatted(today, accountId, categoryId, today, accountId, incomeCategoryId), token);
        assertThat(batch.statusCode()).isEqualTo(201);
        assertThat(get("/api/transactions", token).body()).contains("\"totalElements\":3");
        assertThat(post("/api/transactions/batch", "{\"items\":[]}", token).statusCode()).isEqualTo(400);
        assertThat(get("/api/transactions/summary", token).body())
                .contains("\"income\":2000").contains("\"expense\":6000").contains("\"count\":3");
        assertThat(get("/api/transactions?size=2", token).body()).contains("\"totalPages\":2");

        // Un autre utilisateur ne voit pas les données de Fatou
        String other = extract(post("/api/auth/register",
                "{\"fullName\":\"Modou\",\"email\":\"modou@test.sn\",\"password\":\"secret12\"}", null).body(),
                "\"token\":\"([^\"]+)\"");
        assertThat(get("/api/transactions", other).body()).contains("\"totalElements\":0");
        assertThat(get("/api/accounts/" + accountId, other).statusCode()).isIn(404, 405);
    }

    @Test
    void copiesManuscrites() throws Exception {
        String token = extract(post("/api/auth/register",
                "{\"fullName\":\"Awa Ndiaye\",\"email\":\"awa@test.sn\",\"password\":\"secret12\"}", null).body(),
                "\"token\":\"([^\"]+)\"");
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

        HttpResponse<String> bad = upload(token, "notes.txt", "text/plain", "bonjour".getBytes(), "");
        assertThat(bad.statusCode()).isEqualTo(400);

        HttpResponse<String> up = upload(token, "cahier-mars.png", "image/png", png, "Mars 2024");
        assertThat(up.statusCode()).isEqualTo(201);
        assertThat(up.body()).contains("\"status\":\"ARCHIVED\"").contains("\"title\":\"Mars 2024\"");
        String id = extract(up.body(), "\"id\":(\\d+)");

        assertThat(get("/api/documents", token).body()).contains("\"totalElements\":1").doesNotContain("\"data\"");
        HttpResponse<byte[]> file = http.send(HttpRequest.newBuilder(URI.create(url("/api/documents/" + id + "/file")))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(file.statusCode()).isEqualTo(200);
        assertThat(file.body()).isEqualTo(png);

        assertThat(get("/api/documents/ai-status", token).body()).contains("\"enabled\":false");
        HttpResponse<String> read = post("/api/documents/" + id + "/extract", "", token);
        assertThat(read.statusCode()).isEqualTo(400);
        assertThat(read.body()).contains("ANTHROPIC_API_KEY");

        assertThat(post("/api/documents/" + id + "/imported", "{\"count\":4}", token).body())
                .contains("\"status\":\"IMPORTED\"").contains("\"importedCount\":4");

        String other = extract(post("/api/auth/register",
                "{\"fullName\":\"Ibou\",\"email\":\"ibou@test.sn\",\"password\":\"secret12\"}", null).body(),
                "\"token\":\"([^\"]+)\"");
        assertThat(get("/api/documents/" + id + "/file", other).statusCode()).isEqualTo(404);

        HttpResponse<String> del = http.send(HttpRequest.newBuilder(URI.create(url("/api/documents/" + id)))
                .header("Authorization", "Bearer " + token).DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(del.statusCode()).isEqualTo(204);
        assertThat(get("/api/documents", token).body()).contains("\"totalElements\":0");
    }

    private HttpResponse<String> upload(String token, String name, String type, byte[] data, String title) throws Exception {
        String boundary = "kb" + System.nanoTime();
        var out = new java.io.ByteArrayOutputStream();
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\n" + title + "\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + name
                + "\"\r\nContent-Type: " + type + "\r\n\r\n";
        out.write(head.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write(data);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return http.send(HttpRequest.newBuilder(URI.create(url("/api/documents")))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String extract(String body, String regex) {
        Matcher m = Pattern.compile(regex).matcher(body);
        assertThat(m.find()).as("motif %s dans %s", regex, body).isTrue();
        return m.group(1);
    }
}
