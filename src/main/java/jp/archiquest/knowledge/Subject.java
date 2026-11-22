package jp.archiquest.knowledge;

import net.minecraft.ChatFormatting;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 一級建築士試験の科目。
 * 学科は5科目125問（四肢択一）。合格基準点は令和7年の値（毎年補正されることがある）。
 */
public enum Subject {
    PLANNING("planning", "計画", "計画", "学科Ⅰ", ChatFormatting.GREEN, 0x2E7D32, 20, 11,
            "建物の使いやすさ・歴史・まちづくり。人の寸法と動き、名建築と建築家がよく出るよ。"),
    ENVIRONMENT("environment", "環境・設備", "環境設備", "学科Ⅱ", ChatFormatting.AQUA, 0x00838F, 20, 11,
            "熱・光・音・空気・水と、それを整える設備の科学。快適さと省エネのしくみを学ぼう。"),
    LAW("law", "法規", "法規", "学科Ⅲ", ChatFormatting.RED, 0xC62828, 30, 16,
            "建築基準法などのルール。試験には法令集を持ち込めるので『どこに書いてあるかを素早く引く力』が勝負！"),
    STRUCTURE("structure", "構造", "構造", "学科Ⅳ", ChatFormatting.GOLD, 0xD84315, 30, 16,
            "建物が重さや地震に負けない理由。力のつりあいの計算と、木・鉄筋コンクリート・鉄骨の性質。"),
    CONSTRUCTION("construction", "施工", "施工", "学科Ⅴ", ChatFormatting.YELLOW, 0x8D6E00, 25, 13,
            "現場で実際にどう造るか。コンクリートの打ち方から安全管理・工程管理まで。"),
    DESIGN("design", "設計製図", "製図", "設計製図", ChatFormatting.LIGHT_PURPLE, 0x6A1B9A, 0, 0,
            "学科合格後の第2関門。事前に公表される課題について、6時間30分で図面と『計画の要点』をまとめる試験。");

    public final String id;
    public final String label;
    public final String shortLabel;
    public final String examName;
    public final ChatFormatting color;
    public final int rgb;
    /** 本番の出題数（設計製図は0） */
    public final int examQuestions;
    /** 合格基準点の例（令和7年） */
    public final int passMark;
    public final String description;

    Subject(String id, String label, String shortLabel, String examName, ChatFormatting color, int rgb,
            int examQuestions, int passMark, String description) {
        this.id = id;
        this.label = label;
        this.shortLabel = shortLabel;
        this.examName = examName;
        this.color = color;
        this.rgb = rgb;
        this.examQuestions = examQuestions;
        this.passMark = passMark;
        this.description = description;
    }

    /** 学科試験の科目か */
    public boolean isGakka() {
        return examQuestions > 0;
    }

    @Nullable
    public static Subject byId(@Nullable String id) {
        if (id == null) return null;
        String raw = id.trim();
        String s = raw.toLowerCase(Locale.ROOT);
        for (Subject x : values()) {
            if (x.id.equals(s) || x.label.equals(raw) || x.shortLabel.equals(raw)) return x;
        }
        return null;
    }

    public static Subject byIdOr(@Nullable String id, Subject def) {
        Subject s = byId(id);
        return s != null ? s : def;
    }

    public static List<String> ids() {
        List<String> list = new ArrayList<>();
        for (Subject s : values()) list.add(s.id);
        return list;
    }
}
