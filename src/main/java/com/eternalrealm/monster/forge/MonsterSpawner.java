package com.eternalrealm.monster.forge;

import com.eternalrealm.config.ERServerConfig;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.MonsterTable;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.world.AreaDefinition;
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
 * Field monsters around the players: once per second each player outside the capital may get one monster of the
 * area's table (level from the area's range) 18-34 blocks away on solid ground, up to a few per player and a cap per
 * server. Monsters that ignore players in daylight (spiders, endermen) are pointed at a player in sight.
 */
public final class MonsterSpawner {
    private static final int NEAR_RADIUS = 40;
    private static final int PER_PLAYER_CAP = 7;
    private static final int LEVEL_CAP_BASE = 24;
    private static final int LEVEL_CAP_PER_PLAYER = 9;
    private static final double MIN_DISTANCE = 18.0;
    private static final double MAX_DISTANCE = 34.0;
    private static final double AGGRO_RANGE_SQR = 12.0 * 12.0;
    private static final int RECOUNT_TICKS = 100;
    private static final double DESPAWN_DISTANCE = 96.0;

    private final MinecraftServer server;
    private final SplittableRandom random = new SplittableRandom();
    private int levelCount;
    private long lastCount = Long.MIN_VALUE;

    public MonsterSpawner(MinecraftServer server) {
        this.server = server;
    }

    public void tick(long tick) {
        if (tick % 20 != 7 || !ERServerConfig.MONSTER_SPAWNING.get()) {
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
        for (ServerPlayer player : players) {
            List<Mob> near = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(NEAR_RADIUS),
                    MonsterTags::isMonster);
            aggro(player, near);
            if (levelCount >= cap || near.size() >= PER_PLAYER_CAP || Realm.inCapital(level, player.getX(), player.getZ())) {
                continue;
            }
            if (trySpawn(level, player)) {
                levelCount++;
            }
        }
    }

    /** Spawned in the field (not a dungeon wave or a boss summon). */
    private static boolean isFieldMonster(Entity entity) {
        return !entity.getPersistentData().contains("er_instance") && !entity.getPersistentData().hasUUID("er_fight");
    }

    private static void aggro(ServerPlayer player, List<Mob> near) {
        if (player.isCreative()) {
            return;
        }
        for (Mob mob : near) {
            if (mob.getTarget() == null && mob.distanceToSqr(player) < AGGRO_RANGE_SQR && mob.hasLineOfSight(player)) {
                mob.setTarget(player);
            }
        }
    }

    private boolean trySpawn(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 4; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            if (Realm.inCapital(level, x + 0.5, z + 0.5) || !level.hasChunkAt(new BlockPos(x, player.getBlockY(), z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinBuildHeight() + 1 || Math.abs(y - player.getY()) > 12.0) {
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
            return MonsterFactory.spawn(level, spawn, x + 0.5, spawnY, z + 0.5,
                    (float) (random.nextDouble() * 360.0)).isPresent();
        }
        return false;
    }
}
