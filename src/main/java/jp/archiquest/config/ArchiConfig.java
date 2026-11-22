package jp.archiquest.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * config/archiquest-common.toml
 */
public final class ArchiConfig {
    public static final ForgeConfigSpec SPEC;

    // --- AI ---
    public static final ForgeConfigSpec.ConfigValue<String> AI_PROVIDER;
    public static final ForgeConfigSpec.ConfigValue<String> AI_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> AI_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> AI_ENDPOINT;
    public static final ForgeConfigSpec.IntValue AI_MAX_TOKENS;
    public static final ForgeConfigSpec.IntValue AI_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue AI_MAX_REPLY_CHARS;
    public static final ForgeConfigSpec.BooleanValue AI_AUTO_ADVICE;
    public static final ForgeConfigSpec.IntValue AI_AUTO_ADVICE_COOLDOWN;

    // --- advice ---
    public static final ForgeConfigSpec.BooleanValue ADVICE_ENABLED;
    public static final ForgeConfigSpec.IntValue ADVICE_COOLDOWN;
    public static final ForgeConfigSpec.BooleanValue HEIGHT_MILESTONES;

    // --- quiz / general ---
    public static final ForgeConfigSpec.IntValue XP_PER_CORRECT;
    public static final ForgeConfigSpec.BooleanValue GIVE_STARTER_ITEMS;
    public static final ForgeConfigSpec.BooleanValue LOGIN_TIP;

    // --- site ---
    public static final ForgeConfigSpec.IntValue COVERAGE_LIMIT;
    public static final ForgeConfigSpec.IntValue FAR_LIMIT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("AI先生の設定 / AI sensei settings",
                        "APIキーが空なら、内蔵の知識だけで答える「オフライン先生」として動きます。",
                        "If apiKey is empty, the built-in offline sensei is used.")
                .push("ai");
        AI_PROVIDER = b.comment("anthropic = Claude API / openai = OpenAI互換API（OpenAI, Ollama, LM Studio など） / none = AIを使わない")
                .define("provider", "anthropic");
        AI_API_KEY = b.comment("APIキー。環境変数 ARCHIQUEST_API_KEY（または ANTHROPIC_API_KEY / OPENAI_API_KEY）でも指定できます。",
                        "このファイルを人に渡さないでください。 / Never share this file.")
                .define("apiKey", "");
        AI_MODEL = b.comment("モデル名。空なら既定（anthropic: claude-haiku-5-5）。provider=openai の場合は必須（例: Ollamaなら llama3.1）")
                .define("model", "");
        AI_ENDPOINT = b.comment("APIのURL。空なら既定。",
                        "anthropic: https://api.anthropic.com/v1/messages",
                        "openai: https://api.openai.com/v1/chat/completions（Ollama: http://localhost:11434/v1/chat/completions）")
                .define("endpoint", "");
        AI_MAX_TOKENS = b.comment("AIの最大出力トークン数").defineInRange("maxTokens", 2048, 128, 16000);
        AI_TIMEOUT_SECONDS = b.comment("AIの応答待ち時間（秒）").defineInRange("timeoutSeconds", 45, 5, 300);
        AI_MAX_REPLY_CHARS = b.comment("チャットに表示するAI回答の最大文字数").defineInRange("maxReplyChars", 700, 100, 4000);
        AI_AUTO_ADVICE = b.comment("true: 建築中のアドバイスもAIが考える（API利用料がかかります） / false: 内蔵の豆知識を使う")
                .define("autoAdviceWithAI", false);
        AI_AUTO_ADVICE_COOLDOWN = b.comment("AIアドバイスの最短間隔（秒）").defineInRange("autoAdviceCooldownSeconds", 120, 20, 3600);
        b.pop();

        b.comment("建築中アドバイス / Building advice").push("advice");
        ADVICE_ENABLED = b.comment("ブロックを置いたときにアドバイスを出す").define("enabled", true);
        ADVICE_COOLDOWN = b.comment("アドバイスの間隔（秒）").defineInRange("cooldownSeconds", 45, 5, 3600);
        HEIGHT_MILESTONES = b.comment("高く建てたとき、法律上の節目（10m/16m/20m/31m/60m）を知らせる").define("heightMilestones", true);
        b.pop();

        b.comment("クイズ / Quiz").push("quiz");
        XP_PER_CORRECT = b.comment("正解したときにもらえる経験値").defineInRange("xpPerCorrect", 5, 0, 100);
        b.pop();

        b.comment("全般 / General").push("general");
        GIVE_STARTER_ITEMS = b.comment("初回ログイン時に教科書と建築診断スケールを配る").define("giveStarterItems", true);
        LOGIN_TIP = b.comment("ログイン時に今日の豆知識を表示する").define("loginTip", true);
        b.pop();

        b.comment("敷地診断 / Site check").push("site");
        COVERAGE_LIMIT = b.comment("診断で使う建ぺい率の上限（%）").defineInRange("coverageLimitPercent", 60, 10, 100);
        FAR_LIMIT = b.comment("診断で使う容積率の上限（%）").defineInRange("floorAreaRatioLimitPercent", 200, 50, 1300);
        b.pop();

        SPEC = b.build();
    }

    private ArchiConfig() {}
}
