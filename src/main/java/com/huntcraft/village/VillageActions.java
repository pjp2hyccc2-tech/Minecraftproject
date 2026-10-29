package com.huntcraft.village;

import com.huntcraft.data.HunterData;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.network.HCScreens;
import com.huntcraft.village.companion.CompanionManager;
import com.huntcraft.village.meal.MealSystem;
import com.huntcraft.village.npc.NpcRole;
import com.huntcraft.village.npc.VillageNpc;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;

/** Server actions of the village module (all ids prefixed "village/"). */
public final class VillageActions {
    private VillageActions() {
    }

    public static void register() {
        HCNetwork.registerServerAction("village/box_request", (p, t) -> syncBox(p));
        HCNetwork.registerServerAction("village/meal", (p, t) -> MealSystem.eat(p, t.getString("id")));
        HCNetwork.registerServerAction("village/mofu", CompanionManager::handle);
    }

    /** Sends the item box contents to the client (core only syncs the rest of HunterData). */
    public static void syncBox(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        ContainerHelper.saveAllItems(t, HunterData.get(p).itemBox, true);
        HCNetwork.sendToPlayer(p, "village/box", t);
    }

    public static void npcInteract(ServerPlayer p, VillageNpc npc) {
        NpcRole role = npc.getRole();
        int line = p.getRandom().nextInt(3);
        p.displayClientMessage(Component.translatable("npc.huntcraft." + role.id + ".greet" + line), true);
        p.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                role.mofu ? VillageRegistry.MOFU_MEOW.get() : VillageRegistry.NPC_GREET.get(), SoundSource.NEUTRAL, 0.8F,
                role.mofu ? 1.0F + p.getRandom().nextFloat() * 0.2F : 1.0F);
        CompoundTag data = new CompoundTag();
        data.putString("npc", role.id);
        switch (role) {
            case RECEPTIONIST -> HCScreens.open(p, HCScreens.QUEST_BOARD, data);
            case BLACKSMITH -> {
                syncBox(p);
                HCScreens.open(p, HCScreens.BLACKSMITH, data);
            }
            case MERCHANT -> HCScreens.open(p, HCScreens.MERCHANT, data);
            case CHEF -> HCScreens.open(p, HCScreens.KITCHEN, data);
            case ELDER -> {
                CompoundTag c = new CompoundTag();
                c.putString("op", "cands");
                CompanionManager.handle(p, c);
                syncBox(p);
                HCScreens.open(p, HCScreens.COMPANION, data);
            }
        }
    }
}
