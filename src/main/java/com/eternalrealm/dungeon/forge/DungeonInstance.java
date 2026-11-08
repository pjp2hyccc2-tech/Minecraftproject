package com.eternalrealm.dungeon.forge;

import com.eternalrealm.boss.Bosses;
import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.boss.forge.BossFight;
import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockFill;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.BlockRole;
import com.eternalrealm.dungeon.Door;
import com.eternalrealm.dungeon.DungeonLayout;
import com.eternalrealm.dungeon.DungeonRun;
import com.eternalrealm.dungeon.DungeonRunEvent;
import com.eternalrealm.dungeon.DungeonTheme;
import com.eternalrealm.dungeon.InstanceSlot;
import com.eternalrealm.dungeon.MonsterCount;
import com.eternalrealm.dungeon.RoomLayout;
import com.eternalrealm.dungeon.RunState;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.monster.forge.MonsterFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One running instance: builds its layout a slice per tick, brings the members in, spawns the waves, opens the
 * doors, starts the boss fight and finally sends everyone back to where they came from and clears the blocks.
 */
final class DungeonInstance {
    static final String INSTANCE = "er_instance";
    static final String ROOM = "er_room";
    private static final int BLOCKS_PER_TICK = 6000;
    private static final int EXIT_DELAY_TICKS = 20 * 20;
    private static final int FAIL_EXIT_DELAY_TICKS = 5 * 20;

    record Origin(ResourceKey<Level> dimension, Vec3 position, float yaw) {
    }

    final String instanceId = UUID.randomUUID().toString().substring(0, 8);
    final String contentId;
    final String nameKey;
    final int areaId;
    final int recommendedLevel;
    final Difficulty difficulty;
    final DungeonRun run;
    final DungeonLayout layout;
    final InstanceSlot slot;
    final ServerLevel level;
    final BlockPos origin;
    final Map<UUID, Origin> returns = new HashMap<>();
    private final DungeonService service;
    private final List<BlockFill> plan;
    private final Set<UUID> spawned = new HashSet<>();
    private int fillIndex;
    private long fillOffset;
    private boolean clearing;
    private int clearIndex;
    private long clearOffset;
    private BossFight fight;
    /** The traps (null until built, or when disabled in the server config). */
    private DungeonHazards hazards;
    /** Run events of the killing blow (the clear) held back until the boss has finished collapsing. */
    private List<DungeonRunEvent> afterCollapse = List.of();
    private long exitAt = -1;
    private long now;
    private boolean closed;

    DungeonInstance(DungeonService service, String contentId, String nameKey, int areaId, int recommendedLevel,
                    Difficulty difficulty, DungeonRun run, DungeonLayout layout, InstanceSlot slot, ServerLevel level,
                    Map<UUID, Origin> returns) {
        this.service = service;
        this.contentId = contentId;
        this.nameKey = nameKey;
        this.areaId = areaId;
        this.recommendedLevel = recommendedLevel;
        this.difficulty = difficulty;
        this.run = run;
        this.layout = layout;
        this.slot = slot;
        this.level = level;
        this.origin = DungeonLevels.origin(level, slot);
        this.returns.putAll(returns);
        this.plan = layout.buildPlan();
    }

    Optional<BossFight> fight() {
        return Optional.ofNullable(fight);
    }

    boolean isClosed() {
        return closed;
    }

    // ------------------------------------------------------------------ tick

    void tick(long tick) {
        now = tick;
        if (closed) {
            return;
        }
        if (clearing) {
            clearSlice();
            return;
        }
        RunState state = run.state();
        if (state == RunState.BUILDING) {
            if (buildSlice()) {
                handle(run.onBuilt());
                if (trapsEnabled()) {
                    hazards = DungeonHazards.forInstance(level, layout, origin, difficulty);
                }
                for (UUID member : run.members()) {
                    ServerPlayer player = service.player(member);
                    if (player != null) {
                        bringIn(player);
                    }
                }
                handle(run.onStart(now));
            }
            return;
        }
        if (state.isRunning()) {
            handle(run.tick(now));
            if (fight != null) {
                fight.tick(now);
            }
            if (hazards != null) {
                hazards.tick(now, trapVictims());
            }
            pruneSpawned();
        }
        if (exitAt >= 0 && now >= exitAt) {
            close();
        } else if (exitAt >= 0 && (exitAt - now) % 100 == 0 && exitAt - now > 0) {
            tellAll(Component.translatable("message.eternalrealm.dungeon.leaving", (exitAt - now) / 20)
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Places the next slice of the build plan. @return whether everything is placed */
    private boolean buildSlice() {
        int budget = BLOCKS_PER_TICK;
        DungeonTheme theme = layout.theme();
        while (fillIndex < plan.size() && budget > 0) {
            BlockFill fill = plan.get(fillIndex);
            BlockBox box = fill.box();
            long volume = box.volume();
            while (fillOffset < volume && budget > 0) {
                int dx = (int) (fillOffset % box.sizeX());
                int dz = (int) ((fillOffset / box.sizeX()) % box.sizeZ());
                int dy = (int) (fillOffset / ((long) box.sizeX() * box.sizeZ()));
                int x = origin.getX() + box.minX() + dx;
                int y = origin.getY() + box.minY() + dy;
                int z = origin.getZ() + box.minZ() + dz;
                BlockState state = DungeonPalette.block(theme, fill.role(), x, y, z);
                level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
                fillOffset++;
                budget--;
            }
            if (fillOffset >= volume) {
                fillIndex++;
                fillOffset = 0;
            }
        }
        return fillIndex >= plan.size();
    }

    /** Clears the occupied boxes a slice per tick, then releases the slot. */
    private void clearSlice() {
        List<BlockBox> boxes = layout.occupiedBoxes();
        int budget = BLOCKS_PER_TICK * 2;
        while (clearIndex < boxes.size() && budget > 0) {
            BlockBox box = boxes.get(clearIndex);
            long volume = box.volume();
            while (clearOffset < volume && budget > 0) {
                int dx = (int) (clearOffset % box.sizeX());
                int dz = (int) ((clearOffset / box.sizeX()) % box.sizeZ());
                int dy = (int) (clearOffset / ((long) box.sizeX() * box.sizeZ()));
                level.setBlock(new BlockPos(origin.getX() + box.minX() + dx, origin.getY() + box.minY() + dy,
                        origin.getZ() + box.minZ() + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                clearOffset++;
                budget--;
            }
            if (clearOffset >= volume) {
                clearIndex++;
                clearOffset = 0;
            }
        }
        if (clearIndex >= boxes.size()) {
            closed = true;
            service.release(this);
        }
    }

    // ------------------------------------------------------------------ run events

    /** Events of member changes reported by the service. */
    void dispatch(List<DungeonRunEvent> events) {
        handle(events);
    }

    private void handle(List<DungeonRunEvent> events) {
        for (DungeonRunEvent event : events) {
            if (event instanceof DungeonRunEvent.Started started) {
                setDoor(layout.entranceDoor(), false);
                com.eternalrealm.server.ServerContext.current().ifPresent(context -> context.game().story().trigger(
                        new com.eternalrealm.story.StoryTrigger.DungeonEntered(contentId), null, onlineMembers()));
                tellAll(Component.translatable(DungeonRunEvent.Started.MESSAGE_KEY, Component.translatable(nameKey))
                        .withStyle(ChatFormatting.GOLD), false);
                long seconds = (started.deadlineTick() - started.startTick()) / 20;
                tellAll(Component.translatable("message.eternalrealm.dungeon.time_limit",
                        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)).withStyle(ChatFormatting.GRAY),
                        false);
            } else if (event instanceof DungeonRunEvent.SpawnWave wave) {
                spawnWave(wave);
                tellAll(Component.translatable(DungeonRunEvent.SpawnWave.MESSAGE_KEY, wave.wave() + 1, wave.waveCount())
                        .withStyle(ChatFormatting.RED), true);
            } else if (event instanceof DungeonRunEvent.OpenDoor open) {
                if (open.room() < layout.doors().size()) {
                    setDoor(layout.doors().get(open.room()), false);
                }
                tellAll(Component.translatable(DungeonRunEvent.OpenDoor.MESSAGE_KEY).withStyle(ChatFormatting.GREEN), true);
                playAll(com.eternalrealm.sound.ERSounds.DUNGEON_DOOR_OPEN.get(), 1.0F, 1.0F);
            } else if (event instanceof DungeonRunEvent.StartBoss start) {
                startBoss(start.bossId());
            } else if (event instanceof DungeonRunEvent.Cleared cleared) {
                long seconds = cleared.durationTicks() / 20;
                tellAll(Component.translatable(DungeonRunEvent.Cleared.MESSAGE_KEY, Component.translatable(nameKey),
                        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                service.onCleared(this, cleared);
                stopHazards();
                exitAt = now + EXIT_DELAY_TICKS;
            } else if (event instanceof DungeonRunEvent.Failed failed) {
                tellAll(Component.translatable(failed.reason().messageKey()).withStyle(ChatFormatting.RED), false);
                stopHazards();
                if (fight != null) {
                    fight.end();
                    fight.body().discard();
                }
                exitAt = now + FAIL_EXIT_DELAY_TICKS;
            }
        }
    }

    private void spawnWave(DungeonRunEvent.SpawnWave wave) {
        RoomLayout room = layout.room(wave.room());
        List<BlockPoint> points = room.spawnPoints();
        int index = 0;
        for (MonsterCount count : wave.monsters()) {
            MonsterTemplate template = Monsters.byId(count.templateId()).orElse(null);
            for (int i = 0; i < count.count(); i++) {
                BlockPos pos = DungeonLevels.at(origin, points.get(index++ % points.size()));
                Optional<Mob> mob = template == null ? Optional.empty() : MonsterFactory.create(level,
                        new MonsterSpawn(template, Mth.clamp(recommendedLevel, template.levels().min(),
                                template.levels().max())), true);
                if (mob.isEmpty()) {
                    handle(run.onMonsterKilled(wave.room())); // cannot spawn: count it so the wave completes
                    continue;
                }
                Mob body = mob.get();
                body.moveTo(pos.getX() + 0.5 + (i % 3) * 0.3, pos.getY(), pos.getZ() + 0.5, (float) (i * 47 % 360), 0.0F);
                body.getPersistentData().putString(INSTANCE, instanceId);
                body.getPersistentData().putInt(ROOM, wave.room());
                body.setPersistenceRequired();
                level.addFreshEntity(body);
                spawned.add(body.getUUID());
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, body.getX(), body.getY() + 0.5,
                        body.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
            }
        }
        playAll(com.eternalrealm.sound.ERSounds.DUNGEON_WAVE_START.get(), 0.9F, 1.0F);
    }

    /** A wave monster of this instance died or vanished. */
    void onMonsterGone(UUID entityId, int room) {
        if (spawned.remove(entityId)) {
            handle(run.onMonsterKilled(room));
        }
    }

    /** Wave monsters that vanished without dying (unloaded, discarded) still count. */
    private void pruneSpawned() {
        if (now % 40 != 0 || spawned.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(spawned)) {
            Entity entity = level.getEntity(id);
            if (entity == null) {
                onMonsterGone(id, run.currentRoom());
            }
        }
    }

    private void startBoss(String bossId) {
        Set<UUID> present = new HashSet<>(run.presentMembers());
        BlockPos spawn = DungeonLevels.at(origin, layout.bossSpawn());
        BlockBox floor = layout.bossArena().interior();
        Vec3 arenaCenter = new Vec3(origin.getX() + (floor.minX() + floor.maxX() + 1) / 2.0, spawn.getY(),
                origin.getZ() + (floor.minZ() + floor.maxZ() + 1) / 2.0);
        double arenaHalfSize = Math.min(floor.sizeX(), floor.sizeZ()) / 2.0;
        Optional<BossFight> started = Bosses.byId(bossId).flatMap(definition -> BossFight.start(level, definition,
                difficulty, spawn, arenaCenter, arenaHalfSize, present.isEmpty() ? run.members() : present,
                killed -> {
                    // the clock stops at the killing blow; rewards and messages wait for the collapse
                    List<DungeonRunEvent> events = run.onBossDefeated(now);
                    if (events.stream().anyMatch(event -> event instanceof DungeonRunEvent.Failed)) {
                        handle(events);
                    } else {
                        afterCollapse = events;
                    }
                },
                defeated -> {
                    service.onBossDefeated(this, defeated);
                    List<DungeonRunEvent> events = afterCollapse;
                    afterCollapse = List.of();
                    handle(events.isEmpty() ? run.onBossDefeated(now) : events);
                }));
        if (started.isEmpty()) {
            tellAll(Component.literal("Boss " + bossId + " could not be spawned").withStyle(ChatFormatting.RED), false);
            handle(run.onBossDefeated(now));
            return;
        }
        fight = started.get();
        tellAll(Component.translatable(DungeonRunEvent.StartBoss.MESSAGE_KEY, fight.body().getDisplayName())
                .withStyle(ChatFormatting.DARK_RED), false);
        com.eternalrealm.server.ServerContext.current().ifPresent(context -> context.game().story().trigger(
                new com.eternalrealm.story.StoryTrigger.BossEncounter(bossId), null, onlineMembers()));
    }

    // ------------------------------------------------------------------ members

    void bringIn(ServerPlayer player) {
        BlockPos entry = DungeonLevels.at(origin, layout.entry());
        player.teleportTo(level, entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5, 180.0F, 0.0F);
        player.setHealth(player.getMaxHealth());
    }

    void sendBack(ServerPlayer player) {
        Origin back = returns.get(player.getUUID());
        ServerLevel target = back == null ? null : level.getServer().getLevel(back.dimension());
        if (target == null) {
            target = level.getServer().overworld();
            BlockPos spawn = target.getSharedSpawnPos();
            player.teleportTo(target, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0.0F);
            return;
        }
        player.teleportTo(target, back.position().x, back.position().y, back.position().z, back.yaw(), 0.0F);
    }

    boolean isInside(ServerPlayer player) {
        return player.level() == level && Math.abs(player.getX() - (origin.getX() + 128)) < 200
                && Math.abs(player.getZ() - (origin.getZ() + 128)) < 200;
    }

    /** Sends the members out and starts clearing the blocks. */
    void close() {
        if (clearing || closed) {
            return;
        }
        if (run.state() != RunState.CLOSED) {
            handle(run.close(now));
        }
        stopHazards();
        if (fight != null) {
            fight.end();
            if (fight.body().isAlive()) {
                fight.body().discard();
            }
        }
        for (UUID member : run.members()) {
            ServerPlayer player = service.player(member);
            if (player != null && isInside(player)) {
                sendBack(player);
            }
        }
        for (UUID id : spawned) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        spawned.clear();
        // boss adds and anything else of the game left in the slot (animated bodies never despawn by themselves)
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(origin.getX(), origin.getY() - 8,
                origin.getZ(), origin.getX() + DungeonLayout.FOOTPRINT, origin.getY() + DungeonLayout.MAX_HEIGHT + 8,
                origin.getZ() + DungeonLayout.FOOTPRINT);
        for (Mob leftover : level.getEntitiesOfClass(Mob.class, area, mob -> mob.getTags().contains(
                com.eternalrealm.monster.forge.MonsterTags.TAG) || mob.getTags().contains(BossFight.TAG))) {
            leftover.discard();
        }
        clearing = true;
    }

    private void setDoor(Door door, boolean sealed) {
        BlockBox box = door.opening();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    level.setBlock(pos, sealed ? DungeonPalette.block(layout.theme(), BlockRole.DOOR, pos.getX(),
                            pos.getY(), pos.getZ()) : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    // ------------------------------------------------------------------ traps

    private static boolean trapsEnabled() {
        try {
            return com.eternalrealm.config.ERServerConfig.DUNGEON_TRAPS.get();
        } catch (IllegalStateException e) {
            return true; // config not loaded (tests)
        }
    }

    /** Members inside the instance who can be hurt by traps (spectators and creative players only watch). */
    private List<ServerPlayer> trapVictims() {
        List<ServerPlayer> victims = new ArrayList<>();
        for (ServerPlayer player : onlineMembers()) {
            if (player.isAlive() && !player.isSpectator() && !player.isCreative() && isInside(player)) {
                victims.add(player);
            }
        }
        return victims;
    }

    private void stopHazards() {
        if (hazards != null) {
            hazards.stop();
        }
    }

    /** The traps of this instance for the map (empty before the build is complete or when traps are off). */
    List<com.eternalrealm.dungeon.hazard.HazardSite> trapSites() {
        return hazards == null ? List.of() : hazards.field().sites();
    }

    /** What the map shows of this instance: its layout, the current room and the fixed traps. */
    com.eternalrealm.world.map.MapView.Dungeon mapView() {
        List<com.eternalrealm.world.map.MapView.Rect> rooms = new ArrayList<>();
        for (RoomLayout room : layout.rooms()) {
            rooms.add(rect(room.bounds()));
        }
        List<com.eternalrealm.world.map.MapView.Rect> corridors = new ArrayList<>();
        for (BlockBox corridor : layout.corridors()) {
            corridors.add(rect(corridor));
        }
        RunState state = run.state();
        int room = state == RunState.IN_PROGRESS ? run.currentRoom()
                : state == RunState.BOSS || state == RunState.CLEARED ? layout.rooms().size() : -1;
        List<com.eternalrealm.world.map.MapView.Trap> traps = new ArrayList<>();
        for (com.eternalrealm.dungeon.hazard.HazardSite site : trapSites()) {
            if (site.kind().trigger() != com.eternalrealm.dungeon.hazard.HazardKind.Trigger.TARGETED) {
                traps.add(new com.eternalrealm.world.map.MapView.Trap(origin.getX() + site.x(), origin.getZ() + site.z(),
                        site.kind().id()));
            }
        }
        return new com.eternalrealm.world.map.MapView.Dungeon(contentId, nameKey, origin.getX(), origin.getY(),
                origin.getZ(), rect(layout.entranceHall()), rooms, corridors, rect(layout.bossArena()), room,
                state == RunState.BOSS, traps);
    }

    private static com.eternalrealm.world.map.MapView.Rect rect(BlockBox box) {
        return new com.eternalrealm.world.map.MapView.Rect(box.minX(), box.minZ(), box.maxX(), box.maxZ());
    }

    List<ServerPlayer> onlineMembers() {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID member : run.members()) {
            ServerPlayer player = service.player(member);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    void tellAll(Component message, boolean actionBar) {
        for (UUID member : run.members()) {
            ServerPlayer player = service.player(member);
            if (player != null) {
                if (actionBar) {
                    player.displayClientMessage(message, true);
                } else {
                    player.sendSystemMessage(message);
                }
            }
        }
    }

    private void playAll(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        for (UUID member : run.members()) {
            ServerPlayer player = service.player(member);
            if (player != null && player.level() == level) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.HOSTILE, volume,
                        pitch);
            }
        }
    }
}
