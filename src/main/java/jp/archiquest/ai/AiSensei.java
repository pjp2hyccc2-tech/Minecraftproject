package jp.archiquest.ai;

import jp.archiquest.analysis.BuildingAnalyzer;
import jp.archiquest.analysis.BuildingReport;
import jp.archiquest.analysis.MaterialKind;
import jp.archiquest.config.ArchiConfig;
import jp.archiquest.knowledge.KnowledgeBase;
import jp.archiquest.knowledge.QuizQuestion;
import jp.archiquest.knowledge.Subject;
import jp.archiquest.knowledge.TipCategory;
import jp.archiquest.progress.PlayerProgress;
import jp.archiquest.progress.Ranks;
import jp.archiquest.advice.BuildAdvisor;
import jp.archiquest.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * AI先生「アーキ先生」。
 * APIが設定されていれば生成AIで答え、未設定・失敗時は内蔵の知識（OfflineSensei）で答える。
 */
public final class AiSensei {
    private static final int HISTORY_TURNS = 8;
    private static final Map<UUID, Deque<AiClient.Turn>> HISTORY = new ConcurrentHashMap<>();
    private static final Set<UUID> BUSY = ConcurrentHashMap.newKeySet();

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            あなたはMinecraftの世界に住む、明るくて面倒見のいい建築の先生「アーキ先生」です。
            プレイヤーは一級建築士試験（学科：計画・環境設備・法規・構造・施工、そして設計製図）を目指して、マイクラで建築しながら勉強しています。

            話し方のルール:
            ・日本語で、親しみやすく。でも内容は正確に。
            ・難しい用語は必ずやさしく言い換え、マイクラのブロックや遊びに例える（1ブロック＝1m、ブロック1個分の面積＝1m²）。
            ・試験に出るポイント（数値・条文の番号など）を1つ添える。
            ・法令の数値や条文番号は正確に。自信がないことは断定せず「最新の法令集や公式テキストで確認してね」と添える。
            ・最近の改正も踏まえる（2025年4月：4号特例の縮小（新2号・新3号建築物）、全ての新築への省エネ基準適合義務化、木造の壁量基準の見直し。2023年4月：住宅の居室の採光は照明設備の設置で1/10まで緩和可能）。
            ・チャット欄に表示されるので、全体で{N}文字以内。1文は短く。Markdown記号（#、*、表）は使わず、箇条書きは「・」を使う。
            ・試験や建築と関係ない話題には短く答えてから、建築の話題にやさしく戻す。
            """;

    private AiSensei() {}

    public static void forget(UUID id) {
        HISTORY.remove(id);
        BUSY.remove(id);
    }

    private static String systemPrompt() {
        int n = Math.max(120, ArchiConfig.AI_MAX_REPLY_CHARS.get() - 80);
        return SYSTEM_PROMPT_TEMPLATE.replace("{N}", String.valueOf(n));
    }

    // ------------------------------------------------------------------ public features

    /** 自由な質問 */
    public static void ask(ServerPlayer p, String question) {
        String q = question.trim();
        if (q.isEmpty()) {
            Msg.say(p, "質問を書いてね。例: /archi ask 建ぺい率って何？");
            return;
        }
        if (!AiClient.isConfigured()) {
            OfflineSensei.answer(p, q);
            return;
        }
        if (!acquire(p)) return;
        Msg.say(p, Component.literal("うーん、ちょっと考えるね…").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        Deque<AiClient.Turn> hist = HISTORY.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        List<AiClient.Turn> turns;
        synchronized (hist) {
            turns = new ArrayList<>(hist);
        }
        turns.add(new AiClient.Turn("user", learnerContext(p) + "\n質問: " + q));
        call(p, turns,
                text -> {
                    synchronized (hist) {
                        hist.addLast(new AiClient.Turn("user", q));
                        hist.addLast(new AiClient.Turn("assistant", text));
                        while (hist.size() > HISTORY_TURNS) hist.removeFirst();
                    }
                },
                () -> Component.literal(" ").append(Msg.suggest("続けて質問", "/archi ask ", "チャット欄に /archi ask を入力", ChatFormatting.AQUA))
                        .append(" ").append(Msg.button("会話をリセット", "/archi ai reset", "AI先生との会話の記憶を消す", ChatFormatting.DARK_GRAY)),
                sp -> OfflineSensei.answer(sp, q));
    }

    /** クイズの問題をAIがじっくり解説 */
    public static void explain(ServerPlayer p, String questionId) {
        QuizQuestion q = KnowledgeBase.question(questionId);
        if (q == null) {
            Msg.say(p, "その問題は見つからなかったよ。");
            return;
        }
        if (!AiClient.isConfigured()) {
            OfflineSensei.explain(p, q);
            return;
        }
        if (!acquire(p)) return;
        Msg.say(p, Component.literal("この問題をじっくり解説するね…").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        StringBuilder sb = new StringBuilder();
        sb.append("次の一級建築士試験対策の問題を、初心者向けに解説してください。")
                .append("①正解の理由 ②まちがいの選択肢のどこが違うか ③覚え方のコツ（マイクラの例え歓迎）を、350文字程度で。\n\n")
                .append("科目: ").append(q.subject().label).append("\n")
                .append("問題: ").append(q.question()).append("\n選択肢:\n");
        for (int i = 0; i < q.choices().size(); i++) sb.append(i + 1).append(") ").append(q.choices().get(i)).append("\n");
        sb.append("正解: ").append(q.answer() + 1).append(") ").append(q.choices().get(q.answer())).append("\n");
        sb.append("教材の解説メモ: ").append(q.explanation().replace("\n", " "));
        List<AiClient.Turn> turns = List.of(new AiClient.Turn("user", sb.toString()));
        call(p, turns, null,
                () -> {
                    MutableComponent row = Component.literal(" ");
                    if (q.lesson() != null && !q.lesson().isEmpty()) {
                        row.append(Msg.button("レッスン", "/archi lesson " + q.lesson(), "関連レッスンを読む", ChatFormatting.GREEN)).append(" ");
                    }
                    return row.append(Msg.button("次の問題", "/archi quiz " + q.subject().id, q.subject().label + "からもう1問", ChatFormatting.YELLOW));
                },
                sp -> OfflineSensei.explain(sp, q));
    }

    /** 最後に診断した建物をAIが講評 */
    public static void review(ServerPlayer p) {
        BuildingReport r = BuildingAnalyzer.last(p.getUUID());
        if (r == null) {
            Msg.say(p, "まずは建築診断スケールで建物を右クリックして、診断してから呼んでね！");
            return;
        }
        if (!AiClient.isConfigured()) {
            OfflineSensei.review(p, r);
            return;
        }
        if (!acquire(p)) return;
        Msg.say(p, Component.literal("どれどれ、建物を見せてもらうね…").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        String prompt = "以下は、プレイヤーがマイクラで建てた建物を自動診断したデータです。一級建築士の目線で、"
                + "①良いところを1つ ②改善点を2つ（理由と、マイクラでの具体的な直し方） ③この建物に関係する試験ポイントを1つ、"
                + "400文字程度で楽しく講評してください。データにない部分は推測で断定しないでください。\n\n"
                + learnerContext(p) + "\n" + r.toAiSummary();
        call(p, List.of(new AiClient.Turn("user", prompt)), null,
                () -> Component.literal(" ").append(Msg.button("チャレンジ", "/archi challenge", "建築チャレンジに挑戦", ChatFormatting.LIGHT_PURPLE))
                        .append(" ").append(Msg.button("法規クイズ", "/archi quiz law", "法規のクイズ", ChatFormatting.RED)),
                sp -> OfflineSensei.review(sp, r));
    }

    /** 建築中アドバイスをAIが考える（configで有効化した場合） */
    public static void autoAdvice(ServerPlayer p, BlockState state, TipCategory tc, int height) {
        if (!acquireSilently(p)) {
            BuildAdvisor.sendTip(p, tc, BuildAdvisor.nextTip(p, tc));
            return;
        }
        String blockName = state.getBlock().getName().getString();
        String prompt = "プレイヤーがいま「" + blockName + "」（" + MaterialKind.path(state) + "）を設置しました。地面からの高さは約" + height + "mです。"
                + "このブロックに関係する一級建築士試験の知識（分野の例: " + tc.label() + "）を1つ、マイクラの建築にすぐ活かせる形で、"
                + "120文字以内で教えてください。前置きは不要です。";
        call(p, List.of(new AiClient.Turn("user", prompt)), null,
                () -> {
                    MutableComponent row = Component.literal(" ");
                    if (tc.lesson() != null && !tc.lesson().isEmpty()) {
                        row.append(Msg.button("教科書で読む", "/archi lesson " + tc.lesson(), "関連レッスン", ChatFormatting.GREEN)).append(" ");
                    }
                    return row.append(Msg.button("アドバイスOFF", "/archi advice off", "建築中のアドバイスを止める", ChatFormatting.DARK_GRAY));
                },
                sp -> BuildAdvisor.sendTip(sp, tc, BuildAdvisor.nextTip(sp, tc)));
    }

    public static void resetHistory(ServerPlayer p) {
        HISTORY.remove(p.getUUID());
        Msg.say(p, "会話の記憶をリセットしたよ。新しい気持ちで質問してね！");
    }

    // ------------------------------------------------------------------ internals

    private static boolean acquire(ServerPlayer p) {
        if (BUSY.add(p.getUUID())) return true;
        Msg.say(p, "ちょっと待ってね、いま前の質問を考え中…");
        return false;
    }

    private static boolean acquireSilently(ServerPlayer p) {
        return BUSY.add(p.getUUID());
    }

    private static void call(ServerPlayer p, List<AiClient.Turn> turns, @Nullable Consumer<String> onText,
                             @Nullable Supplier<Component> footer, @Nullable Consumer<ServerPlayer> fallback) {
        MinecraftServer server = p.getServer();
        UUID id = p.getUUID();
        if (server == null) {
            BUSY.remove(id);
            return;
        }
        AiClient.chat(systemPrompt(), turns, ArchiConfig.AI_MAX_TOKENS.get()).whenComplete((text, err) -> server.execute(() -> {
            BUSY.remove(id);
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null) return;
            if (err != null || text == null) {
                Msg.say(sp, Component.literal("AIにつながらなかったよ: " + friendlyError(err)).withStyle(ChatFormatting.RED));
                if (fallback != null) {
                    sp.sendSystemMessage(Component.literal(" かわりに内蔵の知識で答えるね！").withStyle(ChatFormatting.GRAY));
                    fallback.accept(sp);
                }
                return;
            }
            if (onText != null) onText.accept(text);
            deliver(sp, text);
            if (footer != null) sp.sendSystemMessage(footer.get());
        }));
    }

    /** AIの返事をチャット向けに整えて表示 */
    public static void deliver(ServerPlayer p, String raw) {
        String text = clean(raw);
        int max = ArchiConfig.AI_MAX_REPLY_CHARS.get();
        if (text.length() > max) text = text.substring(0, max) + "…";
        boolean first = true;
        for (String line : text.split("\n")) {
            if (line.isBlank()) continue;
            if (first) {
                Msg.say(p, Component.literal(line.strip()).withStyle(ChatFormatting.WHITE));
                first = false;
            } else {
                p.sendSystemMessage(Component.literal(" " + line.strip()).withStyle(ChatFormatting.WHITE));
            }
        }
    }

    static String clean(String s) {
        String t = s.replace("\r", "");
        t = t.replaceAll("(?m)^#{1,6}\\s*", "");
        t = t.replace("**", "").replace("__", "").replace("`", "");
        t = t.replaceAll("(?m)^\\s*[-*]\\s+", "・");
        t = t.replaceAll("\n{3,}", "\n\n");
        return t.strip();
    }

    static String friendlyError(@Nullable Throwable err) {
        if (err == null) return "返事が空でした";
        Throwable e = (err instanceof CompletionException && err.getCause() != null) ? err.getCause() : err;
        if (e instanceof AiClient.AiException ae) {
            return switch (ae.status) {
                case 401, 403 -> "APIキーが正しくないみたい（" + ae.getMessage() + "）";
                case 404 -> "モデル名かURLが違うみたい（" + ae.getMessage() + "）";
                case 429 -> "混み合っているか、利用上限に達したみたい。少し待ってね";
                case 529 -> "AIが混雑中。少し待ってね";
                default -> ae.getMessage();
            };
        }
        if (e instanceof HttpTimeoutException) return "時間切れ（configの timeoutSeconds を増やしてみて）";
        if (e instanceof ConnectException) return "接続できなかった（ネット接続や endpoint のURLを確認してね）";
        return e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
    }

    static String learnerContext(ServerPlayer p) {
        Ranks.Rank rank = Ranks.of(PlayerProgress.points(p));
        Subject weak = PlayerProgress.weakestSubject(p);
        return "（学習者メモ: 称号「" + rank.title() + "」、クイズ正答 " + PlayerProgress.totalCorrect(p) + "/" + PlayerProgress.totalAnswered(p)
                + (weak != null ? "、苦手科目「" + weak.label + "」" : "") + "）";
    }
}
