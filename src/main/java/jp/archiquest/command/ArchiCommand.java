package jp.archiquest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import jp.archiquest.ai.AiClient;
import jp.archiquest.ai.AiSensei;
import jp.archiquest.analysis.BuildingAnalyzer;
import jp.archiquest.challenge.Challenges;
import jp.archiquest.item.InspectorItem;
import jp.archiquest.knowledge.KnowledgeBase;
import jp.archiquest.knowledge.Lesson;
import jp.archiquest.knowledge.Subject;
import jp.archiquest.progress.PlayerProgress;
import jp.archiquest.progress.Ranks;
import jp.archiquest.quiz.QuizManager;
import jp.archiquest.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** /archi コマンド */
public final class ArchiCommand {
    private static final Random RNG = new Random();

    private static final SuggestionProvider<CommandSourceStack> SUBJECTS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(Subject.ids(), builder);

    private static final SuggestionProvider<CommandSourceStack> LESSON_IDS = (ctx, builder) -> {
        List<String> ids = new ArrayList<>();
        for (Lesson l : KnowledgeBase.lessons()) ids.add(l.id());
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> CHALLENGE_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(Challenges.ids(), builder);

    private ArchiCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("archi")
                .executes(c -> help(player(c)))
                .then(Commands.literal("help").executes(c -> help(player(c))))
                .then(Commands.literal("quiz")
                        .executes(c -> QuizManager.startQuiz(player(c), null))
                        .then(Commands.argument("subject", StringArgumentType.word()).suggests(SUBJECTS)
                                .executes(c -> QuizManager.startQuiz(player(c), subject(c)))))
                .then(Commands.literal("answer")
                        .then(Commands.argument("token", StringArgumentType.word())
                                .then(Commands.argument("choice", IntegerArgumentType.integer(0, 9))
                                        .executes(c -> QuizManager.answer(player(c), StringArgumentType.getString(c, "token"),
                                                IntegerArgumentType.getInteger(c, "choice"))))))
                .then(Commands.literal("exam")
                        .executes(c -> QuizManager.startExam(player(c), 10))
                        .then(Commands.literal("quit").executes(c -> QuizManager.quitExam(player(c))))
                        .then(Commands.literal("resume").executes(c -> QuizManager.resumeExam(player(c))))
                        .then(Commands.argument("count", IntegerArgumentType.integer(5, 30))
                                .executes(c -> QuizManager.startExam(player(c), IntegerArgumentType.getInteger(c, "count")))))
                .then(Commands.literal("stats").executes(c -> stats(player(c))))
                .then(Commands.literal("tip")
                        .executes(c -> tip(player(c), null))
                        .then(Commands.argument("subject", StringArgumentType.word()).suggests(SUBJECTS)
                                .executes(c -> tip(player(c), subject(c)))))
                .then(Commands.literal("lessons")
                        .executes(c -> lessons(player(c), null))
                        .then(Commands.argument("subject", StringArgumentType.word()).suggests(SUBJECTS)
                                .executes(c -> lessons(player(c), subject(c)))))
                .then(Commands.literal("lesson")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(LESSON_IDS)
                                .executes(c -> lesson(player(c), StringArgumentType.getString(c, "id")))))
                .then(Commands.literal("ask")
                        .then(Commands.argument("question", StringArgumentType.greedyString())
                                .executes(c -> {
                                    AiSensei.ask(player(c), StringArgumentType.getString(c, "question"));
                                    return 1;
                                })))
                .then(Commands.literal("explain")
                        .then(Commands.argument("questionId", StringArgumentType.word())
                                .executes(c -> {
                                    AiSensei.explain(player(c), StringArgumentType.getString(c, "questionId"));
                                    return 1;
                                })))
                .then(Commands.literal("review").executes(c -> {
                    AiSensei.review(player(c));
                    return 1;
                }))
                .then(Commands.literal("analyze").executes(c -> analyze(player(c))))
                .then(Commands.literal("advice")
                        .then(Commands.literal("on").executes(c -> advice(player(c), true)))
                        .then(Commands.literal("off").executes(c -> advice(player(c), false))))
                .then(Commands.literal("challenge")
                        .executes(c -> Challenges.list(player(c)))
                        .then(Commands.literal("start")
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(CHALLENGE_IDS)
                                        .executes(c -> Challenges.start(player(c), StringArgumentType.getString(c, "id")))))
                        .then(Commands.literal("quit").executes(c -> Challenges.quit(player(c)))))
                .then(Commands.literal("ai")
                        .then(Commands.literal("status").executes(c -> aiStatus(c.getSource())))
                        .then(Commands.literal("reset").executes(c -> {
                            AiSensei.resetHistory(player(c));
                            return 1;
                        })))
                .then(Commands.literal("reload")
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> reload(c.getSource())))
        );
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return c.getSource().getPlayerOrException();
    }

    private static Subject subject(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(c, "subject");
        Subject s = Subject.byId(raw);
        if (s == null) {
            throw new SimpleCommandExceptionType(Component.literal("科目が見つかりません: " + raw + "（" + String.join(", ", Subject.ids()) + "）")).create();
        }
        return s;
    }

    // ------------------------------------------------------------------ handlers

    private static int help(ServerPlayer p) {
        Msg.header(p, "ArchiQuest ─ 一級建築士への道", ChatFormatting.AQUA);
        Msg.say(p, "マイクラで建てながら、一級建築士の勉強をしよう！ ボタンはクリックできるよ。");
        line(p, Msg.button("クイズ", "/archi quiz", "ランダムに1問", ChatFormatting.YELLOW), "1問クイズ（/archi quiz 科目名 で科目指定）");
        line(p, Msg.button("模擬試験", "/archi exam", "本番の比率で10問", ChatFormatting.LIGHT_PURPLE), "5科目ミックスの模試（合格で証書）");
        line(p, Msg.button("チャレンジ", "/archi challenge", "建築チャレンジ一覧", ChatFormatting.LIGHT_PURPLE), "基準を満たす建物を建てる課題");
        line(p, Msg.suggest("先生に質問", "/archi ask ", "例: /archi ask 建ぺい率って何？", ChatFormatting.AQUA), "なんでも質問（例: 座屈ってなに？）");
        line(p, Msg.button("教科書", "/archi lessons", "レッスン一覧", ChatFormatting.GREEN), "かみくだき解説（Kキーで教科書GUI）");
        line(p, Msg.button("豆知識", "/archi tip", "ランダムな豆知識", ChatFormatting.GOLD), "建築の豆知識");
        line(p, Msg.button("成績", "/archi stats", "学習ステータス", ChatFormatting.GREEN), "称号・科目別の正答率");
        line(p, Msg.button("診断", "/archi analyze", "見ている建物を診断", ChatFormatting.AQUA), "見ている建物を診断（スケール右クリックでもOK）");
        line(p, Msg.button("AI先生の講評", "/archi review", "最後に診断した建物を講評", ChatFormatting.AQUA), "診断した建物をAIが講評");
        line(p, Msg.button("アドバイスON", "/archi advice on", "建築中のアドバイスを出す", ChatFormatting.GRAY), "／"
                + "アドバイスOFF は /archi advice off");
        p.sendSystemMessage(Component.literal(" " + AiClient.describe()).withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    private static void line(ServerPlayer p, Component button, String desc) {
        p.sendSystemMessage(Component.literal(" ").append(button).append(Component.literal(" " + desc).withStyle(ChatFormatting.GRAY)));
    }

    private static int stats(ServerPlayer p) {
        int pts = PlayerProgress.points(p);
        Ranks.Rank rank = Ranks.of(pts);
        Ranks.Rank next = Ranks.next(pts);
        Msg.header(p, "学習ステータス", ChatFormatting.GREEN);
        p.sendSystemMessage(Component.literal(" 称号: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(rank.title()).withStyle(rank.color(), ChatFormatting.BOLD))
                .append(Component.literal("（" + pts + "pt）" + (next != null ? " 次の「" + next.title() + "」まであと" + (next.minPoints() - pts) + "pt" : " 最高位！"))
                        .withStyle(ChatFormatting.GRAY)));
        p.sendSystemMessage(Component.literal(" 連続正解: " + PlayerProgress.streak(p) + "（最高 " + PlayerProgress.bestStreak(p) + "）")
                .withStyle(ChatFormatting.WHITE));
        for (Subject s : Subject.values()) {
            int a = PlayerProgress.answered(p, s);
            int c = PlayerProgress.correct(p, s);
            double r = a == 0 ? 0 : (double) c / a;
            p.sendSystemMessage(Component.literal(" " + s.label + " ").withStyle(s.color)
                    .append(Component.literal(Msg.bar(r, 10) + " " + (a == 0 ? "未挑戦" : Msg.pct(r) + "（" + c + "/" + a + "）")).withStyle(ChatFormatting.WHITE))
                    .append(" ").append(Msg.button("解く", "/archi quiz " + s.id, s.label + "のクイズ", ChatFormatting.YELLOW)));
        }
        p.sendSystemMessage(Component.literal(" 模試: 受験" + PlayerProgress.examsTaken(p) + "回 / 合格" + PlayerProgress.examsPassed(p)
                + "回 / 最高 " + PlayerProgress.bestExam(p) + "%").withStyle(ChatFormatting.LIGHT_PURPLE));
        p.sendSystemMessage(Component.literal(" チャレンジ: " + PlayerProgress.doneChallenges(p).size() + "/" + Challenges.ALL.size() + " クリア")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        Subject weak = PlayerProgress.weakestSubject(p);
        if (weak != null) {
            p.sendSystemMessage(Component.literal(" ").append(Msg.button("苦手な「" + weak.label + "」を特訓", "/archi quiz " + weak.id,
                    "正答率が一番低い科目", ChatFormatting.RED)));
        }
        return 1;
    }

    private static int tip(ServerPlayer p, @Nullable Subject s) {
        String t = KnowledgeBase.randomTip(s, RNG);
        if (t == null) {
            Msg.say(p, "豆知識が見つからなかったよ。");
            return 0;
        }
        Msg.say(p, Component.literal("豆知識: ").withStyle(ChatFormatting.GOLD).append(Component.literal(t).withStyle(ChatFormatting.WHITE)));
        p.sendSystemMessage(Component.literal(" ").append(Msg.button("もう1つ", "/archi tip" + (s != null ? " " + s.id : ""), "別の豆知識", ChatFormatting.GOLD)));
        return 1;
    }

    private static int lessons(ServerPlayer p, @Nullable Subject only) {
        Msg.header(p, "教科書の目次（Kキーで教科書GUIも開けるよ）", ChatFormatting.GREEN);
        for (Subject s : Subject.values()) {
            if (only != null && s != only) continue;
            List<Lesson> ls = KnowledgeBase.lessonsOf(s);
            if (ls.isEmpty()) continue;
            p.sendSystemMessage(Component.literal(" " + s.examName + "「" + s.label + "」").withStyle(s.color, ChatFormatting.BOLD));
            for (Lesson l : ls) {
                p.sendSystemMessage(Component.literal("  ").append(Msg.button(l.title(), "/archi lesson " + l.id(), "このレッスンを読む", ChatFormatting.WHITE)));
            }
        }
        return 1;
    }

    private static int lesson(ServerPlayer p, String id) {
        Lesson l = KnowledgeBase.lesson(id);
        if (l == null) {
            Msg.say(p, "そのレッスンは見つからないよ。 /archi lessons で目次を見てね。");
            return 0;
        }
        Msg.header(p, l.subject().label + "｜" + l.title(), l.subject().color);
        Msg.sendParagraphs(p, l.summary(), ChatFormatting.WHITE);
        if (!l.minecraft().isEmpty()) {
            p.sendSystemMessage(Component.literal(" ◆マイクラで例えると").withStyle(ChatFormatting.GREEN));
            Msg.sendParagraphs(p, l.minecraft(), ChatFormatting.WHITE);
        }
        if (!l.points().isEmpty()) {
            p.sendSystemMessage(Component.literal(" ◆試験に出るポイント").withStyle(ChatFormatting.RED));
            for (String pt : l.points()) p.sendSystemMessage(Component.literal("  ・" + pt).withStyle(ChatFormatting.WHITE));
        }
        if (!l.mnemonic().isEmpty()) {
            p.sendSystemMessage(Component.literal(" ◆覚え方").withStyle(ChatFormatting.LIGHT_PURPLE));
            Msg.sendParagraphs(p, l.mnemonic(), ChatFormatting.WHITE);
        }
        MutableComponent row = Component.literal(" ");
        row.append(Msg.button(l.subject().label + "クイズ", "/archi quiz " + l.subject().id, "この科目のクイズに挑戦", ChatFormatting.YELLOW));
        row.append(" ").append(Msg.suggest("先生に質問", "/archi ask " + l.title() + "について ", "このレッスンについて質問", ChatFormatting.AQUA));
        p.sendSystemMessage(row);
        return 1;
    }

    private static int analyze(ServerPlayer p) {
        HitResult hit = p.pick(24.0D, 1.0F, false);
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            Msg.say(p, "建物のブロックを見ながら実行してね（24ブロック以内）。");
            return 0;
        }
        BuildingAnalyzer.inspect(p, p.serverLevel(), bhr.getBlockPos(), InspectorItem.findSite(p));
        return 1;
    }

    private static int advice(ServerPlayer p, boolean on) {
        PlayerProgress.setAdviceOff(p, !on);
        Msg.say(p, on ? "建築中のアドバイスをONにしたよ。ブロックを置くと、ときどき豆知識を教えるね！"
                : "建築中のアドバイスをOFFにしたよ。また聞きたくなったら /archi advice on でね。");
        return 1;
    }

    private static int aiStatus(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("[ArchiQuest] " + AiClient.describe()), false);
        return 1;
    }

    private static int reload(CommandSourceStack src) {
        String summary = KnowledgeBase.reload();
        src.sendSuccess(() -> Component.literal("[ArchiQuest] 再読み込みしました: " + summary), true);
        return 1;
    }
}
