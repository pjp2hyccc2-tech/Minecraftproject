package com.huntcraft.village.client;

import com.huntcraft.data.HunterData;
import com.huntcraft.village.companion.MofuData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/** Client-side helpers: the synced item box copy and quick access to the local hunter's village data. */
@OnlyIn(Dist.CLIENT)
public final class ClientVillage {
    /** Incremented whenever a new item box copy arrives (screens rebuild their lists). */
    public static int boxVersion;

    private ClientVillage() {
    }

    @Nullable
    public static Player player() {
        return Minecraft.getInstance().player;
    }

    public static HunterData data() {
        Player p = player();
        return p == null ? new HunterData() : HunterData.get(p);
    }

    /** The local hunter's village module tag (never null). */
    public static CompoundTag village() {
        Player p = player();
        return p == null ? new CompoundTag() : MofuData.village(p);
    }

    /** Client action "village/box": replaces the local item box copy. */
    public static void loadBox(CompoundTag tag) {
        Player p = player();
        if (p == null) return;
        HunterData d = HunterData.get(p);
        for (int i = 0; i < d.itemBox.size(); i++) d.itemBox.set(i, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, d.itemBox);
        boxVersion++;
    }
}
