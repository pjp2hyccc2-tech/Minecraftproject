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
 * Town NPCs: vanilla villagers with a role tag, no AI, invulnerable and silent, that open the mod's menus instead of
 * trading. Feature modules register what each role does with {@link #onInteract}. Server thread only.
 */
public final class Npcs {
    public static final String TAG = "eternalrealm_npc";
    private static final Map<NpcRole, BiConsumer<ServerPlayer, Villager>> HANDLERS = new EnumMap<>(NpcRole.class);

    private Npcs() {
    }

    /** What talking to an NPC of {@code role} does (registered once during mod construction). */
    public static void onInteract(NpcRole role, BiConsumer<ServerPlayer, Villager> handler) {
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
    public static Optional<Villager> ensure(ServerLevel level, BlockPos pos, NpcRole role, float yaw) {
        return ensure(level, pos, role, yaw, null, null);
    }

    /**
     * Like {@link #ensure(ServerLevel, BlockPos, NpcRole, float)} for one of several NPCs of a role told apart by a
     * persistent string ({@code key} = {@code value}), e.g. the story characters.
     */
    public static Optional<Villager> ensure(ServerLevel level, BlockPos pos, NpcRole role, float yaw, String key,
                                            String value) {
        List<Villager> existing = level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(24.0),
                villager -> roleOf(villager).filter(r -> r == role).isPresent()
                        && (key == null || value.equals(villager.getPersistentData().getString(key))));
        Villager npc = existing.isEmpty() ? null : existing.get(0);
        for (int i = 1; i < existing.size(); i++) {
            existing.get(i).discard();
        }
        if (npc == null) {
            npc = EntityType.VILLAGER.create(level);
            if (npc == null) {
                return Optional.empty();
            }
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
        npc.setVillagerData(new VillagerData(VillagerType.PLAINS, role.profession(), 5));
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
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof Villager villager
                && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
            BiConsumer<ServerPlayer, Villager> handler = HANDLERS.get(role.get());
            if (handler != null) {
                handler.accept(player, villager);
            } else {
                player.sendSystemMessage(Component.translatable("npc.eternalrealm.greeting",
                        villager.getDisplayName()).withStyle(ChatFormatting.YELLOW));
            }
        }
    }
}
