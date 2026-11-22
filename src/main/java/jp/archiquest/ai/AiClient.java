package jp.archiquest.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.archiquest.config.ArchiConfig;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * 生成AIのAPIクライアント（非同期）。
 * <ul>
 *   <li>anthropic: Claude Messages API（POST /v1/messages, x-api-key, anthropic-version: 2023-06-01）</li>
 *   <li>openai: OpenAI互換の Chat Completions API（OpenAI / Ollama / LM Studio など）</li>
 * </ul>
 */
public final class AiClient {
    public static final String DEFAULT_ANTHROPIC_MODEL = "claude-haiku-5-5";
    public static final String ANTHROPIC_ENDPOINT = "https://api.anthropic.com/v1/messages";
    public static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    /** 会話の1ターン（role: user / assistant） */
    public record Turn(String role, String content) {
    }

    /** APIエラー（HTTPステータス付き） */
    public static final class AiException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public final int status;

        public AiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static volatile HttpClient http;

    private AiClient() {}

    private static HttpClient client() {
        HttpClient c = http;
        if (c == null) {
            synchronized (AiClient.class) {
                if (http == null) {
                    HttpClient.Builder b = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15));
                    ProxySelector ps = ProxySelector.getDefault();
                    if (ps != null) b.proxy(ps);
                    http = b.build();
                }
                c = http;
            }
        }
        return c;
    }

    public static String provider() {
        return ArchiConfig.AI_PROVIDER.get().trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isAnthropic() {
        return provider().equals("anthropic") || provider().equals("claude");
    }

    public static boolean isOpenAi() {
        return provider().equals("openai") || provider().equals("ollama");
    }

    public static String apiKey() {
        String k = ArchiConfig.AI_API_KEY.get().trim();
        if (!k.isEmpty()) return k;
        String env = System.getenv("ARCHIQUEST_API_KEY");
        if (env != null && !env.isBlank()) return env.trim();
        env = System.getenv(isAnthropic() ? "ANTHROPIC_API_KEY" : "OPENAI_API_KEY");
        return env == null ? "" : env.trim();
    }

    public static String endpoint() {
        String e = ArchiConfig.AI_ENDPOINT.get().trim();
        if (!e.isEmpty()) return e;
        return isAnthropic() ? ANTHROPIC_ENDPOINT : OPENAI_ENDPOINT;
    }

    public static String model() {
        String m = ArchiConfig.AI_MODEL.get().trim();
        if (!m.isEmpty()) return m;
        return isAnthropic() ? DEFAULT_ANTHROPIC_MODEL : "";
    }

    /** AIが使える設定になっているか */
    public static boolean isConfigured() {
        if (isAnthropic()) return !apiKey().isEmpty();
        if (isOpenAi()) return !model().isEmpty() && !endpoint().isEmpty();
        return false;
    }

    /** 設定状況の説明（APIキーそのものは表示しない） */
    public static String describe() {
        if (!isAnthropic() && !isOpenAi()) return "AI: オフ（provider=" + provider() + "）→ 内蔵のオフライン先生で回答";
        String host;
        try {
            host = URI.create(endpoint()).getHost();
        } catch (Exception ex) {
            host = "（URLが不正）";
        }
        return "AI: " + (isAnthropic() ? "Claude API" : "OpenAI互換API") + " / モデル: " + (model().isEmpty() ? "（未設定）" : model())
                + " / 接続先: " + host + " / APIキー: " + (apiKey().isEmpty() ? "未設定" : "設定済み")
                + (isConfigured() ? " → 利用可能" : " → 未設定のためオフライン先生で回答");
    }

    /** チャットを送って、返答テキストを非同期で受け取る */
    public static CompletableFuture<String> chat(String system, List<Turn> turns, int maxTokens) {
        try {
            HttpRequest req = isAnthropic() ? buildAnthropic(system, turns, maxTokens) : buildOpenAi(system, turns, maxTokens);
            boolean anthropic = isAnthropic();
            return client().sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(resp -> parse(resp.statusCode(), resp.body(), anthropic));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static HttpRequest buildAnthropic(String system, List<Turn> turns, int maxTokens) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model());
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("system", system);
        JsonArray msgs = new JsonArray();
        for (Turn t : turns) {
            JsonObject m = new JsonObject();
            m.addProperty("role", t.role());
            m.addProperty("content", t.content());
            msgs.add(m);
        }
        body.add("messages", msgs);
        return HttpRequest.newBuilder(URI.create(endpoint()))
                .timeout(Duration.ofSeconds(ArchiConfig.AI_TIMEOUT_SECONDS.get()))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
    }

    private static HttpRequest buildOpenAi(String system, List<Turn> turns, int maxTokens) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model());
        body.addProperty("max_tokens", maxTokens);
        JsonArray msgs = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", system);
        msgs.add(sys);
        for (Turn t : turns) {
            JsonObject m = new JsonObject();
            m.addProperty("role", t.role());
            m.addProperty("content", t.content());
            msgs.add(m);
        }
        body.add("messages", msgs);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(endpoint()))
                .timeout(Duration.ofSeconds(ArchiConfig.AI_TIMEOUT_SECONDS.get()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        String key = apiKey();
        if (!key.isEmpty()) b.header("Authorization", "Bearer " + key);
        return b.build();
    }

    static String parse(int status, String body, boolean anthropic) {
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(body == null ? "" : body);
            root = el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            throw new AiException(status, "応答を読み取れませんでした（HTTP " + status + "）");
        }
        if (status >= 400 || root.has("error")) {
            String msg = "HTTP " + status;
            try {
                JsonElement err = root.get("error");
                if (err != null && err.isJsonObject() && err.getAsJsonObject().has("message")) {
                    msg = err.getAsJsonObject().get("message").getAsString();
                } else if (err != null && err.isJsonPrimitive()) {
                    msg = err.getAsString();
                }
            } catch (Exception ignored) {
            }
            throw new AiException(status, msg);
        }
        StringBuilder sb = new StringBuilder();
        if (anthropic) {
            // content は複数ブロック（thinking など）を含むことがあるので text だけ集める
            JsonArray content = root.has("content") && root.get("content").isJsonArray() ? root.getAsJsonArray("content") : new JsonArray();
            for (JsonElement e : content) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                if (o.has("type") && "text".equals(o.get("type").getAsString()) && o.has("text")) {
                    sb.append(o.get("text").getAsString());
                }
            }
        } else {
            JsonArray choices = root.has("choices") && root.get("choices").isJsonArray() ? root.getAsJsonArray("choices") : new JsonArray();
            if (choices.size() > 0 && choices.get(0).isJsonObject()) {
                JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                if (message != null && message.has("content") && !message.get("content").isJsonNull()) {
                    sb.append(message.get("content").getAsString());
                }
            }
        }
        String text = sb.toString().trim();
        if (text.isEmpty()) throw new AiException(status, "AIの返事が空でした（maxTokens を増やすと直ることがあります）");
        return text;
    }
}
