package jp.archiquest.ai;

import jp.archiquest.analysis.BuildingReport;
import jp.archiquest.analysis.RoomInfo;
import jp.archiquest.knowledge.GlossaryTerm;
import jp.archiquest.knowledge.KnowledgeBase;
import jp.archiquest.knowledge.Lesson;
import jp.archiquest.knowledge.QuizQuestion;
import jp.archiquest.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * オフライン先生：APIキーがなくても、内蔵の教科書・用語集・問題解説から検索して答える。
 */
public final class OfflineSensei {
    private static final Set<UUID> HINTED = ConcurrentHashMap.newKeySet();

    private OfflineSensei() {}

    public static void answer(ServerPlayer p, String question) {
        List<KnowledgeBase.Hit> hits = KnowledgeBase.search(question, 4);
        if (hits.isEmpty()) {
            Msg.say(p, "ごめんね、その言葉は教科書に見つからなかった…。「採光」「建ぺい率」「座屈」「結露」みたいなキーワードで聞いてみて！");
            hintApi(p);
            return;
        }
        KnowledgeBase.Hit top = hits.get(0);
        String kind = switch (top.kind()) {
            case "glossary" -> "用語";
            case "lesson" -> "教科書";
            default -> "問題の解説";
        };
        Msg.say(p, Component.literal("「" + top.title() + "」").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                .append(Component.literal("（" + kind + "より）").withStyle(ChatFormatting.GRAY)));
        Msg.sendParagraphs(p, shorten(top.text(), 300), ChatFormatting.WHITE);

        MutableComponent row = Component.literal(" ");
        boolean any = false;
        if (top.lessonId() != null) {
            row.append(Msg.button("教科書で読む", "/archi lesson " + top.lessonId(), "関連レッスンをチャットに表示", ChatFormatting.GREEN));
            any = true;
        }
        if (top.subject() != null) {
            if (any) row.append(" ");
            row.append(Msg.button(top.subject().label + "クイズ", "/archi quiz " + top.subject().id, "この科目のクイズに挑戦", ChatFormatting.YELLOW));
            any = true;
        }
        if (any) p.sendSystemMessage(row);

        if (hits.size() > 1) {
            MutableComponent rel = Component.literal(" 関連: ").withStyle(ChatFormatting.GRAY);
            for (int i = 1; i < hits.size(); i++) {
                KnowledgeBase.Hit h = hits.get(i);
                String label = h.title().length() > 18 ? h.title().substring(0, 18) + "…" : h.title();
                String cmd = h.lessonId() != null && h.kind().equals("lesson") ? "/archi lesson " + h.lessonId() : "/archi ask " + label.replace("…", "");
                rel.append(Msg.button(label, cmd, h.title(), ChatFormatting.AQUA)).append(" ");
            }
            p.sendSystemMessage(rel);
        }
        hintApi(p);
    }

    public static void explain(ServerPlayer p, QuizQuestion q) {
        Msg.say(p, Component.literal("この問題のポイントをおさらいしよう！").withStyle(ChatFormatting.YELLOW));
        p.sendSystemMessage(Component.literal(" Q. " + q.question()).withStyle(ChatFormatting.GRAY));
        p.sendSystemMessage(Component.literal(" 正解: " + q.choices().get(q.answer())).withStyle(ChatFormatting.GREEN));
        Msg.sendParagraphs(p, q.explanation(), ChatFormatting.WHITE);

        int shown = 0;
        for (GlossaryTerm g : KnowledgeBase.glossary()) {
            if (shown >= 2) break;
            if (g.term().length() >= 2 && (q.question() + String.join("", q.choices())).contains(g.term())) {
                p.sendSystemMessage(Component.literal(" ◆用語「" + g.term() + "」: ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(shorten(g.desc(), 120)).withStyle(ChatFormatting.WHITE)));
                shown++;
            }
        }
        Lesson l = KnowledgeBase.lesson(q.lesson());
        if (l != null && !l.points().isEmpty()) {
            p.sendSystemMessage(Component.literal(" ◆試験に出るポイント（" + l.title() + "）").withStyle(ChatFormatting.GOLD));
            for (int i = 0; i < Math.min(3, l.points().size()); i++) {
                p.sendSystemMessage(Component.literal("  ・" + l.points().get(i)).withStyle(ChatFormatting.WHITE));
            }
            p.sendSystemMessage(Component.literal(" ").append(Msg.button("レッスンを読む", "/archi lesson " + l.id(), "レッスン全文", ChatFormatting.GREEN))
                    .append(" ").append(Msg.button("次の問題", "/archi quiz " + q.subject().id, "同じ科目からもう1問", ChatFormatting.YELLOW)));
        }
        hintApi(p);
    }

    public static void review(ServerPlayer p, BuildingReport r) {
        Msg.header(p, "アーキ先生の講評", ChatFormatting.AQUA);
        p.sendSystemMessage(Component.literal(" ◎いいところ: ").withStyle(ChatFormatting.GREEN).append(Component.literal(goodPoint(r)).withStyle(ChatFormatting.WHITE)));

        int n = 0;
        for (BuildingReport.Finding f : r.findings) {
            if (f.text().contains("確認したよ") || f.text().contains("セーフ") || f.text().contains("君の出番")) continue;
            p.sendSystemMessage(Component.literal(" △改善ポイント" + (n + 1) + ": ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(f.text()).withStyle(ChatFormatting.WHITE)));
            if (++n >= 2) break;
        }
        if (n == 0) {
            p.sendSystemMessage(Component.literal(" △次の一歩: ").withStyle(ChatFormatting.YELLOW).append(Component.literal(r.site == null
                    ? "建築診断スケールで敷地を指定して、建ぺい率・容積率もチェックしてみよう！"
                    : "天窓（採光は3倍で計算）や、2方向に逃げられる出入口にも挑戦してみよう！").withStyle(ChatFormatting.WHITE)));
        }

        String lessonId = null;
        for (BuildingReport.Finding f : r.findings) {
            if (f.lesson() != null) {
                lessonId = f.lesson();
                break;
            }
        }
        Lesson l = KnowledgeBase.lesson(lessonId != null ? lessonId : "law_lighting");
        if (l != null && !l.points().isEmpty()) {
            p.sendSystemMessage(Component.literal(" ★試験ポイント: ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(l.points().get(0)).withStyle(ChatFormatting.WHITE))
                    .append(" ").append(Msg.button("解説", "/archi lesson " + l.id(), l.title(), ChatFormatting.GREEN)));
        }
        hintApi(p);
    }

    private static String goodPoint(BuildingReport r) {
        for (RoomInfo ri : r.rooms) {
            if (ri.habitable() && ri.allOk()) return "天井高・採光・換気・出口が全部OKの居室があるね！ この4点セットは試験でも超重要だよ。";
        }
        if (r.height > 20 && r.lightningRods > 0) return "高さ20m超えにちゃんと避雷針！ 法33条をクリアしていて満点！";
        if (r.site != null && r.outsideSite == 0) return "敷地の中にきちんと収まっているね。法規の基本ができてる！";
        if (r.storiesAbove >= 2) return "2階建て以上に挑戦しているのがいいね。上下の動線（階段）は計画の大事なテーマだよ。";
        if (r.glass > 0) return "窓（ガラス）を使って光を取り入れようとしているね。採光は住まいの基本！";
        return "建物を完成させたこと自体がすばらしい！ 次は基準をひとつずつクリアしていこう。";
    }

    private static void hintApi(ServerPlayer p) {
        if (HINTED.add(p.getUUID())) {
            p.sendSystemMessage(Component.literal(" ※いまは内蔵の教科書から答える「オフライン先生」モード。config/archiquest-common.toml でAPIキーを設定すると、AI先生が自由に答えてくれるよ")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
