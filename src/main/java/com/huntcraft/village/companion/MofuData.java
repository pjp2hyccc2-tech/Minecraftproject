package com.huntcraft.village.companion;

import com.huntcraft.data.HunterData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Companion records stored in {@code HunterData.getModuleTag("village")}:
 * <pre>
 * mofu:   list of {uid, name, cname?, pat, type, lv, xp, faint(gameTime), gw, gh, ga (gear tier or -1),
 *                  hunts, exp?:{dest, start(ms), dur(ms), prog, need}}
 * active: uid of the companion that follows the hunter (0 = none)
 * cands:  hire candidates (same record format, plus "cost")
 * </pre>
 * The module tag is synced to the owning client by core (HunterData.markDirty), so screens read it directly.
 */
public final class MofuData {
    public static final int MAX_COMPANIONS = 4;
    public static final int MAX_LEVEL = 50;
    public static final int PATTERNS = 6;
    public static final int NAMES = 32;
    public static final int EXPEDITION_HUNTS = 2;
    public static final int FAINT_TICKS = 600;

    private MofuData() {
    }

    public static CompoundTag village(Player p) {
        return HunterData.get(p).getModuleTag("village");
    }

    public static ListTag list(CompoundTag v) {
        if (!v.contains("mofu", Tag.TAG_LIST)) v.put("mofu", new ListTag());
        return v.getList("mofu", Tag.TAG_COMPOUND);
    }

    public static ListTag candidates(CompoundTag v) {
        if (!v.contains("cands", Tag.TAG_LIST)) v.put("cands", new ListTag());
        return v.getList("cands", Tag.TAG_COMPOUND);
    }

    public static List<CompoundTag> records(CompoundTag v) {
        List<CompoundTag> out = new ArrayList<>();
        ListTag l = list(v);
        for (int i = 0; i < l.size(); i++) out.add(l.getCompound(i));
        return out;
    }

    @Nullable
    public static CompoundTag find(CompoundTag v, int uid) {
        if (uid <= 0) return null;
        ListTag l = list(v);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag r = l.getCompound(i);
            if (r.getInt("uid") == uid) return r;
        }
        return null;
    }

    @Nullable
    public static CompoundTag active(CompoundTag v) {
        return find(v, v.getInt("active"));
    }

    public static boolean remove(CompoundTag v, int uid) {
        ListTag l = list(v);
        for (int i = 0; i < l.size(); i++) {
            if (l.getCompound(i).getInt("uid") == uid) {
                l.remove(i);
                if (v.getInt("active") == uid) v.putInt("active", 0);
                return true;
            }
        }
        return false;
    }

    public static int nextUid(CompoundTag v) {
        int n = Math.max(1, v.getInt("nextUid"));
        v.putInt("nextUid", n + 1);
        return n;
    }

    /** A random new companion (hire candidate). */
    public static CompoundTag create(RandomSource r, int uid, int level) {
        CompoundTag t = new CompoundTag();
        t.putInt("uid", uid);
        t.putInt("name", r.nextInt(NAMES));
        t.putInt("pat", r.nextInt(PATTERNS));
        t.putInt("type", r.nextInt(MofuType.values().length));
        t.putInt("lv", Math.max(1, level));
        t.putInt("xp", 0);
        t.putInt("gw", level >= 12 ? MofuGear.Tier.BONE.ordinal() : MofuGear.Tier.BAMBOO.ordinal());
        t.putInt("gh", r.nextInt(3) == 0 ? MofuGear.Tier.BAMBOO.ordinal() : -1);
        t.putInt("ga", level >= 6 ? MofuGear.Tier.BAMBOO.ordinal() : -1);
        return t;
    }

    /** The free first companion chosen at the mofu elder. */
    public static CompoundTag createFirst(int uid, MofuType type, int pattern, String customName) {
        CompoundTag t = new CompoundTag();
        t.putInt("uid", uid);
        t.putInt("name", Math.floorMod(customName.hashCode(), NAMES));
        if (!customName.isBlank()) t.putString("cname", sanitizeName(customName));
        t.putInt("pat", Math.floorMod(pattern, PATTERNS));
        t.putInt("type", type.ordinal());
        t.putInt("lv", 1);
        t.putInt("xp", 0);
        t.putInt("gw", MofuGear.Tier.BAMBOO.ordinal());
        t.putInt("gh", -1);
        t.putInt("ga", MofuGear.Tier.BAMBOO.ordinal());
        return t;
    }

    public static String sanitizeName(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.trim().toCharArray()) {
            if (c >= ' ' && c != '§' && b.length() < 16) b.append(c);
        }
        return b.toString().trim();
    }

    public static int hireCost(CompoundTag rec) {
        return 400 + 120 * Math.max(1, rec.getInt("lv"));
    }

    public static int trainCost(CompoundTag rec) {
        return 150 + 60 * level(rec);
    }

    public static int retrainCost(CompoundTag rec) {
        return 300 + 40 * level(rec);
    }

    public static Component name(CompoundTag rec) {
        if (rec.contains("cname", Tag.TAG_STRING) && !rec.getString("cname").isBlank()) return Component.literal(rec.getString("cname"));
        return Component.translatable("mofu.huntcraft.name." + Math.floorMod(rec.getInt("name"), NAMES));
    }

    public static MofuType type(CompoundTag rec) {
        return MofuType.byId(rec.getInt("type"));
    }

    public static int level(CompoundTag rec) {
        return Math.max(1, rec.getInt("lv"));
    }

    public static int xpForNext(int level) {
        return 40 + level * 25;
    }

    public static boolean onExpedition(CompoundTag rec) {
        return rec.contains("exp", Tag.TAG_COMPOUND);
    }

    public static boolean fainted(CompoundTag rec, long gameTime) {
        return rec.getLong("faint") > gameTime;
    }

    public static boolean hasSkill(CompoundTag rec, MofuSkill s) {
        return s.has(type(rec), level(rec));
    }

    /** Adds XP; returns the number of levels gained. */
    public static int addXp(CompoundTag rec, int amount) {
        int lv = level(rec);
        if (lv >= MAX_LEVEL) return 0;
        int xp = rec.getInt("xp") + Math.max(0, amount);
        int gained = 0;
        while (lv < MAX_LEVEL && xp >= xpForNext(lv)) {
            xp -= xpForNext(lv);
            lv++;
            gained++;
        }
        if (lv >= MAX_LEVEL) xp = 0;
        rec.putInt("lv", lv);
        rec.putInt("xp", xp);
        return gained;
    }

    // ------------------------------------------------------------------ derived combat stats
    private static float veteran(MofuType t, int lv) {
        return MofuSkill.VETERAN.has(t, lv) ? 1.1F : 1.0F;
    }

    public static float maxHealth(MofuType type, int lv, int armorTier) {
        float hp = 30.0F + lv * 3.0F + (armorTier >= 0 ? MofuGear.Tier.byIndex(armorTier).armorHp : 0);
        return hp * veteran(type, lv);
    }

    public static float armor(MofuType type, int lv, int helmTier, int armorTier) {
        float a = 2.0F + lv * 0.3F;
        if (helmTier >= 0) a += MofuGear.Tier.byIndex(helmTier).helmDef;
        if (armorTier >= 0) a += MofuGear.Tier.byIndex(armorTier).armorDef;
        return Math.min(30.0F, a * veteran(type, lv));
    }

    public static float meleeDamage(MofuType type, int lv, int weaponTier) {
        float base = 2.5F + lv * 0.25F + (weaponTier >= 0 ? MofuGear.Tier.byIndex(weaponTier).attack : 0);
        float k = type == MofuType.FIGHTER ? 1.3F : 0.8F;
        return base * k * veteran(type, lv);
    }

    public static float bombDamage(MofuType type, int lv, int weaponTier) {
        float base = 6.0F + lv * 0.5F + (weaponTier >= 0 ? MofuGear.Tier.byIndex(weaponTier).attack * 1.1F : 0);
        return base * veteran(type, lv);
    }

    public static float healFraction(MofuType type, int lv) {
        float f = 0.25F + 0.005F * lv;
        if (MofuSkill.SOOTHING.has(type, lv)) f += 0.10F;
        return f;
    }

    /** Record-based shortcuts (screens). */
    public static float maxHealth(CompoundTag rec) {
        return maxHealth(type(rec), level(rec), MofuGear.equipped(rec, MofuGear.Slot.ARMOR));
    }

    public static float armor(CompoundTag rec) {
        return armor(type(rec), level(rec), MofuGear.equipped(rec, MofuGear.Slot.HELM), MofuGear.equipped(rec, MofuGear.Slot.ARMOR));
    }

    public static float attack(CompoundTag rec) {
        MofuType t = type(rec);
        int w = MofuGear.equipped(rec, MofuGear.Slot.WEAPON);
        return t == MofuType.BOMBER ? bombDamage(t, level(rec), w) : meleeDamage(t, level(rec), w);
    }
}
