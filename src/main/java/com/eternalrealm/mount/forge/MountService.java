package com.eternalrealm.mount.forge;

import com.eternalrealm.boss.forge.BossFight;
import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.mount.MountIndividual;
import com.eternalrealm.mount.MountKind;
import com.eternalrealm.mount.MountOwnership;
import com.eternalrealm.mount.MountRules;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The mounts on the server (rules: {@link MountRules}): what each player owns (player data extension
 * {@value MountRules#EXTENSION}), buying, calling a mount (a short call that moving, combat or a hit stops), getting
 * off, and the places a mount may not go (dungeons and raids, boss fights). The mount itself is {@link MountEntity}.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MountService {
    /** Calls in progress, by player. */
    private static final Map<UUID, Call> CALLS = new HashMap<>();
    /** Riders who left a mount in the air: no fall damage until they land (or the guard runs out), by player. */
    private static final Map<UUID, Long> FALL_GUARDS = new HashMap<>();
    /** The last state sent to each player's HUD. */
    private static final Map<UUID, String> SENT = new HashMap<>();
    /**
     * When to show the riding controls, by player: a few ticks after mounting, because the client shows vanilla's
     * "press sneak to dismount" line when it learns that the player rides.
     */
    private static final Map<UUID, Long> HINTS = new HashMap<>();
    private static final long HINT_DELAY = 5L;

    /** {@code player} is kept (not looked up by id) so GameTests' fake players can call mounts too. */
    private record Call(ServerPlayer player, MountIndividual mount, Vec3 from, long readyAt) {
        MountKind kind() {
            return mount.kind();
        }
    }

    private MountService() {
    }

    /** Registers the key actions and the stable's (once, during mod construction). */
    public static void register() {
        UiActions.register(MountRules.TOGGLE_ACTION, (player, context, args) -> toggle(player));
        UiActions.register(MountRules.CHOOSE_ACTION, (player, context, args) -> MountStable.openChooser(player));
        MountStable.register();
    }

    // ------------------------------------------------------------------ ownership

    /** What {@code player} keeps (nothing if the character is not loaded). */
    public static MountOwnership ownership(ServerPlayer player) {
        return ServerContext.current().flatMap(context -> context.playerData().extension(player.getUUID(),
                MountRules.EXTENSION)).map(text -> MountOwnership.fromJson(text, player.getUUID()))
                .orElse(MountOwnership.NONE);
    }

    static boolean save(ServerPlayer player, MountOwnership ownership) {
        return ServerContext.current().map(context -> context.playerData().setExtension(player.getUUID(),
                MountRules.EXTENSION, ownership.toJson())).orElse(false);
    }

    /**
     * Buys {@code mount} (one the stablemaster showed) if the rules allow it; tells the player either way. The
     * player learns its values from the stable screen.
     */
    public static MountRules.Purchase buy(ServerPlayer player, MountIndividual mount) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return MountRules.Purchase.NOT_ENOUGH_GOLD;
        }
        MountKind kind = mount.kind();
        UUID id = player.getUUID();
        int level = context.playerData().snapshot(id).map(CharacterSnapshot::level).orElse(0);
        MountOwnership owned = ownership(player);
        MountRules.Purchase result = MountRules.purchase(kind, level, context.playerData().gold(id), owned);
        if (result == MountRules.Purchase.OK) {
            if (!context.playerData().spendGold(id, kind.price())) {
                result = MountRules.Purchase.NOT_ENOUGH_GOLD;
            } else if (!save(player, owned.withAdded(mount))) {
                context.playerData().grantGold(id, kind.price()); // could not record it: the gold goes back
                return MountRules.Purchase.NOT_ENOUGH_GOLD;
            }
        }
        Component name = Component.translatable(kind.translationKey()).withStyle(ChatFormatting.GOLD);
        switch (result) {
            case OK -> {
                player.displayClientMessage(Component.translatable(MountRules.BOUGHT, name, keyName())
                        .withStyle(ChatFormatting.GREEN), false);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
                        SoundSource.PLAYERS, 0.7F, 1.2F);
            }
            case LEVEL_TOO_LOW -> player.displayClientMessage(Component.translatable(MountRules.LEVEL_TOO_LOW,
                    kind.requiredLevel()).withStyle(ChatFormatting.RED), true);
            case NOT_ENOUGH_GOLD -> player.displayClientMessage(Component.translatable(MountRules.NOT_ENOUGH_GOLD,
                    kind.price()).withStyle(ChatFormatting.RED), true);
            case ALREADY_OWNED -> player.displayClientMessage(Component.translatable(MountRules.ALREADY_OWNED)
                    .withStyle(ChatFormatting.GRAY), true);
            case STABLE_FULL -> player.displayClientMessage(Component.translatable(MountRules.STABLE_FULL,
                    MountRules.STABLE_CAPACITY).withStyle(ChatFormatting.RED), true);
        }
        sendState(player);
        return result;
    }

    /** Buys the first mount the stablemaster shows of {@code kind} (operators' commands and tests). */
    public static MountRules.Purchase buy(ServerPlayer player, MountKind kind) {
        return MountStable.buyOffer(player, kind, 0);
    }

    /** Makes kept mount {@code id} the one the player calls. */
    public static boolean select(ServerPlayer player, UUID id) {
        MountOwnership owned = ownership(player);
        Optional<MountIndividual> mount = owned.mount(id);
        if (mount.isEmpty() || !save(player, owned.withSelected(id))) {
            return false;
        }
        player.displayClientMessage(Component.translatable(MountRules.SELECTED,
                Component.translatable(mount.get().kind().translationKey()).withStyle(ChatFormatting.GOLD)), true);
        sendState(player);
        return true;
    }

    /** Makes the first kept mount of {@code kind} the one the player calls (tests). */
    public static boolean select(ServerPlayer player, MountKind kind) {
        return ownership(player).first(kind).map(mount -> select(player, mount.id())).orElse(false);
    }

    /** Operators and tests: gives a standard mount of {@code kind} without gold or level. */
    public static boolean grant(ServerPlayer player, MountKind kind) {
        return keep(player, MountIndividual.standard(UUID.randomUUID(), kind));
    }

    /** Records {@code mount} in the player's stable (picked to be called); false if it could not be saved. */
    public static boolean keep(ServerPlayer player, MountIndividual mount) {
        boolean saved = save(player, ownership(player).withAdded(mount));
        sendState(player);
        return saved;
    }

    private static Component keyName() {
        return Component.keybind("key." + ModInfo.MOD_ID + ".mount").withStyle(ChatFormatting.YELLOW);
    }

    // ------------------------------------------------------------------ calling and getting off

    /** The mount key: get off, stop a call, or start one. */
    public static void toggle(ServerPlayer player) {
        if (player.getVehicle() instanceof MountEntity) {
            dismiss(player, true);
            return;
        }
        if (CALLS.remove(player.getUUID()) != null) {
            player.displayClientMessage(Component.translatable(MountRules.CALL_STOPPED).withStyle(ChatFormatting.GRAY),
                    true);
            return;
        }
        startCall(player);
    }

    /** Starts calling the selected mount; {@link MountRules.Call#OK} if the call began. */
    public static MountRules.Call startCall(ServerPlayer player) {
        MountOwnership owned = ownership(player);
        MountRules.Call check = MountRules.call(owned, situation(player, owned.selectedOrFirst()
                .map(MountIndividual::kind).orElse(null)));
        if (check != MountRules.Call.OK) {
            player.displayClientMessage(Component.translatable(check.messageKey()).withStyle(ChatFormatting.RED), true);
            return check;
        }
        MountIndividual mount = owned.selectedOrFirst().orElseThrow();
        MountKind kind = mount.kind();
        long now = player.server.getTickCount();
        CALLS.put(player.getUUID(), new Call(player, mount, player.position(), now + MountRules.SUMMON_TICKS));
        player.displayClientMessage(Component.translatable(MountRules.CALLING,
                Component.translatable(kind.translationKey()).withStyle(ChatFormatting.GOLD)), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), callSound(kind),
                SoundSource.PLAYERS, 0.8F, 1.0F);
        return check;
    }

    /** Whether {@code player} is calling a mount right now. */
    public static boolean isCalling(ServerPlayer player) {
        return CALLS.containsKey(player.getUUID());
    }

    /**
     * Brings the first kept mount of {@code kind} (a standard one if none is kept) at once under the player
     * (GameTests skip the wait).
     */
    public static Optional<MountEntity> summonNow(ServerPlayer player, MountKind kind) {
        return summonNow(player, ownership(player).first(kind)
                .orElseGet(() -> MountIndividual.standard(UUID.randomUUID(), kind)));
    }

    /**
     * Brings {@code individual} at once under the player (the end of a call). Empty if there is no room or the entity
     * could not be made.
     */
    public static Optional<MountEntity> summonNow(ServerPlayer player, MountIndividual individual) {
        MountKind kind = individual.kind();
        ServerLevel level = player.serverLevel();
        MountEntity mount = MountEntities.type(kind).create(level);
        if (mount == null) {
            return Optional.empty();
        }
        mount.become(individual);
        mount.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        mount.setYBodyRot(player.getYRot());
        mount.setYHeadRot(player.getYRot());
        mount.setOwner(player.getUUID());
        if (!level.addFreshEntity(mount)) {
            return Optional.empty();
        }
        player.resetFallDistance();
        if (!player.startRiding(mount, true)) {
            mount.discard();
            return Optional.empty();
        }
        level.sendParticles(ParticleTypes.CLOUD, mount.getX(), mount.getY() + 0.6, mount.getZ(), 30,
                mount.getBbWidth() * 0.5, 0.4, mount.getBbWidth() * 0.5, 0.03);
        level.sendParticles(ParticleTypes.END_ROD, mount.getX(), mount.getY() + 1.0, mount.getZ(), 16, 0.5, 0.6, 0.5,
                0.05);
        level.playSound(null, mount.getX(), mount.getY(), mount.getZ(), arriveSound(kind), SoundSource.NEUTRAL, 1.0F,
                1.0F);
        HINTS.put(player.getUUID(), player.server.getTickCount() + HINT_DELAY);
        sendState(player);
        return Optional.of(mount);
    }

    /** The player gets off their mount, which leaves. */
    public static void dismiss(ServerPlayer player, boolean tell) {
        if (!(player.getVehicle() instanceof MountEntity mount)) {
            return;
        }
        boolean airborne = !mount.grounded();
        player.stopRiding();
        if (airborne) {
            guardFall(player);
        }
        mount.vanish();
        if (tell) {
            player.displayClientMessage(Component.translatable(MountRules.DISMISSED,
                    Component.translatable(mount.kind().translationKey())), true);
        }
        sendState(player);
    }

    /** No fall damage for {@code player} until they land (or {@link MountRules#FALL_GUARD_TICKS} pass). */
    static void guardFall(ServerPlayer player) {
        player.resetFallDistance();
        FALL_GUARDS.put(player.getUUID(), player.server.getTickCount() + (long) MountRules.FALL_GUARD_TICKS);
    }

    /** Where the player stands, for {@link MountRules#call}. */
    public static MountRules.Situation situation(ServerPlayer player, MountKind kind) {
        boolean allowed = !player.level().dimension().equals(com.eternalrealm.dungeon.forge.DungeonLevels.DUNGEONS)
                && !player.isSpectator();
        boolean bossFight = !BossFight.fightsOf(player.getUUID()).isEmpty();
        boolean combat = ServerContext.current().map(context -> context.combat().inCombat(player)).orElse(false);
        boolean down = !player.isAlive() || com.eternalrealm.revival.forge.RevivalService.isDown(player.getUUID());
        boolean water = player.isInWater() || player.isInLava();
        boolean room = kind == null || hasRoom(player, kind);
        return new MountRules.Situation(allowed, bossFight, combat, down, water, room);
    }

    /** Whether the mount's body fits where the player stands. */
    static boolean hasRoom(ServerPlayer player, MountKind kind) {
        AABB box = MountEntities.type(kind).getDimensions().makeBoundingBox(player.position()).deflate(0.05);
        return player.level().noCollision(box);
    }

    private static SoundEvent callSound(MountKind kind) {
        return switch (kind) {
            case HORSE -> SoundEvents.HORSE_AMBIENT;
            case GRIFFIN -> SoundEvents.PARROT_FLY;
            case BIKE -> SoundEvents.BEACON_POWER_SELECT;
        };
    }

    private static SoundEvent arriveSound(MountKind kind) {
        return switch (kind) {
            case HORSE -> SoundEvents.HORSE_GALLOP;
            case GRIFFIN -> SoundEvents.ENDER_DRAGON_FLAP;
            case BIKE -> SoundEvents.BEACON_ACTIVATE;
        };
    }

    // ------------------------------------------------------------------ the clock

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || server == null) {
            return;
        }
        long now = server.getTickCount();
        tickCalls(server, now);
        tickGuards(server, now);
        tickHints(server, now);
        if (now % 10 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                watchRider(player);
            }
        }
        if (now % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sendStateIfChanged(player);
            }
        }
    }

    private static void tickCalls(MinecraftServer server, long now) {
        Iterator<Map.Entry<UUID, Call>> it = CALLS.entrySet().iterator();
        while (it.hasNext()) {
            Call call = it.next().getValue();
            ServerPlayer player = call.player();
            if (player.isRemoved() || player.hasDisconnected()) {
                it.remove();
                continue;
            }
            MountOwnership owned = ownership(player);
            MountRules.Call check = owned.mount(call.mount().id()).isEmpty() ? MountRules.Call.NONE_OWNED
                    : MountRules.call(owned, situation(player, call.kind()));
            if (check != MountRules.Call.OK || player.position().distanceTo(call.from())
                    > MountRules.SUMMON_MOVE_TOLERANCE || player.isPassenger()) {
                it.remove();
                Component why = check != MountRules.Call.OK ? Component.translatable(check.messageKey())
                        : Component.translatable(MountRules.CALL_STOPPED);
                player.displayClientMessage(why.copy().withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            if (now >= call.readyAt()) {
                it.remove();
                summonNow(player, call.mount());
            } else if (now % 4 == 0) {
                double progress = 1.0 - (call.readyAt() - now) / (double) MountRules.SUMMON_TICKS;
                player.serverLevel().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 0.2,
                        player.getZ(), 8 + (int) (progress * 16), 0.8, 0.1, 0.8, 0.6);
            }
        }
    }

    private static void tickGuards(MinecraftServer server, long now) {
        Iterator<Map.Entry<UUID, Long>> it = FALL_GUARDS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || now > entry.getValue()
                    || (player.onGround() && entry.getValue() - now < MountRules.FALL_GUARD_TICKS - 2L)) {
                it.remove();
            }
        }
    }

    private static void tickHints(MinecraftServer server, long now) {
        Iterator<Map.Entry<UUID, Long>> it = HINTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now < entry.getValue()) {
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.getVehicle() instanceof MountEntity mount) {
                player.displayClientMessage(Component.translatable(mount.kind().flies() ? MountRules.FLIGHT_HINT
                        : MountRules.HINT).withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    /** A rider in a dungeon or in a boss fight gets off. */
    private static void watchRider(ServerPlayer player) {
        if (!(player.getVehicle() instanceof MountEntity)) {
            return;
        }
        MountRules.Situation where = situation(player, null);
        if (!where.mountAllowedHere()) {
            dismiss(player, false);
            player.displayClientMessage(Component.translatable(MountRules.LEFT_HERE).withStyle(ChatFormatting.GRAY),
                    true);
        } else if (where.inBossFight()) {
            dismiss(player, false);
            player.displayClientMessage(Component.translatable(MountRules.LEFT_BOSS_FIGHT)
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    // ------------------------------------------------------------------ events

    /** Getting off in the air (sneaking off a jumping horse, a forced dismount): the fall is spared. */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof MountEntity mount
                && event.getEntityMounting() instanceof ServerPlayer player && !mount.grounded()) {
            guardFall(player);
        }
    }

    /**
     * A rider's head may poke into a low ceiling: no suffocation damage on a mount. A rider who strikes (a blow, a
     * skill, a shot) does not hit from the saddle: the blow is dropped and the rider gets off to fight on foot.
     */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getVehicle() instanceof MountEntity
                && event.getSource().is(DamageTypes.IN_WALL)) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer rider && rider.getVehicle() instanceof MountEntity
                && event.getEntity() != rider) {
            event.setCanceled(true);
            dismiss(rider, false);
            rider.displayClientMessage(Component.translatable(MountRules.FIGHT_ON_FOOT)
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && FALL_GUARDS.containsKey(player.getUUID())) {
            event.setDistance(0.0F);
            event.setDamageMultiplier(0.0F);
            FALL_GUARDS.remove(player.getUUID());
        }
    }

    /** Logging out: the mount leaves before the player is saved (so it is never saved with them). */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            dismiss(player, false);
            CALLS.remove(player.getUUID());
            FALL_GUARDS.remove(player.getUUID());
            SENT.remove(player.getUUID());
            HINTS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SENT.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CALLS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            dismiss(player, false);
        }
        CALLS.clear();
        FALL_GUARDS.clear();
        SENT.clear();
        HINTS.clear();
    }

    // ------------------------------------------------------------------ the HUD

    /** {@link MountRules#STATE_CHANNEL}: what the player owns, picks and rides. */
    public static void sendState(ServerPlayer player) {
        String state = state(player);
        SENT.put(player.getUUID(), state);
        UiData.send(player, MountRules.STATE_CHANNEL, com.google.gson.JsonParser.parseString(state));
    }

    private static void sendStateIfChanged(ServerPlayer player) {
        String state = state(player);
        if (!state.equals(SENT.get(player.getUUID()))) {
            SENT.put(player.getUUID(), state);
            UiData.send(player, MountRules.STATE_CHANNEL, com.google.gson.JsonParser.parseString(state));
        }
    }

    private static String state(ServerPlayer player) {
        MountOwnership owned = ownership(player);
        JsonObject json = new JsonObject();
        JsonArray ids = new JsonArray();
        for (MountKind kind : owned.kinds()) {
            ids.add(kind.id());
        }
        json.add("owned", ids);
        json.addProperty("selected", owned.selectedOrFirst().map(mount -> mount.kind().id()).orElse(""));
        json.addProperty("riding", player.getVehicle() instanceof MountEntity mount ? mount.kind().id() : "");
        return json.toString();
    }

    /** GameTests: whether {@code player} is spared fall damage right now. */
    public static boolean fallGuarded(ServerPlayer player) {
        return FALL_GUARDS.containsKey(player.getUUID());
    }

    /** Whether {@code entity} is somebody's mount. */
    public static boolean isMount(Entity entity) {
        return entity instanceof MountEntity;
    }
}
