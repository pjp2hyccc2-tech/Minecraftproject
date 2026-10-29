package com.huntcraft.village.companion;

import com.huntcraft.core.HCEvents;
import com.huntcraft.core.Rank;
import com.huntcraft.core.SmithingRegistry;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.registry.HCSounds;
import com.huntcraft.village.VillageRegistry;
import com.huntcraft.village.menu.ItemBoxContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side companion logic: keeps the active mofu next to its hunter (respawning it in the hunter's current
 * dimension), faint timers, XP from hunts, hiring, equipment, training and expeditions.
 */
public final class CompanionManager {
    private static final Map<UUID, MofuEntity> ACTIVE = new HashMap<>();
    private static final Map<UUID, long[]> OWNER_TARGET = new HashMap<>();
    /** Encore (healer horn) attack buff: player -> game time it ends. */
    private static final Map<UUID, Long> ENCORE = new HashMap<>();

    public static final int REFRESH_COST = 100;

    private CompanionManager() {
    }

    public static void clear() {
        ACTIVE.clear();
        OWNER_TARGET.clear();
        ENCORE.clear();
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

    // ------------------------------------------------------------------ encore buff (HuntHooks attack multiplier)
    public static void grantEncore(Player p, int ticks) {
        ENCORE.put(p.getUUID(), p.level().getGameTime() + ticks);
    }

    public static double attackMultiplier(Player p) {
        Long until = ENCORE.get(p.getUUID());
        if (until == null || p.level() == null) return 1.0;
        if (p.level().getGameTime() > until) {
            ENCORE.remove(p.getUUID());
            return 1.0;
        }
        return 1.10;
    }

    // ------------------------------------------------------------------ follow / respawn
    /** Called every 10 ticks for each server player. */
    public static void tick(ServerPlayer p) {
        CompoundTag v = MofuData.village(p);
        int uid = v.getInt("active");
        CompoundTag rec = MofuData.find(v, uid);
        long now = p.level().getGameTime();
        MofuEntity e = ACTIVE.get(p.getUUID());
        boolean want = rec != null && !MofuData.onExpedition(rec) && !MofuData.fainted(rec, now) && p.isAlive() && !p.isSpectator()
                && !v.getBoolean("stay");
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

    // ------------------------------------------------------------------ hunts -> XP / expedition progress / gatherer
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
                boolean isActive = rec.getInt("uid") == active;
                int xp = large ? 60 : 6;
                if (!isActive) xp = Math.max(1, xp / 4);
                if (isActive && large) rec.putInt("hunts", rec.getInt("hunts") + 1);
                changed = true;
                levelUp(p, rec, MofuData.addXp(rec, xp));
                if (isActive && large && MofuData.hasSkill(rec, MofuSkill.GATHERER)) gather(p, m, rec);
            }
            if (changed) HunterData.get(p).markDirty();
        }
    }

    private static void levelUp(ServerPlayer p, CompoundTag rec, int gained) {
        if (gained <= 0) return;
        int lv = MofuData.level(rec);
        p.displayClientMessage(Component.translatable("message.huntcraft.mofu_level", MofuData.name(rec), lv).withStyle(ChatFormatting.YELLOW), false);
        for (MofuSkill s : MofuSkill.forType(MofuData.type(rec))) {
            if (s.level > lv - gained && s.level <= lv) {
                p.displayClientMessage(Component.translatable("message.huntcraft.mofu_skill", MofuData.name(rec), s.displayName())
                        .withStyle(ChatFormatting.AQUA), false);
            }
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), HCSounds.LEVEL_UP.get(), SoundSource.PLAYERS, 0.7F, 1.3F);
        MofuEntity e = ACTIVE.get(p.getUUID());
        if (e != null && e.getRecordUid() == rec.getInt("uid")) {
            e.applyRecord(rec);
            e.refreshStats();
            e.setHealth(e.getMaxHealth());
            e.playAction(4);
        }
    }

    /** Gatherer skill: the active companion picks up one extra material from the slain monster. */
    private static void gather(ServerPlayer p, HuntMonster m, CompoundTag rec) {
        Rank rank = m.getRank();
        ItemStack st = m.profile().carves.roll(p.getRandom(), rank);
        if (st.isEmpty()) return;
        ItemStack rest = ItemBoxContainer.insert(HunterData.get(p).itemBox, st);
        if (!rest.isEmpty() && !p.getInventory().add(rest)) p.drop(rest, false);
        p.displayClientMessage(Component.translatable("message.huntcraft.mofu_gathered", MofuData.name(rec), st.getHoverName())
                .withStyle(ChatFormatting.GREEN), false);
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
            long dur = exp.contains("dur") ? exp.getLong("dur") : Expeditions.Length.SHORT.millis;
            boolean done = exp.getInt("prog") >= exp.getInt("need") || ms - exp.getLong("start") >= dur;
            if (!done) continue;
            finishExpedition(p, rec, exp);
            changed = true;
        }
        if (changed) HunterData.get(p).markDirty();
    }

    private static void finishExpedition(ServerPlayer p, CompoundTag rec, CompoundTag exp) {
        Expeditions.Destination d = Expeditions.get(exp.getInt("dest"));
        Expeditions.Length len = Expeditions.Length.byIndex(exp.getInt("len"));
        List<ItemStack> loot = Expeditions.roll(d, MofuData.level(rec), len, p.getRandom());
        HunterData data = HunterData.get(p);
        int count = 0;
        for (ItemStack st : loot) {
            count += st.getCount();
            ItemStack rest = ItemBoxContainer.insert(data.itemBox, st);
            if (!rest.isEmpty() && !p.getInventory().add(rest)) p.drop(rest, false);
        }
        rec.remove("exp");
        rec.put("lastLoot", lootTag(loot));
        p.displayClientMessage(Component.translatable("message.huntcraft.mofu_returned", MofuData.name(rec), d.displayName(), count)
                .withStyle(ChatFormatting.GREEN), false);
        levelUp(p, rec, MofuData.addXp(rec, len.xp));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), VillageRegistry.MOFU_MEOW.get(), SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    private static ListTag lootTag(List<ItemStack> loot) {
        ListTag l = new ListTag();
        for (ItemStack st : loot) {
            if (l.size() >= 12) break;
            l.add(st.save(new CompoundTag()));
        }
        return l;
    }

    // ------------------------------------------------------------------ GUI operations (action "village/mofu")
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
            case "first" -> createFirst(p, v, t);
            case "hire" -> hire(p, v, d, t.getInt("idx"));
            case "active" -> {
                if (rec != null && !MofuData.onExpedition(rec)) {
                    v.putInt("active", uid);
                    v.remove("stay");
                    despawn(p.getUUID());
                    p.displayClientMessage(Component.translatable("message.huntcraft.mofu_active", MofuData.name(rec)), true);
                }
            }
            case "stay" -> {
                v.putBoolean("stay", t.getBoolean("value"));
                if (t.getBoolean("value")) despawn(p.getUUID());
            }
            case "rest" -> {
                v.putInt("active", 0);
                despawn(p.getUUID());
            }
            case "expedition" -> {
                if (rec != null && !MofuData.onExpedition(rec)) startExpedition(p, v, rec, uid, t.getInt("dest"), t.getInt("len"));
            }
            case "recall" -> {
                if (rec != null && MofuData.onExpedition(rec)) {
                    rec.remove("exp");
                    p.displayClientMessage(Component.translatable("message.huntcraft.mofu_recalled", MofuData.name(rec)), true);
                }
            }
            case "dismiss" -> {
                if (rec != null) {
                    if (v.getInt("active") == uid) despawn(p.getUUID());
                    returnAllGear(p, rec);
                    MofuData.remove(v, uid);
                    p.displayClientMessage(Component.translatable("message.huntcraft.mofu_dismissed", MofuData.name(rec)), true);
                }
            }
            case "rename" -> {
                if (rec != null) {
                    String name = MofuData.sanitizeName(t.getString("name"));
                    if (name.isEmpty()) rec.remove("cname");
                    else rec.putString("cname", name);
                }
            }
            case "equip" -> {
                if (rec != null) equip(p, rec, t.getString("item"));
            }
            case "unequip" -> {
                if (rec != null) unequip(p, rec, slotOf(t.getString("slot")));
            }
            case "train" -> {
                if (rec != null) train(p, d, rec);
            }
            case "retrain" -> {
                if (rec != null) retrain(p, d, rec, t.getInt("type"));
            }
            default -> {
            }
        }
        refreshEntity(p, rec);
        d.markDirty();
    }

    private static void refreshEntity(ServerPlayer p, @Nullable CompoundTag rec) {
        MofuEntity e = ACTIVE.get(p.getUUID());
        if (e != null && rec != null && e.getRecordUid() == rec.getInt("uid")) {
            e.applyRecord(rec);
            e.refreshStats();
        }
    }

    private static void ensureCandidates(ServerPlayer p, CompoundTag v, boolean force) {
        ListTag c = MofuData.candidates(v);
        if (!force && !c.isEmpty()) return;
        c.clear();
        int hr = HunterData.get(p).hunterRank;
        for (int i = 0; i < 3; i++) {
            int lv = 1 + p.getRandom().nextInt(Math.min(15, 2 + hr * 2));
            c.add(MofuData.create(p.getRandom(), 0, lv));
        }
    }

    /** The free first companion (type, fur pattern and name chosen by the hunter). */
    private static void createFirst(ServerPlayer p, CompoundTag v, CompoundTag t) {
        if (!MofuData.list(v).isEmpty() || v.getBoolean("firstMofu")) return;
        MofuType type = MofuType.byId(t.getInt("type"));
        CompoundTag rec = MofuData.createFirst(MofuData.nextUid(v), type, t.getInt("pat"), t.getString("name"));
        MofuData.list(v).add(rec);
        v.putInt("active", rec.getInt("uid"));
        v.putBoolean("firstMofu", true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), VillageRegistry.MOFU_MEOW.get(), SoundSource.PLAYERS, 1.0F, 1.1F);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), HCSounds.LEVEL_UP.get(), SoundSource.PLAYERS, 0.6F, 1.2F);
        p.displayClientMessage(Component.translatable("message.huntcraft.mofu_first", MofuData.name(rec), type.displayName())
                .withStyle(ChatFormatting.GREEN), false);
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
        v.putBoolean("firstMofu", true);
        if (v.getInt("active") == 0) v.putInt("active", rec.getInt("uid"));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), VillageRegistry.MOFU_MEOW.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        p.displayClientMessage(Component.translatable("message.huntcraft.mofu_hired", MofuData.name(rec)).withStyle(ChatFormatting.GREEN), false);
    }

    private static void startExpedition(ServerPlayer p, CompoundTag v, CompoundTag rec, int uid, int destIdx, int lenIdx) {
        Expeditions.Destination dest = Expeditions.get(destIdx);
        if (MofuData.level(rec) < dest.minLevel()) {
            p.displayClientMessage(Component.translatable("message.huntcraft.mofu_need_level", dest.minLevel()), true);
            return;
        }
        if (HunterData.get(p).hunterRank < dest.requiredHR()) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_hr", dest.requiredHR()), true);
            return;
        }
        if (v.getInt("active") == uid) {
            v.putInt("active", 0);
            despawn(p.getUUID());
        }
        Expeditions.Length len = Expeditions.Length.byIndex(lenIdx);
        CompoundTag exp = new CompoundTag();
        exp.putInt("dest", Expeditions.ALL.indexOf(dest));
        exp.putInt("len", len.ordinal());
        exp.putLong("start", System.currentTimeMillis());
        exp.putLong("dur", len.millis);
        exp.putInt("prog", 0);
        exp.putInt("need", MofuData.EXPEDITION_HUNTS * (len == Expeditions.Length.LONG ? 2 : 1));
        rec.put("exp", exp);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), VillageRegistry.MOFU_MEOW.get(), SoundSource.PLAYERS, 0.8F, 0.9F);
        p.displayClientMessage(Component.translatable("message.huntcraft.mofu_expedition", MofuData.name(rec), dest.displayName()), false);
    }

    // ------------------------------------------------------------------ equipment
    @Nullable
    public static MofuGear.Slot slotOf(String id) {
        for (MofuGear.Slot s : MofuGear.Slot.values()) if (s.id.equals(id)) return s;
        return null;
    }

    private static void equip(ServerPlayer p, CompoundTag rec, String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        if (!(item instanceof MofuGearItem g)) return;
        if (SmithingRegistry.count(p, item) <= 0) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_materials"), true);
            return;
        }
        SmithingRegistry.consume(p, item, 1);
        int prev = MofuGear.equipped(rec, g.slot);
        rec.putInt(g.slot.key, g.tier.ordinal());
        if (prev >= 0) giveBack(p, MofuGear.item(g.slot, MofuGear.Tier.byIndex(prev)).get());
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.2F);
        p.displayClientMessage(Component.translatable("message.huntcraft.mofu_equipped", MofuData.name(rec), new ItemStack(item).getHoverName()), true);
    }

    private static void unequip(ServerPlayer p, CompoundTag rec, @Nullable MofuGear.Slot slot) {
        if (slot == null) return;
        int prev = MofuGear.equipped(rec, slot);
        if (prev < 0) return;
        rec.putInt(slot.key, -1);
        giveBack(p, MofuGear.item(slot, MofuGear.Tier.byIndex(prev)).get());
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    private static void returnAllGear(ServerPlayer p, CompoundTag rec) {
        for (MofuGear.Slot s : MofuGear.Slot.values()) {
            int prev = MofuGear.equipped(rec, s);
            if (prev >= 0) giveBack(p, MofuGear.item(s, MofuGear.Tier.byIndex(prev)).get());
        }
    }

    /** Returned gear goes to the item box (inventory / ground when the box is full). */
    private static void giveBack(ServerPlayer p, Item item) {
        ItemStack rest = ItemBoxContainer.insert(HunterData.get(p).itemBox, new ItemStack(item));
        if (!rest.isEmpty() && !p.getInventory().add(rest)) p.drop(rest, false);
    }

    // ------------------------------------------------------------------ training
    private static void train(ServerPlayer p, HunterData d, CompoundTag rec) {
        if (MofuData.level(rec) >= MofuData.MAX_LEVEL) return;
        int cost = MofuData.trainCost(rec);
        if (!d.spendZenny(cost)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_zenny"), true);
            return;
        }
        int xp = Math.round(MofuData.xpForNext(MofuData.level(rec)) * 0.4F);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), VillageRegistry.MOFU_ATTACK.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        p.displayClientMessage(Component.translatable("message.huntcraft.mofu_trained", MofuData.name(rec), xp), true);
        levelUp(p, rec, MofuData.addXp(rec, xp));
    }

    private static void retrain(ServerPlayer p, HunterData d, CompoundTag rec, int type) {
        MofuType nt = MofuType.byId(type);
        if (nt == MofuData.type(rec)) return;
        int cost = MofuData.retrainCost(rec);
        if (!d.spendZenny(cost)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_zenny"), true);
            return;
        }
        rec.putInt("type", nt.ordinal());
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), VillageRegistry.MOFU_MEOW.get(), SoundSource.PLAYERS, 1.0F, 1.3F);
        p.displayClientMessage(Component.translatable("message.huntcraft.mofu_retrained", MofuData.name(rec), nt.displayName()), false);
    }
}
