package jp.archiquest.analysis;

import jp.archiquest.config.ArchiConfig;
import jp.archiquest.knowledge.KnowledgeBase;
import jp.archiquest.knowledge.Subject;
import jp.archiquest.knowledge.TipCategory;
import jp.archiquest.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 建築診断の結果 */
public final class BuildingReport {
    /** 指摘事項 */
    public record Finding(String text, Subject subject, @Nullable String lesson) {
    }

    private static final Random RNG = new Random();

    @Nullable
    public String error;
    public boolean truncated;
    public boolean roomsSkipped;
    public long millis;

    public int blockCount;
    public int footprint;      // 建築面積
    public int totalFloorArea; // 延べ面積（内法）
    public int height;
    public int groundY;
    public int minY;
    public int maxY;
    public int storiesAbove;
    public int storiesBelow;

    public int glass;
    public int stairs;
    public int lightningRods;
    public int openings;

    public final Map<MaterialKind, Integer> materials = new EnumMap<>(MaterialKind.class);
    public MaterialKind main = MaterialKind.OTHER;
    public final List<RoomInfo> rooms = new ArrayList<>();
    public final List<Finding> findings = new ArrayList<>();

    @Nullable
    public Site site;
    public int outsideSite;

    // ------------------------------------------------------------------ derived values

    public double coverage() {
        return site == null ? 0 : (double) footprint / site.area();
    }

    public double floorAreaRatio() {
        return site == null ? 0 : (double) totalFloorArea / site.area();
    }

    /** 部屋が見つからない場合は高さから階数を推定（1階あたり約3m） */
    public int effectiveStories() {
        if (storiesAbove > 0) return storiesAbove;
        return Math.max(1, (int) Math.round(height / 3.0));
    }

    public int habitableCount() {
        int n = 0;
        for (RoomInfo r : rooms) if (r.habitable()) n++;
        return n;
    }

    private int share(MaterialKind k) {
        int total = 0;
        for (int v : materials.values()) total += v;
        return total == 0 ? 0 : (int) Math.round(100.0 * materials.getOrDefault(k, 0) / total);
    }

    public String structureLabel() {
        if (share(MaterialKind.CONCRETE) >= 30 && share(MaterialKind.STEEL) >= 10) return "SRC造（鉄骨鉄筋コンクリート造）っぽい";
        return switch (main) {
            case WOOD -> "木造っぽい";
            case CONCRETE -> "RC造（鉄筋コンクリート造）っぽい";
            case STEEL -> "S造（鉄骨造）っぽい";
            case MASONRY -> "組積造（石・レンガ造）っぽい";
            default -> "いろいろな材料のミックス";
        };
    }

    @Nullable
    public String structTipId() {
        return switch (main) {
            case WOOD -> "struct_wood";
            case CONCRETE -> "struct_rc";
            case STEEL -> "struct_steel";
            case MASONRY -> "struct_masonry";
            default -> null;
        };
    }

    public String materialBreakdown() {
        StringBuilder sb = new StringBuilder();
        for (MaterialKind k : MaterialKind.values()) {
            int s = share(k);
            if (s <= 0) continue;
            if (sb.length() > 0) sb.append(" / ");
            sb.append(k.label).append(" ").append(s).append("%");
        }
        return sb.toString();
    }

    public String storiesLabel() {
        if (storiesAbove <= 0) return "不明";
        return storiesAbove + "階" + (storiesBelow > 0 ? "・地下" + storiesBelow + "階" : "");
    }

    // ------------------------------------------------------------------ chat output

    public void send(ServerPlayer p) {
        Msg.header(p, "建築診断レポート", ChatFormatting.AQUA);
        if (error != null) {
            Msg.say(p, error);
            return;
        }

        p.sendSystemMessage(Component.literal(" ■構造: ").withStyle(ChatFormatting.GOLD)
                .append(Msg.hover(structureLabel(), "使っているブロックの割合から推定したよ。\n" + materialBreakdown()
                        + "\n※マイクラでは壁の中の鉄筋や柱は見えないので、あくまで「っぽい」判定です。", ChatFormatting.WHITE)));

        p.sendSystemMessage(Component.literal(" ■").withStyle(ChatFormatting.GOLD)
                .append(Msg.hover("建築面積 " + footprint + "m²",
                        "建物を真上から見た面積（水平投影面積）。1ブロック＝1m²。\n※本当は壁や柱の中心線で測り、はね出し1m以下の軒などは除きます。",
                        ChatFormatting.WHITE))
                .append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Msg.hover("延べ面積 " + totalFloorArea + "m²",
                        "各階の床面積の合計。ここでは部屋の内側（内法）で計算。\n※本当は壁の中心線（壁芯）で測るので、もう少し大きくなります。",
                        ChatFormatting.WHITE)));

        p.sendSystemMessage(Component.literal(" ■").withStyle(ChatFormatting.GOLD)
                .append(Msg.hover("高さ " + height + "m", "地面から一番上までの高さ（1ブロック＝1m）。", ChatFormatting.WHITE))
                .append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Msg.hover("階数 " + storiesLabel(), "部屋の床の高さから推定したよ。", ChatFormatting.WHITE))
                .append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Msg.hover("部屋 " + rooms.size() + "室", "壁・床・天井で囲まれた空間を部屋として数えたよ（ドアは閉めておいてね）。", ChatFormatting.WHITE)));

        if (truncated) {
            p.sendSystemMessage(Component.literal(" ※大きすぎる部分・読み込まれていない部分は診断を省略したよ").withStyle(ChatFormatting.GRAY));
        }

        // 敷地
        if (site != null) {
            int covLimit = ArchiConfig.COVERAGE_LIMIT.get();
            int farLimit = ArchiConfig.FAR_LIMIT.get();
            double cov = coverage();
            double far = floorAreaRatio();
            p.sendSystemMessage(Component.literal(" ■敷地 " + site.area() + "m²（" + site.width() + "m×" + site.depth() + "m）")
                    .withStyle(ChatFormatting.GOLD));
            p.sendSystemMessage(Component.literal("  ・").withStyle(ChatFormatting.WHITE)
                    .append(Msg.hover("建ぺい率 " + footprint + "÷" + site.area() + " = " + Msg.pct(cov),
                            "建ぺい率 ＝ 建築面積 ÷ 敷地面積（法53条）。\n角地や、防火地域の耐火建築物などは+10%の緩和があるよ。",
                            ChatFormatting.WHITE))
                    .append(Component.literal("（上限" + covLimit + "%）").withStyle(ChatFormatting.GRAY))
                    .append(okNg(cov * 100 <= covLimit + 1e-9)));
            p.sendSystemMessage(Component.literal("  ・").withStyle(ChatFormatting.WHITE)
                    .append(Msg.hover("容積率 " + totalFloorArea + "÷" + site.area() + " = " + Msg.pct(far),
                            "容積率 ＝ 延べ面積 ÷ 敷地面積（法52条）。\n前面道路の幅が12m未満だと、道路幅×0.4（住居系）や×0.6でも制限されるよ。",
                            ChatFormatting.WHITE))
                    .append(Component.literal("（上限" + farLimit + "%）").withStyle(ChatFormatting.GRAY))
                    .append(okNg(far * 100 <= farLimit + 1e-9)));
        } else {
            p.sendSystemMessage(Component.literal(" ■敷地: 未設定 ").withStyle(ChatFormatting.GOLD)
                    .append(Msg.hover("（設定方法）", "建築診断スケールを持ってスニーク＋右クリックで、敷地の角を2か所指定してね。\n建ぺい率・容積率も診断できるようになるよ。",
                            ChatFormatting.GRAY)));
        }

        // 部屋
        if (roomsSkipped) {
            p.sendSystemMessage(Component.literal(" ■部屋の診断: 建物が大きすぎるので省略したよ").withStyle(ChatFormatting.GRAY));
        } else if (rooms.isEmpty()) {
            p.sendSystemMessage(Component.literal(" ■部屋: 囲まれた空間が見つからなかったよ。壁・床・天井で囲むと部屋として診断できるよ（ドアは閉めてね）")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            p.sendSystemMessage(Component.literal(" ■部屋の診断（マウスを乗せると詳しく出るよ）").withStyle(ChatFormatting.GOLD));
            int n = 0;
            for (RoomInfo ri : rooms) {
                n++;
                if (n > 8) {
                    p.sendSystemMessage(Component.literal("  …ほか" + (rooms.size() - 8) + "室").withStyle(ChatFormatting.GRAY));
                    break;
                }
                p.sendSystemMessage(roomLine(ri, n));
            }
        }

        // 指摘事項
        if (!findings.isEmpty()) {
            p.sendSystemMessage(Component.literal(" ■アーキ先生のチェック").withStyle(ChatFormatting.GOLD));
            for (Finding f : findings) {
                MutableComponent line = Component.literal("  ・" + f.text()).withStyle(ChatFormatting.WHITE);
                if (f.lesson() != null && !f.lesson().isEmpty()) {
                    line.append(" ").append(Msg.button("解説", "/archi lesson " + f.lesson(), "関連レッスンを読む", ChatFormatting.GREEN));
                }
                p.sendSystemMessage(line);
            }
        }

        // 構造の豆知識
        String tipId = structTipId();
        TipCategory sc = tipId == null ? null : KnowledgeBase.tipCategory(tipId);
        if (sc != null && !sc.tips().isEmpty()) {
            p.sendSystemMessage(Component.literal(" ◆" + sc.label() + "：").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(sc.tips().get(RNG.nextInt(sc.tips().size()))).withStyle(ChatFormatting.WHITE)));
        }

        MutableComponent row = Component.literal(" ");
        row.append(Msg.button("AI先生の講評", "/archi review", "この建物をAI先生が講評するよ（APIキー未設定なら内蔵アドバイス）", ChatFormatting.AQUA));
        row.append(" ").append(Msg.button("法規クイズ", "/archi quiz law", "法規のクイズに挑戦", ChatFormatting.RED));
        row.append(" ").append(Msg.button("チャレンジ", "/archi challenge", "建築チャレンジの一覧", ChatFormatting.LIGHT_PURPLE));
        p.sendSystemMessage(row);
        p.sendSystemMessage(Component.literal(" （ブロック" + blockCount + "個を" + millis + "msで診断）").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent okNg(boolean ok) {
        return Component.literal(ok ? " ✔OK" : " ✘オーバー").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static MutableComponent check(String label, int state) {
        // state: 2=OK, 1=緩和でOK, 0=NG
        String mark = state == 2 ? "✔" : state == 1 ? "△" : "✘";
        ChatFormatting c = state == 2 ? ChatFormatting.GREEN : state == 1 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return Component.literal(label + mark + " ").withStyle(c);
    }

    private MutableComponent roomLine(RoomInfo ri, int n) {
        String name = ri.storyLabel() + " 部屋" + n + (ri.bed() ? "（寝室）" : "") + (!ri.habitable() ? "（納戸）" : "");
        MutableComponent line = Component.literal("  ・" + name + " 床" + ri.floorArea() + "m² 天井" + Msg.fmt1(ri.avgCeiling()) + "m ")
                .withStyle(ChatFormatting.WHITE);
        if (!ri.habitable()) {
            line.append(Component.literal("（4m²未満は居室の基準の対象外）").withStyle(ChatFormatting.GRAY));
        } else {
            line.append(check("天井高", ri.ceilingOk() ? 2 : 0));
            line.append(check("採光", ri.lightOk() ? 2 : ri.lightRelaxedOk() ? 1 : 0));
            line.append(check("換気", ri.ventOk() ? 2 : 0));
            line.append(check("出口", ri.hasExit() ? 2 : 0));
        }
        String detail = "【" + name + "】\n"
                + "床面積: " + ri.floorArea() + "m² / 体積: " + ri.volume() + "m³\n"
                + "平均天井高: " + Msg.fmt1(ri.avgCeiling()) + "m（居室は2.1m以上／令21条）\n"
                + "窓: " + ri.windows() + "m² ・ 天窓: " + ri.skylights() + "m²（天窓は3倍で計算）\n"
                + "→ 採光 " + Msg.fraction(ri.lightRatio()) + "（住宅の居室は1/7以上／法28条。\n   床面50lx以上の照明があれば1/10まで緩和）\n"
                + "ドア等: " + ri.openings() + "m² → 換気 " + Msg.fraction(ri.ventRatio()) + "（1/20以上／法28条）\n"
                + (ri.linkedExit() ? "出口: この階にドアはないけど、階段などでつながった別の階から出られるよ\n" : "")
                + "照明: " + ri.lights() + "個\n"
                + "※マイクラ用に簡略化しています（採光補正係数などは省略）";
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(detail));
        return line.withStyle(st -> st.withHoverEvent(hover));
    }

    // ------------------------------------------------------------------ AI summary

    public String toAiSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("建物データ（Minecraft、1ブロック=1m）\n");
        sb.append("- 構造の推定: ").append(structureLabel()).append("（").append(materialBreakdown()).append("）\n");
        sb.append("- 建築面積 ").append(footprint).append("m²、延べ面積(内法) ").append(totalFloorArea).append("m²、高さ ")
                .append(height).append("m、階数 ").append(storiesLabel()).append("\n");
        if (site != null) {
            sb.append("- 敷地 ").append(site.area()).append("m²、建ぺい率 ").append(Msg.pct(coverage())).append("（上限")
                    .append(ArchiConfig.COVERAGE_LIMIT.get()).append("%）、容積率 ").append(Msg.pct(floorAreaRatio())).append("（上限")
                    .append(ArchiConfig.FAR_LIMIT.get()).append("%）、敷地外にはみ出し ").append(outsideSite).append("m²\n");
        } else {
            sb.append("- 敷地: 未設定\n");
        }
        sb.append("- 階段ブロック ").append(stairs).append("個、ガラス ").append(glass).append("個、ドア等 ").append(openings)
                .append("個、避雷針 ").append(lightningRods).append("個\n");
        int n = 0;
        for (RoomInfo ri : rooms) {
            if (++n > 10) break;
            sb.append("- 部屋").append(n).append("（").append(ri.storyLabel()).append(ri.bed() ? "・寝室" : "").append("）: 床")
                    .append(ri.floorArea()).append("m²、平均天井高").append(Msg.fmt1(ri.avgCeiling())).append("m、窓")
                    .append(ri.windows()).append("m²、天窓").append(ri.skylights()).append("m²、ドア等").append(ri.openings())
                    .append("m²、照明").append(ri.lights()).append("個 → 天井高").append(ri.ceilingOk() ? "OK" : "NG")
                    .append("、採光").append(ri.lightOk() ? "OK" : ri.lightRelaxedOk() ? "照明で緩和OK" : "NG")
                    .append("、換気").append(ri.ventOk() ? "OK" : "NG").append("\n");
        }
        for (Finding f : findings) sb.append("- 指摘: ").append(f.text()).append("\n");
        return sb.toString();
    }
}
