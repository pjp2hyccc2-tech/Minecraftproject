package jp.archiquest.advice;

import jp.archiquest.ai.AiClient;
import jp.archiquest.ai.AiSensei;
import jp.archiquest.analysis.Terrain;
import jp.archiquest.config.ArchiConfig;
import jp.archiquest.knowledge.KnowledgeBase;
import jp.archiquest.knowledge.TipCategory;
import jp.archiquest.progress.PlayerProgress;
import jp.archiquest.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 建築中のアドバイス。ブロックを置くと、そのブロックに関係する試験知識を一定間隔で教えてくれる。
 * 高く建てると、法律上の節目（10m/16m/20m/31m/60m）も知らせる。
 */
public final class BuildAdvisor {
    private static final int[] HEIGHTS = {10, 16, 20, 31, 60};
    private static final long MILESTONE_REPEAT_MS = 20L * 60L * 1000L;
    private static final Random RNG = new Random();

    private static final Map<UUID, Long> LAST_TIP = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_AI = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Integer>> ROTATION = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<Integer, Long>> MILESTONES = new ConcurrentHashMap<>();

    private BuildAdvisor() {}

    public static void forget(UUID id) {
        LAST_TIP.remove(id);
        LAST_AI.remove(id);
        ROTATION.remove(id);
        MILESTONES.remove(id);
    }

    public static void onPlace(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
        if (!ArchiConfig.ADVICE_ENABLED.get() || PlayerProgress.adviceOff(p)) return;
        if (ArchiConfig.HEIGHT_MILESTONES.get() && checkHeight(p, level, pos)) return;

        long now = System.currentTimeMillis();
        Long last = LAST_TIP.get(p.getUUID());
        if (last != null && now - last < ArchiConfig.ADVICE_COOLDOWN.get() * 1000L) return;

        String cat = BlockCategories.categorize(state);
        if (cat == null) return;
        TipCategory tc = KnowledgeBase.tipCategory(cat);
        if (tc == null || tc.tips().isEmpty()) return;
        LAST_TIP.put(p.getUUID(), now);

        if (ArchiConfig.AI_AUTO_ADVICE.get() && AiClient.isConfigured()) {
            Long lastAi = LAST_AI.get(p.getUUID());
            if (lastAi == null || now - lastAi >= ArchiConfig.AI_AUTO_ADVICE_COOLDOWN.get() * 1000L) {
                LAST_AI.put(p.getUUID(), now);
                AiSensei.autoAdvice(p, state, tc, groundHeight(level, pos));
                return;
            }
        }
        sendTip(p, tc, nextTip(p, tc));
    }

    /** カテゴリ内の豆知識を順番に出す（最初はランダムな位置から） */
    public static String nextTip(ServerPlayer p, TipCategory tc) {
        Map<String, Integer> m = ROTATION.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>());
        int size = tc.tips().size();
        int i = m.getOrDefault(tc.id(), RNG.nextInt(size));
        m.put(tc.id(), (i + 1) % size);
        return tc.tips().get(i % size);
    }

    public static void sendTip(ServerPlayer p, TipCategory tc, String tip) {
        Msg.say(p, Component.literal("◆" + tc.label()).withStyle(tc.subject().color, ChatFormatting.BOLD));
        Msg.sendParagraphs(p, tip, ChatFormatting.WHITE);
        MutableComponent row = Component.literal(" ");
        if (tc.lesson() != null && !tc.lesson().isEmpty()) {
            row.append(Msg.button("教科書で読む", "/archi lesson " + tc.lesson(), "関連レッスンをチャットに表示", ChatFormatting.GREEN)).append(" ");
        }
        row.append(Msg.button("クイズ", "/archi quiz " + tc.subject().id, tc.subject().label + "のクイズに挑戦", ChatFormatting.YELLOW));
        row.append(" ").append(Msg.button("アドバイスOFF", "/archi advice off", "建築中のアドバイスを止める（/archi advice on で再開）", ChatFormatting.DARK_GRAY));
        p.sendSystemMessage(row);
    }

    // ------------------------------------------------------------------ height milestones

    private static boolean checkHeight(ServerPlayer p, ServerLevel level, BlockPos pos) {
        int h = groundHeight(level, pos);
        if (h < HEIGHTS[0]) return false;
        int hit = -1;
        for (int t : HEIGHTS) if (h >= t) hit = t;
        if (hit < 0) return false;

        Map<Integer, Long> m = MILESTONES.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        Long last = m.get(hit);
        if (last != null && now - last < MILESTONE_REPEAT_MS) return false;
        m.put(hit, now);
        for (int t : HEIGHTS) if (t < hit) m.putIfAbsent(t, now);

        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle(hit)).withStyle(ChatFormatting.YELLOW)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("高さ " + hit + "m 突破！").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.0f);

        TipCategory tc = KnowledgeBase.tipCategory("height_" + hit);
        Msg.say(p, Component.literal("◆高さ" + h + "m（地面から）").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (tc != null && !tc.tips().isEmpty()) {
            Msg.sendParagraphs(p, tc.tips().get(RNG.nextInt(tc.tips().size())), ChatFormatting.WHITE);
            if (tc.lesson() != null && !tc.lesson().isEmpty()) {
                p.sendSystemMessage(Component.literal(" ").append(Msg.button("教科書で読む", "/archi lesson " + tc.lesson(), "関連レッスン", ChatFormatting.GREEN)));
            }
        } else {
            p.sendSystemMessage(Component.literal(" " + subtitle(hit)).withStyle(ChatFormatting.WHITE));
        }
        return true;
    }

    private static String subtitle(int h) {
        return switch (h) {
            case 10 -> "低層住居専用地域の絶対高さ制限は10mか12m";
            case 16 -> "二級建築士が設計できるのは高さ16mまで";
            case 20 -> "高さ20m超は避雷設備が必要（法33条）";
            case 31 -> "高さ31m超は非常用エレベーターが必要";
            case 60 -> "60m超は超高層！大臣認定が必要";
            default -> "";
        };
    }

    /** 置いた位置の地面からの高さ（m）。見つからなければ0 */
    public static int groundHeight(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos m = pos.mutable();
        int minY = level.getMinBuildHeight();
        for (int i = 1; i <= 256; i++) {
            int y = pos.getY() - i;
            if (y < minY) return 0;
            m.setY(y);
            if (!level.isLoaded(m)) return 0;
            if (Terrain.isGround(level.getBlockState(m))) return i;
        }
        return 0;
    }
}
