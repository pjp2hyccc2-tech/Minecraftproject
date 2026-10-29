package com.huntcraft.quest;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HCDimensions;
import com.huntcraft.core.HuntHooks;
import com.huntcraft.core.HunterSkills;
import com.huntcraft.core.Rank;
import com.huntcraft.core.SafeZones;
import com.huntcraft.core.SkillType;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.network.HCScreens;
import com.huntcraft.registry.HCEffects;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Server-authoritative quest flow: start, tick, targets, faints, completion, rewards, return, HUD sync. */
public final class QuestManager {
    public static final int MAX_FAINTS = 3;
    public static final int RETURN_DELAY_CLEAR = 20 * 20;
    public static final int RETURN_DELAY_FAIL = 20 * 10;
    public static final double CAMP_RADIUS = 18.0;
    public static final String SUPPLY_TAG = "HCSupply";
    private static final int SMALL_CAP = 22;

    private static final Set<UUID> TELEPORTING = new HashSet<>();
    private static final Set<String> ZONES = new HashSet<>();
    private static final Map<UUID, PendingScreen> PENDING_SCREENS = new HashMap<>();

    private record PendingScreen(long at, CompoundTag tag) {
    }

    private QuestManager() {
    }

    // ================================================================== lookup
    public static QuestSavedData data(MinecraftServer server) {
        return QuestSavedData.get(server);
    }

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    @Nullable
    public static QuestInstance instanceOf(ServerPlayer p) {
        return instanceOf(p.server, p.getUUID());
    }

    @Nullable
    public static QuestInstance instanceOf(MinecraftServer server, UUID id) {
        for (QuestInstance i : data(server).instances.values()) if (i.members.contains(id)) return i;
        return null;
    }

    @Nullable
    public static QuestInstance instanceAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel sl)) return null;
        for (QuestInstance i : data(sl.getServer()).instances.values()) {
            QuestDef q = i.quest();
            if (q != null && q.map.dimension == level.dimension() && i.contains(pos.getX() + 0.5, pos.getZ() + 0.5)) return i;
        }
        return null;
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server, QuestInstance inst) {
        QuestDef q = inst.quest();
        return q == null ? null : server.getLevel(q.map.dimension);
    }

    public static boolean isTeleporting(UUID id) {
        return TELEPORTING.contains(id);
    }

    public static List<ServerPlayer> online(MinecraftServer server, QuestInstance inst) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID u : inst.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) out.add(p);
        }
        return out;
    }

    // ================================================================== hooks
    public static void toBaseCamp(ServerPlayer p) {
        QuestInstance inst = instanceOf(p);
        if (inst == null) return;
        ServerLevel level = level(p.server, inst);
        if (level == null) return;
        teleport(p, level, QuestCamps.arrival(inst.camp, p.getRandom().nextInt(4)), yawToCenter(inst));
    }

    @Nullable
    public static Rank rankOf(Player p) {
        if (p == null) return null;
        if (p.level().isClientSide) return QuestHudState.active ? Rank.byOrdinal(QuestHudState.rank) : null;
        if (p instanceof ServerPlayer sp) {
            QuestInstance inst = instanceOf(sp);
            QuestDef q = inst == null ? null : inst.quest();
            return q == null ? null : q.rank;
        }
        return null;
    }

    public static Rank gatherRank(ServerPlayer p) {
        Rank r = rankOf(p);
        return r == null ? Rank.LOW : r;
    }

    // ================================================================== start
    @Nullable
    public static QuestInstance start(MinecraftServer server, QuestDef quest, List<ServerPlayer> party) {
        ServerLevel level = server.getLevel(quest.map.dimension);
        if (level == null) {
            for (ServerPlayer p : party) p.sendSystemMessage(Component.translatable("message.huntcraft.quest.no_map").withStyle(ChatFormatting.RED));
            return null;
        }
        QuestSavedData data = data(server);
        int slot = data.allocateSlot(quest.map);
        if (slot < 0) {
            for (ServerPlayer p : party) p.sendSystemMessage(Component.translatable("message.huntcraft.quest.map_busy").withStyle(ChatFormatting.RED));
            return null;
        }
        QuestInstance inst;
        try {
            BlockPos center = QuestRegions.center(slot);
            String campKey = quest.map.id + ":" + slot;
            BlockPos site = data.camps.get(campKey);
            if (site == null) site = QuestCamps.findCampSite(level, center);
            BlockPos camp = QuestCamps.buildCamp(level, site, quest.map);
            data.camps.put(campKey, camp);
            inst = new QuestInstance(data.nextId(), quest.id, slot, center, camp);
            long now = now(server);
            inst.startTick = now;
            inst.endTick = now + quest.timeLimitTicks();
            inst.progress = new int[quest.targets.size()];
            if (!party.isEmpty()) inst.leader = party.get(0).getUUID();
            for (ServerPlayer p : party) {
                inst.members.add(p.getUUID());
                inst.names.put(p.getUUID(), p.getGameProfile().getName());
                inst.returnPoints.put(p.getUUID(), returnPoint(p));
            }
            fillSupplies(inst, quest, Math.max(1, party.size()));
            data.instances.put(inst.id, inst);
            attachSupplyListener(data, inst);
            registerZone(inst);
            clearStaleEntities(level, inst);
            spawnTargets(level, inst, quest);
            data.setDirty();
        } catch (RuntimeException e) {
            HuntCraft.LOGGER.error("Failed to start quest {}", quest.id, e);
            data.freeSlot(quest.map, slot);
            for (ServerPlayer p : party) p.sendSystemMessage(Component.translatable("message.huntcraft.quest.start_failed").withStyle(ChatFormatting.RED));
            return null;
        }
        for (int k = 0; k < party.size(); k++) enter(party.get(k), level, inst, quest, k);
        HuntCraft.LOGGER.info("Quest {} started as {} (slot {}, camp {})", quest.id, inst.id, inst.slot, inst.camp);
        return inst;
    }

    private static void attachSupplyListener(QuestSavedData data, QuestInstance inst) {
        inst.supplies.addListener(c -> data.setDirty());
    }

    private static CompoundTag returnPoint(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", p.level().dimension().location().toString());
        t.putDouble("x", p.getX());
        t.putDouble("y", p.getY());
        t.putDouble("z", p.getZ());
        t.putFloat("yaw", p.getYRot());
        return t;
    }

    private static void enter(ServerPlayer p, ServerLevel level, QuestInstance inst, QuestDef q, int index) {
        teleport(p, level, QuestCamps.arrival(inst.camp, index), yawToCenter(inst));
        title(p, Component.translatable("title.huntcraft.quest.start").withStyle(ChatFormatting.GOLD), q.title(), 10, 60, 20);
        jingle(p, HCSounds.QUEST_START.get());
        p.sendSystemMessage(Component.translatable("message.huntcraft.quest.started", q.title(), q.map.displayName(), q.timeLimitMinutes)
                .withStyle(ChatFormatting.YELLOW));
        syncHud(p.server, inst);
    }

    private static float yawToCenter(QuestInstance inst) {
        double dx = inst.center.getX() - inst.camp.getX(), dz = inst.center.getZ() - inst.camp.getZ();
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
    }

    public static void teleport(ServerPlayer p, ServerLevel level, Vec3 pos, float yaw) {
        TELEPORTING.add(p.getUUID());
        try {
            level.getChunk(BlockPos.containing(pos));
            p.teleportTo(level, pos.x, pos.y, pos.z, yaw, 0.0F);
        } finally {
            TELEPORTING.remove(p.getUUID());
        }
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static void fillSupplies(QuestInstance inst, QuestDef q, int n) {
        supply(inst, HCItems.FIRST_AID_MED, 5 * n);
        supply(inst, HCItems.RATION, 3 * n);
        supply(inst, HCItems.PAINTBALL, 2 * n);
        supply(inst, QuestRegistry.QUEST_MAP, n);
        supply(inst, HCItems.FARCASTER, n);
        if (q.map.climate == QuestMap.Climate.COLD) supply(inst, HCItems.HOT_DRINK, n);
        if (q.map.climate == QuestMap.Climate.HOT) supply(inst, HCItems.COOL_DRINK, n);
        if (q.type == QuestDef.Type.CAPTURE) {
            supply(inst, HCItems.TRANQ_BOMB, 2 * n);
            supply(inst, HCItems.SHOCK_TRAP, 1);
        }
        if (q.urgent) supply(inst, HCItems.MAX_POTION, n);
        if (q.isHighRank()) supply(inst, HCItems.MEGA_POTION, n);
    }

    private static void supply(QuestInstance inst, Supplier<? extends Item> item, int count) {
        Item it = item.get();
        while (count > 0) {
            ItemStack st = new ItemStack(it);
            int n = Math.min(count, st.getMaxStackSize());
            st.setCount(n);
            st.getOrCreateTag().putString(SUPPLY_TAG, inst.id);
            count -= n;
            boolean placed = false;
            for (int i = 0; i < inst.supplies.getContainerSize(); i++) {
                if (inst.supplies.getItem(i).isEmpty()) {
                    inst.supplies.setItem(i, st);
                    placed = true;
                    break;
                }
            }
            if (!placed) return;
        }
    }

    private static void spawnTargets(ServerLevel level, QuestInstance inst, QuestDef q) {
        if (q.type != QuestDef.Type.HUNT && q.type != QuestDef.Type.CAPTURE) return;
        RandomSource r = level.random;
        List<Integer> areas = new ArrayList<>();
        for (int a = 1; a <= 9; a++) if (a != QuestRegions.CAMP_AREA) areas.add(a);
        for (QuestDef.Target t : q.targets) {
            for (int c = 0; c < t.count(); c++) {
                if (MonsterRegistry.get(t.id()) == null) {
                    HuntCraft.LOGGER.warn("Quest {}: target monster '{}' is not registered", q.id, t.id());
                    continue;
                }
                BlockPos pos = null;
                for (int attempt = 0; attempt < 6 && pos == null && !areas.isEmpty(); attempt++) {
                    int area = areas.get(r.nextInt(areas.size()));
                    pos = QuestCamps.randomPosInArea(level, inst, area, r, 40);
                    if (pos != null) areas.remove(Integer.valueOf(area));
                }
                if (pos == null) {
                    int area = 5;
                    BlockPos g = QuestCamps.dryGround(level, QuestRegions.areaCenterX(inst.center.getX(), area), QuestRegions.areaCenterZ(inst.center.getZ(), area));
                    pos = g != null ? g : new BlockPos(inst.center.getX(), QuestCamps.surfaceY(level, inst.center.getX(), inst.center.getZ()) + 1, inst.center.getZ());
                }
                HuntMonster m = MonsterRegistry.spawn(level, t.id(), pos, q.rank, inst.id);
                if (m != null) {
                    inst.targets.add(m.getUUID());
                    inst.roamAt.put(m.getUUID(), now(level.getServer()) + 2400 + r.nextInt(2400));
                }
            }
        }
    }

    private static void clearStaleEntities(ServerLevel level, QuestInstance inst) {
        AABB box = new AABB(inst.camp).inflate(48, 32, 48);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> e instanceof HuntMonster || e instanceof ItemEntity)) {
            if (e instanceof HuntMonster hm && hm.getQuestId() != null && hm.getQuestId().equals(inst.id)) continue;
            e.discard();
        }
    }

    // ================================================================== safe zones
    private static String zoneId(QuestInstance inst) {
        return "huntcraft_quest_camp_" + inst.id;
    }

    private static void registerZone(QuestInstance inst) {
        QuestDef q = inst.quest();
        if (q == null) return;
        SafeZones.register(zoneId(inst), q.map.dimension, Vec3.atBottomCenterOf(inst.camp), CAMP_RADIUS);
        ZONES.add(zoneId(inst));
    }

    private static void unregisterZone(QuestInstance inst) {
        SafeZones.unregister(zoneId(inst));
        ZONES.remove(zoneId(inst));
    }

    public static void onServerStarted(MinecraftServer server) {
        QuestSavedData data = data(server);
        for (QuestInstance inst : data.instances.values()) {
            registerZone(inst);
            attachSupplyListener(data, inst);
        }
        HuntCraft.LOGGER.info("Quest system: {} running quest instance(s) restored", data.instances.size());
    }

    public static void onServerStopping() {
        for (String z : ZONES) SafeZones.unregister(z);
        ZONES.clear();
        TELEPORTING.clear();
        PENDING_SCREENS.clear();
        QuestBoardService.clear();
    }

    // ================================================================== tick
    public static void tick(MinecraftServer server) {
        long now = now(server);
        if (!PENDING_SCREENS.isEmpty()) {
            Iterator<Map.Entry<UUID, PendingScreen>> it = PENDING_SCREENS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, PendingScreen> e = it.next();
                if (now < e.getValue().at) continue;
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p != null) HCScreens.open(p, HCScreens.QUEST_RESULT, e.getValue().tag);
                it.remove();
            }
        }
        QuestSavedData data = data(server);
        if (data.instances.isEmpty()) return;
        for (QuestInstance inst : new ArrayList<>(data.instances.values())) {
            try {
                tickInstance(server, data, inst, now);
            } catch (RuntimeException e) {
                HuntCraft.LOGGER.error("Quest instance {} tick failed", inst.id, e);
            }
        }
    }

    private static void tickInstance(MinecraftServer server, QuestSavedData data, QuestInstance inst, long now) {
        QuestDef q = inst.quest();
        ServerLevel level = level(server, inst);
        if (q == null || level == null) {
            cleanup(server, inst);
            return;
        }
        boolean second = (now + inst.slot) % 20 == 0;
        if (inst.state == QuestInstance.State.ACTIVE) {
            if (now >= inst.endTick) {
                fail(server, inst, "time");
                return;
            }
            if (inst.members.isEmpty()) {
                fail(server, inst, "abandon");
                return;
            }
            int minutesLeft = (int) ((inst.endTick - now) / 1200);
            if ((minutesLeft == 10 || minutesLeft == 5 || minutesLeft == 1) && minutesLeft < inst.warnedMinutes && (inst.endTick - now) % 1200 > 1100) {
                inst.warnedMinutes = minutesLeft;
                broadcast(server, inst, Component.translatable("message.huntcraft.quest.time_left", minutesLeft).withStyle(ChatFormatting.GOLD));
            }
            if (second) {
                List<ServerPlayer> players = online(server, inst);
                spawnSmalls(level, inst, q, now, players);
                syncHud(server, inst);
            }
            if ((now + inst.slot) % 100 == 0) manageMonsters(level, inst, now);
        } else {
            if (second) syncHud(server, inst);
            if (now >= inst.returnTick) finish(server, inst);
        }
    }

    // ================================================================== monsters
    private static void spawnSmalls(ServerLevel level, QuestInstance inst, QuestDef q, long now, List<ServerPlayer> players) {
        inst.smalls.removeIf(u -> {
            Entity e = level.getEntity(u);
            return e == null || !e.isAlive();
        });
        if (inst.smalls.size() >= SMALL_CAP) return;
        RandomSource r = level.random;
        for (ServerPlayer p : players) {
            if (p.level() != level || p.isSpectator()) continue;
            int area = QuestRegions.area(inst.center.getX(), inst.center.getZ(), p.getX(), p.getZ());
            if (area == 0) continue;
            if (p.distanceToSqr(Vec3.atCenterOf(inst.camp)) < 48 * 48) continue;
            long next = inst.areaSpawn.getOrDefault(area, 0L);
            if (now < next) continue;
            boolean slayQuest = q.type == QuestDef.Type.SLAY && !inst.allTargetsDone();
            inst.areaSpawn.put(area, now + 20L * (slayQuest ? 40 : 90));
            int alive = 0;
            for (UUID u : inst.smalls) {
                Entity e = level.getEntity(u);
                if (e != null && QuestRegions.area(inst.center.getX(), inst.center.getZ(), e.getX(), e.getZ()) == area) alive++;
            }
            if (alive >= (slayQuest ? 5 : 3)) continue;
            QuestMap.Spawn spawn = pickSpawn(q, inst, r);
            if (spawn == null) continue;
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = 22 + r.nextDouble() * 12;
            int x = Mth.floor(p.getX() + Math.cos(ang) * dist), z = Mth.floor(p.getZ() + Math.sin(ang) * dist);
            if (!QuestRegions.inside(inst.center, x, z, 8)) continue;
            if (SafeZones.isInSafeZone(level, new Vec3(x, p.getY(), z))) continue;
            int n = spawn.min() + (spawn.max() > spawn.min() ? r.nextInt(spawn.max() - spawn.min() + 1) : 0);
            for (int i = 0; i < n && inst.smalls.size() < SMALL_CAP; i++) {
                BlockPos pos = QuestCamps.dryGround(level, x + r.nextInt(7) - 3, z + r.nextInt(7) - 3);
                if (pos == null) continue;
                HuntMonster m = MonsterRegistry.spawn(level, spawn.id(), pos, q.rank, inst.id);
                if (m != null) inst.smalls.add(m.getUUID());
            }
        }
    }

    @Nullable
    private static QuestMap.Spawn pickSpawn(QuestDef q, QuestInstance inst, RandomSource r) {
        if (q.type == QuestDef.Type.SLAY && r.nextFloat() < 0.65F) {
            for (int i = 0; i < q.targets.size(); i++) {
                QuestDef.Target t = q.targets.get(i);
                if (inst.progress[i] < t.count() && MonsterRegistry.get(t.id()) != null) {
                    int min = 2, max = 4;
                    for (QuestMap.Spawn s : q.map.spawns) if (s.id().equals(t.id())) {
                        min = Math.max(1, s.min());
                        max = Math.max(min, s.max());
                    }
                    return new QuestMap.Spawn(t.id(), 1, min, max);
                }
            }
        }
        int total = 0;
        for (QuestMap.Spawn s : q.map.spawns) if (MonsterRegistry.get(s.id()) != null) total += s.weight();
        if (total <= 0) return null;
        int x = r.nextInt(total);
        for (QuestMap.Spawn s : q.map.spawns) {
            if (MonsterRegistry.get(s.id()) == null) continue;
            x -= s.weight();
            if (x < 0) return s;
        }
        return null;
    }

    private static void manageMonsters(ServerLevel level, QuestInstance inst, long now) {
        RandomSource r = level.random;
        for (UUID u : inst.targets) {
            Entity e = level.getEntity(u);
            if (!(e instanceof HuntMonster m) || m.isCarcass() || !m.isAlive()) continue;
            // keep the monster inside its region
            if (!QuestRegions.inside(inst.center, m.getX(), m.getZ(), 6)) {
                int area = 2 + r.nextInt(8);
                BlockPos home = QuestCamps.randomPosInArea(level, inst, area, r, 30);
                if (home != null) {
                    m.setHomePos(home);
                    if (!QuestRegions.inside(inst.center, m.getX(), m.getZ(), -12)) {
                        m.stopAction();
                        m.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
                    }
                }
                continue;
            }
            // roam between areas when not fighting (MH-style area changes)
            long roam = inst.roamAt.computeIfAbsent(u, k -> now + 2400 + r.nextInt(2400));
            if (now >= roam && m.getTarget() == null && !m.isLocked()) {
                int cur = QuestRegions.area(inst.center.getX(), inst.center.getZ(), m.getX(), m.getZ());
                int area = 2 + r.nextInt(8);
                if (area == cur) area = area == 9 ? 2 : area + 1;
                BlockPos home = QuestCamps.randomPosInArea(level, inst, area, r, 35);
                if (home != null) m.setHomePos(home);
                inst.roamAt.put(u, now + 2400 + r.nextInt(3600));
            }
        }
    }

    @Nullable
    private static QuestInstance instanceForMonster(MinecraftServer server, HuntMonster m) {
        QuestSavedData data = data(server);
        String qid = m.getQuestId();
        if (qid != null) {
            QuestInstance i = data.instances.get(qid);
            if (i != null) return i;
        }
        for (QuestInstance i : data.instances.values()) {
            QuestDef q = i.quest();
            if (q != null && q.map.dimension == m.level().dimension() && i.contains(m.getX(), m.getZ())) return i;
        }
        return null;
    }

    // ================================================================== events
    public static void onMonsterHunted(HuntMonster m, @Nullable Player killer, boolean captured) {
        if (!(m.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        QuestInstance inst = instanceForMonster(server, m);
        if (inst == null || inst.state != QuestInstance.State.ACTIVE) return;
        QuestDef q = inst.quest();
        if (q == null) return;
        String id = m.profile().id;
        if (captured && m.profile().large) inst.captured.add(id);
        boolean changed = false;
        for (int t = 0; t < q.targets.size(); t++) {
            QuestDef.Target tg = q.targets.get(t);
            if (tg.isItem() || !tg.id().equals(id) || inst.progress[t] >= tg.count()) continue;
            if (q.type == QuestDef.Type.CAPTURE && !captured) {
                fail(server, inst, "target_slain");
                return;
            }
            inst.progress[t]++;
            changed = true;
            Component msg = Component.translatable(captured ? "message.huntcraft.quest.target_captured" : "message.huntcraft.quest.target_slain",
                    tg.name(), inst.progress[t], tg.count()).withStyle(ChatFormatting.AQUA);
            broadcast(server, inst, msg);
            break;
        }
        if (changed) {
            data(server).setDirty();
            if (inst.allTargetsDone()) complete(server, inst);
            else syncHud(server, inst);
        }
    }

    public static void onPartBroken(HuntMonster m, String part) {
        if (!(m.level() instanceof ServerLevel level)) return;
        QuestInstance inst = instanceForMonster(level.getServer(), m);
        if (inst == null || inst.state == QuestInstance.State.FAILED) return;
        if (inst.brokenParts.add(m.profile().id + "|" + part + "|" + m.getUUID())) data(level.getServer()).setDirty();
    }

    public static void onGather(ServerPlayer p, ItemStack stack) {
        QuestInstance inst = instanceOf(p);
        if (inst == null || inst.state != QuestInstance.State.ACTIVE) return;
        QuestDef q = inst.quest();
        if (q == null || q.type != QuestDef.Type.GATHER) return;
        for (QuestDef.Target t : q.targets) {
            if (t.item() != null && t.item().get() == stack.getItem()) {
                int have = p.getInventory().countItem(stack.getItem());
                p.sendSystemMessage(Component.translatable("message.huntcraft.quest.gather_target", stack.getHoverName(), have, t.count())
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }

    /** Called from LivingDeathEvent. Returns true if the death was turned into a faint (event must be cancelled). */
    public static boolean onPlayerDeath(ServerPlayer p) {
        if (!HCDimensions.isQuestMap(p.level())) return false;
        QuestInstance inst = instanceOf(p);
        if (inst == null) return false;
        ServerLevel level = level(p.server, inst);
        if (level == null || p.level() != level) return false;
        p.setHealth(p.getMaxHealth());
        p.removeAllEffects();
        p.clearFire();
        p.fallDistance = 0;
        HunterData d = HunterData.get(p);
        d.stamina = d.maxStamina;
        d.exhausted = false;
        d.markDirty();
        MinecraftServer server = p.server;
        if (inst.state == QuestInstance.State.ACTIVE) {
            inst.faints++;
            data(server).setDirty();
            int left = Math.max(0, MAX_FAINTS - inst.faints);
            broadcast(server, inst, Component.translatable("message.huntcraft.quest.fainted", p.getDisplayName(), left).withStyle(ChatFormatting.RED));
            title(p, Component.translatable("title.huntcraft.quest.fainted").withStyle(ChatFormatting.RED), Component.empty(), 5, 50, 15);
            if (inst.faints >= MAX_FAINTS) fail(server, inst, "faints");
        }
        teleport(p, level, QuestCamps.arrival(inst.camp, p.getRandom().nextInt(4)), yawToCenter(inst));
        syncHud(server, inst);
        return true;
    }

    // ================================================================== supply / delivery boxes
    public static void openSupplies(ServerPlayer p, BlockPos pos) {
        QuestInstance inst = instanceAt(p.level(), pos);
        if (inst == null || !inst.isMember(p.getUUID())) {
            p.displayClientMessage(Component.translatable("message.huntcraft.quest.no_supplies"), true);
            return;
        }
        p.level().playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8F, 1.1F);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, inst.supplies),
                Component.translatable("container.huntcraft.supply_box")));
    }

    public static void deliver(ServerPlayer p, BlockPos pos) {
        QuestInstance inst = instanceAt(p.level(), pos);
        if (inst == null || !inst.isMember(p.getUUID())) {
            p.displayClientMessage(Component.translatable("message.huntcraft.quest.no_delivery"), true);
            return;
        }
        QuestDef q = inst.quest();
        if (q == null || inst.state != QuestInstance.State.ACTIVE) return;
        if (q.type != QuestDef.Type.GATHER) {
            p.displayClientMessage(Component.translatable("message.huntcraft.quest.nothing_to_deliver"), true);
            return;
        }
        boolean any = false;
        for (int t = 0; t < q.targets.size(); t++) {
            QuestDef.Target tg = q.targets.get(t);
            if (tg.item() == null) continue;
            int need = tg.count() - inst.progress[t];
            if (need <= 0) continue;
            int removed = removeItems(p, tg.item().get(), need);
            if (removed > 0) {
                inst.progress[t] += removed;
                any = true;
                broadcast(p.server, inst, Component.translatable("message.huntcraft.quest.delivered", p.getDisplayName(), tg.name(), inst.progress[t], tg.count())
                        .withStyle(ChatFormatting.AQUA));
            } else {
                p.displayClientMessage(Component.translatable("message.huntcraft.quest.need_delivery", tg.name(), need), true);
            }
        }
        if (any) {
            p.level().playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 0.8F);
            data(p.server).setDirty();
            if (inst.allTargetsDone()) complete(p.server, inst);
            else syncHud(p.server, inst);
        }
    }

    private static int removeItems(ServerPlayer p, Item item, int max) {
        Inventory inv = p.getInventory();
        int removed = 0;
        for (int i = 0; i < inv.getContainerSize() && removed < max; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || st.getItem() != item) continue;
            int n = Math.min(max - removed, st.getCount());
            st.shrink(n);
            removed += n;
        }
        if (removed > 0) inv.setChanged();
        return removed;
    }

    // ================================================================== completion / failure
    public static void complete(MinecraftServer server, QuestInstance inst) {
        if (inst.state != QuestInstance.State.ACTIVE) return;
        QuestDef q = inst.quest();
        long now = now(server);
        inst.state = QuestInstance.State.COMPLETE;
        inst.clearTicks = now - inst.startTick;
        inst.returnTick = now + RETURN_DELAY_CLEAR;
        for (int i = 0; i < inst.progress.length && q != null; i++) inst.progress[i] = Math.max(inst.progress[i], q.targets.get(i).count());
        for (ServerPlayer p : online(server, inst)) {
            title(p, Component.translatable("title.huntcraft.quest.clear").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    q != null ? q.title() : Component.empty(), 10, 80, 20);
            jingle(p, HCSounds.QUEST_CLEAR.get());
            p.sendSystemMessage(Component.translatable("message.huntcraft.quest.return_in", RETURN_DELAY_CLEAR / 20).withStyle(ChatFormatting.YELLOW));
        }
        data(server).setDirty();
        syncHud(server, inst);
    }

    public static void fail(MinecraftServer server, QuestInstance inst, String reason) {
        if (inst.state != QuestInstance.State.ACTIVE) return;
        long now = now(server);
        inst.state = QuestInstance.State.FAILED;
        inst.failReason = reason;
        inst.returnTick = now + ("abandon".equals(reason) ? 60 : RETURN_DELAY_FAIL);
        for (ServerPlayer p : online(server, inst)) {
            title(p, Component.translatable("title.huntcraft.quest.failed").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    Component.translatable("message.huntcraft.quest.fail_reason." + reason), 10, 70, 20);
            jingle(p, HCSounds.QUEST_FAIL.get());
        }
        data(server).setDirty();
        syncHud(server, inst);
    }

    /** Leader abandons: the quest fails for everyone. Other members leave alone. */
    public static void abandon(ServerPlayer p) {
        QuestInstance inst = instanceOf(p);
        if (inst == null) return;
        if (inst.state != QuestInstance.State.ACTIVE) return;
        if (p.getUUID().equals(inst.leader) || inst.members.size() <= 1) {
            fail(p.server, inst, "abandon");
        } else {
            leaveInstance(p, inst);
        }
    }

    private static void leaveInstance(ServerPlayer p, QuestInstance inst) {
        QuestDef q = inst.quest();
        inst.members.remove(p.getUUID());
        if (p.getUUID().equals(inst.leader)) inst.leader = inst.members.isEmpty() ? null : inst.members.get(0);
        CompoundTag rp = inst.returnPoints.remove(p.getUUID());
        removeSupplies(p);
        clearHud(p);
        sendHome(p, rp);
        if (q != null) broadcast(p.server, inst, Component.translatable("message.huntcraft.quest.member_left", p.getDisplayName()).withStyle(ChatFormatting.GRAY));
        if (q != null) {
            CompoundTag res = QuestRewards.baseResult(inst, q, false);
            res.putString("reason", "abandon");
            scheduleScreen(p, res, 30);
        }
        data(p.server).setDirty();
    }

    /** End of the return countdown: rewards, return to the village, result screen, cleanup. */
    public static void finish(MinecraftServer server, QuestInstance inst) {
        QuestDef q = inst.quest();
        if (q == null) {
            cleanup(server, inst);
            return;
        }
        boolean success = inst.state == QuestInstance.State.COMPLETE;
        RandomSource r = server.overworld().random;
        QuestSavedData data = data(server);
        for (UUID id : new ArrayList<>(inst.members)) {
            CompoundTag result = success ? QuestRewards.rollSuccess(inst, q, r) : QuestRewards.baseResult(inst, q, false);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                applyResult(p, result);
                removeSupplies(p);
                clearHud(p);
                sendHome(p, inst.returnPoints.get(id));
                scheduleScreen(p, result, 30);
            } else {
                data.pendingResults.put(id, result);
            }
        }
        cleanup(server, inst);
    }

    /** Applies zenny / HRP / clears / pending items of a result to the player (tag gets "hrUp"/"newHr"). */
    public static void applyResult(ServerPlayer p, CompoundTag result) {
        if (!result.getBoolean("success")) return;
        if (result.getBoolean("applied")) return;
        result.putBoolean("applied", true);
        HunterData d = HunterData.get(p);
        String qid = result.getString("quest");
        QuestDef q = Quests.get(qid);
        Set<String> before = new HashSet<>(d.completedQuests);
        int zenny = result.getInt("zenny");
        if (zenny > 0) d.addZenny(zenny);
        boolean firstClear = !d.completedQuests.contains(qid);
        d.completedQuests.add(qid);
        d.questClears.merge(qid, 1, Integer::sum);
        int oldHr = d.hunterRank;
        if (Quests.LR_URGENT.equals(qid) && d.hunterRank < Quests.HIGH_RANK_HR) {
            d.hunterRank = Quests.HIGH_RANK_HR;
            d.hrPoints = 0;
        }
        d.addHrPoints(result.getInt("hrp"), Quests.hrCap(d.completedQuests));
        QuestRewards.addPending(d, result.getList("items", 10));
        d.markDirty();
        result.putInt("oldHr", oldHr);
        result.putInt("newHr", d.hunterRank);
        p.sendSystemMessage(Component.translatable("message.huntcraft.quest.reward_zenny", zenny, result.getInt("hrp")).withStyle(ChatFormatting.YELLOW));
        if (d.hunterRank > oldHr) {
            p.sendSystemMessage(Component.translatable("message.huntcraft.quest.hr_up", d.hunterRank).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            jingle(p, HCSounds.LEVEL_UP.get());
        }
        if (firstClear && q != null) announceUnlocks(p, before, d);
    }

    private static void announceUnlocks(ServerPlayer p, Set<String> before, HunterData d) {
        if (!Quests.highRankUnlocked(before) && Quests.highRankUnlocked(d.completedQuests)) {
            p.sendSystemMessage(Component.translatable("message.huntcraft.quest.high_rank_unlocked").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        }
        Set<Integer> stars = new HashSet<>();
        boolean urgent = false;
        for (QuestDef q : Quests.all()) {
            boolean was = Quests.starUnlocked(q.stars, q.urgent, before);
            boolean is = Quests.starUnlocked(q.stars, q.urgent, d.completedQuests);
            if (!was && is) {
                if (q.urgent) urgent = true;
                else stars.add(q.stars);
            }
        }
        for (int s : stars) p.sendSystemMessage(Component.translatable("message.huntcraft.quest.star_unlocked", s).withStyle(ChatFormatting.GREEN));
        if (urgent) p.sendSystemMessage(Component.translatable("message.huntcraft.quest.urgent_appeared").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private static void scheduleScreen(ServerPlayer p, CompoundTag result, int delay) {
        PENDING_SCREENS.put(p.getUUID(), new PendingScreen(now(p.server) + delay, result.copy()));
    }

    private static void cleanup(MinecraftServer server, QuestInstance inst) {
        QuestSavedData data = data(server);
        data.instances.remove(inst.id);
        QuestDef q = inst.quest();
        if (q != null) data.freeSlot(q.map, inst.slot);
        unregisterZone(inst);
        ServerLevel level = level(server, inst);
        if (level != null) {
            for (UUID u : inst.targets) {
                Entity e = level.getEntity(u);
                if (e != null) e.discard();
            }
            for (UUID u : inst.smalls) {
                Entity e = level.getEntity(u);
                if (e != null) e.discard();
            }
            AABB box = new AABB(inst.center.getX() - QuestRegions.HALF, level.getMinBuildHeight(), inst.center.getZ() - QuestRegions.HALF,
                    inst.center.getX() + QuestRegions.HALF, level.getMaxBuildHeight(), inst.center.getZ() + QuestRegions.HALF);
            for (HuntMonster m : level.getEntitiesOfClass(HuntMonster.class, box, m -> inst.id.equals(m.getQuestId()))) m.discard();
            for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, box)) it.discard();
        }
        for (ServerPlayer p : online(server, inst)) clearHud(p);
        data.setDirty();
        HuntCraft.LOGGER.info("Quest instance {} ({}) closed", inst.id, inst.questId);
    }

    // ================================================================== return / supplies
    public static void removeSupplies(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.hasTag() && st.getTag().contains(SUPPLY_TAG)) inv.setItem(i, ItemStack.EMPTY);
        }
        ItemStack carried = p.containerMenu.getCarried();
        if (!carried.isEmpty() && carried.hasTag() && carried.getTag().contains(SUPPLY_TAG)) p.containerMenu.setCarried(ItemStack.EMPTY);
    }

    /** Sends a player back from a quest map: village hook first, then the saved return point / world spawn. */
    public static void sendHome(ServerPlayer p, @Nullable CompoundTag returnPoint) {
        if (!HCDimensions.isQuestMap(p.level())) return;
        TELEPORTING.add(p.getUUID());
        try {
            HuntHooks.toVillage.accept(p);
        } catch (RuntimeException e) {
            HuntCraft.LOGGER.error("toVillage hook failed", e);
        } finally {
            TELEPORTING.remove(p.getUUID());
        }
        if (!HCDimensions.isQuestMap(p.level())) return;
        MinecraftServer server = p.server;
        if (returnPoint != null) {
            ResourceLocation rl = ResourceLocation.tryParse(returnPoint.getString("dim"));
            ServerLevel target = rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
            if (target != null && !HCDimensions.isQuestMap(target)) {
                teleport(p, target, new Vec3(returnPoint.getDouble("x"), returnPoint.getDouble("y"), returnPoint.getDouble("z")), returnPoint.getFloat("yaw"));
                return;
            }
        }
        ServerLevel ow = server.overworld();
        BlockPos spawn = ow.getSharedSpawnPos();
        int y = QuestCamps.surfaceY(ow, spawn.getX(), spawn.getZ());
        teleport(p, ow, new Vec3(spawn.getX() + 0.5, Math.max(y, spawn.getY()), spawn.getZ() + 0.5), 0);
    }

    // ================================================================== player tick (border, climate, orphans)
    public static void playerTick(ServerPlayer p) {
        if (!HCDimensions.isQuestMap(p.level()) || p.isSpectator()) return;
        QuestInstance inst = instanceOf(p);
        QuestDef q = inst == null ? null : inst.quest();
        if (inst == null || q == null || q.map.dimension != p.level().dimension()) {
            if (p.isCreative()) return;
            // not on a quest in this map: send home after a short grace period
            int t = p.getPersistentData().getInt("hcQuestOrphan") + 10;
            p.getPersistentData().putInt("hcQuestOrphan", t);
            if (t >= 100) {
                p.getPersistentData().remove("hcQuestOrphan");
                removeSupplies(p);
                sendHome(p, null);
            }
            return;
        }
        p.getPersistentData().remove("hcQuestOrphan");
        // ---- region border
        double lim = QuestRegions.HALF - 1.0;
        double dx = p.getX() - inst.center.getX(), dz = p.getZ() - inst.center.getZ();
        if (Math.abs(dx) > lim || Math.abs(dz) > lim) {
            double nx = inst.center.getX() + Mth.clamp(dx, -lim + 2, lim - 2);
            double nz = inst.center.getZ() + Mth.clamp(dz, -lim + 2, lim - 2);
            ServerLevel level = p.serverLevel();
            int y = QuestCamps.surfaceY(level, Mth.floor(nx), Mth.floor(nz));
            p.teleportTo(nx, Math.max(p.getY(), y), nz);
            p.setDeltaMovement(Vec3.ZERO);
            p.displayClientMessage(Component.translatable("message.huntcraft.quest.border"), true);
        } else if (Math.abs(dx) > lim - 4 || Math.abs(dz) > lim - 4) {
            double px = Math.abs(dx) > lim - 4 ? -Math.signum(dx) * 0.35 : 0;
            double pz = Math.abs(dz) > lim - 4 ? -Math.signum(dz) * 0.35 : 0;
            p.setDeltaMovement(px, p.getDeltaMovement().y, pz);
            p.hurtMarked = true;
            if (p.tickCount % 40 == 0) p.displayClientMessage(Component.translatable("message.huntcraft.quest.border"), true);
        }
        // ---- climate
        boolean inCamp = p.distanceToSqr(Vec3.atBottomCenterOf(inst.camp)) < CAMP_RADIUS * CAMP_RADIUS;
        if (inCamp || HunterSkills.level(p, SkillType.HEAT_COLD_RES) > 0) return;
        if (q.map.climate == QuestMap.Climate.COLD && !p.hasEffect(HCEffects.HOT_DRINK.get()) && p.tickCount % 40 == 0) {
            HunterData d = HunterData.get(p);
            if (d.maxStamina > HunterData.STAMINA_FLOOR) {
                d.maxStamina = Math.max(HunterData.STAMINA_FLOOR, d.maxStamina - 1.5F);
                d.stamina = Math.min(d.stamina, d.maxStamina);
                d.markDirty();
            }
            if (p.tickCount % 600 == 0) p.displayClientMessage(Component.translatable("message.huntcraft.quest.cold"), true);
        } else if (q.map.climate == QuestMap.Climate.HOT && !p.hasEffect(HCEffects.COOL_DRINK.get()) && p.tickCount % 60 == 0) {
            if (p.getHealth() > 2.0F && !p.isCreative()) p.setHealth(p.getHealth() - 1.0F);
            if (p.tickCount % 600 == 0) p.displayClientMessage(Component.translatable("message.huntcraft.quest.hot"), true);
        }
    }

    public static void onLogin(ServerPlayer p) {
        QuestSavedData data = data(p.server);
        CompoundTag pending = data.pendingResults.remove(p.getUUID());
        if (pending != null) {
            applyResult(p, pending);
            scheduleScreen(p, pending, 60);
            data.setDirty();
        }
        QuestInstance inst = instanceOf(p);
        if (inst != null) syncHud(p.server, inst);
        else clearHud(p);
    }

    public static void onLogout(ServerPlayer p) {
        QuestBoardService.onLogout(p);
    }

    /** A member left the quest map by other means (command, portal...): they leave the quest. */
    public static void onChangedDimension(ServerPlayer p, ResourceKey<Level> from) {
        if (isTeleporting(p.getUUID())) return;
        QuestInstance inst = instanceOf(p);
        if (inst == null) return;
        QuestDef q = inst.quest();
        if (q != null && q.map.dimension == from && !HCDimensions.isQuestMap(p.level())) {
            inst.members.remove(p.getUUID());
            inst.returnPoints.remove(p.getUUID());
            removeSupplies(p);
            clearHud(p);
            broadcast(p.server, inst, Component.translatable("message.huntcraft.quest.member_left", p.getDisplayName()).withStyle(ChatFormatting.GRAY));
            data(p.server).setDirty();
        }
    }

    // ================================================================== HUD sync
    public static void syncHud(MinecraftServer server, QuestInstance inst) {
        QuestDef q = inst.quest();
        if (q == null) return;
        long now = now(server);
        CompoundTag base = new CompoundTag();
        base.putBoolean("active", true);
        base.putString("q", inst.questId);
        base.putInt("st", inst.state.ordinal());
        long until = inst.state == QuestInstance.State.ACTIVE ? inst.endTick : inst.returnTick;
        base.putInt("rem", (int) Math.max(0, until - now));
        base.putInt("f", inst.faints);
        base.putInt("mf", MAX_FAINTS);
        base.putInt("cx", inst.center.getX());
        base.putInt("cz", inst.center.getZ());
        base.putInt("half", QuestRegions.HALF);
        base.putInt("kx", inst.camp.getX());
        base.putInt("ky", inst.camp.getY());
        base.putInt("kz", inst.camp.getZ());
        base.putIntArray("p", inst.progress);
        base.putInt("rank", q.rank.ordinal());
        base.putString("reason", inst.failReason);
        base.putBoolean("leader", false);
        ServerLevel level = level(server, inst);
        ListTag mons = new ListTag();
        if (level != null) {
            for (UUID u : inst.targets) {
                Entity e = level.getEntity(u);
                if (e instanceof HuntMonster m && m.isAlive() && (m.isPainted() || m.isCarcass())) {
                    CompoundTag t = new CompoundTag();
                    t.putDouble("x", m.getX());
                    t.putDouble("z", m.getZ());
                    t.putString("id", m.profile().id);
                    t.putBoolean("e", m.isEnraged());
                    t.putBoolean("d", m.isCarcass());
                    t.putBoolean("c", m.isCapturable());
                    mons.add(t);
                }
            }
        }
        base.put("m", mons);
        List<ServerPlayer> players = online(server, inst);
        for (ServerPlayer p : players) {
            CompoundTag tag = base.copy();
            tag.putBoolean("leader", p.getUUID().equals(inst.leader));
            ListTag party = new ListTag();
            for (ServerPlayer o : players) {
                if (o == p || o.level() != p.level()) continue;
                CompoundTag t = new CompoundTag();
                t.putDouble("x", o.getX());
                t.putDouble("z", o.getZ());
                t.putString("n", o.getGameProfile().getName());
                party.add(t);
            }
            tag.put("party", party);
            HCNetwork.sendToPlayer(p, "quest/hud", tag);
        }
    }

    public static void clearHud(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("active", false);
        HCNetwork.sendToPlayer(p, "quest/hud", t);
    }

    // ================================================================== small helpers
    public static void broadcast(MinecraftServer server, QuestInstance inst, Component msg) {
        for (ServerPlayer p : online(server, inst)) p.sendSystemMessage(msg);
    }

    public static void title(ServerPlayer p, Component title, Component subtitle, int in, int stay, int out) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    public static void jingle(ServerPlayer p, SoundEvent sound) {
        p.playNotifySound(sound, SoundSource.RECORDS, 1.0F, 1.0F);
    }

    // ================================================================== admin
    public static boolean forceComplete(ServerPlayer p) {
        QuestInstance inst = instanceOf(p);
        if (inst == null || inst.state != QuestInstance.State.ACTIVE) return false;
        complete(p.server, inst);
        return true;
    }

    public static boolean forceFail(ServerPlayer p) {
        QuestInstance inst = instanceOf(p);
        if (inst == null || inst.state != QuestInstance.State.ACTIVE) return false;
        fail(p.server, inst, "admin");
        return true;
    }
}
