package com.eternalrealm.dungeon.forge;

import com.eternalrealm.boss.Bosses;
import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.boss.forge.BossFight;
import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.ExperienceSource;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.dungeon.DungeonDefinition;
import com.eternalrealm.dungeon.DungeonLayout;
import com.eternalrealm.dungeon.DungeonLayoutGenerator;
import com.eternalrealm.dungeon.DungeonRun;
import com.eternalrealm.dungeon.DungeonRunEvent;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.dungeon.FailurePolicy;
import com.eternalrealm.dungeon.InstanceAllocator;
import com.eternalrealm.dungeon.InstanceSlot;
import com.eternalrealm.dungeon.RaidDefinition;
import com.eternalrealm.item.EquipmentGenerator;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.ItemTier;
import com.eternalrealm.item.Materials;
import com.eternalrealm.item.forge.ERGearItems;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.forge.Inventories;
import com.eternalrealm.job.JobType;
import com.eternalrealm.monster.MonsterScaling;
import com.eternalrealm.monster.MonsterStats;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.monster.forge.LootDrops;
import com.eternalrealm.party.Party;
import com.eternalrealm.quest.GameEvent;
import com.eternalrealm.ranking.BossDifficulty;
import com.eternalrealm.ranking.TimeAttackMember;
import com.eternalrealm.ranking.TimeAttackRun;
import com.eternalrealm.server.ServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Dungeons and the final raid: the party leader starts one at the gatekeeper, the instance is built in the dungeon
 * dimension, the members fight through the rooms to the boss and get their rewards; deaths, logouts and returns are
 * reported to the run. Server thread only.
 */
public final class DungeonService {
    /** Boss rewards are this many kills of the boss's own minion at the boss's level. */
    private static final int BOSS_REWARD_FACTOR = 40;
    private static final SplittableRandom RANDOM = new SplittableRandom();

    private final MinecraftServer server;
    private final InstanceAllocator allocator = new InstanceAllocator();
    private final List<DungeonInstance> instances = new ArrayList<>();
    private final Map<UUID, DungeonInstance> byMember = new HashMap<>();

    public DungeonService(MinecraftServer server) {
        this.server = server;
    }

    ServerPlayer player(UUID id) {
        return server.getPlayerList().getPlayer(id);
    }

    public Optional<BossFight> fightOf(Entity body) {
        if (!body.getPersistentData().hasUUID("er_fight")) {
            return Optional.empty();
        }
        UUID fightId = body.getPersistentData().getUUID("er_fight");
        for (DungeonInstance instance : instances) {
            Optional<BossFight> fight = instance.fight().filter(f -> f.id().equals(fightId));
            if (fight.isPresent()) {
                return fight;
            }
        }
        return Optional.empty();
    }

    public boolean isInDungeon(UUID playerId) {
        return byMember.containsKey(playerId);
    }

    // ------------------------------------------------------------------ start

    /** The party (or the solo player) enters a dungeon ("d1".."d8") or the final raid ("final"). */
    public void start(ServerPlayer leader, String contentId, Difficulty difficulty) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return;
        }
        Optional<Party> party = context.game().parties().service().partyOf(leader.getUUID());
        if (party.isPresent() && !party.get().isLeader(leader.getUUID())) {
            fail(leader, "message.eternalrealm.dungeon.leader_only");
            return;
        }
        List<ServerPlayer> members = new ArrayList<>();
        for (UUID id : party.map(Party::memberIds).orElse(List.of(leader.getUUID()))) {
            ServerPlayer member = player(id);
            if (member == null) {
                fail(leader, "message.eternalrealm.dungeon.member_offline");
                return;
            }
            if (byMember.containsKey(id)) {
                fail(leader, "message.eternalrealm.dungeon.member_busy");
                return;
            }
            members.add(member);
        }
        Optional<DungeonDefinition> dungeon = Dungeons.byId(contentId);
        RaidDefinition raid = Dungeons.FINAL_RAID.id().equals(contentId) ? Dungeons.FINAL_RAID : null;
        if (dungeon.isEmpty() && raid == null) {
            return;
        }
        int areaId;
        int minSize;
        int maxSize;
        boolean sizeOk;
        String nameKey;
        int level;
        if (dungeon.isPresent()) {
            DungeonDefinition definition = dungeon.get();
            areaId = definition.areaId();
            minSize = definition.minPartySize();
            maxSize = definition.maxPartySize();
            sizeOk = definition.allowsPartySize(members.size());
            nameKey = definition.translationKey();
            level = definition.recommendedLevel();
        } else {
            areaId = raid.areaId();
            minSize = raid.minPartySize();
            maxSize = raid.maxPartySize();
            sizeOk = raid.allowsPartySize(members.size());
            nameKey = raid.translationKey();
            level = raid.recommendedLevel();
        }
        if (!sizeOk) {
            leader.displayClientMessage(Component.translatable("message.eternalrealm.dungeon.party_size", minSize, maxSize)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!context.game().world().isUnlocked(areaId)
                || (raid != null && !context.game().world().progress().finalBossUnlocked())) {
            fail(leader, "message.eternalrealm.dungeon.locked");
            return;
        }
        Optional<InstanceSlot> slot = allocator.allocate();
        if (slot.isEmpty()) {
            leader.displayClientMessage(Component.translatable(InstanceAllocator.FULL_MESSAGE_KEY,
                    allocator.maxInstances()).withStyle(ChatFormatting.RED), true);
            return;
        }
        List<UUID> ids = members.stream().map(ServerPlayer::getUUID).toList();
        DungeonRun run;
        DungeonLayout layout;
        if (dungeon.isPresent()) {
            run = new DungeonRun(dungeon.get(), ids, FailurePolicy.defaults());
            layout = DungeonLayoutGenerator.generate(dungeon.get(), RANDOM.nextLong());
        } else {
            run = DungeonRun.raid(raid, ids, FailurePolicy.defaults());
            layout = DungeonLayoutGenerator.raidArena(raid);
        }
        Map<UUID, DungeonInstance.Origin> returns = new HashMap<>();
        for (ServerPlayer member : members) {
            returns.put(member.getUUID(), new DungeonInstance.Origin(member.level().dimension(), member.position(),
                    member.getYRot()));
        }
        DungeonInstance instance = new DungeonInstance(this, contentId, nameKey, areaId, level, difficulty, run, layout,
                slot.get(), DungeonLevels.level(server), returns);
        instances.add(instance);
        for (UUID id : ids) {
            byMember.put(id, instance);
        }
        run.startBuilding();
        instance.tellAll(Component.translatable("message.eternalrealm.dungeon.preparing", Component.translatable(nameKey),
                Component.translatable(difficulty.translationKey())).withStyle(ChatFormatting.YELLOW), false);
    }

    private static void fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------ tick and events

    public void tick(long now) {
        for (DungeonInstance instance : List.copyOf(instances)) {
            try {
                instance.tick(now);
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(DungeonService.class).error("Dungeon instance {} failed; closing it",
                        instance.instanceId, e);
                instance.close();
            }
        }
    }

    void release(DungeonInstance instance) {
        instances.remove(instance);
        allocator.release(instance.slot);
        byMember.values().removeIf(value -> value == instance);
    }

    /** A living entity died: wave monsters advance the run, boss adds may break a shield. */
    public void onEntityDeath(LivingEntity entity) {
        String instanceId = entity.getPersistentData().getString(DungeonInstance.INSTANCE);
        if (!instanceId.isEmpty()) {
            for (DungeonInstance instance : instances) {
                if (instance.instanceId.equals(instanceId)) {
                    instance.onMonsterGone(entity.getUUID(), entity.getPersistentData().getInt(DungeonInstance.ROOM));
                }
            }
        }
        if (entity.getPersistentData().hasUUID("er_fight")) {
            fightOf(entity).ifPresent(fight -> fight.onAddGone(entity.getUUID()));
        }
    }

    public void onPlayerDeath(ServerPlayer player) {
        DungeonInstance instance = byMember.get(player.getUUID());
        if (instance != null) {
            instance.tellAll(Component.translatable("message.eternalrealm.dungeon.member_down", player.getDisplayName(),
                    instance.run.deaths() + 1).withStyle(ChatFormatting.GRAY), false);
            instance.dispatch(instance.run.onPlayerDied(player.getUUID()));
        }
    }

    /** A dead member comes back into the instance while the run is on. */
    public void onRespawn(ServerPlayer player) {
        DungeonInstance instance = byMember.get(player.getUUID());
        if (instance != null && instance.run.state().isRunning()) {
            instance.bringIn(player);
        }
    }

    public void onLogout(UUID playerId) {
        DungeonInstance instance = byMember.get(playerId);
        if (instance != null) {
            instance.dispatch(instance.run.onPlayerLeft(playerId));
        }
    }

    public void onLogin(ServerPlayer player) {
        DungeonInstance instance = byMember.get(player.getUUID());
        if (instance != null && instance.run.state().isRunning()) {
            instance.dispatch(instance.run.onPlayerReturned(player.getUUID()));
        } else if (instance == null && DungeonLevels.isDungeonLevel(player.level())) {
            // the run is gone (server restart): back to the capital
            ServerLevel overworld = server.overworld();
            var spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        }
    }

    /** The member gives up: back out, the run continues for the others. */
    public void leave(ServerPlayer player) {
        DungeonInstance instance = byMember.remove(player.getUUID());
        if (instance == null) {
            player.displayClientMessage(Component.translatable("message.eternalrealm.dungeon.not_inside")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (instance.isInside(player)) {
            instance.sendBack(player);
        }
        instance.dispatch(instance.run.onPlayerLeft(player.getUUID()));
    }

    /** Server stopping: everyone out (blocks are left; a fresh run overwrites its slot). */
    public void shutdown() {
        for (DungeonInstance instance : List.copyOf(instances)) {
            for (UUID member : instance.run.members()) {
                ServerPlayer player = player(member);
                if (player != null && instance.isInside(player)) {
                    instance.sendBack(player);
                }
            }
        }
    }

    // ------------------------------------------------------------------ results

    void onBossDefeated(DungeonInstance instance, BossFight fight) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return;
        }
        com.eternalrealm.world.BossDefeatResult result = context.game().world().onBossDefeated(fight.bossId());
        List<ServerPlayer> party = instance.onlineMembers();
        context.game().story().trigger(new com.eternalrealm.story.StoryTrigger.BossDefeated(fight.bossId(),
                result.firstDefeat()), null, party);
        for (com.eternalrealm.world.AreaDefinition area : result.newlyUnlockedAreas()) {
            context.game().story().trigger(new com.eternalrealm.story.StoryTrigger.AreaUnlocked(area.id()), null, party);
        }
        if ("final".equals(fight.bossId())) {
            context.game().rankings().recordFinalBossClear();
            if (result.firstDefeat()) {
                context.game().story().startEnding(party.stream().map(p -> p.getGameProfile().getName()).toList());
            }
        }
    }

    void onCleared(DungeonInstance instance, DungeonRunEvent.Cleared cleared) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return;
        }
        String bossId = instance.run.bossId();
        MonsterTemplate minion = Monsters.byId(Bosses.minionTemplateId(bossId)).orElse(null);
        int bossLevel = Bosses.byId(bossId).map(definition -> definition.level()).orElse(instance.recommendedLevel);
        MonsterStats stats = minion == null ? null : MonsterScaling.stats(minion, Math.max(minion.levels().min(),
                Math.min(minion.levels().max(), bossLevel)));
        List<TimeAttackMember> attackers = new ArrayList<>();
        for (UUID member : instance.run.members()) {
            ServerPlayer player = player(member);
            if (player == null || !instance.isInside(player)) {
                continue;
            }
            CharacterSnapshot snapshot = context.playerData().snapshot(member).orElse(null);
            attackers.add(new TimeAttackMember(member, player.getGameProfile().getName(),
                    snapshot == null ? TimeAttackMember.NO_JOB : JobType.fromStored(snapshot.jobId()).id(),
                    snapshot == null ? 1 : snapshot.level()));
            if (stats != null) {
                context.playerData().grantExperience(member, stats.exp() * BOSS_REWARD_FACTOR, ExperienceSource.BOSS);
                context.playerData().grantGold(member, stats.goldMax() * BOSS_REWARD_FACTOR / 2);
            }
            reward(context, player, instance);
            context.game().quests().event(player, new GameEvent.DungeonCleared(instance.contentId));
            context.game().quests().event(player, new GameEvent.BossDefeated(bossId));
            context.game().rankings().onDungeonClear(player, instance.run.members().size());
            context.game().rankings().onBossKill(player);
        }
        if (!attackers.isEmpty()) {
            BossDifficulty difficulty = BossDifficulty.valueOf(instance.difficulty.name());
            long nowMillis = System.currentTimeMillis();
            long durationMillis = cleared.durationTicks() * TimeAttackRun.MILLIS_PER_TICK;
            context.game().rankings().submitTimeAttack(new TimeAttackRun(bossId, difficulty, UUID.randomUUID(),
                    cleared.startTick(), cleared.endTick(), Math.max(0, nowMillis - durationMillis), nowMillis,
                    attackers, false, false, attackers.size() != instance.run.members().size()));
        }
    }

    /** One piece of the area's tier (rarity floor by difficulty) and enhancement stones for each member present. */
    private static void reward(ServerContext context, ServerPlayer player, DungeonInstance instance) {
        double luck = context.combat().service().get(player.getUUID()).map(Combatant::stats)
                .map(CombatStats::lootBonus).orElse(0.0);
        ItemRarity rarity = EquipmentGenerator.rollRarity(luck, RANDOM);
        ItemRarity floor = switch (instance.difficulty) {
            case NORMAL -> ItemRarity.UNCOMMON;
            case HARD -> ItemRarity.RARE;
            case EX -> ItemRarity.EPIC;
        };
        if (rarity.rank() < floor.rank()) {
            rarity = floor;
        }
        ItemTier tier = tierOfArea(instance.areaId);
        ItemStack piece = EquipmentStacks.create(EquipmentGenerator.roll(LootDrops.baseFor(player, tier), rarity, RANDOM));
        player.sendSystemMessage(Component.translatable("message.eternalrealm.dungeon.reward", piece.getDisplayName())
                .withStyle(ChatFormatting.GOLD));
        Inventories.give(player, piece);
        int stones = switch (instance.difficulty) {
            case NORMAL -> 2;
            case HARD -> 3;
            case EX -> 5;
        };
        net.minecraft.world.item.Item stone = ERGearItems.material(Materials.ENHANCEMENT_STONE);
        if (stone != null) {
            Inventories.give(player, new ItemStack(stone, stones));
        }
    }

    static ItemTier tierOfArea(int areaId) {
        ItemTier best = ItemTier.IRON;
        for (ItemTier tier : ItemTier.values()) {
            if (tier.area() <= areaId && tier.number() >= best.number()) {
                best = tier;
            }
        }
        return best;
    }
}
