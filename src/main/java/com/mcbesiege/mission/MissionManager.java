package com.mcbesiege.mission;

import com.mcbesiege.MCBesiege;
import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.entity.MachineEntity;
import com.mcbesiege.machine.Blueprint;
import com.mcbesiege.machine.Machine;
import com.mcbesiege.machine.MachineManager;
import com.mcbesiege.net.Net;
import com.mcbesiege.part.PartConfig;
import com.mcbesiege.part.PartType;
import com.mcbesiege.physics.V3;
import com.mcbesiege.registry.ModBlocks;
import com.mcbesiege.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Runs siege missions: arena generation, build-zone rule, objectives, victory, return home. */
public final class MissionManager {
    private MissionManager() {}

    public static final ResourceKey<Level> DIM = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(MCBesiege.MODID, "siege_lands"));
    public static final int GROUND = 45;

    static final class State {
        final Missions.Def def;
        final Arena arena;
        int op;
        boolean ready;
        boolean complete;
        int targetsTotal;
        int targetsDestroyed;
        final Set<UUID> mobs = new HashSet<>();
        int kills;
        int propId = -1;
        boolean delivered;
        boolean reached;
        int ticks;
        String lastStatus = "";

        State(Missions.Def def, Arena arena) {
            this.def = def;
            this.arena = arena;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    public static boolean inMission(ServerPlayer p) {
        return ACTIVE.containsKey(p.getUUID()) && p.level().dimension() == DIM;
    }

    // ---------------------------------------------------------------------------------------------------- UI

    public static void openTable(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        ListTag l = new ListTag();
        CompoundTag done = persisted(p).getCompound("mcbesiege_done");
        for (Missions.Def d : Missions.ALL) {
            CompoundTag c = new CompoundTag();
            c.putString("id", d.id());
            c.putInt("island", d.island());
            c.putInt("stars", d.stars());
            c.putBoolean("done", done.getBoolean(d.id()));
            l.add(c);
        }
        t.put("missions", l);
        State s = ACTIVE.get(p.getUUID());
        t.putString("active", s == null ? "" : s.def.id());
        Net.send(p, "missions", t);
    }

    private static CompoundTag persisted(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // ---------------------------------------------------------------------------------------------------- start / leave

    public static void start(ServerPlayer p, String id) {
        Missions.Def def = Missions.byId(id);
        if (def == null) {
            p.displayClientMessage(Component.translatable("msg.mcbesiege.no_mission"), true);
            return;
        }
        ServerLevel dim = p.server.getLevel(DIM);
        if (dim == null) {
            p.displayClientMessage(Component.literal("siege dimension missing"), false);
            return;
        }
        MachineEntity own;
        int guard = 0;
        while ((own = MachineManager.ownedMachine(p)) != null && guard++ < 16) MachineManager.reset(own, false);
        State old = ACTIVE.remove(p.getUUID());
        if (old != null) cleanup(old);
        if (p.level().dimension() != DIM) {
            CompoundTag ret = new CompoundTag();
            ret.putString("dim", p.level().dimension().location().toString());
            ret.putDouble("x", p.getX());
            ret.putDouble("y", p.getY());
            ret.putDouble("z", p.getZ());
            ret.putFloat("yaw", p.getYRot());
            persisted(p).put("mcbesiege_return", ret);
        }
        int idx = Missions.index(def);
        BlockPos origin = new BlockPos(idx * 512, GROUND, 0);
        Arena a = new Arena(dim, origin);
        def.build().accept(a);
        // starting block in the middle of the build zone, facing the enemy
        a.set(0, 0, 0, ModBlocks.part(PartType.STARTING_BLOCK).defaultBlockState().setValue(MachinePartBlock.FACING, Direction.SOUTH));
        State s = new State(def, a);
        ACTIVE.put(p.getUUID(), s);
        // remove leftovers (mobs, items, props) of an earlier attempt
        AABB area = new AABB(origin.getX() - 48, origin.getY() - 20, origin.getZ() - 24, origin.getX() + 48, origin.getY() + 48, origin.getZ() + 100);
        for (Entity e : dim.getEntities((Entity) null, area, e -> !(e instanceof Player))) {
            if (e instanceof MachineEntity me) MachineManager.reset(me, false);
            else e.discard();
        }
        p.teleportTo(dim, origin.getX() + 0.5, origin.getY() + 1, origin.getZ() - a.buildHalf - 3 + 0.5, 0f, 10f);
        p.displayClientMessage(Component.translatable("msg.mcbesiege.generating"), true);
        sendStatus(p, s, true);
    }

    public static void leave(ServerPlayer p) {
        State s = ACTIVE.remove(p.getUUID());
        if (s != null) cleanup(s);
        MachineEntity own;
        int guard = 0;
        while ((own = MachineManager.ownedMachine(p)) != null && guard++ < 16) MachineManager.reset(own, false);
        stripMissionItems(p);
        CompoundTag ret = persisted(p).getCompound("mcbesiege_return");
        ServerLevel target = null;
        if (ret.contains("dim")) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(ret.getString("dim")));
            target = p.server.getLevel(key);
        }
        if (target != null) p.teleportTo(target, ret.getDouble("x"), ret.getDouble("y"), ret.getDouble("z"), ret.getFloat("yaw"), 0f);
        else if (p.level().dimension() == DIM) {
            ServerLevel ow = p.server.overworld();
            BlockPos sp = ow.getSharedSpawnPos();
            p.teleportTo(ow, sp.getX() + 0.5, sp.getY() + 1, sp.getZ() + 0.5, 0f, 0f);
        }
        Net.send(p, "mission_status", new CompoundTag());
    }

    private static void cleanup(State s) {
        if (s.propId >= 0) {
            Entity e = s.arena.level.getEntity(s.propId);
            if (e instanceof MachineEntity me) MachineManager.reset(me, false);
        }
    }

    private static void stripMissionItems(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.hasTag() && st.getTag().getBoolean("mcbesiege_mission")) p.getInventory().setItem(i, ItemStack.EMPTY);
        }
    }

    public static void onDimensionChange(ServerPlayer p) {
        if (p.level().dimension() == DIM) return;
        State s = ACTIVE.remove(p.getUUID());
        if (s != null) cleanup(s);
        stripMissionItems(p);
        Net.send(p, "mission_status", new CompoundTag());
    }

    // ---------------------------------------------------------------------------------------------------- rules

    /** Besiege rule: the whole machine must be inside the build zone when the simulation starts. */
    public static String checkStart(ServerPlayer p, Blueprint bp) {
        State s = ACTIVE.get(p.getUUID());
        if (s == null || p.level().dimension() != DIM) return null;
        if (!s.ready) return "msg.mcbesiege.arena_generating";
        AABB zone = s.arena.buildZone();
        for (Blueprint.Entry e : bp.entries) {
            BlockPos q = bp.origin.offset(e.rel);
            if (!zone.contains(q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5)) return "msg.mcbesiege.outside_zone";
        }
        return null;
    }

    public static void onSimulationStarted(ServerPlayer p, MachineEntity e) {}

    public static void onDeath(LivingEntity e) {
        for (State s : ACTIVE.values()) {
            if (s.mobs.remove(e.getUUID())) s.kills++;
        }
    }

    // ---------------------------------------------------------------------------------------------------- tick

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, State>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> en = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            State s = en.getValue();
            if (p == null) {
                cleanup(s);
                it.remove();
                continue;
            }
            if (!s.ready) {
                generate(s);
                if (s.ready) onReady(p, s);
                continue;
            }
            s.ticks++;
            if (s.complete || s.ticks % 10 != 0) continue;
            evaluate(p, s);
        }
    }

    private static void generate(State s) {
        Arena a = s.arena;
        int n = Math.min(a.opPos.size(), s.op + 24000);
        for (int i = s.op; i < n; i++) {
            BlockPos pos = a.opPos.get(i);
            BlockState st = a.opState.get(i);
            if (a.level.getBlockState(pos) != st) a.level.setBlock(pos, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        }
        s.op = n;
        if (s.op >= a.opPos.size()) s.ready = true;
    }

    private static void onReady(ServerPlayer p, State s) {
        Arena a = s.arena;
        ServerLevel level = a.level;
        for (Arena.Mob m : a.mobs) {
            Entity e = m.type().create(level);
            if (e == null) continue;
            BlockPos bp = a.at(m.x(), m.y(), m.z());
            e.moveTo(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5, 180f, 0f);
            if (e instanceof Mob mob) {
                mob.setPersistenceRequired();
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(bp), MobSpawnType.EVENT, null, null);
            }
            e.addTag("mcbesiege_mission");
            level.addFreshEntity(e);
            s.mobs.add(e.getUUID());
        }
        s.arena.killCount = s.mobs.size();
        for (Arena.Prop pr : a.props) {
            BlockPos bp = a.at(pr.x(), pr.y(), pr.z());
            s.propId = spawnProp(level, bp, pr.mass());
        }
        s.targetsTotal = a.targets.size();
        level.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.8f, 1.0f);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("mission.mcbesiege." + s.def.id())));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("mission.mcbesiege." + s.def.id() + ".desc")));
        p.displayClientMessage(Component.translatable("msg.mcbesiege.build_hint"), false);
        sendStatus(p, s, true);
    }

    /** Spawns an ownerless physics prop (a heavy treasure crate). */
    static int spawnProp(ServerLevel level, BlockPos bp, float mass) {
        Blueprint b = new Blueprint();
        b.origin = bp;
        b.forward = Direction.SOUTH;
        PartConfig cfg = new PartConfig(PartType.BALLAST);
        cfg.setSlider("mass", mass);
        CompoundTag data = new CompoundTag();
        data.put("cfg", cfg.save());
        b.entries.add(new Blueprint.Entry(BlockPos.ZERO, ModBlocks.part(PartType.BALLAST).defaultBlockState(), data));
        MachineEntity ent = new MachineEntity(ModEntities.MACHINE.get(), level);
        ent.setPos(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
        Machine m = Machine.create(ent, level, b, null);
        ent.setMachine(m);
        level.addFreshEntity(ent);
        m.addToWorld();
        return ent.getId();
    }

    private static void evaluate(ServerPlayer p, State s) {
        Arena a = s.arena;
        ServerLevel level = a.level;
        if (a.destroyTargets) {
            int d = 0;
            for (BlockPos t : a.targets) if (!level.getBlockState(t).is(ModBlocks.SIEGE_TARGET.get())) d++;
            s.targetsDestroyed = d;
        }
        if (a.killAll) {
            // mobs that wandered off / despawned count as gone only when dead; drop missing ones
            s.mobs.removeIf(u -> {
                Entity e = level.getEntity(u);
                if (e == null || !e.isAlive()) {
                    s.kills++;
                    return true;
                }
                return false;
            });
        }
        if (a.deliver && s.propId >= 0 && a.goal != null) {
            Entity e = level.getEntity(s.propId);
            if (e instanceof MachineEntity me && me.machine != null) {
                V3 c = me.machine.corePos();
                if (a.goal.contains(c.x, c.y, c.z)) s.delivered = true;
            }
        }
        if (a.reach && a.goal != null) {
            MachineEntity own = MachineManager.ownedMachine(p);
            if (own != null && own.machine != null) {
                V3 c = own.machine.corePos();
                if (a.goal.contains(c.x, c.y, c.z)) s.reached = true;
            }
        }
        boolean ok = true;
        if (a.destroyTargets && s.targetsDestroyed < s.targetsTotal) ok = false;
        if (a.killAll && s.kills < a.killCount) ok = false;
        if (a.deliver && !s.delivered) ok = false;
        if (a.reach && !s.reached) ok = false;
        sendStatus(p, s, false);
        if (ok) victory(p, s);
    }

    private static void victory(ServerPlayer p, State s) {
        s.complete = true;
        persisted(p).getCompound("mcbesiege_done");
        CompoundTag done = persisted(p).getCompound("mcbesiege_done");
        done.putBoolean(s.def.id(), true);
        persisted(p).put("mcbesiege_done", done);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.mcbesiege.victory")));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("mission.mcbesiege." + s.def.id())));
        ServerLevel level = s.arena.level;
        level.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        for (int i = 0; i < 5; i++) {
            ItemStack fw = new ItemStack(Items.FIREWORK_ROCKET);
            CompoundTag fireworks = new CompoundTag();
            fireworks.putByte("Flight", (byte) 1);
            ListTag ex = new ListTag();
            CompoundTag e = new CompoundTag();
            e.putByte("Type", (byte) (i % 2 == 0 ? 1 : 4));
            e.putIntArray("Colors", new int[]{0xFFD700, 0xE03030, 0x3060E0});
            e.putBoolean("Trail", true);
            e.putBoolean("Flicker", i % 2 == 1);
            ex.add(e);
            fireworks.put("Explosions", ex);
            fw.getOrCreateTag().put("Fireworks", fireworks);
            double ox = (level.random.nextDouble() - 0.5) * 8, oz = (level.random.nextDouble() - 0.5) * 8;
            level.addFreshEntity(new FireworkRocketEntity(level, p.getX() + ox, p.getY() + 1, p.getZ() + oz, fw));
        }
        p.displayClientMessage(Component.translatable("msg.mcbesiege.victory_hint"), false);
        sendStatus(p, s, true);
    }

    private static void sendStatus(ServerPlayer p, State s, boolean force) {
        CompoundTag t = new CompoundTag();
        t.putString("id", s.def.id());
        t.putString("state", !s.ready ? "gen" : (s.complete ? "done" : "run"));
        ListTag objs = new ListTag();
        Arena a = s.arena;
        if (a.destroyTargets) objs.add(obj("targets", s.targetsDestroyed, s.targetsTotal));
        if (a.killAll) objs.add(obj("kill", Math.min(s.kills, a.killCount), a.killCount));
        if (a.deliver) objs.add(obj("deliver", s.delivered ? 1 : 0, 1));
        if (a.reach) objs.add(obj("reach", s.reached ? 1 : 0, 1));
        t.put("objs", objs);
        AABB z = a.buildZone();
        t.putIntArray("zone", new int[]{(int) z.minX, (int) z.minY + 1, (int) z.minZ, (int) z.maxX, (int) z.minY + 1, (int) z.maxZ});
        if (a.goal != null) t.putIntArray("goal", new int[]{(int) a.goal.minX, (int) a.goal.minY + 2, (int) a.goal.minZ, (int) a.goal.maxX, (int) a.goal.minY + 2, (int) a.goal.maxZ});
        String key = t.toString();
        if (!force && key.equals(s.lastStatus)) return;
        s.lastStatus = key;
        Net.send(p, "mission_status", t);
    }

    private static CompoundTag obj(String k, int cur, int max) {
        CompoundTag c = new CompoundTag();
        c.putString("k", k);
        c.putInt("c", cur);
        c.putInt("t", max);
        return c;
    }

    public static List<String> activeIds() {
        List<String> l = new ArrayList<>();
        for (State s : ACTIVE.values()) l.add(s.def.id());
        return l;
    }

    /** Test helper: is the arena of the given player generated? */
    public static boolean isReady(ServerPlayer p) {
        State s = ACTIVE.get(p.getUUID());
        return s != null && s.ready;
    }
}
