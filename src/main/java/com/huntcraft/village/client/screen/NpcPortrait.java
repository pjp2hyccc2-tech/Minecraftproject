package com.huntcraft.village.client.screen;

import com.huntcraft.village.VillageRegistry;
import com.huntcraft.village.companion.MofuEntity;
import com.huntcraft.village.companion.MofuType;
import com.huntcraft.village.npc.NpcRole;
import com.huntcraft.village.npc.VillageNpc;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/** Client-only entities used as live 3D portraits in screens (never added to the level). */
@OnlyIn(Dist.CLIENT)
public final class NpcPortrait {
    private NpcPortrait() {
    }

    @Nullable
    public static VillageNpc npc(NpcRole role) {
        Level lvl = Minecraft.getInstance().level;
        if (lvl == null) return null;
        try {
            VillageNpc n = (role.mofu ? VillageRegistry.MOFU_NPC.get() : VillageRegistry.HUMAN_NPC.get()).create(lvl);
            if (n == null) return null;
            n.setRole(role);
            n.setCustomName(null);
            n.setCustomNameVisible(false);
            return n;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    public static MofuEntity mofu(int pattern, MofuType type, int level, int weapon, int helm, int armor) {
        Level lvl = Minecraft.getInstance().level;
        if (lvl == null) return null;
        try {
            MofuEntity m = VillageRegistry.MOFU.get().create(lvl);
            if (m == null) return null;
            m.setTestMode(true);
            m.setVisual(pattern, type, level, weapon, helm, armor);
            return m;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Advances the idle animation of a portrait entity (call from Screen.tick). */
    public static void tick(@Nullable LivingEntity e) {
        if (e == null) return;
        e.tickCount++;
        if (e instanceof VillageNpc n) n.idleAnim.startIfStopped(n.tickCount);
        if (e instanceof MofuEntity m) m.idleAnim.startIfStopped(m.tickCount);
    }
}
