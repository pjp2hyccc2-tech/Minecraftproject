package com.eternalrealm.npc.forge;

import com.eternalrealm.npc.NpcLooks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;

/**
 * Town NPCs: {@link NpcEntity} bodies with a role tag, invulnerable and silent, that open the mod's menus instead of
 * trading. Feature modules register what each role does with {@link #onInteract}; handlers only rely on {@link Mob}, so
 * the body the NPC wears can change without touching them. NPCs placed by older versions as vanilla villagers are
 * replaced by an {@link NpcEntity} that keeps their persistent data. Server thread only.
 */
public final class Npcs {
    public static final String TAG = "eternalrealm_npc";
    /** Persistent data key of the gatekeepers at the dungeon entrances (dungeon id). */
    public static final String ENTRANCE = "eternalrealm_entrance";
    private static final Map<NpcRole, BiConsumer<ServerPlayer, Mob>> HANDLERS = new EnumMap<>(NpcRole.class);
    /** Keys that tell NPCs of one role apart (story character, dungeon entrance, and any key passed to ensure). */
    private static final Set<String> IDENTITY_KEYS = new CopyOnWriteArraySet<>(List.of(StoryNpcs.CHARACTER, ENTRANCE));

    private Npcs() {
    }

    /** What talking to an NPC of {@code role} does (registered once during mod construction). */
    public static void onInteract(NpcRole role, BiConsumer<ServerPlayer, Mob> handler) {
        HANDLERS.put(role, handler);
    }

    public static Optional<NpcRole> roleOf(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(TAG) ? NpcRole.byId(data.getString(TAG)) : Optional.empty();
    }

    /**
     * Who an NPC is, whatever body it wears: {@code "<role>"}, {@code "<role>|<key>=<value>"} for one of several NPCs
     * of a role, {@code "spot|<name>"} for a townsperson or guard; {@code null} for anything else.
     */
    public static String identity(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (data.contains(TAG)) {
            String role = data.getString(TAG);
            for (String key : IDENTITY_KEYS) {
                if (data.contains(key)) {
                    return role + "|" + key + "=" + data.getString(key);
                }
            }
            return role;
        }
        if (data.contains(Crowds.SPOT)) {
            return "spot|" + data.getString(Crowds.SPOT);
        }
        return null;
    }

    /**
     * Makes sure exactly one NPC of {@code role} stands at {@code pos} (the chunk must be loaded): an existing one
     * nearby is moved back into place, duplicates are removed. @return the NPC
     */
    public static Optional<Mob> ensure(ServerLevel level, BlockPos pos, NpcRole role, float yaw) {
        return ensure(level, pos, role, yaw, null, null);
    }

    /**
     * Like {@link #ensure(ServerLevel, BlockPos, NpcRole, float)} for one of several NPCs of a role told apart by a
     * persistent string ({@code key} = {@code value}), e.g. the story characters. A villager placed by an older version
     * is replaced by an {@link NpcEntity} carrying the same persistent data.
     */
    public static Optional<Mob> ensure(ServerLevel level, BlockPos pos, NpcRole role, float yaw, String key,
                                       String value) {
        if (key != null) {
            IDENTITY_KEYS.add(key);
        }
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4); // the NPC's chunk must be loaded
        List<Mob> existing = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(24.0),
                mob -> roleOf(mob).filter(r -> r == role).isPresent()
                        && (key == null || value.equals(mob.getPersistentData().getString(key))));
        NpcEntity npc = null;
        for (Mob mob : existing) {
            if (mob instanceof NpcEntity body) {
                npc = body;
                break;
            }
        }
        Mob legacy = null;
        for (Mob mob : existing) {
            if (mob == npc) {
                continue;
            }
            if (npc == null && legacy == null) {
                legacy = mob; // an older body (villager) to take over
            } else {
                mob.discard();
            }
        }
        if (npc == null) {
            npc = NpcEntities.NPC.get().create(level);
            if (npc == null) {
                return Optional.empty();
            }
            if (legacy != null) {
                npc.getPersistentData().merge(legacy.getPersistentData().copy());
                legacy.discard();
            }
            npc.getPersistentData().putString(TAG, role.id());
            if (key != null) {
                npc.getPersistentData().putString(key, value);
            }
            npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
            level.addFreshEntity(npc);
        }
        npc.setAppearance(StoryNpcs.CHARACTER.equals(key) ? NpcLooks.forCharacter(value)
                : ENTRANCE.equals(key) ? NpcLooks.WARDEN : NpcLooks.forRole(role.id()));
        npc.setPost(pos, yaw);
        npc.setInvulnerable(true);
        npc.setSilent(true);
        npc.setPersistenceRequired();
        npc.setCustomName(Component.translatable(role.translationKey()).withStyle(ChatFormatting.GOLD));
        npc.setCustomNameVisible(true);
        npc.markEnsured(level.getGameTime());
        return Optional.of(npc);
    }

    /** Forge bus: talking to a town NPC opens its feature instead of the vanilla trade screen. */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Optional<NpcRole> role = roleOf(event.getTarget());
        if (role.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof Mob npc
                && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
            player.playNotifySound(com.eternalrealm.sound.ERSounds.UI_NPC_TALK.get(),
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 1.0F);
            if (npc instanceof NpcEntity body) {
                body.startTalking(player);
            }
            BiConsumer<ServerPlayer, Mob> handler = HANDLERS.get(role.get());
            if (handler != null) {
                handler.accept(player, npc);
            } else {
                player.sendSystemMessage(Component.translatable("npc.eternalrealm.greeting",
                        npc.getDisplayName()).withStyle(ChatFormatting.YELLOW));
            }
        }
    }
}
