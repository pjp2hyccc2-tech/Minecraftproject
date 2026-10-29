package com.huntcraft.village;

import com.huntcraft.core.HCDimensions;
import com.huntcraft.data.HunterData;
import com.huntcraft.village.companion.MofuData;
import com.huntcraft.village.menu.ItemBoxMenu;
import com.huntcraft.village.world.VillageBuilder;
import com.huntcraft.village.world.VillageTravel;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Admin commands: /hcvillage tp|back|rebuild|npcs|box|mofu. */
public final class VillageCommands {
    private VillageCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("hcvillage").requires(s -> s.hasPermission(2))
                .then(Commands.literal("tp").executes(ctx -> {
                    VillageTravel.toVillage(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("back").executes(ctx -> {
                    VillageTravel.toOverworld(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("rebuild").executes(ctx -> {
                    ServerLevel v = ctx.getSource().getServer().getLevel(HCDimensions.HUNTER_VILLAGE);
                    if (v == null) return 0;
                    VillageBuilder.rebuild(v);
                    ctx.getSource().sendSuccess(() -> Component.literal("Kazami village rebuilt"), true);
                    return 1;
                }))
                .then(Commands.literal("npcs").executes(ctx -> {
                    ServerLevel v = ctx.getSource().getServer().getLevel(HCDimensions.HUNTER_VILLAGE);
                    if (v == null) return 0;
                    VillageBuilder.maintain(v);
                    return 1;
                }))
                .then(Commands.literal("box").executes(ctx -> {
                    ItemBoxMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("mofu").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    CompoundTag v = MofuData.village(p);
                    if (MofuData.list(v).size() >= MofuData.MAX_COMPANIONS) return 0;
                    CompoundTag rec = MofuData.create(p.getRandom(), MofuData.nextUid(v), 5);
                    MofuData.list(v).add(rec);
                    if (v.getInt("active") == 0) v.putInt("active", rec.getInt("uid"));
                    HunterData.get(p).markDirty();
                    return 1;
                })));
    }
}
