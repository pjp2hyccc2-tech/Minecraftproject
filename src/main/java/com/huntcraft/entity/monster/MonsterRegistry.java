package com.huntcraft.entity.monster;

import com.huntcraft.core.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registry of all hunt monsters by string id ("ignivald", "jagraptor"...).
 * Monster modules call {@link #register} in their init(); quests / notes look monsters up here.
 */
public final class MonsterRegistry {
    public record Entry(String id, Supplier<? extends EntityType<? extends HuntMonster>> type, MonsterProfile profile) {
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    private MonsterRegistry() {
    }

    public static void register(String id, Supplier<? extends EntityType<? extends HuntMonster>> type, MonsterProfile profile) {
        ENTRIES.put(id, new Entry(id, type, profile));
    }

    @Nullable
    public static Entry get(String id) {
        return ENTRIES.get(id);
    }

    @Nullable
    public static MonsterProfile profile(String id) {
        Entry e = ENTRIES.get(id);
        return e == null ? null : e.profile();
    }

    public static List<Entry> all() {
        return Collections.unmodifiableList(new ArrayList<>(ENTRIES.values()));
    }

    public static List<Entry> large() {
        List<Entry> l = new ArrayList<>();
        for (Entry e : ENTRIES.values()) if (e.profile().large) l.add(e);
        return l;
    }

    public static List<Entry> small() {
        List<Entry> l = new ArrayList<>();
        for (Entry e : ENTRIES.values()) if (!e.profile().large) l.add(e);
        return l;
    }

    /**
     * Spawns a monster of the given id. Returns null if unknown.
     *
     * @param questId quest instance id the monster belongs to (may be null)
     */
    @Nullable
    public static HuntMonster spawn(ServerLevel level, String id, BlockPos pos, Rank rank, @Nullable String questId) {
        Entry e = ENTRIES.get(id);
        if (e == null) return null;
        HuntMonster m = e.type().get().create(level);
        if (m == null) return null;
        m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0);
        m.setRank(rank);
        m.rollScale(level.random);
        if (questId != null) m.setQuestId(questId);
        m.setHomePos(pos);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        m.setRank(rank);
        level.addFreshEntity(m);
        return m;
    }
}
