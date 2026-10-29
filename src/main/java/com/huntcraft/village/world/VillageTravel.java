package com.huntcraft.village.world;

import com.huntcraft.core.HCDimensions;
import com.huntcraft.data.HunterData;
import com.huntcraft.village.VillageRegistry;
import com.huntcraft.village.companion.MofuData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/** HuntHooks.toVillage / toOverworld implementations. */
public final class VillageTravel {
    private VillageTravel() {
    }

    public static void toVillage(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        ServerLevel village = server.getLevel(HCDimensions.HUNTER_VILLAGE);
        if (village == null) {
            p.displayClientMessage(Component.translatable("message.huntcraft.village_missing").withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!HCDimensions.isHuntDimension(p.level())) rememberReturn(p);
        VillageBuilder.ensureBuilt(village);
        double x = VillageLayout.SPAWN_X, y = VillageLayout.SPAWN_Y, z = VillageLayout.SPAWN_Z;
        village.getChunk(BlockPos.containing(x, y, z));
        p.teleportTo(village, x, y, z, VillageLayout.SPAWN_YAW, 0.0F);
        p.fallDistance = 0;
        p.displayClientMessage(Component.translatable("message.huntcraft.arrive_village").withStyle(ChatFormatting.GOLD), true);
        village.playSound(null, x, y, z, VillageRegistry.NPC_GREET.get(), SoundSource.AMBIENT, 0.8F, 1.0F);
    }

    private static void rememberReturn(ServerPlayer p) {
        CompoundTag r = new CompoundTag();
        r.putString("dim", p.level().dimension().location().toString());
        r.putDouble("x", p.getX());
        r.putDouble("y", p.getY());
        r.putDouble("z", p.getZ());
        r.putFloat("yaw", p.getYRot());
        r.putFloat("pitch", p.getXRot());
        MofuData.village(p).put("ret", r);
        HunterData.get(p).markDirty();
    }

    public static void toOverworld(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        CompoundTag v = MofuData.village(p);
        ServerLevel target = null;
        double x = 0, y = 0, z = 0;
        float yaw = p.getYRot(), pitch = 0;
        if (v.contains("ret", Tag.TAG_COMPOUND)) {
            CompoundTag r = v.getCompound("ret");
            target = level(server, r.getString("dim"));
            if (target != null && !HCDimensions.isHuntDimension(target)) {
                x = r.getDouble("x");
                y = r.getDouble("y");
                z = r.getDouble("z");
                yaw = r.getFloat("yaw");
                pitch = r.getFloat("pitch");
            } else {
                target = null;
            }
        }
        if (target == null) {
            target = server.overworld();
            BlockPos sp = target.getSharedSpawnPos();
            target.getChunk(sp);
            int top = target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
            x = sp.getX() + 0.5;
            y = Math.max(top, target.getMinBuildHeight() + 1);
            z = sp.getZ() + 0.5;
        } else {
            target.getChunk(BlockPos.containing(x, y, z));
        }
        p.teleportTo(target, x, y, z, yaw, pitch);
        p.fallDistance = 0;
    }

    @Nullable
    private static ServerLevel level(MinecraftServer server, String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        return rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
    }

    /** True if the position is inside the protected village area. */
    public static boolean inVillageBounds(BlockPos pos) {
        int[] b = VillageLayout.BOUNDS;
        return pos.getX() >= b[0] && pos.getX() <= b[2] && pos.getZ() >= b[1] && pos.getZ() <= b[3];
    }
}
