package com.eternalrealm.world.forge;

import com.eternalrealm.block.forge.WaystoneBlock;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiDataPacket;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.Waystones;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The waystones (転移碑) on the server: walking up to one records it for the player (saved with the character), touching
 * one opens the travel menu ({@link Waystones#UI_CHANNEL}), and a travel request ({@link Waystones#TRAVEL_ACTION}) is
 * checked with {@link Waystones#check} — standing at a waystone, destination recorded and its field open, not in
 * combat, cooldown over — then the player is moved in front of the destination stone with light and sound on both
 * ends. Only in アストリア.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WaystoneService {
    private static final Logger LOG = LogUtils.getLogger();
    /** Vertical tolerance: a player more than this far above or below a stone's ground is not "at" it. */
    private static final double VERTICAL_REACH = 12.0;
    private static final Map<UUID, Long> LAST_TRAVEL = new HashMap<>();
    private static MinecraftServer owner;

    private WaystoneService() {
    }

    /** Registers the travel request handler (once, during mod construction). */
    public static void register() {
        UiActions.register(Waystones.TRAVEL_ACTION, WaystoneService::onTravel);
    }

    /** The waystones {@code playerId} has recorded (empty if the character is not loaded). */
    public static Set<String> recorded(ServerContext context, UUID playerId) {
        return context.playerData().extension(playerId, Waystones.EXTENSION).map(Waystones::fromJson).orElse(Set.of());
    }

    private static boolean inAstoria(ServerPlayer player) {
        return player.level().dimension() == Realm.ASTORIA;
    }

    /** The waystone the player stands at (horizontal {@code radius}, and not far above or below its ground). */
    private static Optional<FieldLayout.Waystone> at(ServerPlayer player, double radius) {
        if (!inAstoria(player)) {
            return Optional.empty();
        }
        return Waystones.nearest(player.getX(), player.getZ(), radius).filter(w -> {
            int ground = player.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, w.x(), w.z());
            return Math.abs(player.getY() - ground) <= VERTICAL_REACH;
        });
    }

    // ------------------------------------------------------------------ recording

    /** Records {@code waystone} for the player once: a message, a chime and a burst of light. */
    static boolean record(ServerContext context, ServerPlayer player, FieldLayout.Waystone waystone) {
        Set<String> known = recorded(context, player.getUUID());
        if (known.contains(waystone.id())) {
            return false;
        }
        Set<String> updated = new TreeSet<>(known);
        updated.add(waystone.id());
        if (!context.playerData().setExtension(player.getUUID(), Waystones.EXTENSION, Waystones.toJson(updated))) {
            return false;
        }
        player.displayClientMessage(Component.translatable("waystone.eternalrealm.recorded",
                Component.translatable(Waystones.translationKey(waystone.id())).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.WHITE), false);
        player.displayClientMessage(Component.translatable("waystone.eternalrealm.recorded.hint")
                .withStyle(ChatFormatting.GRAY), false);
        ServerLevel level = player.serverLevel();
        BlockPos stone = findStone(level, waystone);
        double x = stone != null ? stone.getX() + 0.5 : waystone.x() + 0.5;
        double y = stone != null ? stone.getY() + 2.5 : player.getY() + 2.0;
        double z = stone != null ? stone.getZ() + 0.5 : waystone.z() + 0.5;
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.4F, 1.1F);
        level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7F, 1.6F);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 48, 0.5, 0.9, 0.5, 0.06);
        level.sendParticles(ParticleTypes.ENCHANT, x, y - 1.0, z, 60, 1.4, 0.6, 1.4, 0.4);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || server == null || server.getTickCount() % 10 != 3) {
            return;
        }
        if (owner != server) {
            owner = server;
            LAST_TRAVEL.clear();
        }
        ServerContext context = ServerContext.current().orElse(null);
        ServerLevel astoria = server.getLevel(Realm.ASTORIA);
        if (context == null || context.server() != server || astoria == null) {
            return;
        }
        for (ServerPlayer player : astoria.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            try {
                at(player, Waystones.DISCOVER_RADIUS).ifPresent(waystone -> record(context, player, waystone));
            } catch (RuntimeException e) {
                LOG.warn("Recording a waystone for {} failed", player.getGameProfile().getName(), e);
            }
        }
    }

    // ------------------------------------------------------------------ the travel menu

    /** A player touched the waystone whose lower half is at {@code pos}: record it and open the travel menu. */
    public static void use(ServerPlayer player, BlockPos pos) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return;
        }
        Optional<FieldLayout.Waystone> waystone = inAstoria(player)
                ? Waystones.nearest(pos.getX() + 0.5, pos.getZ() + 0.5, Waystones.IDENTIFY_RADIUS) : Optional.empty();
        if (waystone.isEmpty()) {
            player.displayClientMessage(Component.translatable("waystone.eternalrealm.dormant")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        record(context, player, waystone.get());
        JsonObject data = new JsonObject();
        data.addProperty("at", waystone.get().id());
        long left = Waystones.cooldownLeft(System.currentTimeMillis(),
                LAST_TRAVEL.getOrDefault(player.getUUID(), 0L));
        data.addProperty("cooldown", left / 1000.0);
        JsonArray known = new JsonArray();
        recorded(context, player.getUUID()).forEach(known::add);
        data.add("recorded", known);
        data.addProperty("t", System.nanoTime()); // a fresh document each time, so the client reopens the menu
        ERNetwork.sendToPlayer(player, new UiDataPacket(Waystones.UI_CHANNEL, data.toString()));
        player.serverLevel().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.8F);
    }

    private static void onTravel(ServerPlayer player, ServerContext context, JsonObject args) {
        String to = UiArgs.id(args, "to");
        long now = System.currentTimeMillis();
        long last = LAST_TRAVEL.getOrDefault(player.getUUID(), 0L);
        Optional<FieldLayout.Waystone> at = at(player, Waystones.USE_RADIUS);
        Waystones.Result result = Waystones.check(at, to, recorded(context, player.getUUID()),
                context.game().world()::isUnlocked, now, last, context.combat().inCombat(player));
        if (result != Waystones.Result.OK) {
            Component message = result == Waystones.Result.COOLDOWN
                    ? Component.translatable(result.translationKey(), (Waystones.cooldownLeft(now, last) + 999L) / 1000L)
                    : Component.translatable(result.translationKey());
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        FieldLayout.Waystone target = Waystones.byId(to).orElseThrow();
        ServerLevel level = player.serverLevel();
        // departure
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.4,
                0.9, 0.4, 0.08);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.3, 0.8,
                0.3, 0.12);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 0.9F, 1.4F);
        Arrival arrival = arrival(level, target);
        LAST_TRAVEL.put(player.getUUID(), now);
        player.stopRiding();
        player.teleportTo(level, arrival.position().x, arrival.position().y, arrival.position().z, arrival.yaw(), 0.0F);
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
        // arrival
        Vec3 p = arrival.position();
        level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 1.0, p.z, 40, 0.4, 1.0, 0.4, 0.05);
        level.sendParticles(ParticleTypes.ENCHANT, p.x, p.y + 0.5, p.z, 80, 1.0, 0.4, 1.0, 0.6);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9F, 1.3F);
        ERNetwork.sendToPlayer(player, new UiDataPacket(Waystones.ARRIVE_CHANNEL, "{\"t\":" + now + "}"));
        player.displayClientMessage(Component.translatable(Waystones.Result.OK.translationKey(),
                Component.translatable(Waystones.translationKey(target.id())).withStyle(ChatFormatting.AQUA)), true);
        record(context, player, target);
    }

    // ------------------------------------------------------------------ where the stone is, where one arrives

    record Arrival(Vec3 position, float yaw) {
    }

    /** The lower half of the waystone block near the waystone's point, or null (not generated, or unloaded). */
    static BlockPos findStone(ServerLevel level, FieldLayout.Waystone waystone) {
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, waystone.x(), waystone.z());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -8; dy <= 6; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    cursor.set(waystone.x() + dx, ground + dy, waystone.z() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.getBlock() instanceof WaystoneBlock
                            && state.getValue(WaystoneBlock.HALF) == DoubleBlockHalf.LOWER) {
                        return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }

    /** In front of the destination stone (the side it faces), on solid ground with room to stand, facing the stone. */
    static Arrival arrival(ServerLevel level, FieldLayout.Waystone waystone) {
        level.getChunk(waystone.x() >> 4, waystone.z() >> 4); // make sure it is loaded (and generated)
        BlockPos stone = findStone(level, waystone);
        Direction facing = stone != null ? level.getBlockState(stone).getValue(WaystoneBlock.FACING) : Direction.SOUTH;
        int baseX = stone != null ? stone.getX() : waystone.x();
        int baseZ = stone != null ? stone.getZ() : waystone.z();
        int hintY = stone != null ? stone.getY()
                : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, waystone.x(), waystone.z());
        Direction side = facing.getClockWise();
        float yaw = facing.getOpposite().toYRot();
        for (int distance = 2; distance <= 5; distance++) {
            for (int offset : new int[]{0, 1, -1, 2, -2}) {
                int x = baseX + facing.getStepX() * distance + side.getStepX() * offset;
                int z = baseZ + facing.getStepZ() * distance + side.getStepZ() * offset;
                Integer y = standable(level, x, z, hintY);
                if (y != null) {
                    return new Arrival(new Vec3(x + 0.5, y, z + 0.5), yaw);
                }
            }
        }
        int x = baseX + facing.getStepX() * 3;
        int z = baseZ + facing.getStepZ() * 3;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new Arrival(new Vec3(x + 0.5, y, z + 0.5), yaw);
    }

    /** Feet height of a safe standing spot in column (x, z) within a few blocks of {@code hintY}, or null. */
    private static Integer standable(ServerLevel level, int x, int z, int hintY) {
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        for (int dy = 3; dy >= -4; dy--) {
            feet.set(x, hintY + dy, z);
            BlockState below = level.getBlockState(feet.below());
            if (!below.isFaceSturdy(level, feet.below(), Direction.UP) || !below.getFluidState().isEmpty()) {
                continue;
            }
            BlockState body = level.getBlockState(feet);
            BlockState head = level.getBlockState(feet.above());
            if (body.getCollisionShape(level, feet).isEmpty() && body.getFluidState().isEmpty()
                    && head.getCollisionShape(level, feet.above()).isEmpty() && head.getFluidState().isEmpty()) {
                return feet.getY();
            }
        }
        return null;
    }
}
