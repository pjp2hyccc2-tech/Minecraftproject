package com.clockworkdiver.story;

import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.customer.CustomerEntity;
import com.clockworkdiver.customer.CustomerProfile;
import com.clockworkdiver.data.PlayerProgress;
import com.clockworkdiver.data.WorldProgress;
import com.clockworkdiver.item.KeepsakeItem;
import com.clockworkdiver.network.ModNetwork;
import com.clockworkdiver.network.S2C;
import com.clockworkdiver.registry.ModEntities;
import com.clockworkdiver.registry.ModItems;
import com.clockworkdiver.util.DelayedTasks;
import com.clockworkdiver.workshop.Upgrades;
import com.clockworkdiver.workshop.WorkshopManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Runs the story: which commission comes next, the chapter and quest title cards, rewards and keepsakes, and
 * the friends who gather for the big scenes (the tower plaza in chapter 5, the shop for the finale).
 */
public final class StoryManager {
    public static final String SCENE_TOWER = "tower";
    public static final String SCENE_FINALE = "finale";

    /** Where the player stands to inspect the Great Clock, in front of the tower gate (relative to the shop origin). */
    public static final BlockPos PLAZA_BENCH = new BlockPos(0, 0, 32);
    /** Where Pip waits in the plaza. */
    public static final BlockPos PIP_SPOT = new BlockPos(3, 0, 32);
    /** The tower gate (lower half). */
    public static final BlockPos TOWER_GATE = new BlockPos(0, 0, 34);

    private static final Map<CustomerProfile, BlockPos> TOWER_SPOTS = Map.of(
            CustomerProfile.BOLT, new BlockPos(-6, 0, 32),
            CustomerProfile.ADA, new BlockPos(6, 0, 33),
            CustomerProfile.PHANTOM, new BlockPos(-7, 0, 36),
            CustomerProfile.CELES, new BlockPos(7, 0, 36),
            CustomerProfile.LILY, new BlockPos(-5, 0, 39),
            CustomerProfile.GREGORY, new BlockPos(5, 0, 39),
            CustomerProfile.VIKTOR, new BlockPos(-3, 0, 32));
    private static final Map<CustomerProfile, BlockPos> FINALE_SPOTS = Map.of(
            CustomerProfile.GREGORY, new BlockPos(-3, 0, -4),
            CustomerProfile.PIP, new BlockPos(3, 0, -4),
            CustomerProfile.BOLT, new BlockPos(-8, 0, 6),
            CustomerProfile.ADA, new BlockPos(-6, 0, 4),
            CustomerProfile.LILY, new BlockPos(-4, 0, 7),
            CustomerProfile.CELES, new BlockPos(4, 0, 7),
            CustomerProfile.PHANTOM, new BlockPos(6, 0, 4),
            CustomerProfile.VIKTOR, new BlockPos(8, 0, 6));

    private StoryManager() {}

    // ------------------------------------------------------------------ state

    /** The story runs after the tutorial until the last quest is delivered. */
    public static boolean active(PlayerProgress p) {
        return p.tutorialDone && p.storyIndex < StoryQuests.count();
    }

    public static boolean finished(PlayerProgress p) {
        return p.storyIndex >= StoryQuests.count();
    }

    @Nullable
    public static StoryQuest current(PlayerProgress p) {
        return active(p) ? StoryQuests.get(p.storyIndex) : null;
    }

    @Nullable
    public static StoryQuest of(@Nullable ClockData clock) {
        return clock == null || !clock.isStory() ? null : StoryQuests.byId(clock.story);
    }

    public static boolean isTower(@Nullable ClockData clock) {
        StoryQuest q = of(clock);
        return q != null && q.tower;
    }

    /** The next story commission. */
    public static ClockData commission(PlayerProgress p, long id) {
        return StoryQuests.get(p.storyIndex).createClock(id);
    }

    public static void sync(ServerPlayer player) {
        if (player.connection == null) return;
        PlayerProgress p = WorldProgress.of(player);
        ModNetwork.send(player, new S2C.StorySync(p.storyIndex, p.keepsakes, p.repairedClocks, p.reputation));
    }

    // ------------------------------------------------------------------ events

    /** A story client is on the way: chapter card (when a chapter opens) and the quest's title card. */
    public static void announce(ServerPlayer player, StoryQuest q) {
        PlayerProgress progress = WorldProgress.of(player);
        Runnable questCard = () -> {
            ModNetwork.send(player, new S2C.Transition(S2C.TRANSITION_QUEST, q.id, q.client.nameKey()));
            if (q.tower && progress.markSeen(PlayerProgress.HINT_TOWER)) {
                ModNetwork.send(player, new S2C.Hint(List.of("hint.clockworkdiver.tower.0", "hint.clockworkdiver.tower.1")));
            }
        };
        if (q.opensChapter()) {
            ModNetwork.send(player, new S2C.Transition(S2C.TRANSITION_CHAPTER, String.valueOf(q.chapter), StoryQuests.chapterKey(q.chapter)));
            DelayedTasks.schedule(player.server, 110, questCard);
        } else {
            questCard.run();
        }
        if (progress.markSeen(PlayerProgress.HINT_STORY)) {
            DelayedTasks.schedule(player.server, q.opensChapter() ? 200 : 90, () -> ModNetwork.send(player,
                    new S2C.Hint(List.of("hint.clockworkdiver.story.0", "hint.clockworkdiver.story.1"))));
        }
    }

    /**
     * The progress side of delivering a story clock: next quest, keepsake flag, scene hold. Returns the quest if its
     * keepsake is new (so the caller hands it over), otherwise null.
     */
    @Nullable
    public static StoryQuest advance(PlayerProgress progress, ClockData clock, long now) {
        StoryQuest q = of(clock);
        if (q == null) return null;
        if (q.number - 1 == progress.storyIndex) progress.storyIndex++;
        boolean newKeepsake = (progress.keepsakes & (1 << (q.number - 1))) == 0;
        progress.keepsakes |= 1 << (q.number - 1);
        if (!q.extras.isEmpty()) {
            progress.sceneHold = q.id;
            progress.sceneHoldUntil = now + 20L * 40;
        }
        return newKeepsake ? q : null;
    }

    /** A story clock was handed back: keepsake, bonus, and on to the next quest. */
    public static void onDelivered(ServerPlayer player, PlayerProgress progress, ClockData clock, long now) {
        StoryQuest q = advance(progress, clock, now);
        if (q != null) {
            ItemStack keepsake = KeepsakeItem.create(q);
            if (!player.getInventory().add(keepsake)) player.drop(keepsake, false);
            applyBonus(player, progress, q.bonus);
        }
        sync(player);
    }

    private static void applyBonus(ServerPlayer player, PlayerProgress p, int bonus) {
        if (bonus == StoryQuest.BONUS_NONE) return;
        int kind = switch (bonus) {
            case StoryQuest.BONUS_STEAM -> Upgrades.STEAM;
            case StoryQuest.BONUS_LENS -> Upgrades.LENS;
            default -> Upgrades.WRENCH;
        };
        if (Upgrades.level(p, kind) < Upgrades.MAX_LEVEL) {
            switch (kind) {
                case Upgrades.STEAM -> p.steamLevel++;
                case Upgrades.LENS -> p.lensLevel++;
                default -> p.wrenchLevel++;
            }
            player.displayClientMessage(Component.translatable("message.clockworkdiver.story_upgrade",
                    Component.translatable("upgrade.clockworkdiver." + kind), Upgrades.level(p, kind)), false);
        } else {
            ItemStack coins = new ItemStack(ModItems.BRASS_COIN.get(), 15);
            if (!player.getInventory().add(coins)) player.drop(coins, false);
        }
    }

    // ------------------------------------------------------------------ gathered friends

    /** The quest whose friends should be standing around right now, if any. */
    @Nullable
    private static StoryQuest sceneQuest(PlayerProgress p, long now) {
        StoryQuest q = of(p.activeClock);
        if (q != null && !q.extras.isEmpty()) return q;
        if (!p.sceneHold.isEmpty() && now < p.sceneHoldUntil) return StoryQuests.byId(p.sceneHold);
        return null;
    }

    public static String sceneOf(StoryQuest q) {
        return q.tower ? SCENE_TOWER : SCENE_FINALE;
    }

    /** Spawns the friends a scene needs and sends home the ones it no longer needs. Called while the player is at the shop. */
    public static void tickScene(ServerPlayer player, PlayerProgress progress, ServerLevel ws) {
        BlockPos o = WorkshopManager.origin(progress.slot);
        StoryQuest q = sceneQuest(progress, ws.getGameTime());
        String scene = q == null ? "" : sceneOf(q);
        List<CustomerProfile> want = q == null ? List.of() : q.extras;
        List<CustomerEntity> present = ws.getEntitiesOfClass(CustomerEntity.class, new AABB(o).inflate(56.0),
                e -> e.isExtra() && player.getUUID().equals(e.owner()));
        for (CustomerEntity e : present) {
            if (!scene.equals(e.scene()) || !want.contains(e.profile())) e.discard();
        }
        if (q == null) return;
        Map<CustomerProfile, BlockPos> spots = q.tower ? TOWER_SPOTS : FINALE_SPOTS;
        for (CustomerProfile profile : want) {
            boolean there = false;
            for (CustomerEntity e : present) {
                if (e.isAlive() && scene.equals(e.scene()) && e.profile() == profile) there = true;
            }
            BlockPos rel = spots.get(profile);
            if (there || rel == null) continue;
            CustomerEntity friend = ModEntities.CUSTOMER.get().create(ws);
            if (friend == null) continue;
            BlockPos spot = o.offset(rel);
            friend.setupExtra(player.getUUID(), profile, spot, scene);
            BlockPos look = q.tower ? o.offset(PLAZA_BENCH) : o.offset(WorkshopManager.COUNTER_SPOT);
            float yaw = (float) Math.toDegrees(Math.atan2(-(look.getX() - spot.getX()), look.getZ() - spot.getZ()));
            friend.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0f);
            friend.setYHeadRot(yaw);
            friend.setYBodyRot(yaw);
            ws.addFreshEntity(friend);
        }
    }

    /** Debug and self test: positions of the scene spots (relative to the shop origin). */
    public static Map<CustomerProfile, BlockPos> spots(boolean tower) {
        return tower ? TOWER_SPOTS : FINALE_SPOTS;
    }
}
