package com.eternalrealm.monster.forge;

import com.eternalrealm.config.ERServerConfig;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.MonsterTable;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.field.Habitats;
import com.eternalrealm.world.forge.Realm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Field monsters. In アストリア they live in their fixed habitats ({@link Habitats}): twice per second every habitat a
 * player is near fills up to its population (quickly while it is half empty, then a monster every second or two, so a
 * killed one comes back), at the monster's own levels, now and then the field's elite instead, each at least
 * {@value #SPACING} blocks from any other monster (owner, 2026-11-12: 「敵の湧く間隔を5ブロックごとにしてもう少し
 * スポーンさせて」), anywhere over the habitat's spawn radius where {@link Habitats#spawnable} allows and never so far
 * from every player that it would be despawned at once (owner, 2026-11-12: 「フィールドの敵スポーン範囲を広くして、
 * 湧く数を今の３．５倍にして」: half as far out again, 3.5 times as many). Habitat monsters keep near the spot they
 * appeared at, walk back when they stray and give up a chase far from home. Every field monster is calm until a
 * player hurts it or comes within the aggro radius ({@link MonsterTempers}: 5 blocks). Elsewhere (a world without
 * アストリア) monsters of the area's table appear around each player: 14-56 blocks away on solid ground, up to 74 per
 * player and a cap per server. Monsters that ignore players in daylight (spiders, endermen) are pointed at a player in
 * sight once they may fight them.
 *
 * <p>Owner, 2026-11-14: 「たまに敵がわかないことがある。原因を突き止めて常に湧くようにして」. Causes found: about half of
 * the fields lay out of every habitat's reach (nothing ever appeared there; now monsters of the field roam around a
 * player there, {@link #tickRoamers}); a spot on a snow layer, a carpet or a path was never "solid" ({@link #standY}); monsters were spawned into chunks whose saved monsters had not loaded yet
 * (then the habitat was over-full for a while); vanilla bodies despawned on their own when the player was 32 blocks
 * away (now persistent: the habitat or the far-away count decides).</p>
 */
public final class MonsterSpawner {
    private static final int NEAR_RADIUS = 64;
    /** 3.5 times the 21 before 2026-11-12 (itself three times the original seven). */
    private static final int PER_PLAYER_CAP = 74;
    /** 3.5 times the 72 + 27 per player before 2026-11-12. */
    private static final int LEVEL_CAP_BASE = 252;
    private static final int LEVEL_CAP_PER_PLAYER = 95;
    private static final double MIN_DISTANCE = 14.0;
    /** Before 2026-11-12: 40. */
    private static final double MAX_DISTANCE = 56.0;
    /** Height difference to the player a spawn spot may have (hills and valleys). */
    private static final double MAX_HEIGHT_DIFFERENCE = 20.0;
    private static final int ATTEMPTS = 10;
    /** Chance that a normal monster comes with company (one or two more of its kind next to it). */
    private static final double PACK_CHANCE = 0.5;
    /** Spawns per player per round while far below the cap (the field fills up quickly again; before: 2). */
    private static final int BURST = 7;
    private static final int RECOUNT_TICKS = 100;
    private static final double DESPAWN_DISTANCE = 96.0;

    /** Persistent tag naming a monster's habitat. */
    public static final String HABITAT_TAG = "er_habitat";
    /** Spawns per habitat per round while it is less than half full (before 2026-11-12: 2). */
    private static final int FILL_BURST = 7;
    /**
     * Ticks between spawns of a habitat that is at least half full (a killed monster is back after this; before
     * 2026-11-12: 80, for a habitat a third as crowded).
     */
    private static final int RESPAWN_TICKS = 23;
    /** A monster never appears farther than this from every player (it would be despawned at the next count). */
    private static final double SPAWN_REACH = 80.0;
    /** Monsters never appear closer than this to a player. */
    private static final double PLAYER_CLEARANCE = 9.0;
    /**
     * Blocks between a new habitat monster and any other monster (owner, 2026-11-12: 「敵の湧く間隔を5ブロックごとに」;
     * before: 4).
     */
    static final double SPACING = 5.0;
    /** Persistent tags of the spot a habitat monster appeared at (it keeps near it). */
    public static final String HOME_X_TAG = "er_home_x";
    public static final String HOME_Z_TAG = "er_home_z";
    /** A habitat monster without a target this far from its spot walks back to it. */
    private static final double HOME_DRIFT = 12.0;

    private final MinecraftServer server;
    private final SplittableRandom random = new SplittableRandom();
    private final java.util.Map<String, Long> nextHabitatSpawn = new java.util.HashMap<>();
    private int levelCount;
    private long lastCount = Long.MIN_VALUE;

    public MonsterSpawner(MinecraftServer server) {
        this.server = server;
    }

    public void tick(long tick) {
        if (tick % 10 != 7 || !ERServerConfig.MONSTER_SPAWNING.get()) {
            return;
        }
        ServerLevel level = Realm.level(server);
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.isAlive()) {
                players.add(player);
            }
        }
        if (players.isEmpty()) {
            return;
        }
        if (tick - lastCount >= RECOUNT_TICKS) {
            levelCount = 0;
            List<Entity> far = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (!MonsterTags.isMonster(entity)) {
                    continue;
                }
                if (isFieldMonster(entity) && level.getNearestPlayer(entity, DESPAWN_DISTANCE) == null) {
                    far.add(entity);
                } else {
                    levelCount++;
                }
            }
            for (Entity entity : far) {
                entity.discard(); // field monsters nobody is near go away (animated bodies never despawn by themselves)
            }
            lastCount = tick;
        }
        int cap = LEVEL_CAP_BASE + LEVEL_CAP_PER_PLAYER * players.size();
        if (level.dimension() == Realm.ASTORIA) {
            for (ServerPlayer player : players) {
                aggro(player, level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(NEAR_RADIUS),
                        MonsterTags::isMonster));
            }
            tickHabitats(level, players, tick, cap);
            tickRoamers(level, players, tick, cap);
            return;
        }
        java.util.Set<Mob> watched = new java.util.LinkedHashSet<>();
        for (ServerPlayer player : players) {
            List<Mob> near = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(NEAR_RADIUS),
                    MonsterTags::isMonster);
            aggro(player, near);
            watched.addAll(near);
            if (levelCount >= cap || near.size() >= PER_PLAYER_CAP || Realm.inCapital(level, player.getX(), player.getZ())) {
                continue;
            }
            int room = Math.min(cap - levelCount, PER_PLAYER_CAP - near.size());
            int rounds = near.size() < PER_PLAYER_CAP / 2 ? BURST : 1;
            for (int round = 0; round < rounds && room > 0; round++) {
                int spawned = trySpawn(level, player, room);
                levelCount += spawned;
                room -= spawned;
            }
        }
        if (tick % MonsterTempers.LOOK_STEP == 7) {
            MonsterTempers.look(level, new ArrayList<>(watched), players);
        }
    }

    /** Spawned in the field (not a dungeon wave or a boss summon). */
    private static boolean isFieldMonster(Entity entity) {
        return !entity.getPersistentData().contains("er_instance") && !entity.getPersistentData().hasUUID("er_fight");
    }

    /**
     * Monsters that may fight {@code player} (provoked, or without a temper) and stand within the aggro radius with
     * the player in sight turn on them (some vanilla monsters ignore players in daylight).
     */
    private static void aggro(ServerPlayer player, List<Mob> near) {
        if (player.isCreative()) {
            return;
        }
        double range = Math.max(ERServerConfig.AGGRO_RADIUS.get(), 1.0);
        for (Mob mob : near) {
            if (mob.getTarget() == null && mob.distanceToSqr(player) < range * range && mob.hasLineOfSight(player)
                    && MonsterTempers.mayFight(mob, player)) {
                mob.setTarget(player);
            }
        }
    }

    /** Spawns a monster (and maybe its pack, up to {@code room} in all) near the player; returns how many. */
    private int trySpawn(ServerLevel level, ServerPlayer player, int room) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            if (Realm.inCapital(level, x + 0.5, z + 0.5) || !level.hasChunkAt(new BlockPos(x, player.getBlockY(), z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinBuildHeight() + 1 || Math.abs(y - player.getY()) > MAX_HEIGHT_DIFFERENCE) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                    || !level.getFluidState(pos).isEmpty()) {
                continue;
            }
            AreaDefinition area = Realm.areaAt(level, x + 0.5, z + 0.5);
            MonsterTable table = Monsters.tableFor(area);
            MonsterSpawn spawn = table.pick(area.levelRange(), random);
            EntityType<?> type = EntityType.byString(spawn.template().baseEntityType()).orElse(null);
            if (type == null) {
                continue;
            }
            double spawnY = type == EntityType.PHANTOM ? y + 6.0 : y;
            if (!level.noCollision(type.getAABB(x + 0.5, spawnY, z + 0.5))) {
                continue;
            }
            java.util.Optional<Mob> first = MonsterFactory.spawn(level, spawn, x + 0.5, spawnY, z + 0.5,
                    (float) (random.nextDouble() * 360.0));
            if (first.isEmpty()) {
                return 0;
            }
            MonsterTempers.makeCalm(first.get());
            int spawned = 1;
            if (spawn.template().role() == com.eternalrealm.monster.MonsterRole.NORMAL && room > 1
                    && random.nextDouble() < PACK_CHANCE) {
                int company = 1 + (room > 2 && random.nextBoolean() ? 1 : 0);
                for (int i = 0; i < company; i++) {
                    int cx = x + random.nextInt(-3, 4);
                    int cz = z + random.nextInt(-3, 4);
                    int cy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
                    BlockPos at = new BlockPos(cx, cy, cz);
                    if (Math.abs(cy - y) > 3 || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)
                            || !level.getFluidState(at).isEmpty() || !level.noCollision(type.getAABB(cx + 0.5, cy, cz + 0.5))) {
                        continue;
                    }
                    MonsterSpawn mate = table.pick(area.levelRange(), random);
                    if (!mate.template().id().equals(spawn.template().id())) {
                        mate = new MonsterSpawn(spawn.template(), Math.max(spawn.template().levels().min(),
                                Math.min(spawn.template().levels().max(), spawn.level() + random.nextInt(-1, 2))));
                    }
                    java.util.Optional<Mob> companion = MonsterFactory.spawn(level, mate, cx + 0.5, cy, cz + 0.5,
                            (float) (random.nextDouble() * 360.0));
                    if (companion.isPresent()) {
                        MonsterTempers.makeCalm(companion.get());
                        spawned++;
                    }
                }
            }
            return spawned;
        }
        return 0;
    }

    // ------------------------------------------------------------------ habitats

    private void tickHabitats(ServerLevel level, List<ServerPlayer> players, long tick, int cap) {
        for (Habitats.Habitat habitat : Habitats.all()) {
            if (!anyPlayerNear(players, habitat)) {
                continue;
            }
            int reach = habitat.spawnRadius() + Habitats.LEASH_MARGIN + 16;
            List<Mob> home = level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(
                    habitat.x() - reach, level.getMinBuildHeight(), habitat.z() - reach,
                    habitat.x() + reach, level.getMaxBuildHeight(), habitat.z() + reach),
                    mob -> habitat.id().equals(mob.getPersistentData().getString(HABITAT_TAG)) && mob.isAlive());
            if (tick % MonsterTempers.LOOK_STEP == 7) {
                leash(habitat, home);
                MonsterTempers.look(level, home, players);
            }
            int missing = habitat.population() - home.size();
            if (missing <= 0 || levelCount >= cap || tick < nextHabitatSpawn.getOrDefault(habitat.id(), Long.MIN_VALUE)
                    || !level.isPositionEntityTicking(new BlockPos(habitat.x(), 64, habitat.z()))) {
                continue; // (until the centre's chunk ticks, its saved monsters are not counted yet)
            }
            boolean filling = home.size() * 2 < habitat.population();
            int spawns = filling ? Math.min(FILL_BURST, missing) : 1;
            for (int i = 0; i < spawns && levelCount < cap; i++) {
                if (spawnInHabitat(level, habitat, players)) {
                    levelCount++;
                }
            }
            nextHabitatSpawn.put(habitat.id(), tick + (filling ? 10 : RESPAWN_TICKS + random.nextInt(12)));
        }
    }

    private static boolean anyPlayerNear(List<ServerPlayer> players, Habitats.Habitat habitat) {
        double reach = Habitats.ACTIVE_RADIUS + habitat.spawnRadius();
        for (ServerPlayer player : players) {
            double dx = player.getX() - habitat.x();
            double dz = player.getZ() - habitat.z();
            if (dx * dx + dz * dz <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strays walk back to the spot they appeared at (so the monsters stay spread over the habitat instead of bunching
     * in its middle); a chase that leads far from home is given up.
     */
    private static void leash(Habitats.Habitat habitat, List<Mob> home) {
        for (Mob mob : home) {
            double distance = habitat.distanceTo(mob.getX(), mob.getZ());
            boolean chasing = mob.getTarget() != null && mob.getTarget().isAlive();
            double[] spot = homeSpot(mob, habitat);
            if (chasing && distance > habitat.spawnRadius() + Habitats.LEASH_MARGIN) {
                mob.setTarget(null);
                mob.getNavigation().moveTo(spot[0], mob.getY(), spot[1], 1.1);
            } else if (!chasing && mob.getNavigation().isDone()
                    && (distance > habitat.spawnRadius() + Habitats.WANDER_MARGIN
                    || Math.hypot(mob.getX() - spot[0], mob.getZ() - spot[1]) > HOME_DRIFT)) {
                mob.getNavigation().moveTo(spot[0], mob.getY(), spot[1], 1.0);
            }
        }
    }

    /**
     * Where a habitat monster appeared. A monster from before the spots were kept (and before habitats were calm)
     * gets its spot now: where it stands, pulled inside the habitat; it becomes calm like the others.
     */
    private static double[] homeSpot(Mob mob, Habitats.Habitat habitat) {
        net.minecraft.nbt.CompoundTag data = mob.getPersistentData();
        if (!data.contains(HOME_X_TAG) || !data.contains(HOME_Z_TAG)) {
            double dx = mob.getX() - habitat.x();
            double dz = mob.getZ() - habitat.z();
            double distance = Math.hypot(dx, dz);
            double keep = distance <= habitat.spawnRadius() - 2.0 || distance < 1.0e-6 ? 1.0
                    : (habitat.spawnRadius() - 2.0) / distance;
            data.putInt(HOME_X_TAG, Mth.floor(habitat.x() + dx * keep));
            data.putInt(HOME_Z_TAG, Mth.floor(habitat.z() + dz * keep));
            MonsterTempers.makeCalm(mob);
        }
        return new double[]{data.getInt(HOME_X_TAG) + 0.5, data.getInt(HOME_Z_TAG) + 0.5};
    }

    /** One monster of the habitat (now and then the field's elite) at a free spot inside it. */
    private boolean spawnInHabitat(ServerLevel level, Habitats.Habitat habitat, List<ServerPlayer> players) {
        MonsterTemplate template = Monsters.byId(habitat.monster()).orElse(null);
        if (template == null) {
            return false;
        }
        MonsterTable table = Monsters.tableFor(Realm.areaAt(level, habitat.x() + 0.5, habitat.z() + 0.5));
        if (table.eliteChance() > 0.0 && random.nextDouble() < table.eliteChance() && !table.eliteEntries().isEmpty()) {
            template = table.eliteEntries().get(random.nextInt(table.eliteEntries().size())).template();
        }
        EntityType<?> type = EntityType.byString(template.baseEntityType()).orElse(null);
        if (type == null) {
            return false;
        }
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(random.nextDouble()) * habitat.spawnRadius();
            int x = Mth.floor(habitat.x() + Math.cos(angle) * distance);
            int z = Mth.floor(habitat.z() + Math.sin(angle) * distance);
            if (!Habitats.spawnable(habitat, x + 0.5, z + 0.5) || !level.hasChunkAt(new BlockPos(x, 64, z))
                    || tooCloseToPlayer(players, x + 0.5, z + 0.5) || !withinReach(players, x + 0.5, z + 0.5)) {
                continue;
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!level.isPositionEntityTicking(new BlockPos(x, top, z)) || crowded(level, x + 0.5, top, z + 0.5)) {
                continue; // monsters saved in that chunk may not be back yet (they would be counted twice)
            }
            double ground = standY(level, type, x, top, z);
            if (Double.isNaN(ground)) {
                continue;
            }
            double spawnY = type == EntityType.PHANTOM ? ground + 6.0 : ground;
            MonsterSpawn spawn = new MonsterSpawn(template, template.levels().random(random));
            java.util.Optional<Mob> mob = MonsterFactory.spawn(level, spawn, x + 0.5, spawnY, z + 0.5,
                    (float) (random.nextDouble() * 360.0));
            if (mob.isEmpty()) {
                return false;
            }
            mob.get().setPersistenceRequired(); // vanilla bodies would despawn on their own: the habitat decides
            mob.get().getPersistentData().putString(HABITAT_TAG, habitat.id());
            mob.get().getPersistentData().putInt(HOME_X_TAG, x);
            mob.get().getPersistentData().putInt(HOME_Z_TAG, z);
            MonsterTempers.makeCalm(mob.get());
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ roaming

    /** Persistent tag of a monster roaming the open field around players (not of a habitat). */
    public static final String ROAMER_TAG = "er_roamer";
    /** Roaming monsters around a player are counted within this. */
    private static final double ROAM_COUNT_RADIUS = 64.0;
    /** ... and appear this far from them (seen, never on top of them). */
    private static final double ROAM_MIN = 18.0;
    private static final double ROAM_MAX = 52.0;
    private static final int ROAM_BURST = 3;
    private static final int ROAM_TICKS = 40;
    private final java.util.Map<java.util.UUID, Long> nextRoam = new java.util.HashMap<>();

    /**
     * Where no habitat is near (about half of the fields), monsters of the field roam around each player: up to
     * {@link Habitats#roamersWanted} within {@value #ROAM_COUNT_RADIUS} blocks, appearing a few at a time
     * {@value #ROAM_MIN}-{@value #ROAM_MAX} blocks away, calm until provoked like the habitats' monsters. They go when
     * nobody is near (the count's discard), so the field around a player is never empty.
     */
    private void tickRoamers(ServerLevel level, List<ServerPlayer> players, long tick, int cap) {
        for (ServerPlayer player : players) {
            int wanted = Habitats.roamersWanted(player.getX(), player.getZ());
            if (wanted <= 0 || levelCount >= cap || tick < nextRoam.getOrDefault(player.getUUID(), Long.MIN_VALUE)) {
                continue;
            }
            int around = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(ROAM_COUNT_RADIUS, 32.0,
                    ROAM_COUNT_RADIUS), mob -> mob.isAlive() && mob.getPersistentData().getBoolean(ROAMER_TAG)).size();
            int missing = wanted - around;
            if (missing <= 0) {
                continue;
            }
            int spawns = Math.min(missing, around * 2 < wanted ? ROAM_BURST * 2 : ROAM_BURST);
            for (int i = 0; i < spawns && levelCount < cap; i++) {
                if (spawnRoamer(level, player, players)) {
                    levelCount++;
                }
            }
            nextRoam.put(player.getUUID(), tick + ROAM_TICKS + random.nextInt(20));
        }
        if (tick % 1200 == 7) {
            nextRoam.keySet().removeIf(id -> level.getServer().getPlayerList().getPlayer(id) == null);
        }
    }

    private boolean spawnRoamer(ServerLevel level, ServerPlayer player, List<ServerPlayer> players) {
        for (int attempt = 0; attempt < ATTEMPTS * 2; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = ROAM_MIN + random.nextDouble() * (ROAM_MAX - ROAM_MIN);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            if (!Habitats.roamable(x + 0.5, z + 0.5) || tooCloseToPlayer(players, x + 0.5, z + 0.5)) {
                continue;
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(top - player.getY()) > MAX_HEIGHT_DIFFERENCE
                    || !level.isPositionEntityTicking(new BlockPos(x, top, z)) || crowded(level, x + 0.5, top, z + 0.5)) {
                continue;
            }
            AreaDefinition area = Realm.areaAt(level, x + 0.5, z + 0.5);
            MonsterTable table = Monsters.tableFor(area);
            MonsterSpawn spawn = table.pick(area.levelRange(), random);
            EntityType<?> type = EntityType.byString(spawn.template().baseEntityType()).orElse(null);
            if (type == null) {
                continue;
            }
            double ground = standY(level, type, x, top, z);
            if (Double.isNaN(ground)) {
                continue;
            }
            double spawnY = type == EntityType.PHANTOM ? ground + 6.0 : ground;
            java.util.Optional<Mob> mob = MonsterFactory.spawn(level, spawn, x + 0.5, spawnY, z + 0.5,
                    (float) (random.nextDouble() * 360.0));
            if (mob.isEmpty()) {
                return false;
            }
            mob.get().setPersistenceRequired();
            mob.get().getPersistentData().putBoolean(ROAMER_TAG, true);
            mob.get().getPersistentData().putInt(HOME_X_TAG, x);
            mob.get().getPersistentData().putInt(HOME_Z_TAG, z);
            MonsterTempers.makeCalm(mob.get());
            return true;
        }
        return false;
    }

    /**
     * The height a monster of {@code type} stands at on the surface of column (x, z) ({@code top}: the first block over
     * the ground), or NaN: on a floor between half a block and a block tall (a path or farmland counts, a fence not),
     * on a thin layer lying on it (snow, a carpet), never on or in water, with room for its body.
     */
    static double standY(ServerLevel level, EntityType<?> type, int x, int top, int z) {
        BlockPos feet = new BlockPos(x, top, z);
        BlockPos under = feet.below();
        if (top <= level.getMinBuildHeight() + 1 || !level.getFluidState(feet).isEmpty()
                || !level.getFluidState(under).isEmpty()) {
            return Double.NaN;
        }
        net.minecraft.world.phys.shapes.VoxelShape floor = level.getBlockState(under).getCollisionShape(level, under);
        if (floor.isEmpty() || floor.max(Direction.Axis.Y) < 0.5 || floor.max(Direction.Axis.Y) > 1.0) {
            return Double.NaN;
        }
        double base = top - 1 + floor.max(Direction.Axis.Y);
        net.minecraft.world.phys.shapes.VoxelShape layer = level.getBlockState(feet).getCollisionShape(level, feet);
        if (!layer.isEmpty()) {
            if (layer.min(Direction.Axis.Y) > 0.01 || layer.max(Direction.Axis.Y) > 0.5) {
                return Double.NaN;
            }
            base = top + layer.max(Direction.Axis.Y);
        }
        return level.noCollision(type.getAABB(x + 0.5, base + 0.01, z + 0.5)) ? base + 0.01 : Double.NaN;
    }

    /** Another monster within {@link #SPACING} blocks of (x, y, z). */
    public static boolean crowded(ServerLevel level, double x, double y, double z) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(x - SPACING, y - 4.0, z - SPACING,
                x + SPACING, y + 6.0, z + SPACING);
        return !level.getEntitiesOfClass(Mob.class, box, mob -> MonsterTags.isMonster(mob) && mob.isAlive()
                && Math.hypot(mob.getX() - x, mob.getZ() - z) < SPACING).isEmpty();
    }

    /** Some player is within {@link #SPAWN_REACH} of (x, z) (so the monster is not despawned at once). */
    private static boolean withinReach(List<ServerPlayer> players, double x, double z) {
        for (ServerPlayer player : players) {
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            if (dx * dx + dz * dz <= SPAWN_REACH * SPAWN_REACH) {
                return true;
            }
        }
        return false;
    }

    private static boolean tooCloseToPlayer(List<ServerPlayer> players, double x, double z) {
        for (ServerPlayer player : players) {
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            if (dx * dx + dz * dz < PLAYER_CLEARANCE * PLAYER_CLEARANCE) {
                return true;
            }
        }
        return false;
    }
}
