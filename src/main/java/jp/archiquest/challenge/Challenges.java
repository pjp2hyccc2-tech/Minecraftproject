package jp.archiquest.challenge;

import jp.archiquest.analysis.BuildingReport;
import jp.archiquest.analysis.RoomInfo;
import jp.archiquest.config.ArchiConfig;
import jp.archiquest.knowledge.Subject;
import jp.archiquest.progress.PlayerProgress;
import jp.archiquest.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 建築チャレンジ：課題に合った建物を建てて、建築診断スケールで診断するとクリア判定される。
 * 「設計製図」のミニ版として、法規や計画の基準を手を動かして覚える。
 */
public final class Challenges {
    public record Check(String label, boolean ok) {
    }

    public record Challenge(String id, String title, String goal, String hint, Subject subject, int points,
                            Supplier<ItemStack> reward, String rewardLabel,
                            Function<BuildingReport, List<Check>> checker) {
    }

    public static final List<Challenge> ALL = List.of(
            new Challenge("first_room", "はじめての居室",
                    "床面積6m²以上で、天井高2.1m以上・採光1/7以上・出入口ありの部屋を1つつくろう",
                    "天井は3マス（3.0m）にすると2.1mをクリア。床6マスなら窓は1枚（1/6）でOK！",
                    Subject.LAW, 60, () -> new ItemStack(Items.EMERALD, 3), "エメラルド×3",
                    r -> {
                        RoomInfo best = bestRoom(r, ri -> ri.floorArea() >= 6,
                                List.of(RoomInfo::ceilingOk, RoomInfo::lightOk, RoomInfo::hasExit));
                        return List.of(
                                new Check("床面積6m²以上の部屋がある", best != null),
                                new Check("天井高2.1m以上", best != null && best.ceilingOk()),
                                new Check("採光1/7以上（窓面積÷床面積）", best != null && best.lightOk()),
                                new Check("出入口（ドアなど）がある", best != null && best.hasExit()));
                    }),
            new Challenge("skylight", "天窓マジック",
                    "天井にガラス（天窓）がある部屋をつくり、採光1/7以上を達成しよう",
                    "天窓は壁の窓の3倍の採光効果として計算されるよ（施行令20条）。屋根にガラスを1枚！",
                    Subject.LAW, 50, () -> new ItemStack(Items.GLASS, 16), "ガラス×16",
                    r -> {
                        RoomInfo best = bestRoom(r, ri -> ri.skylights() > 0 && ri.habitable(), List.of(RoomInfo::lightOk));
                        return List.of(
                                new Check("天窓のある居室がある", best != null),
                                new Check("その部屋の採光が1/7以上", best != null && best.lightOk()));
                    }),
            new Challenge("two_story_house", "快適な2階建て住宅",
                    "2階建て以上で、居室3つ以上。全居室が天井高2.1m・採光・換気の基準をクリアし、寝室（ベッド）と階段がある家",
                    "部屋ごとにドア（換気1/20）とガラス（採光1/7）を忘れずに。階段は部屋の外に置いてもOK",
                    Subject.LAW, 120, () -> new ItemStack(Items.DIAMOND, 2), "ダイヤモンド×2",
                    r -> {
                        boolean allOk = r.habitableCount() > 0;
                        boolean bed = false;
                        for (RoomInfo ri : r.rooms) {
                            if (!ri.habitable()) continue;
                            if (!(ri.ceilingOk() && ri.lightAcceptable() && ri.ventOk() && ri.hasExit())) allOk = false;
                            if (ri.bed()) bed = true;
                        }
                        return List.of(
                                new Check("2階建て以上", r.storiesAbove >= 2),
                                new Check("居室（4m²以上の部屋）が3つ以上", r.habitableCount() >= 3),
                                new Check("全居室が天井高・採光・換気・出口OK", allOk),
                                new Check("寝室（ベッドのある部屋）がある", bed),
                                new Check("階段がある", r.stairs > 0));
                    }),
            new Challenge("site_rules", "建ぺい率・容積率を守れ！",
                    "敷地を指定して、はみ出さずに、建ぺい率・容積率の上限以内で、延べ面積50m²以上の家を建てよう",
                    "スケールでスニーク＋右クリック×2で敷地指定。2階建てにすると建ぺい率を抑えつつ延べ面積を稼げるよ",
                    Subject.LAW, 100, () -> new ItemStack(Items.EMERALD, 8), "エメラルド×8",
                    r -> List.of(
                            new Check("敷地を指定している", r.site != null),
                            new Check("建物が敷地からはみ出していない", r.site != null && r.outsideSite == 0),
                            new Check("建ぺい率が上限（" + ArchiConfig.COVERAGE_LIMIT.get() + "%）以内",
                                    r.site != null && r.coverage() * 100 <= ArchiConfig.COVERAGE_LIMIT.get() + 1e-9),
                            new Check("容積率が上限（" + ArchiConfig.FAR_LIMIT.get() + "%）以内",
                                    r.site != null && r.floorAreaRatio() * 100 <= ArchiConfig.FAR_LIMIT.get() + 1e-9),
                            new Check("延べ面積50m²以上", r.totalFloorArea >= 50))),
            new Challenge("classroom", "明るい教室",
                    "床面積40m²以上の部屋で、学校の教室の採光基準1/5以上を満たし、出入口を2か所以上つくろう",
                    "学校の教室は住宅（1/7）より厳しい1/5。出入口が2つあると、片方がふさがっても避難できるよ",
                    Subject.PLANNING, 120, () -> new ItemStack(Items.DIAMOND, 2), "ダイヤモンド×2",
                    r -> {
                        RoomInfo best = bestRoom(r, ri -> ri.floorArea() >= 40,
                                List.of(ri -> ri.lightRatio() >= 0.2 - 1e-9, RoomInfo::ceilingOk, ri -> ri.openings() >= 4, ri -> ri.lights() > 0));
                        return List.of(
                                new Check("床面積40m²以上の部屋がある", best != null),
                                new Check("採光1/5以上", best != null && best.lightRatio() >= 0.2 - 1e-9),
                                new Check("天井高2.1m以上", best != null && best.ceilingOk()),
                                new Check("出入口2か所以上（ドア2枚＝開口4m²以上）", best != null && best.openings() >= 4),
                                new Check("照明がある", best != null && best.lights() > 0));
                    }),
            new Challenge("lightning", "20mの塔と避雷針",
                    "高さ20m以上の建物を建てて、避雷針を付けよう",
                    "高さ20mを超える建築物には避雷設備が必要（法33条）。銅インゴット3個で避雷針がクラフトできるよ",
                    Subject.LAW, 80, () -> new ItemStack(Items.COPPER_INGOT, 16), "銅インゴット×16",
                    r -> List.of(
                            new Check("高さ20m以上", r.height >= 20),
                            new Check("避雷針がある", r.lightningRods > 0)))
    );

    private Challenges() {}

    @Nullable
    public static Challenge byId(String id) {
        for (Challenge c : ALL) if (c.id().equals(id)) return c;
        return null;
    }

    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (Challenge c : ALL) out.add(c.id());
        return out;
    }

    /** 条件 filter を満たす部屋のうち、checks を一番多く満たす部屋 */
    @Nullable
    private static RoomInfo bestRoom(BuildingReport r, Predicate<RoomInfo> filter, List<Predicate<RoomInfo>> checks) {
        RoomInfo best = null;
        int bestScore = -1;
        for (RoomInfo ri : r.rooms) {
            if (!filter.test(ri)) continue;
            int score = 0;
            for (Predicate<RoomInfo> c : checks) if (c.test(ri)) score++;
            if (score > bestScore) {
                bestScore = score;
                best = ri;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ commands

    public static int list(ServerPlayer p) {
        Msg.header(p, "建築チャレンジ", ChatFormatting.LIGHT_PURPLE);
        String current = PlayerProgress.challenge(p);
        for (Challenge c : ALL) {
            boolean done = PlayerProgress.challengeDone(p, c.id());
            MutableComponent line = Component.literal(done ? " ✔ " : " ・").withStyle(done ? ChatFormatting.GREEN : ChatFormatting.WHITE)
                    .append(Msg.hover(c.title(), c.goal() + "\n報酬: " + c.points() + "pt＋" + c.rewardLabel(), ChatFormatting.WHITE));
            if (c.id().equals(current)) {
                line.append(Component.literal(" ←挑戦中").withStyle(ChatFormatting.YELLOW));
            } else {
                line.append(" ").append(Msg.button("挑戦する", "/archi challenge start " + c.id(), c.goal(), ChatFormatting.LIGHT_PURPLE));
            }
            p.sendSystemMessage(line);
        }
        p.sendSystemMessage(Component.literal(" 建てたら「建築診断スケール」で建物を右クリックすると判定するよ！").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    public static int start(ServerPlayer p, String id) {
        Challenge c = byId(id);
        if (c == null) {
            Msg.say(p, "そのチャレンジは見つからないよ。 /archi challenge で一覧を見てね。");
            return 0;
        }
        PlayerProgress.setChallenge(p, c.id());
        Msg.header(p, "チャレンジ開始：" + c.title(), ChatFormatting.LIGHT_PURPLE);
        p.sendSystemMessage(Component.literal(" 目標: " + c.goal()).withStyle(ChatFormatting.WHITE));
        p.sendSystemMessage(Component.literal(" ヒント: " + c.hint()).withStyle(ChatFormatting.YELLOW));
        p.sendSystemMessage(Component.literal(" 報酬: " + c.points() + "pt＋" + c.rewardLabel()).withStyle(ChatFormatting.GOLD));
        p.sendSystemMessage(Component.literal(" 完成したら建築診断スケールで建物を右クリック！").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    public static int quit(ServerPlayer p) {
        PlayerProgress.setChallenge(p, "");
        Msg.say(p, "チャレンジをやめたよ。");
        return 1;
    }

    /** 建築診断のあとに呼ばれる */
    public static void onReport(ServerPlayer p, BuildingReport r) {
        String id = PlayerProgress.challenge(p);
        if (id.isEmpty()) return;
        Challenge c = byId(id);
        if (c == null) return;
        List<Check> checks = c.checker().apply(r);
        boolean all = true;
        Msg.header(p, "チャレンジ判定：" + c.title(), ChatFormatting.LIGHT_PURPLE);
        for (Check ch : checks) {
            p.sendSystemMessage(Component.literal(ch.ok() ? "  ✔ " : "  ✘ ").withStyle(ch.ok() ? ChatFormatting.GREEN : ChatFormatting.RED)
                    .append(Component.literal(ch.label()).withStyle(ChatFormatting.WHITE)));
            if (!ch.ok()) all = false;
        }
        if (!all) {
            p.sendSystemMessage(Component.literal("  あと少し！ ヒント: " + c.hint()).withStyle(ChatFormatting.YELLOW));
            return;
        }
        boolean first = !PlayerProgress.challengeDone(p, c.id());
        PlayerProgress.markChallengeDone(p, c.id());
        PlayerProgress.setChallenge(p, "");
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(c.title()).withStyle(ChatFormatting.LIGHT_PURPLE)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("チャレンジクリア！").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        if (first) {
            ItemHandlerHelper.giveItemToPlayer(p, c.reward().get());
            Msg.say(p, Component.literal("すごい！「" + c.title() + "」クリア！ 報酬: " + c.points() + "pt＋" + c.rewardLabel())
                    .withStyle(ChatFormatting.GOLD));
            PlayerProgress.addPoints(p, c.points());
        } else {
            Msg.say(p, Component.literal("「" + c.title() + "」を再びクリア！（報酬は初回のみ）＋10pt").withStyle(ChatFormatting.GOLD));
            PlayerProgress.addPoints(p, 10);
        }
        p.sendSystemMessage(Component.literal(" ").append(Msg.button("次のチャレンジ", "/archi challenge", "チャレンジ一覧", ChatFormatting.LIGHT_PURPLE)));
    }
}
