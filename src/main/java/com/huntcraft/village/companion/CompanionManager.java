package com.huntcraft.village.companion;

import com.huntcraft.core.HCEvents;
import com.huntcraft.core.SmithingRegistry;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import com.huntcraft.village.VillageRegistry;
import com.huntcraft.village.menu.ItemBoxContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side companion logic: keeps the active mofu next to its hunter (respawning it in the hunter's current
 * dimension), faint timers, XP from hunts, hiring and expeditions.
 */
public final class CompanionManager {
    private static final Map<UUID, MofuEntity> ACTIVE = new HashMap<>();
    private static final Map<UUID, long[]> OWNER_TARGET = new HashMap<>();

    private CompanionManager() {
    }

    public static void clear() {
        ACTIVE.clear();
        OWNER_TARGET.clear();
    }

    @Nullable
    public static MofuEntity current(@Nullable UUID owner) {
        return owner == null ? null : ACTIVE.get(owner);
    }

    // ------------------------------------------------------------------ targeting support
    public static void noteOwnerHit(ServerPlayer p, LivingEntity target) {
        if (target instanceof MofuEntity) return;
        OWNER_TARGET.put(p.getUUID(), new long[]{target.getId(), p.level().getGameTime()});
    }

    @Nullable
    public static LivingEntity ownerTarget(ServerPlayer p) {
        long[] t = OWNER_TARGET.get(p.getUUID());
        if (t == null || p.level().getGameTime() - t[1] > 300) return null;
        Entity e = p.level().getEntity((int) t[0]);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    // ------------------------------------------------------------------ follow / respawn
    /** Called every 10 ticks for each server player. */
    public static void tick(ServerPlayer p) {
        CompoundTag v = MofuData.village(p);
        int uid = v.getInt("active");
        CompoundTag rec = MofuData.find(v, uid);
        long now = p.level().getGameTime();
        MofuEntity e = ACTIVE.get(p.getUUID());
        boolean want = rec != null && !MofuData.onExpedition(rec) && !MofuData.fainted(rec, now) && p.isAlive() && !p.isSpectator();
        if (!want) {
            if (rec != null && rec.getBoolean("wasFainted") && !MofuData.fainted(rec, now)) rec.remove("wasFainted");
            despawn(p.getUUID());
            return;
        }
        if (e != null && (e.isRemoved() || e.level() != p.level() || e.getRecordUid() != uid)) {
            if (!e.isRemoved()) e.discard();
            ACTIVE.remove(p.getUUID());
            e = null;
        }
        if (e == null) {
            e = spawn(p, rec);
            if (e != null && rec.getBoolean("wasFainted")) {
                rec.remove("wasFainted");
                p.displayClientMessage(Component.translatable("message.huntcraft.mofu_back", MofuData.name(rec)), true);
                HunterData.get(p).markDirty();
            }
        } else {
            e.applyRecord(rec);
            if (e.distanceToSqr(p) > 28 * 28) e.teleportNear(p);
        }
    }

    @Nullable
    private static MofuEntity spawn(ServerPlayer p, CompoundTag rec) {
        ServerLevel level = p.serverLevel();
        MofuEntity e = VillageRegistry.MOFU.get().create(level);
        if (e == null) return null;
        e.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
        e.setup(p, rec);
        e.teleportNear(p);
        ACTIVE.put(p.getUUID(), e);
        if (!level.addFreshEntity(e)) {
            ACTIVE.remove(p.getUUID());
            return null;
        }
        return e;
    }

    public static void despawn(UUID owner) {
        MofuEntity e = ACTIVE.remove(owner);
        if (e != null && !e.isRemoved()) e.discard();
    }

    public static void onFaint(MofuEntity e) {
        UUID owner = e.getOwnerId();
        if (owner == null) return;
        ACTIVE.remove(owner);
        ServerPlayer p = e.getOwner();
        if (p == null) return;
        CompoundTag rec = MofuData.find(MofuData.village(p), e.getRecordUid());
        if (rec == null) return;
        rec.putLong("faint", p.level().getGameTime() + MofuData.FAINT_TICKS);
        rec.putBoolean("wasFainted", true);
        p.displayClientMessage(Component.translatable("message.huntcraft.mofu_fainted", MofuData.name(rec)).withStyle(ChatFormatting.GOLD), false);
        HunterData.get(p).markDirty();
    }

    // ------------------------------------------------------------------ hunts -> XP / expedition progress
    public static void onHunted(HCEvents.MonsterHuntedEvent event) {
        HuntMonster m = event.getMonster();
        if (m.level().isClientSide) return;
        boolean large = m.profile().large;
        for (ServerPlayer p : participants(event)) {
            CompoundTag v = MofuData.village(p);
            int active = v.getInt("active");
            boolean changed = false;
            for (CompoundTag rec : MofuData.records(v)) {
                if (MofuData.onExpedition(rec)) {
                    if (large) {
                        CompoundTag exp = rec.getCompound("exp");
                        exp.putInt("prog", exp.getInt("prog") + 1);
                        changed = true;
                    }
                    continue;
                }
                int xp = large ? 60 : 5;
                if (rec.getInt("uid") != active) xp = Math.max(1, xp / 4);
                int gained = MofuData.addXp(rec, xp);
                changed = true;
                if (gained > 0) {
                    p.displayClientMessage(Component.translatable("message.huntcraft.mofu_level", MofuData.name(rec), MofuData.level(rec))
                            .withStyle(ChatFormatting.YELLOW), false);
                    p.level().playSound(null, p.getX(), p.getY(), p.getZ(), HCSounds.LEVEL_UP.get(), SoundSource.PLAYERS, 0.7F, 1.3F);
                    MofuEntity e = ACTIVE.get(p.getUUID());
                    if (e != null && e.getRecordUid() == rec.getInt("uid")) {
                        e.applyRecord(rec);
                        e.refreshStats();
                        e.setHealth(e.getMaxHealth());
                        e.playAction(4);
                    }
                }
            }
            if (changed) HunterData.get(p).markDirty();
        }
    }

    /** Killer plus every hunter near the monster. */
    public static Set<ServerPlayer> participants(HCEvents.MonsterHuntedEvent event) {
        Set<ServerPlayer> set = new LinkedHashSet<>();
        if (event.getPlayer() instanceof ServerPlayer sp) set.add(sp);
        HuntMonster m = event.getMonster();
        for (Player p : m.level().getEntitiesOfClass(Player.class, m.getBoundingBox().inflate(48.0D))) {
            if (p instanceof ServerPlayer sp && !sp.isSpectator()) set.add(sp);
        }
        return set;
    }

    /** Finishes expeditions whose time or hunt count is reached (called every few seconds). */
    public static void tickExpeditions(ServerPlayer p) {
        CompoundTag v = MofuData.village(p);
        long ms = System.currentTimeMillis();
        boolean changed = false;
        for (CompoundTag rec : MofuData.records(v)) {
            if (!MofuData.onExpedition(rec)) continue;
            CompoundTag exp = rec.getCompound("exp");
            boolean done = exp.getInt("prog") >= exp.getInt("need") || ms - exp.getLong("start") >= MofuData.EXPEDITION_MS;
            if (!done) continue;
            Expeditions.Destination d = Expeditions.get(exp.getInt("dest"));
            List<ItemStack> loot = Expeditions.roll(d, MofuData.level(rec), p.getRandom());
            HunterData data = HunterData.get(p);
            for (ItemStack st : loot) {
                ItemStack rest = ItemBoxContainer.insert(data.itemBox, st);
                if (!rest.isEmpty() && !p.getInventory().add(rest)) p.drop(rest, false);
            }
            rec.remove("exp");
            int gained = MofuData.addXp(rec, 45);
            p.displayClientMessage(Component.translatable("message.huntcraft.mofu_returned", MofuData.name(rec), d.displayName(), loot.size())
                    .withStyle(ChatFormatting.GREEN), false);
            if (gained > 0) {
                p.displayClientMessage(Component.translatable("message.huntcraft.mofu_level", MofuData.name(rec), MofuData.level(rec))
                        .withStyle(ChatFormatting.YELLOW), false);
            }
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), VillageRegistry.MOFU_MEOW.get(), SoundSource.PLAYERS, 0.8F, 1.2F);
            changed = true;
        }
        if (changed) HunterData.get(p).markDirty();
    }

    // ------------------------------------------------------------------ GUI operations (action "village/mofu")
    public static final int REFRESH_COST = 100;

    public static void handle(ServerPlayer p, CompoundTag t) {
        CompoundTag v = MofuData.village(p);
        HunterData d = HunterData.get(p);
        String op = t.getString("op");
        int uid = t.getInt("uid");
        CompoundTag rec = MofuData.find(v, uid);
        switch (op) {
            case "cands" -> ensureCandidates(p, v, false);
            case "refresh" -> {
                if (d.spendZenny(REFRESH_COST)) ensureCandidates(p, v, true);
                else p.displayClientMessage(Component.translatable("message.huntcraft.need_zenny"), true);
            }
            case "hire" -> hire(p, v, d, t.getInt("idx"));
            case "active" -> {
                if (rec != null && !MofuData.onExpedition(rec)) {
                    v.putInt("active", uid);
                    despawn(p.getUUID());
                    p.displayClientMessage(Component.translatable("message.huntcraft.mofu_active", MofuData.name(rec)), true);
                }
            }
            case "rest" -> {
                v.putInt("active", 0);
                despawn(p.getUUID());
            }
            case "expedition" -> {
                if (rec != null && !MofuData.onExpedition(rec)) {
                    Expeditions.Destination dest = Expeditions.get(t.getInt("dest"));
                    if (MofuData.level(rec) < dest.minLevel()) {
                        p.displayClientMessage(Component.translatable("message.huntcraft.mofu_need_level", dest.minLevel()), true);
                        break;
                    }
                    if (v.getInt("active") == uid) {
                        v.putInt("active", 0);
                        despawn(p.getUUID());
                    }
                    CompoundTag exp = new CompoundTag();
                    exp.putInt("dest", Expeditions.ALL.indexOf(dest));
                    exp.putLong("start", System.currentTimeMillis());
                    exp.putInt("prog", 0);
                    exp.putInt("need", MofuData.EXPEDITION_HUNTS);
                    rec.put("exp", exp);
                    p.displayClientMessage(Component.translatable("message.huntcraft.mofu_expedition", MofuData.name(rec), dest.displayName()), false);
                }
            }
            case "recall" -> {
                if (rec != null) rec.remove("exp");
            }
            case "dismiss" -> {
                if (rec != null) {
                    if (v.getInt("active") == uid) despawn(p.getUUID());
                    MofuData.remove(v, uid);
                    p.displayClientMessage(Component.translatable("message.huntcraft.mofu_dismissed", MofuData.name(rec)), true);
                }
            }
            case "rename" -> {
                if (rec != null) {
                    String name = t.getString("name").trim();
                    if (name.length() > 16) name = name.substring(0, 16);
                    if (name.isEmpty()) rec.remove("cname");
                    else rec.putString("cname", name);
                }
            }
            case "gear" -> {
                if (rec != null) buyGear(p, d, rec, t.getString("slot"), t.getInt("value"));
            }
            default -> {
            }
        }
        d.markDirty();
    }

    private static void ensureCandidates(ServerPlayer p, CompoundTag v, boolean force) {
        ListTag c = MofuData.candidates(v);
        if (!force && !c.isEmpty()) return;
        c.clear();
        int hr = HunterData.get(p).hunterRank;
        for (int i = 0; i < 3; i++) {
            int lv = 1 + p.getRandom().nextInt(Math.min(12, 2 + hr));
            c.add(MofuData.create(p.getRandom(), 0, lv));
        }
    }

    private static void hire(ServerPlayer p, CompoundTag v, HunterData d, int idx) {
        ListTag c = MofuData.candidates(v);
        if (idx < 0 || idx >= c.size()) return;
        if (MofuData.list(v).size() >= MofuData.MAX_COMPANIONS) {
            p.displayClientMessage(Component.translatable("message.huntcraft.mofu_full", MofuData.MAX_COMPANIONS), true);
            return;
        }
        CompoundTag cand = c.getCompound(idx);
        int cost = MofuData.hireCost(cand);
        if (!d.spendZenny(cost)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_zenny"), true);
            return;
        }
        CompoundTag rec = cand.copy();
        rec.putInt("uid", MofuData.nextUid(v));
        MofuData.list(v).add(rec);
        c.remove(idx);
        if (v.getInt("active") == 0) v.putInt("active", rec.getInt("uid"));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), VillageRegistry.MOFU_MEOW.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        p.displayClientMessage(Component.translatable("message.huntcraft.mofu_hired", MofuData.name(rec)).withStyle(ChatFormatting.GREEN), false);
    }

    /** Gear shop at the elder: helm 0..2, armor 0..2. Returns the price table for the client too. */
    public static int gearZenny(String slot, int value) {
        if ("helm".equals(slot)) return value == 1 ? 300 : value == 2 ? 1500 : 0;
        return value == 1 ? 600 : value == 2 ? 2200 : 0;
    }

    public static List<SmithingRegistry.Cost> gearCosts(String slot, int value) {
        if ("helm".equals(slot)) {
            return value == 2 ? List.of(SmithingRegistry.Cost.of(HCItems.EARTH_CRYSTAL, 3)) : List.of();
        }
        if (value == 1) return List.of(SmithingRegistry.Cost.of(HCItems.JAGRAPTOR_HIDE, 2));
        if (value == 2) return List.of(SmithingRegistry.Cost.of(HCItems.EARTH_CRYSTAL, 4), SmithingRegistry.Cost.of(HCItems.AZURITE_ORE, 1));
        return List.of();
    }

    private static void buyGear(ServerPlayer p, HunterData d, CompoundTag rec, String slot, int value) {
        if (!"helm".equals(slot) && !"armor".equals(slot)) return;
        value = Math.max(0, Math.min(2, value));
        if (rec.getInt(slot) == value) return;
        int zenny = gearZenny(slot, value);
        List<SmithingRegistry.Cost> costs = gearCosts(slot, value);
        if (d.zenny < zenny) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_zenny"), true);
            return;
        }
        if (!SmithingRegistry.hasAll(p, costs)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_materials"), true);
            return;
        }
        d.spendZenny(zenny);
        for (SmithingRegistry.Cost c : costs) SmithingRegistry.consume(p, c.item().get(), c.count());
        rec.putInt(slot, value);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.2F);
        MofuEntity e = ACTIVE.get(p.getUUID());
        if (e != null && e.getRecordUid() == rec.getInt("uid")) {
            e.applyRecord(rec);
            e.refreshStats();
        }
    }
}
