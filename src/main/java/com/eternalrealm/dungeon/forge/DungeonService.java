package com.eternalrealm.dungeon.forge;

import com.eternalrealm.boss.Bosses;
import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.boss.forge.BossFight;
import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.ExperienceSource;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.core.ModInfo;
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
import com.eternalrealm.item.NamedTrinkets;
import com.eternalrealm.item.Trinkets;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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
    /** Time per tick for the sites of runs that wait for their build (the party is told it is being prepared). */
    private static final long ACTIVE_BUILD_NANOS = 12_000_000L;
    /** Time per tick for building prepared sites and clearing finished ones, when the server has time to spare. */
    private static final long BACKGROUND_NANOS = 3_000_000L;
    /** A server busier than this (average tick, ms) gets a third of the background time. */
    private static final double BUSY_TICK_MILLIS = 40.0;
    /** A player this close to a dungeon's gate (blocks) has its site prepared. */
    private static final int PREPARE_RADIUS = 48;
    private static final int MAX_PREPARED = 2;
    /** Prepared sites only take a slot while more than this many are free (runs always find one). */
    private static final int FREE_SLOTS_KEPT = 2;
    /** A prepared site nobody wanted for this long is cleared again. */
    private static final long PREPARED_TTL_TICKS = 20L * 60 * 5;

    private final MinecraftServer server;
    private final InstanceAllocator allocator = new InstanceAllocator();
    private final List<DungeonInstance> instances = new ArrayList<>();
    private final Map<UUID, DungeonInstance> byMember = new HashMap<>();
    /** Sites built ahead of a run, by dungeon ("d1".."d8", "final"). */
    private final Map<String, DungeonSite> prepared = new LinkedHashMap<>();
    /** Sites of finished runs (and of prepared sites nobody used) being cleared. */
    private final List<DungeonSite> clearing = new ArrayList<>();
    /** The last dungeon each player ran (prepared again when they ask a gatekeeper). */
    private final Map<UUID, String> lastRun = new HashMap<>();
    /** Slots holding blocks, saved with the dungeon level (null until first needed). */
    private SlotLedger ledger;
    /** Whether the slots left holding blocks by the last session were handed to the clearing. */
    private boolean restored;
    /** Parties getting ready to set out (出発の準備). */
    private final ReadyChecks readyChecks;

    public DungeonService(MinecraftServer server) {
        this.server = server;
        this.readyChecks = new ReadyChecks(this, server);
    }

    public ReadyChecks readyChecks() {
        return readyChecks;
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

    /** What a start needs once its checks have passed. */
    private record Plan(List<ServerPlayer> members, Optional<DungeonDefinition> dungeon, RaidDefinition raid,
                        int areaId, boolean sizeOk, String nameKey, int level) {
    }

    /** The reason a start is refused, or what it needs. */
    private record Checked(Optional<Component> refusal, Plan plan) {
        static Checked no(Component reason) {
            return new Checked(Optional.of(reason), null);
        }
    }

    /**
     * Why {@code leader} could not set out for {@code contentId} right now (not the party's leader, a member offline
     * or already in a dungeon, the wrong party size, sealed); empty when they could. Changes nothing.
     */
    public Optional<Component> refusal(ServerPlayer leader, String contentId) {
        return check(leader, contentId).refusal();
    }

    private Checked check(ServerPlayer leader, String contentId) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return Checked.no(red("message.eternalrealm.gate.unavailable"));
        }
        Optional<Party> party = context.game().parties().service().partyOf(leader.getUUID());
        if (party.isPresent() && !party.get().isLeader(leader.getUUID())) {
            return Checked.no(red("message.eternalrealm.dungeon.leader_only"));
        }
        List<ServerPlayer> members = new ArrayList<>();
        for (UUID id : party.map(Party::memberIds).orElse(List.of(leader.getUUID()))) {
            ServerPlayer member = player(id);
            if (member == null) {
                return Checked.no(red("message.eternalrealm.dungeon.member_offline"));
            }
            if (byMember.containsKey(id)) {
                return Checked.no(red("message.eternalrealm.dungeon.member_busy"));
            }
            members.add(member);
        }
        Optional<DungeonDefinition> dungeon = Dungeons.byId(contentId);
        RaidDefinition raid = Dungeons.FINAL_RAID.id().equals(contentId) ? Dungeons.FINAL_RAID : null;
        if (dungeon.isEmpty() && raid == null) {
            return Checked.no(red("message.eternalrealm.gate.unavailable"));
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
            return Checked.no(Component.translatable("message.eternalrealm.dungeon.party_size", minSize, maxSize)
                    .withStyle(ChatFormatting.RED));
        }
        if (!context.game().world().isUnlocked(areaId)
                || (raid != null && !context.game().world().progress().finalBossUnlocked())) {
            return Checked.no(red("message.eternalrealm.dungeon.locked"));
        }
        return new Checked(Optional.empty(), new Plan(List.copyOf(members), dungeon, raid, areaId, sizeOk, nameKey,
                level));
    }

    /**
     * The party (or the solo player) enters a dungeon ("d1".."d8") or the final raid ("final").
     *
     * @return why the run could not start (the caller shows it: the gatekeeper's screen does), or empty once the run is
     *         being prepared
     */
    public Optional<Component> start(ServerPlayer leader, String contentId, Difficulty difficulty) {
        Checked checked = check(leader, contentId);
        if (checked.refusal().isPresent()) {
            return checked.refusal();
        }
        Plan plan = checked.plan();
        List<ServerPlayer> members = plan.members();
        Optional<DungeonDefinition> dungeon = plan.dungeon();
        RaidDefinition raid = plan.raid();
        int areaId = plan.areaId();
        boolean sizeOk = plan.sizeOk();
        String nameKey = plan.nameKey();
        int level = plan.level();
        DungeonSite site = prepared.remove(contentId);
        Optional<InstanceSlot> slot = site != null ? Optional.of(site.slot) : allocator.allocate();
        if (slot.isEmpty() && !prepared.isEmpty()) {
            // a site prepared for another dungeon makes room (its slot is free again once it is cleared)
            retire(prepared.remove(prepared.keySet().iterator().next()));
            slot = allocator.allocate();
        }
        if (slot.isEmpty()) {
            return Optional.of(Component.translatable(InstanceAllocator.FULL_MESSAGE_KEY, allocator.maxInstances())
                    .withStyle(ChatFormatting.RED));
        }
        List<UUID> ids = members.stream().map(ServerPlayer::getUUID).toList();
        DungeonRun run;
        DungeonLayout layout = site != null ? site.layout : null;
        boolean relaxed = !sizeOk; // only possible with dungeons.ignorePartySize
        if (dungeon.isPresent()) {
            DungeonDefinition definition = dungeon.get();
            if (relaxed) {
                definition = new DungeonDefinition(definition.id(), definition.areaId(), definition.recommendedLevel(),
                        1, Math.max(definition.maxPartySize(), ids.size()), definition.theme(), definition.rooms(),
                        definition.bossId(), definition.timeLimitTicks());
            }
            run = new DungeonRun(definition, ids, FailurePolicy.defaults());
            if (layout == null) {
                layout = DungeonLayoutGenerator.generate(definition, RANDOM.nextLong());
            }
        } else {
            RaidDefinition definition = raid;
            if (relaxed) {
                definition = new RaidDefinition(raid.id(), raid.areaId(), raid.recommendedLevel(), 1,
                        Math.max(raid.maxPartySize(), ids.size()), raid.theme(), raid.bossId(), raid.timeLimitTicks());
            }
            run = DungeonRun.raid(definition, ids, FailurePolicy.defaults());
            if (layout == null) {
                layout = DungeonLayoutGenerator.raidArena(definition);
            }
        }
        if (site == null) {
            site = newSite(contentId, layout, slot.get());
        }
        Map<UUID, DungeonInstance.Origin> returns = new HashMap<>();
        for (ServerPlayer member : members) {
            returns.put(member.getUUID(), new DungeonInstance.Origin(member.level().dimension(), member.position(),
                    member.getYRot()));
        }
        DungeonInstance instance = new DungeonInstance(this, contentId, nameKey, areaId, level, difficulty, run, site,
                returns);
        instances.add(instance);
        for (UUID id : ids) {
            byMember.put(id, instance);
            lastRun.put(id, contentId);
        }
        run.startBuilding();
        instance.tellAll(Component.translatable("message.eternalrealm.dungeon.preparing", Component.translatable(nameKey),
                Component.translatable(difficulty.translationKey())).withStyle(ChatFormatting.YELLOW), false);
        return Optional.empty();
    }

    private static Component red(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.RED);
    }

    /** The dungeon (or "final") the player last set out for since the server started, if any. */
    public Optional<String> lastRun(UUID playerId) {
        return Optional.ofNullable(lastRun.get(playerId));
    }

    // ------------------------------------------------------------------ tick and events

    public void tick(long now) {
        if (!restored) {
            restoreLeftovers();
        }
        try {
            readyChecks.tick(now);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(DungeonService.class).error("Ready checks failed", e);
        }
        long buildDeadline = System.nanoTime() + ACTIVE_BUILD_NANOS;
        for (DungeonInstance instance : List.copyOf(instances)) {
            try {
                instance.tick(now, buildDeadline);
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(DungeonService.class).error("Dungeon instance {} failed; closing it",
                        instance.instanceId, e);
                instance.close();
            }
        }
        try {
            background(now);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(DungeonService.class).error("Preparing or clearing dungeon sites failed",
                    e);
        }
    }

    /** A run is over: its members are free again and its site is cleared in the background. */
    void retire(DungeonInstance instance) {
        instances.remove(instance);
        byMember.values().removeIf(value -> value == instance);
        retire(instance.site);
    }

    private void retire(DungeonSite site) {
        if (!site.touched()) {
            site.releaseChunks();
            allocator.release(site.slot); // nothing was built there
            return;
        }
        site.startClearing();
        clearing.add(site);
    }

    /**
     * Clears finished sites and builds prepared ones, a little each tick while the server has time; once a second
     * looks for players near a gate.
     */
    private void background(long now) {
        long budget = averageTickMillis() > BUSY_TICK_MILLIS ? BACKGROUND_NANOS / 3 : BACKGROUND_NANOS;
        long deadline = System.nanoTime() + budget;
        for (DungeonSite site : List.copyOf(clearing)) {
            if (site.clear(deadline, now)) {
                clearing.remove(site);
                ledger().free(site.slot.index());
                allocator.release(site.slot);
            }
            if (System.nanoTime() >= deadline) {
                return;
            }
        }
        if (now % 20 == 0) {
            prepareNearGates(now);
        }
        for (DungeonSite site : List.copyOf(prepared.values())) {
            if (!site.isBuilt()) {
                site.build(deadline, now);
                if (System.nanoTime() >= deadline) {
                    return;
                }
            }
        }
    }

    /** Players near a dungeon's gate (in アストリア) get its site prepared; sites nobody wants any more go. */
    private void prepareNearGates(long now) {
        ServerLevel astoria = server.getLevel(com.eternalrealm.world.forge.AstoriaDimension.LEVEL);
        if (astoria != null && prebuildEnabled()) {
            for (ServerPlayer player : astoria.players()) {
                if (player.isSpectator() || byMember.containsKey(player.getUUID())) {
                    continue;
                }
                for (com.eternalrealm.world.gen.LandmarkSite gate : com.eternalrealm.world.gen.LandmarkSites.all()) {
                    double dx = player.getX() - gate.x();
                    double dz = player.getZ() - gate.z();
                    if (dx * dx + dz * dz <= (double) PREPARE_RADIUS * PREPARE_RADIUS) {
                        prepare(gate.dungeonId(), now);
                    }
                }
            }
        }
        Iterator<DungeonSite> sites = prepared.values().iterator();
        while (sites.hasNext()) {
            DungeonSite site = sites.next();
            if (now - site.lastWanted > PREPARED_TTL_TICKS) {
                sites.remove();
                retire(site);
            }
        }
    }

    /** A gatekeeper's list was opened: the dungeon this player ran last is prepared again. */
    /** A party is getting ready for {@code contentId} (a ready check): its site starts being built meanwhile. */
    public void prepareAhead(String contentId) {
        if (prebuildEnabled()) {
            prepare(contentId, server.getTickCount());
        }
    }

    public void onGatekeeper(ServerPlayer player) {
        String last = lastRun.get(player.getUUID());
        if (last != null && !byMember.containsKey(player.getUUID()) && prebuildEnabled()) {
            prepare(last, server.getTickCount());
        }
    }

    /** Starts building a dungeon's site ahead of its run (if it is open, and slots are plentiful). */
    private void prepare(String contentId, long now) {
        DungeonSite existing = prepared.get(contentId);
        if (existing != null) {
            existing.lastWanted = now;
            return;
        }
        if (prepared.size() >= MAX_PREPARED || allocator.maxInstances() - allocator.inUse() <= FREE_SLOTS_KEPT
                || server instanceof net.minecraft.gametest.framework.GameTestServer) {
            return;
        }
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return;
        }
        DungeonLayout layout;
        Optional<DungeonDefinition> dungeon = Dungeons.byId(contentId);
        if (dungeon.isPresent()) {
            if (!context.game().world().isUnlocked(dungeon.get().areaId())) {
                return;
            }
            layout = DungeonLayoutGenerator.generate(dungeon.get(), RANDOM.nextLong());
        } else if (Dungeons.FINAL_RAID.id().equals(contentId)) {
            if (!context.game().world().isUnlocked(Dungeons.FINAL_RAID.areaId())
                    || !context.game().world().progress().finalBossUnlocked()) {
                return;
            }
            layout = DungeonLayoutGenerator.raidArena(Dungeons.FINAL_RAID);
        } else {
            return;
        }
        Optional<InstanceSlot> slot = allocator.allocate();
        if (slot.isEmpty()) {
            return;
        }
        DungeonSite site = newSite(contentId, layout, slot.get());
        site.lastWanted = now;
        prepared.put(contentId, site);
    }

    /** A site that writes its slot down as holding blocks when it sets the first one. */
    private DungeonSite newSite(String contentId, DungeonLayout layout, InstanceSlot slot) {
        DungeonSite site = new DungeonSite(contentId, layout, slot, DungeonLevels.level(server));
        site.onTouched = touched -> ledger().hold(touched.slot.index(), touched.contentId, touched.layout.seed());
        return site;
    }

    private SlotLedger ledger() {
        if (ledger == null) {
            ledger = SlotLedger.get(DungeonLevels.level(server));
        }
        return ledger;
    }

    /**
     * Slots the last session left holding blocks (a run or a prepared site when the server stopped) are taken and
     * cleared in the background before anything is built there again: their layouts come back from dungeon and seed.
     */
    private void restoreLeftovers() {
        restored = true;
        if (server instanceof net.minecraft.gametest.framework.GameTestServer) {
            return;
        }
        for (Map.Entry<Integer, String> entry : List.copyOf(ledger().slots.entrySet())) {
            int index = entry.getKey();
            String record = entry.getValue();
            int bar = record.lastIndexOf('|');
            DungeonLayout layout = null;
            String contentId = bar > 0 ? record.substring(0, bar) : "";
            try {
                long seed = Long.parseLong(record.substring(bar + 1));
                if (Dungeons.FINAL_RAID.id().equals(contentId)) {
                    layout = DungeonLayoutGenerator.raidArena(Dungeons.FINAL_RAID);
                } else {
                    layout = Dungeons.byId(contentId).map(dungeon -> DungeonLayoutGenerator.generate(dungeon, seed))
                            .orElse(null);
                }
            } catch (RuntimeException e) {
                // a broken record: forget it
            }
            if (layout == null || index < 0 || index >= InstanceAllocator.HARD_LIMIT) {
                ledger().free(index);
                continue;
            }
            InstanceSlot slot = new InstanceSlot(index);
            if (!allocator.reserve(slot)) {
                continue;
            }
            DungeonSite site = newSite(contentId, layout, slot);
            site.markTouched();
            site.startClearing();
            clearing.add(site);
        }
    }

    /**
     * The slots holding blocks of a site ({@code data/eternalrealm_dungeon_slots.dat} of the dungeon level): written
     * when a site sets its first block, crossed out when it has been cleared.
     */
    static final class SlotLedger extends SavedData {
        private static final String NAME = ModInfo.MOD_ID + "_dungeon_slots";
        /** slot index -> "dungeon|seed" */
        final Map<Integer, String> slots = new TreeMap<>();

        static SlotLedger get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(SlotLedger::load, SlotLedger::new, NAME);
        }

        static SlotLedger load(CompoundTag tag) {
            SlotLedger ledger = new SlotLedger();
            for (String key : tag.getAllKeys()) {
                try {
                    ledger.slots.put(Integer.parseInt(key), tag.getString(key));
                } catch (NumberFormatException e) {
                    // not ours
                }
            }
            return ledger;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            slots.forEach((slot, record) -> tag.putString(String.valueOf(slot), record));
            return tag;
        }

        void hold(int slot, String contentId, long seed) {
            slots.put(slot, contentId + "|" + seed);
            setDirty();
        }

        void free(int slot) {
            if (slots.remove(slot) != null) {
                setDirty();
            }
        }
    }

    private static boolean prebuildEnabled() {
        try {
            return com.eternalrealm.config.ERServerConfig.DUNGEON_PREBUILD.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private double averageTickMillis() {
        long[] times = server.tickTimes;
        long sum = 0L;
        for (long time : times) {
            sum += time;
        }
        return times.length == 0 ? 0.0 : sum / (double) times.length / 1_000_000.0;
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
     * The revival system sends a fallen member back to the instance's revival point (in front of the revival monument
     * of the entrance hall). False when the player is not inside a running or cleared instance.
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
        instance.toRevivalPoint(player);
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

    /**
     * Server stopping: everyone out (blocks are left, prepared and half-cleared sites too; a fresh run overwrites its
     * slot).
     */
    public void shutdown() {
        for (DungeonInstance instance : List.copyOf(instances)) {
            for (UUID member : instance.run.members()) {
                ServerPlayer player = player(member);
                if (player != null && instance.isInside(player)) {
                    instance.sendBack(player);
                }
            }
        }
        prepared.clear();
        clearing.clear();
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
        ItemRarity floor = com.eternalrealm.dungeon.DungeonRewards.rarityFloor(instance.difficulty);
        if (rarity.rank() < floor.rank()) {
            rarity = floor;
        }
        ItemTier tier = tierOfArea(instance.areaId);
        // the piece is now and then an accessory or a charm of the tier's grade (装身具)
        ItemStack piece = RANDOM.nextDouble() < Trinkets.REWARD_SHARE
                ? EquipmentStacks.create(EquipmentGenerator.roll(Trinkets.forDrop(tier, instance.recommendedLevel,
                        LootDrops.jobStats(player), RANDOM), rarity, RANDOM))
                : EquipmentStacks.create(EquipmentGenerator.roll(LootDrops.baseFor(player, tier), rarity, RANDOM));
        player.sendSystemMessage(Component.translatable("message.eternalrealm.dungeon.reward", piece.getDisplayName())
                .withStyle(ChatFormatting.GOLD));
        given.add(piece.copy());
        Inventories.give(player, piece);
        int stones = com.eternalrealm.dungeon.DungeonRewards.stones(instance.difficulty);
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
        // the dungeon's own trinkets (名品の装身具): an accessory and a charm per dungeon
        if (RANDOM.nextDouble() < NamedTrinkets.Difficulty.of(instance.difficulty.name()).chance()) {
            NamedTrinkets.dungeonDrop(instance.contentId, RANDOM).ifPresent(design -> {
                ItemStack trinket = ArmoryStacks.reward(player, design, floor, RANDOM);
                ArmoryStacks.announce(player, trinket, design);
                given.add(trinket.copy());
                Inventories.give(player, trinket);
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
        // about one kill in a thousand: one of the boss's relics (金枠)
        com.eternalrealm.item.forge.RelicDrops.roll(player, com.eternalrealm.item.Relics.SourceKind.BOSS,
                instance.run.bossId(), RANDOM).ifPresent(relic -> {
                    given.add(relic.copy());
                    Inventories.give(player, relic);
                });
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
