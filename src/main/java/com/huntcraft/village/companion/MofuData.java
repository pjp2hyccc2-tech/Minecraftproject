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
 * mofu:   list of {uid, name, cname?, pat, type, lv, xp, faint(gameTime), helm, armor, exp?:{dest, start(ms), prog, need}}
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
    public static final int HELMS = 3;
    public static final int ARMORS = 3;
    public static final long EXPEDITION_MS = 20L * 60L * 1000L;
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

    /** A random new companion (candidate). */
    public static CompoundTag create(RandomSource r, int uid, int level) {
        CompoundTag t = new CompoundTag();
        t.putInt("uid", uid);
        t.putInt("name", r.nextInt(NAMES));
        t.putInt("pat", r.nextInt(PATTERNS));
        t.putInt("type", r.nextInt(MofuType.values().length));
        t.putInt("lv", Math.max(1, level));
        t.putInt("xp", 0);
        t.putInt("helm", r.nextInt(4) == 0 ? 1 : 0);
        t.putInt("armor", 0);
        return t;
    }

    public static int hireCost(CompoundTag rec) {
        return 400 + 120 * Math.max(1, rec.getInt("lv"));
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
    public static float maxHealth(int lv) {
        return 30.0F + lv * 3.0F;
    }

    public static float armor(int lv) {
        return 2.0F + lv * 0.3F;
    }

    public static float meleeDamage(int lv, MofuType type) {
        float base = 3.0F + lv * 0.35F;
        return type == MofuType.FIGHTER ? base * 1.35F : base * 0.8F;
    }

    public static float bombDamage(int lv) {
        return 7.0F + lv * 0.6F;
    }

    public static float healFraction(int lv) {
        return 0.25F + 0.005F * lv;
    }
}
