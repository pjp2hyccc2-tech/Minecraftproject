package jp.archiquest.quiz;

import jp.archiquest.config.ArchiConfig;
import jp.archiquest.knowledge.KnowledgeBase;
import jp.archiquest.knowledge.QuizQuestion;
import jp.archiquest.knowledge.Subject;
import jp.archiquest.progress.PlayerProgress;
import jp.archiquest.registry.ModItems;
import jp.archiquest.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * クイズと模擬試験。チャットの選択肢をクリックして回答する（/archi answer &lt;token&gt; &lt;n&gt;）。
 */
public final class QuizManager {
    private static final String[] LABELS = {"1", "2", "3", "4", "5", "6"};
    private static final Random RNG = new Random();

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, ExamSession> EXAMS = new ConcurrentHashMap<>();
    private static final Map<UUID, Deque<String>> RECENT = new ConcurrentHashMap<>();

    /** order[表示位置] = 元の選択肢インデックス */
    private record Pending(QuizQuestion q, String token, int[] order, boolean exam) {
    }

    private static final class ExamSession {
        final List<QuizQuestion> questions;
        final Map<Subject, int[]> score = new EnumMap<>(Subject.class); // {正解数, 出題数}
        int index = 0;

        ExamSession(List<QuizQuestion> questions) {
            this.questions = questions;
        }
    }

    private QuizManager() {}

    public static void forget(UUID id) {
        PENDING.remove(id);
        EXAMS.remove(id);
        RECENT.remove(id);
    }

    // ------------------------------------------------------------------ quiz

    public static int startQuiz(ServerPlayer p, @Nullable Subject subject) {
        if (EXAMS.containsKey(p.getUUID())) {
            Msg.say(p, Component.literal("いまは模擬試験の途中だよ。 ")
                    .append(Msg.button("続きを解く", "/archi exam resume", "今の問題をもう一度表示", ChatFormatting.GREEN))
                    .append(" ")
                    .append(Msg.button("模試をやめる", "/archi exam quit", "模擬試験を中断する", ChatFormatting.RED)));
            return 0;
        }
        List<QuizQuestion> pool = subject == null ? KnowledgeBase.questions() : KnowledgeBase.questionsOf(subject);
        if (pool.isEmpty()) {
            Msg.say(p, "問題データが見つからないよ…（OPなら /archi reload を試してね）");
            return 0;
        }
        present(p, pickFresh(p, pool), null, false);
        return 1;
    }

    private static QuizQuestion pickFresh(ServerPlayer p, List<QuizQuestion> pool) {
        Deque<String> recent = RECENT.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        List<QuizQuestion> fresh = new ArrayList<>();
        for (QuizQuestion q : pool) if (!recent.contains(q.id())) fresh.add(q);
        List<QuizQuestion> from = fresh.isEmpty() ? pool : fresh;
        QuizQuestion q = from.get(RNG.nextInt(from.size()));
        recent.remove(q.id());
        recent.addLast(q.id());
        while (recent.size() > 40) recent.removeFirst();
        return q;
    }

    private static void present(ServerPlayer p, QuizQuestion q, @Nullable String header, boolean exam) {
        int n = Math.min(q.choices().size(), LABELS.length);
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        Collections.shuffle(idx, RNG);
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = idx.get(i);
        String token = Integer.toString(RNG.nextInt(36 * 36 * 36 * 36), 36);
        PENDING.put(p.getUUID(), new Pending(q, token, order, exam));

        String head = (header != null ? header + " " : "") + "【" + q.subject().label + "】" + stars(q.difficulty());
        Msg.header(p, head, q.subject().color);
        p.sendSystemMessage(Component.literal("Q. " + q.question()).withStyle(ChatFormatting.WHITE));
        for (int i = 0; i < n; i++) {
            String label = LABELS[i];
            p.sendSystemMessage(Component.literal("  ").append(Msg.button(label + " " + q.choices().get(order[i]),
                    "/archi answer " + token + " " + i, "クリックで「" + label + "」を選ぶ", ChatFormatting.AQUA)));
        }
        p.sendSystemMessage(Component.literal("  ↑ 選択肢をクリックして回答してね").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static String stars(int d) {
        int c = Math.max(1, Math.min(3, d));
        return "★".repeat(c) + "☆".repeat(3 - c);
    }

    public static int answer(ServerPlayer p, String token, int choice) {
        UUID id = p.getUUID();
        Pending pd = PENDING.get(id);
        if (pd == null || !pd.token().equals(token)) {
            Msg.say(p, Component.literal("この問題はもう回答済みだよ。 ")
                    .append(Msg.button("新しい問題", "/archi quiz", "ランダムに出題", ChatFormatting.YELLOW)));
            return 0;
        }
        if (choice < 0 || choice >= pd.order().length) return 0;
        PENDING.remove(id);

        QuizQuestion q = pd.q();
        boolean ok = pd.order()[choice] == q.answer();
        int correctPos = 0;
        for (int i = 0; i < pd.order().length; i++) if (pd.order()[i] == q.answer()) correctPos = i;

        PlayerProgress.recordAnswer(p, q.subject(), ok);
        int streak = PlayerProgress.streak(p);
        int gained;
        if (ok) {
            gained = 10 + 5 * (Math.max(1, Math.min(3, q.difficulty())) - 1) + Math.min(Math.max(streak - 1, 0), 5) * 2;
            int xp = ArchiConfig.XP_PER_CORRECT.get();
            if (xp > 0) p.giveExperiencePoints(xp);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.2f);
            Msg.say(p, Component.literal("✔ 正解！ ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    .append(Component.literal("+" + gained + "pt" + (streak >= 2 ? "（" + streak + "連続正解！）" : ""))
                            .withStyle(ChatFormatting.YELLOW)));
        } else {
            gained = 2;
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1.0f);
            Msg.say(p, Component.literal("✘ 残念！ ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal("正解は「" + LABELS[correctPos] + " " + q.choices().get(q.answer()) + "」")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" +2pt（挑戦賞）").withStyle(ChatFormatting.GRAY)));
        }
        p.sendSystemMessage(Component.literal(" ◆かみくだき解説").withStyle(ChatFormatting.GOLD));
        Msg.sendParagraphs(p, q.explanation(), ChatFormatting.WHITE);
        PlayerProgress.addPoints(p, gained);

        ExamSession ex = pd.exam() ? EXAMS.get(id) : null;
        if (ex != null) {
            int[] sc = ex.score.computeIfAbsent(q.subject(), k -> new int[2]);
            if (ok) sc[0]++;
            sc[1]++;
            ex.index++;
            if (ex.index < ex.questions.size()) presentExam(p, ex);
            else finishExam(p, ex);
        } else {
            MutableComponent row = Component.literal(" ");
            row.append(Msg.button("次の問題", "/archi quiz " + q.subject().id, q.subject().label + "からもう1問", ChatFormatting.YELLOW));
            row.append(" ").append(Msg.button("AI先生に詳しく聞く", "/archi explain " + q.id(), "この問題をもっと詳しく解説してもらう", ChatFormatting.AQUA));
            if (q.lesson() != null && !q.lesson().isEmpty()) {
                row.append(" ").append(Msg.button("レッスン", "/archi lesson " + q.lesson(), "関連レッスンを読む", ChatFormatting.GREEN));
            }
            p.sendSystemMessage(row);
        }
        return 1;
    }

    // ------------------------------------------------------------------ mock exam

    public static int startExam(ServerPlayer p, int count) {
        List<QuizQuestion> qs = buildExam(Math.max(5, Math.min(30, count)));
        if (qs.isEmpty()) {
            Msg.say(p, "模試に使える問題が足りないみたい…");
            return 0;
        }
        ExamSession s = new ExamSession(qs);
        EXAMS.put(p.getUUID(), s);
        PENDING.remove(p.getUUID());
        Msg.header(p, "模擬試験スタート！（全" + qs.size() + "問）", ChatFormatting.LIGHT_PURPLE);
        Msg.say(p, "本番と同じ5科目（計画・環境設備・法規・構造・施工）から、本番の出題比率で出すよ。");
        p.sendSystemMessage(Component.literal(" 合格ライン：総合70%以上 かつ 各科目50%以上").withStyle(ChatFormatting.YELLOW));
        p.sendSystemMessage(Component.literal(" （本番は各科目で過半数、総得点は近年 87〜92点/125点 前後が合格ライン）").withStyle(ChatFormatting.GRAY));
        presentExam(p, s);
        return 1;
    }

    private static void presentExam(ServerPlayer p, ExamSession s) {
        present(p, s.questions.get(s.index), "模試 " + (s.index + 1) + "/" + s.questions.size(), true);
    }

    public static int resumeExam(ServerPlayer p) {
        ExamSession s = EXAMS.get(p.getUUID());
        if (s == null) {
            Msg.say(p, "進行中の模試はないよ。 /archi exam で始められるよ！");
            return 0;
        }
        presentExam(p, s);
        return 1;
    }

    public static int quitExam(ServerPlayer p) {
        ExamSession s = EXAMS.remove(p.getUUID());
        PENDING.remove(p.getUUID());
        Msg.say(p, s == null ? "進行中の模試はないよ。" : "模擬試験を中断したよ。またいつでも挑戦してね！");
        return 1;
    }

    private static void finishExam(ServerPlayer p, ExamSession s) {
        EXAMS.remove(p.getUUID());
        int correct = 0;
        int total = 0;
        boolean allSubjectsOk = true;
        Subject worst = null;
        double worstRate = 2;
        Msg.header(p, "模擬試験 結果発表", ChatFormatting.LIGHT_PURPLE);
        for (Subject sub : Subject.values()) {
            int[] sc = s.score.get(sub);
            if (sc == null || sc[1] == 0) continue;
            correct += sc[0];
            total += sc[1];
            double r = (double) sc[0] / sc[1];
            boolean ok = r >= 0.5;
            if (!ok) allSubjectsOk = false;
            if (r < worstRate) {
                worstRate = r;
                worst = sub;
            }
            p.sendSystemMessage(Component.literal(" " + sub.label + " ").withStyle(sub.color)
                    .append(Component.literal(Msg.bar(r, 10) + " " + sc[0] + "/" + sc[1]).withStyle(ok ? ChatFormatting.WHITE : ChatFormatting.RED))
                    .append(Component.literal(ok ? " ✔" : " ✘足切り").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }
        double rate = total == 0 ? 0 : (double) correct / total;
        int pct = (int) Math.round(rate * 100);
        boolean pass = rate >= 0.7 - 1e-9 && allSubjectsOk;
        p.sendSystemMessage(Component.literal(" 総合: " + correct + "/" + total + "（" + pct + "%）").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        PlayerProgress.recordExam(p, pct, pass);

        if (pass) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("正答率 " + pct + "%").withStyle(ChatFormatting.YELLOW)));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("模試 合格！").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
            ItemStack cert = new ItemStack(ModItems.CERTIFICATE.get());
            CompoundTag t = cert.getOrCreateTag();
            t.putString("Owner", p.getName().getString());
            t.putInt("Score", pct);
            t.putInt("Correct", correct);
            t.putInt("Total", total);
            t.putString("Date", LocalDate.now().toString());
            ItemHandlerHelper.giveItemToPlayer(p, cert);
            Msg.say(p, Component.literal("合格おめでとう！ 合格証書をプレゼント！ +50pt").withStyle(ChatFormatting.GOLD));
            PlayerProgress.addPoints(p, 50);
        } else {
            String why = !allSubjectsOk ? "足切り（50%未満）の科目があったね。" : "総合で70%に届かなかったね。";
            Msg.say(p, Component.literal("今回は不合格… " + why + "でも、間違えた問題こそ伸びしろ！").withStyle(ChatFormatting.YELLOW));
        }
        MutableComponent row = Component.literal(" ");
        row.append(Msg.button("もう一度模試", "/archi exam", "新しい問題で再挑戦", ChatFormatting.LIGHT_PURPLE));
        if (worst != null) {
            row.append(" ").append(Msg.button(worst.label + "を特訓", "/archi quiz " + worst.id, "一番点が低かった科目のクイズ", ChatFormatting.YELLOW));
        }
        row.append(" ").append(Msg.button("成績を見る", "/archi stats", "これまでの学習ステータス", ChatFormatting.GREEN));
        p.sendSystemMessage(row);
    }

    /** 本番の出題比率（20:20:30:30:25）で count 問を選ぶ */
    static List<QuizQuestion> buildExam(int count) {
        List<Subject> subs = new ArrayList<>();
        for (Subject s : Subject.values()) {
            if (s.isGakka() && !KnowledgeBase.questionsOf(s).isEmpty()) subs.add(s);
        }
        if (subs.isEmpty()) return List.of();
        int totalWeight = 0;
        for (Subject s : subs) totalWeight += s.examQuestions;

        int k = subs.size();
        int[] alloc = new int[k];
        double[] remainder = new double[k];
        int assigned = 0;
        for (int i = 0; i < k; i++) {
            double exact = (double) count * subs.get(i).examQuestions / totalWeight;
            alloc[i] = Math.max(1, (int) Math.floor(exact));
            remainder[i] = exact - Math.floor(exact);
            assigned += alloc[i];
        }
        List<Integer> byRemainder = new ArrayList<>();
        for (int i = 0; i < k; i++) byRemainder.add(i);
        byRemainder.sort((a, b) -> Double.compare(remainder[b], remainder[a]));
        int cursor = 0;
        while (assigned < count) {
            alloc[byRemainder.get(cursor % k)]++;
            cursor++;
            assigned++;
        }
        while (assigned > count) {
            int maxI = 0;
            for (int i = 1; i < k; i++) if (alloc[i] > alloc[maxI]) maxI = i;
            if (alloc[maxI] <= 1) break;
            alloc[maxI]--;
            assigned--;
        }

        List<QuizQuestion> out = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            List<QuizQuestion> pool = new ArrayList<>(KnowledgeBase.questionsOf(subs.get(i)));
            Collections.shuffle(pool, RNG);
            out.addAll(pool.subList(0, Math.min(alloc[i], pool.size())));
        }
        return out;
    }
}
