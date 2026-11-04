package jp.satsujinki.game;

import jp.satsujinki.SatsujinkiConfig;
import jp.satsujinki.entity.ContestantEntity;
import jp.satsujinki.network.HostSyncPacket;
import jp.satsujinki.network.ModNetwork;
import jp.satsujinki.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Joy points (喜), combo and the 殺戮ランク style meter: D 退屈 → C 微笑 → B 愉快 → A 歓喜 → S 狂喜 → SS 恍惚 → SSS 殺人喜.
 */
public final class ScoreManager {
    public static final int MAX_RANK = 6;
    public static final float[] RANK_MULT = {1.0F, 1.2F, 1.5F, 2.0F, 2.5F, 3.0F, 4.0F};
    private static final float[] DECAY = {0.0012F, 0.0018F, 0.0022F, 0.0026F, 0.0030F, 0.0034F, 0.0040F};
    public static final int COMBO_WINDOW = 100;

    private static final class Live {
        int combo;
        long lastKill = -100000L;
        float gauge;
        int rank;
        @Nullable KillStyle lastStyle;
        int syncTimer;
    }

    private static final Map<UUID, Live> LIVE = new HashMap<>();

    private ScoreManager() {
    }

    private static Live live(UUID id) {
        return LIVE.computeIfAbsent(id, k -> new Live());
    }

    public static void onKill(ServerPlayer p, @Nullable ContestantEntity victim, KillStyle style) {
        HostData data = HostData.get(p.server);
        HostData.Stats s = data.of(p.getUUID());
        Live l = live(p.getUUID());
        long now = p.serverLevel().getGameTime();
        l.combo = now - l.lastKill <= COMBO_WINDOW ? l.combo + 1 : 1;
        l.lastKill = now;
        boolean variety = l.lastStyle != null && l.lastStyle != style;
        l.lastStyle = style;
        float comboMul = 1.0F + 0.25F * Math.min(l.combo - 1, 12);
        float gain = style.styleGain * (1.0F + 0.08F * Math.min(l.combo - 1, 10)) * (variety ? 1.25F : 1.0F);
        int oldRank = l.rank;
        l.gauge += gain;
        while (l.gauge >= 1.0F && l.rank < MAX_RANK) {
            l.gauge -= 1.0F;
            l.rank++;
        }
        if (l.rank == MAX_RANK) l.gauge = Math.min(l.gauge, 1.0F);
        int points = Math.round(style.basePoints * comboMul * RANK_MULT[l.rank] * (variety ? 1.2F : 1.0F));
        s.points += points;
        s.totalKills++;
        s.styleKills[style.ordinal()]++;
        s.maxCombo = Math.max(s.maxCombo, l.combo);
        s.bestRank = Math.max(s.bestRank, l.rank);
        data.setDirty();

        Adv.grant(p, "first_blood");
        switch (style) {
            case MIXER -> Adv.grant(p, "mixer");
            case PRESS -> Adv.grant(p, "press");
            case SAW -> Adv.grant(p, "saw");
            case GUILLOTINE -> Adv.grant(p, "guillotine");
            case SPIKES -> Adv.grant(p, "spikes");
            case THROW -> Adv.grant(p, "throw");
            default -> {
            }
        }
        if (l.combo >= 5) Adv.grant(p, "combo5");
        if (l.combo >= 10) Adv.grant(p, "combo10");
        if (l.rank >= 4) Adv.grant(p, "rank_s");
        if (l.rank >= MAX_RANK) Adv.grant(p, "rank_sss");
        if (s.totalKills >= 100) Adv.grant(p, "massacre");

        sync(p, style.ordinal(), points, l.rank > oldRank);
    }

    public static int points(ServerPlayer p) {
        return HostData.get(p.server).of(p.getUUID()).points;
    }

    public static void addPoints(ServerPlayer p, int amount) {
        HostData data = HostData.get(p.server);
        HostData.Stats s = data.of(p.getUUID());
        s.points = Math.max(0, s.points + amount);
        data.setDirty();
        sync(p, -1, 0, false);
    }

    public static boolean spend(ServerPlayer p, int amount) {
        HostData data = HostData.get(p.server);
        HostData.Stats s = data.of(p.getUUID());
        if (s.points < amount) return false;
        s.points -= amount;
        data.setDirty();
        sync(p, -1, 0, false);
        return true;
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Live l = LIVE.get(p.getUUID());
            if (l == null) continue;
            long now = p.serverLevel().getGameTime();
            boolean changed = false;
            if (l.combo > 0 && now - l.lastKill > COMBO_WINDOW) {
                l.combo = 0;
                changed = true;
            }
            if (l.rank > 0 || l.gauge > 0.0F) {
                l.gauge -= DECAY[l.rank];
                if (l.gauge < 0.0F) {
                    if (l.rank > 0) {
                        l.rank--;
                        l.gauge = 0.8F;
                    } else {
                        l.gauge = 0.0F;
                    }
                    changed = true;
                }
                if (++l.syncTimer >= 10) {
                    l.syncTimer = 0;
                    changed = true;
                }
            }
            if (changed) sync(p, -1, 0, false);
        }
    }

    public static void sync(ServerPlayer p, int popupStyle, int popupPoints, boolean rankUp) {
        HostData.Stats s = HostData.get(p.server).of(p.getUUID());
        Live l = live(p.getUUID());
        ModNetwork.sendTo(p, new HostSyncPacket(s.points, s.totalKills, l.combo, l.rank, l.gauge, popupStyle, popupPoints,
                rankUp, s.maxCombo, s.bestRank, s.roundsPlayed, s.roundsPerfect, s.styleKills.clone()));
    }

    public static void onLogin(ServerPlayer p) {
        HostData data = HostData.get(p.server);
        HostData.Stats s = data.of(p.getUUID());
        Adv.grant(p, "root");
        if (!s.starterGiven && SatsujinkiConfig.starterKit()) {
            s.starterGiven = true;
            data.setDirty();
            giveStarterKit(p);
            p.sendSystemMessage(Component.translatable("satsujinki.starter").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
        sync(p, -1, 0, false);
    }

    public static void giveStarterKit(ServerPlayer p) {
        ItemStack[] kit = {
                new ItemStack(ModItems.HOST_HAND.get()),
                new ItemStack(ModItems.HOST_REMOTE.get()),
                new ItemStack(ModItems.ARENA_KIT.get()),
                new ItemStack(ModItems.MIXER_KIT.get()),
                new ItemStack(ModItems.PRESS_KIT.get()),
                new ItemStack(ModItems.SAW.get(), 2),
                new ItemStack(ModItems.GUILLOTINE.get()),
                new ItemStack(ModItems.SPIKE_BED.get(), 9),
                new ItemStack(ModItems.CONTESTANT_TICKET.get(), 16),
                new ItemStack(ModItems.HOST_TERMINAL.get()),
                new ItemStack(ModItems.MOP.get())};
        for (ItemStack st : kit) {
            if (!p.getInventory().add(st)) p.drop(st, false);
        }
    }

    public static void forget(UUID id) {
        LIVE.remove(id);
    }

    public static void clear() {
        LIVE.clear();
    }
}
