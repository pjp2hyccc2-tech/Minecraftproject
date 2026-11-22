package jp.archiquest.knowledge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.archiquest.ArchiQuest;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

/**
 * 教科書・クイズ・豆知識・用語集のデータ。
 * <p>
 * MOD内蔵の assets/archiquest/knowledge/*.json に加えて、
 * config/archiquest/*.json を置くと自作の問題やレッスンを追加できる（同じ形式・同じIDなら上書き）。
 */
public final class KnowledgeBase {
    private static final String BASE = "assets/archiquest/knowledge/";
    private static final String[] FILES = {"lessons.json", "quiz.json", "tips.json", "glossary.json"};

    private static volatile Data data;

    private KnowledgeBase() {}

    private static final class Data {
        final List<Lesson> lessons = new ArrayList<>();
        final Map<String, Lesson> lessonById = new HashMap<>();
        final List<QuizQuestion> questions = new ArrayList<>();
        final Map<String, QuizQuestion> questionById = new HashMap<>();
        final Map<String, TipCategory> tips = new LinkedHashMap<>();
        final List<GlossaryTerm> glossary = new ArrayList<>();
    }

    private static Data d() {
        Data local = data;
        if (local == null) {
            synchronized (KnowledgeBase.class) {
                if (data == null) reload();
                local = data;
            }
        }
        return local;
    }

    // ------------------------------------------------------------------ loading

    /** データを読み直す。戻り値は件数のサマリー。 */
    public static synchronized String reload() {
        Data nd = new Data();
        int files = 0;
        for (String f : FILES) {
            try (InputStream in = openBuiltin(BASE + f)) {
                if (in == null) {
                    ArchiQuest.LOGGER.warn("[ArchiQuest] built-in knowledge file not found: {}", f);
                    continue;
                }
                merge(nd, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)), f);
                files++;
            } catch (Exception e) {
                ArchiQuest.LOGGER.error("[ArchiQuest] failed to load {}", f, e);
            }
        }
        Path dir = customDir();
        if (dir != null && Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                List<Path> list = s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted().toList();
                for (Path p : list) {
                    try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                        merge(nd, JsonParser.parseReader(r), p.getFileName().toString());
                        files++;
                    } catch (Exception e) {
                        ArchiQuest.LOGGER.error("[ArchiQuest] failed to load custom file {}", p, e);
                    }
                }
            } catch (Exception e) {
                ArchiQuest.LOGGER.error("[ArchiQuest] failed to scan {}", dir, e);
            }
        }
        data = nd;
        String summary = String.format(Locale.ROOT, "lessons=%d, questions=%d, tipCategories=%d, glossary=%d (files=%d)",
                nd.lessons.size(), nd.questions.size(), nd.tips.size(), nd.glossary.size(), files);
        ArchiQuest.LOGGER.info("[ArchiQuest] knowledge loaded: {}", summary);
        return summary;
    }

    @Nullable
    private static Path customDir() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve("archiquest");
        } catch (Throwable t) {
            return null;
        }
    }

    /** MOD内蔵ファイルを開く（いくつかの方法を順に試す） */
    @Nullable
    private static InputStream openBuiltin(String path) {
        try {
            InputStream in = KnowledgeBase.class.getResourceAsStream("/" + path);
            if (in != null) return in;
        } catch (Exception ignored) {
        }
        try {
            IModFileInfo info = ModList.get() != null ? ModList.get().getModFileById(ArchiQuest.MODID) : null;
            if (info != null) {
                Path p = info.getFile().findResource(path.split("/"));
                if (p != null && Files.exists(p)) return Files.newInputStream(p);
            }
        } catch (Throwable ignored) {
        }
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) {
                InputStream in = cl.getResourceAsStream(path);
                if (in != null) return in;
            }
        } catch (Exception ignored) {
        }
        try {
            ClassLoader cl = KnowledgeBase.class.getClassLoader();
            if (cl != null) return cl.getResourceAsStream(path);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void merge(Data d, @Nullable JsonElement rootEl, String source) {
        if (rootEl == null || !rootEl.isJsonObject()) {
            ArchiQuest.LOGGER.warn("[ArchiQuest] {} is not a JSON object, skipped", source);
            return;
        }
        JsonObject root = rootEl.getAsJsonObject();

        if (root.has("lessons") && root.get("lessons").isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray("lessons")) {
                try {
                    JsonObject o = e.getAsJsonObject();
                    Lesson l = new Lesson(req(o, "id"), Subject.byIdOr(str(o, "subject"), Subject.PLANNING), req(o, "title"),
                            text(o, "summary"), text(o, "minecraft"), strList(o, "points"), text(o, "mnemonic"), strList(o, "keywords"));
                    Lesson old = d.lessonById.put(l.id(), l);
                    if (old != null) d.lessons.remove(old);
                    d.lessons.add(l);
                } catch (Exception ex) {
                    ArchiQuest.LOGGER.warn("[ArchiQuest] bad lesson in {}: {}", source, ex.toString());
                }
            }
        }

        if (root.has("questions") && root.get("questions").isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray("questions")) {
                try {
                    JsonObject o = e.getAsJsonObject();
                    List<String> choices = strList(o, "choices");
                    int answer = o.has("answer") ? o.get("answer").getAsInt() : -1;
                    if (choices.size() < 2 || choices.size() > 6 || answer < 0 || answer >= choices.size()) {
                        ArchiQuest.LOGGER.warn("[ArchiQuest] question {} in {} has invalid choices/answer, skipped", str(o, "id"), source);
                        continue;
                    }
                    int diff = o.has("difficulty") ? o.get("difficulty").getAsInt() : 1;
                    QuizQuestion q = new QuizQuestion(req(o, "id"), Subject.byIdOr(str(o, "subject"), Subject.PLANNING),
                            Math.max(1, Math.min(3, diff)), req(o, "question"), choices, answer, text(o, "explanation"), str(o, "lesson"));
                    QuizQuestion old = d.questionById.put(q.id(), q);
                    if (old != null) d.questions.remove(old);
                    d.questions.add(q);
                } catch (Exception ex) {
                    ArchiQuest.LOGGER.warn("[ArchiQuest] bad question in {}: {}", source, ex.toString());
                }
            }
        }

        if (root.has("categories") && root.get("categories").isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : root.getAsJsonObject("categories").entrySet()) {
                try {
                    JsonObject o = en.getValue().getAsJsonObject();
                    String id = en.getKey();
                    TipCategory old = d.tips.get(id);
                    List<String> tips = new ArrayList<>(old != null ? old.tips() : List.of());
                    tips.addAll(strList(o, "tips"));
                    String label = o.has("label") ? str(o, "label") : (old != null ? old.label() : id);
                    Subject subject = o.has("subject") ? Subject.byIdOr(str(o, "subject"), Subject.PLANNING)
                            : (old != null ? old.subject() : Subject.PLANNING);
                    String lesson = o.has("lesson") ? str(o, "lesson") : (old != null ? old.lesson() : "");
                    d.tips.put(id, new TipCategory(id, label, subject, lesson, Collections.unmodifiableList(tips)));
                } catch (Exception ex) {
                    ArchiQuest.LOGGER.warn("[ArchiQuest] bad tip category in {}: {}", source, ex.toString());
                }
            }
        }

        if (root.has("glossary") && root.get("glossary").isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray("glossary")) {
                try {
                    JsonObject o = e.getAsJsonObject();
                    GlossaryTerm g = new GlossaryTerm(req(o, "term"), str(o, "reading"),
                            Subject.byIdOr(str(o, "subject"), Subject.PLANNING), text(o, "desc"), str(o, "lesson"));
                    d.glossary.removeIf(x -> x.term().equals(g.term()));
                    d.glossary.add(g);
                } catch (Exception ex) {
                    ArchiQuest.LOGGER.warn("[ArchiQuest] bad glossary entry in {}: {}", source, ex.toString());
                }
            }
        }
    }

    private static String req(JsonObject o, String key) {
        String v = str(o, key);
        if (v.isEmpty()) throw new IllegalArgumentException("missing '" + key + "'");
        return v;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return "";
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    /** 文字列 or 文字列配列（段落）を受け付け、配列なら改行で連結する */
    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return "";
        if (e.isJsonArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonElement x : e.getAsJsonArray()) parts.add(x.getAsString());
            return String.join("\n", parts);
        }
        return e.getAsString();
    }

    private static List<String> strList(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) return List.of();
        JsonArray a = e.getAsJsonArray();
        List<String> out = new ArrayList<>(a.size());
        for (JsonElement x : a) out.add(x.getAsString());
        return Collections.unmodifiableList(out);
    }

    // ------------------------------------------------------------------ accessors

    public static List<Lesson> lessons() {
        return Collections.unmodifiableList(d().lessons);
    }

    public static List<Lesson> lessonsOf(Subject s) {
        List<Lesson> out = new ArrayList<>();
        for (Lesson l : d().lessons) if (l.subject() == s) out.add(l);
        return out;
    }

    @Nullable
    public static Lesson lesson(@Nullable String id) {
        return id == null ? null : d().lessonById.get(id);
    }

    public static List<QuizQuestion> questions() {
        return Collections.unmodifiableList(d().questions);
    }

    public static List<QuizQuestion> questionsOf(Subject s) {
        List<QuizQuestion> out = new ArrayList<>();
        for (QuizQuestion q : d().questions) if (q.subject() == s) out.add(q);
        return out;
    }

    @Nullable
    public static QuizQuestion question(@Nullable String id) {
        return id == null ? null : d().questionById.get(id);
    }

    @Nullable
    public static TipCategory tipCategory(String id) {
        return d().tips.get(id);
    }

    public static Collection<TipCategory> tipCategories() {
        return Collections.unmodifiableCollection(d().tips.values());
    }

    public static List<GlossaryTerm> glossary() {
        return Collections.unmodifiableList(d().glossary);
    }

    /** ランダムな豆知識。subject が null なら全カテゴリから（高さ・構造種別の解説カテゴリは除く） */
    @Nullable
    public static String randomTip(@Nullable Subject subject, Random rng) {
        List<TipCategory> cats = new ArrayList<>();
        for (TipCategory c : d().tips.values()) {
            if (c.tips().isEmpty() || c.id().startsWith("height_") || c.id().startsWith("struct_")) continue;
            if (subject == null || c.subject() == subject) cats.add(c);
        }
        if (cats.isEmpty()) return null;
        TipCategory c = cats.get(rng.nextInt(cats.size()));
        return c.tips().get(rng.nextInt(c.tips().size()));
    }

    // ------------------------------------------------------------------ search (offline sensei)

    /** 検索結果 */
    public record Hit(String kind, String title, String text, @Nullable String lessonId, @Nullable Subject subject, double score) {
    }

    private static final String[] STOP_WORDS;

    static {
        String[] words = {"とはなんですか", "とは何ですか", "ってなんですか", "って何ですか", "ってなに", "って何", "とは", "って",
                "なんですか", "何ですか", "ですか", "教えてください", "おしえてください", "教えて", "おしえて", "についての", "について",
                "どういう意味", "どういうこと", "の意味", "意味", "わからない", "分からない", "わかりません", "知りたい", "なんで", "なぜ",
                "どうして", "ください", "下さい", "かんたんに", "簡単に", "詳しく", "くわしく", "説明して", "説明", "なに", "何",
                "どれくらい", "どのくらい", "いくつ", "ですか", "ますか", "でしょうか", "かな", "知ってる", "しってる"};
        Arrays.sort(words, Comparator.comparingInt(String::length).reversed());
        STOP_WORDS = words;
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\u3000、。，．,.・？?！!「」『』（）()\\[\\]【】:：;；〜~\"'`／/]", "");
    }

    static String stripStopWords(String n) {
        String t = n;
        for (String w : STOP_WORDS) t = t.replace(w, "");
        return t;
    }

    static List<String> grams(String n) {
        List<String> g = new ArrayList<>();
        if (n.isEmpty()) return g;
        if (n.length() == 1) {
            g.add(n);
            return g;
        }
        for (int i = 0; i + 2 <= n.length(); i++) g.add(n.substring(i, i + 2));
        return g;
    }

    static double coverage(List<String> qg, String target) {
        if (qg.isEmpty() || target.isEmpty()) return 0;
        int hit = 0;
        for (String g : qg) if (target.contains(g)) hit++;
        return (double) hit / qg.size();
    }

    /** ひらがなを除いた文字列（「天井の高さ」→「天井高」のように、助詞をはさんだ言い方でも一致させるため） */
    static String stripHiragana(String s) {
        return s.replaceAll("[\\u3041-\\u309F]", "");
    }

    /** 文字列 text が、クエリ q（またはひらがなを除いた qk）に含まれるか */
    private static boolean mentioned(String q, String qk, String text) {
        if (text.length() >= 2 && q.contains(text)) return true;
        String tk = stripHiragana(text);
        return tk.length() >= 2 && qk.contains(tk);
    }

    /** キーワードで教科書・用語集・問題解説を検索（文字の2-gram一致＋キーワード一致でスコア付け） */
    public static List<Hit> search(String query, int limit) {
        String q = stripStopWords(normalize(query));
        if (q.isEmpty()) return List.of();
        String qk = stripHiragana(q);
        List<String> qg = grams(q);
        List<String> qgk = grams(qk);
        List<Hit> hits = new ArrayList<>();
        Data dd = d();

        for (GlossaryTerm g : dd.glossary) {
            String term = normalize(g.term());
            String reading = normalize(g.reading());
            double s = 0;
            if (!term.isEmpty() && mentioned(q, qk, term)) s += 10 + term.length();
            else if (reading.length() >= 2 && q.contains(reading)) s += 9 + reading.length() * 0.5;
            else if (q.length() >= 2 && term.contains(q)) s += 7;
            s += 3 * cov(qg, qgk, term) + cov(qg, qgk, normalize(g.desc()));
            if (s >= 2.5) hits.add(new Hit("glossary", g.term(), g.desc(), emptyToNull(g.lesson()), g.subject(), s));
        }

        for (Lesson l : dd.lessons) {
            double s = 3 * cov(qg, qgk, normalize(l.title()));
            StringBuilder kw = new StringBuilder();
            int kwHits = 0;
            for (String k : l.keywords()) {
                String nk = normalize(k);
                kw.append(nk).append('|');
                if (mentioned(q, qk, nk)) kwHits++;
            }
            s += 3 * cov(qg, qgk, kw.toString()) + 6 * kwHits;
            s += 2 * cov(qg, qgk, normalize(l.summary() + String.join("", l.points())));
            if (s >= 2.0) hits.add(new Hit("lesson", l.title(), firstParagraph(l.summary()), l.id(), l.subject(), s));
        }

        for (QuizQuestion qq : dd.questions) {
            double s = 1.5 * cov(qg, qgk, normalize(qq.question() + qq.explanation()));
            if (s >= 1.2) hits.add(new Hit("quiz", qq.question(), qq.explanation(), emptyToNull(qq.lesson()), qq.subject(), s));
        }

        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
    }

    private static double cov(List<String> qg, List<String> qgk, String target) {
        return Math.max(coverage(qg, target), coverage(qgk, target));
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    public static String firstParagraph(String text) {
        if (text == null) return "";
        int i = text.indexOf('\n');
        return i < 0 ? text : text.substring(0, i);
    }
}
