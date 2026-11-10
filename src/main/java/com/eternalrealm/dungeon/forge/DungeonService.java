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
import com.eternalrealm.item.ArmoryDrops;
import com.eternalrealm.item.EquipmentGenerator;
import com.eternalrealm.item.Signatures;
import com.eternalrealm.item.forge.ArmoryStacks;
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
        // dungeon bosses and field calamities alike
        return BossFight.live(body.getPersistentData().getUUID("er_fight"));
    }

    /** The boss fight the player is part of right now. */
    public Optional<BossFight> fightFor(ServerPlayer player) {
        DungeonInstance instance = byMember.get(player.getUUID());
        return instance == null ? Optional.empty() : instance.fight().filter(fight -> !fight.isOver());
    }

    public boolean isInDungeon(UUID playerId) {
        return byMember.containsKey(playerId);
    }

    /** The map view of the instance a player belongs to (layout, current room, traps), if any. */
    public Optional<com.eternalrealm.world.map.MapView.Dungeon> mapView(UUID playerId) {
        DungeonInstance instance = byMember.get(playerId);
        return instance == null || instance.isClosed() ? Optional.empty() : Optional.of(instance.mapView());
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
        if (!sizeOk && !com.eternalrealm.config.ERServerConfig.IGNORE_PARTY_SIZE.get()) {
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
        boolean relaxed = !sizeOk; // only possible with dungeons.ignorePartySize
        if (dungeon.isPresent()) {
            DungeonDefinition definition = dungeon.get();
            if (relaxed) {
                definition = new DungeonDefinition(definition.id(), definition.areaId(), definition.recommendedLevel(),
                        1, Math.max(definition.maxPartySize(), ids.size()), definition.theme(), definition.rooms(),
                        definition.bossId(), definition.timeLimitTicks());
            }
            run = new DungeonRun(definition, ids, FailurePolicy.defaults());
            layout = DungeonLayoutGenerator.generate(definition, RANDOM.nextLong());
        } else {
            RaidDefinition definition = raid;
            if (relaxed) {
                definition = new RaidDefinition(raid.id(), raid.areaId(), raid.recommendedLevel(), 1,
                        Math.max(raid.maxPartySize(), ids.size()), raid.theme(), raid.bossId(), raid.timeLimitTicks());
            }
            run = DungeonRun.raid(definition, ids, FailurePolicy.defaults());
            layout = DungeonLayoutGenerator.raidArena(definition);
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

    /**
     * The revival system sends a fallen member back to the instance's revival point (its entrance). False when the
     * player is not inside a running or cleared instance.
     */
    public boolean reviveAtRevivalPoint(ServerPlayer player) {
        DungeonInstance instance = byMember.get(player.getUUID());
        if (instance == null || !instance.isInside(player)) {
            return false;
        }
        com.eternalrealm.dungeon.RunState state = instance.run.state();
        if (state == com.eternalrealm.dungeon.RunState.FAILED || state == com.eternalrealm.dungeon.RunState.CLOSED) {
            return false;
        }
        instance.bringIn(player);
        return true;
    }

    /** Whether the player's instance is fighting its boss right now. */
    public boolean inBossFight(UUID playerId) {
        DungeonInstance instance = byMember.get(playerId);
        return instance != null && instance.run.state() == com.eternalrealm.dungeon.RunState.BOSS;
    }

    /** Recommended level of the player's instance (0 outside of instances). */
    public int recommendedLevel(UUID playerId) {
        DungeonInstance instance = byMember.get(playerId);
        return instance == null ? 0 : instance.recommendedLevel;
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

    /** After a clear: the member leaves at once instead of waiting for the instance to close. */
    public boolean leaveCleared(ServerPlayer player) {
        DungeonInstance instance = byMember.get(player.getUUID());
        if (instance == null || instance.run.state() != com.eternalrealm.dungeon.RunState.CLEARED) {
            return false;
        }
        leave(player);
        return true;
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
        DungeonResults.clear();
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
        Map<UUID, DungeonResults.Haul> hauls = new HashMap<>();
        for (UUID member : instance.run.members()) {
            ServerPlayer player = player(member);
            if (player == null || !instance.isInside(player)) {
                continue;
            }
            long exp = 0L;
            long gold = 0L;
            CharacterSnapshot snapshot = context.playerData().snapshot(member).orElse(null);
            attackers.add(new TimeAttackMember(member, player.getGameProfile().getName(),
                    snapshot == null ? TimeAttackMember.NO_JOB : JobType.fromStored(snapshot.jobId()).id(),
                    snapshot == null ? 1 : snapshot.level()));
            if (stats != null) {
                exp = stats.exp() * BOSS_REWARD_FACTOR;
                gold = stats.goldMax() * BOSS_REWARD_FACTOR / 2;
                context.playerData().grantExperience(member, exp, ExperienceSource.BOSS);
                context.playerData().grantGold(member, gold);
            }
            hauls.put(member, new DungeonResults.Haul(exp, gold, reward(context, player, instance)));
            com.eternalrealm.sound.forge.MusicContexts.jingle(player, "victory");
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
        DungeonResults.show(context, instance, cleared, hauls);
    }

    /**
     * One piece of the area's tier (rarity floor by difficulty) and enhancement stones for each member present.
     *
     * @return copies of what the player got (for the result screen)
     */
    private static List<ItemStack> reward(ServerContext context, ServerPlayer player, DungeonInstance instance) {
        List<ItemStack> given = new ArrayList<>();
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
        given.add(piece.copy());
        Inventories.give(player, piece);
        int stones = switch (instance.difficulty) {
            case NORMAL -> 2;
            case HARD -> 3;
            case EX -> 5;
        };
        net.minecraft.world.item.Item stone = ERGearItems.material(Materials.ENHANCEMENT_STONE);
        if (stone != null) {
            given.add(new ItemStack(stone, stones));
            Inventories.give(player, new ItemStack(stone, stones));
        }
        // named designs (armory): one of the dungeon's own, and rarely the boss's weapon
        ArmoryDrops.Difficulty odds = ArmoryDrops.Difficulty.of(instance.difficulty.name());
        if (RANDOM.nextDouble() < odds.designChance()) {
            ArmoryDrops.dungeonDesign(instance.contentId, RANDOM).ifPresent(design -> {
                ItemStack named = ArmoryStacks.reward(player, design, floor, RANDOM);
                ArmoryStacks.announce(player, named, design);
                given.add(named.copy());
                Inventories.give(player, named);
            });
        }
        if (RANDOM.nextDouble() < odds.bossWeaponChance()) {
            Signatures.bossWeapon(instance.run.bossId()).ifPresent(design -> {
                ItemStack weapon = ArmoryStacks.reward(player, design, floor, RANDOM);
                ArmoryStacks.announce(player, weapon, design);
                given.add(weapon.copy());
                Inventories.give(player, weapon);
            });
        }
        // the boss material of the dungeon's field (boss weapons are forged from these)
        if (instance.areaId >= 1 && instance.areaId <= 10) {
            net.minecraft.world.item.Item soul = ERGearItems.material(
                    com.eternalrealm.monster.Materials.bossMaterial(instance.areaId));
            if (soul != null) {
                given.add(new ItemStack(soul, stones >= 5 ? 3 : stones - 1));
                Inventories.give(player, new ItemStack(soul, stones >= 5 ? 3 : stones - 1));
            }
        }
        return given;
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
