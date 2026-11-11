package com.eternalrealm.guild.house.forge;

import com.eternalrealm.guild.Guild;
import com.eternalrealm.guild.house.GateAccess;
import com.eternalrealm.guild.house.GuildHouseCatalog;
import com.eternalrealm.world.forge.AstoriaTravel;
import com.eternalrealm.world.forge.Capital;
import com.eternalrealm.world.forge.HubLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What happens in a guild gate's light (docs/modules/guildhouse.md):
 * <ul>
 *     <li>In a guild house: the return gate takes the player back where they came from (in front of the gate in the
 *     capital).</li>
 *     <li>Anywhere else (the twenty gates of the Guild Gate Terrace): a member of a guild that owns the gate's design
 *     steps out in front of the gate (where they will come back to) and through into their own guild's house; anyone
 *     else is told why not and pushed gently back out of the light.</li>
 * </ul>
 * A player in the light is handled once, then left alone for a moment (the light touches them every tick).
 */
public final class GuildGates {
    /** Ticks between two gate actions for one player. */
    static final int COOLDOWN = 40;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private GuildGates() {
    }

    public static void onPortal(ServerPlayer player, BlockPos pos, BlockState state) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < COOLDOWN && now >= last) {
            return;
        }
        LAST.put(player.getUUID(), now);
        GuildEstates estates = GuildHouses.estates();
        if (estates.houseAt(player.level(), pos).isPresent()) {
            if (!estates.leave(player)) {
                AstoriaTravel.sendToHub(player);
            }
            return;
        }
        String design = state.getValue(GuildPortalBlock.THEME).id();
        Optional<Guild> guild = GuildHouses.guildOf(player);
        Optional<GuildHouses> houses = GuildHouses.get();
        GateAccess access = houses.map(h -> h.service().gate(design, guild.map(Guild::id).orElse(null)))
                .orElse(GateAccess.NO_HOUSE);
        if (access != GateAccess.ENTER) {
            refuse(player, pos, state, access, guild);
            return;
        }
        UUID guildId = guild.orElseThrow().id();
        if (!estates.available()) {
            refuse(player, pos, state, null, guild);
            player.displayClientMessage(Component.translatable("message.eternalrealm.guildhouse.error.unavailable")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!estates.ready(guildId)) {
            pushBack(player, pos, state);
            player.displayClientMessage(Component.translatable("message.eternalrealm.guildhouse.error.building",
                    estates.percent(guildId)).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        stepOut(player, pos, state);
        ServerLevel from = player.serverLevel();
        from.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.4, 0.8, 0.4,
                0.04);
        from.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.9F, 1.3F);
        if (estates.enter(player, guildId) == GuildEstates.Result.OK) {
            welcome(player, guild.get());
        }
    }

    /** Title on arrival: the guild's name, the message of the day below it. */
    static void welcome(ServerPlayer player, Guild guild) {
        String motd = GuildHouses.get().map(h -> h.service().house(guild.id()).motd()).orElse("");
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(
                "message.eternalrealm.guildhouse.welcome", guild.name()).withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(motd)
                .withStyle(ChatFormatting.WHITE)));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F);
        GuildHouseUi.sendBoard(player);
    }

    private static void refuse(ServerPlayer player, BlockPos pos, BlockState state, GateAccess access,
                               Optional<Guild> guild) {
        pushBack(player, pos, state);
        if (access == null) {
            return;
        }
        Component message = access == GateAccess.OTHER_DESIGN
                ? Component.translatable(access.messageKey(), guild.flatMap(g -> GuildHouses.get()
                        .flatMap(h -> h.service().design(g.id()))).map(d -> Component.translatable(d.nameKey()))
                        .orElse(Component.literal("?")))
                : Component.translatable(access.messageKey());
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.LIGHT_PURPLE), true);
        player.serverLevel().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.8F, 0.6F);
    }

    /** Out of the light: a gentle shove back the way the player came, out of the portal's plane. */
    static void pushBack(ServerPlayer player, BlockPos pos, BlockState state) {
        Vec3 out = outward(player, pos, state);
        Vec3 target = new Vec3(player.getX(), player.getY(), player.getZ()).add(out.scale(0.9));
        player.teleportTo(target.x, player.getY(), target.z);
        player.setDeltaMovement(out.x * 0.45, 0.25, out.z * 0.45);
        player.hurtMarked = true;
        player.serverLevel().sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.0,
                pos.getZ() + 0.5, 20, 0.6, 0.6, 0.6, 0.6);
    }

    /**
     * Where the player will come back to: in front of the gate, facing away from it. The capital's
     * {@code guildgate.<theme>} spot names the portal's centre and the way into it; elsewhere the side the player came
     * from.
     */
    static void stepOut(ServerPlayer player, BlockPos pos, BlockState state) {
        String design = state.getValue(GuildPortalBlock.THEME).id();
        Optional<ServerLevel> astoria = AstoriaTravel.astoria(player.server);
        if (astoria.isPresent() && player.serverLevel() == astoria.get()) {
            HubLayout layout = Capital.layout(astoria.get());
            Optional<BlockPos> spot = layout.named("guildgate." + design);
            if (spot.isPresent() && spot.get().distSqr(pos) < 64) {
                float yaw = layout.namedYaw("guildgate." + design, player.getYRot());
                double rad = Math.toRadians(yaw);
                double dx = -Math.sin(rad);
                double dz = Math.cos(rad);
                BlockPos s = spot.get();
                player.teleportTo(astoria.get(), s.getX() + 0.5 - dx * 2.5, s.getY(), s.getZ() + 0.5 - dz * 2.5,
                        Mth.wrapDegrees(yaw + 180.0F), 0.0F);
                return;
            }
        }
        Vec3 out = outward(player, pos, state);
        player.teleportTo(pos.getX() + 0.5 + out.x * 2.0, player.getY(), pos.getZ() + 0.5 + out.z * 2.0);
    }

    /** Unit vector from the portal's plane towards the side the player stands on (or came from). */
    private static Vec3 outward(ServerPlayer player, BlockPos pos, BlockState state) {
        boolean alongX = state.getValue(GuildPortalBlock.AXIS) == net.minecraft.core.Direction.Axis.X;
        double side = alongX ? player.getZ() - (pos.getZ() + 0.5) : player.getX() - (pos.getX() + 0.5);
        if (Math.abs(side) < 0.05) {
            Vec3 motion = player.getDeltaMovement();
            side = -(alongX ? motion.z : motion.x);
            if (Math.abs(side) < 1.0E-4) {
                double rad = Math.toRadians(player.getYRot());
                side = alongX ? Math.cos(rad) * -1.0 : -Math.sin(rad) * -1.0;
            }
        }
        double sign = side >= 0 ? 1.0 : -1.0;
        return alongX ? new Vec3(0, 0, sign) : new Vec3(sign, 0, 0);
    }

    /** Forget a player's cooldown (logout). */
    public static void forget(UUID player) {
        LAST.remove(player);
    }

    /** Whether {@code design} is a known design (for commands and tests). */
    public static boolean known(String design) {
        return GuildHouseCatalog.standard().contains(design);
    }
}
