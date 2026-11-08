package com.eternalrealm.npc.forge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Town NPCs: mobs with a role tag, no AI, invulnerable and silent, that open the mod's menus instead of trading.
 * Feature modules register what each role does with {@link #onInteract}; handlers only rely on {@link Mob}, so the
 * body the NPC wears can change without touching them. Server thread only.
 */
public final class Npcs {
    public static final String TAG = "eternalrealm_npc";
    private static final Map<NpcRole, BiConsumer<ServerPlayer, Mob>> HANDLERS = new EnumMap<>(NpcRole.class);

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
     * Makes sure exactly one NPC of {@code role} stands at {@code pos} (the chunk must be loaded): an existing one
     * nearby is moved back into place, duplicates are removed. @return the NPC
     */
    public static Optional<Mob> ensure(ServerLevel level, BlockPos pos, NpcRole role, float yaw) {
        return ensure(level, pos, role, yaw, null, null);
    }

    /**
     * Like {@link #ensure(ServerLevel, BlockPos, NpcRole, float)} for one of several NPCs of a role told apart by a
     * persistent string ({@code key} = {@code value}), e.g. the story characters.
     */
    public static Optional<Mob> ensure(ServerLevel level, BlockPos pos, NpcRole role, float yaw, String key,
                                       String value) {
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4); // the NPC's chunk must be loaded
        List<Mob> existing = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(24.0),
                mob -> roleOf(mob).filter(r -> r == role).isPresent()
                        && (key == null || value.equals(mob.getPersistentData().getString(key))));
        Mob npc = existing.isEmpty() ? null : existing.get(0);
        for (int i = 1; i < existing.size(); i++) {
            existing.get(i).discard();
        }
        if (npc == null) {
            Villager villager = EntityType.VILLAGER.create(level);
            if (villager == null) {
                return Optional.empty();
            }
            npc = villager;
            npc.getPersistentData().putString(TAG, role.id());
            if (key != null) {
                npc.getPersistentData().putString(key, value);
            }
            npc.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null, null);
            level.addFreshEntity(npc);
        }
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        npc.setYHeadRot(yaw);
        npc.setNoAi(true);
        npc.setInvulnerable(true);
        npc.setSilent(true);
        npc.setPersistenceRequired();
        if (npc instanceof Villager villager) {
            villager.setVillagerData(new VillagerData(VillagerType.PLAINS, role.profession(), 5));
        }
        npc.setCustomName(Component.translatable(role.translationKey()).withStyle(ChatFormatting.GOLD));
        npc.setCustomNameVisible(true);
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
