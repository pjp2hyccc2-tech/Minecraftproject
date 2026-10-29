package com.huntcraft.village;

import com.huntcraft.core.HCDimensions;
import com.huntcraft.data.HunterData;
import com.huntcraft.village.companion.MofuData;
import com.huntcraft.village.menu.ItemBoxMenu;
import com.huntcraft.village.world.VillageBuilder;
import com.huntcraft.village.world.VillageTravel;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands: {@code /village} (any player: travel between the overworld and Kazami village, like the guild pass) and the
 * admin tools {@code /hcvillage tp|back|rebuild|npcs|box|mofu [level]|reset}.
 */
public final class VillageCommands {
    private VillageCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("village").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (HCDimensions.isQuestMap(p.level())) {
                p.displayClientMessage(Component.translatable("message.huntcraft.pass_in_quest"), true);
                return 0;
            }
            CompoundTag v = MofuData.village(p);
            long now = p.level().getGameTime();
            if (now - v.getLong("cmdTravel") < 60 && now >= v.getLong("cmdTravel")) return 0;
            v.putLong("cmdTravel", now);
            if (HCDimensions.isVillage(p.level())) VillageTravel.toOverworld(p);
            else VillageTravel.toVillage(p);
            return 1;
        }));
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
                .then(Commands.literal("reset").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    HunterData.get(p).moduleData.remove("village");
                    HunterData.get(p).markDirty();
                    ctx.getSource().sendSuccess(() -> Component.literal("Village progress reset"), false);
                    return 1;
                }))
                .then(Commands.literal("mofu")
                        .executes(ctx -> addMofu(ctx.getSource().getPlayerOrException(), 5))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, MofuData.MAX_LEVEL))
                                .executes(ctx -> addMofu(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "level"))))));
    }

    private static int addMofu(ServerPlayer p, int level) {
        CompoundTag v = MofuData.village(p);
        if (MofuData.list(v).size() >= MofuData.MAX_COMPANIONS) return 0;
        CompoundTag rec = MofuData.create(p.getRandom(), MofuData.nextUid(v), level);
        MofuData.list(v).add(rec);
        v.putBoolean("firstMofu", true);
        if (v.getInt("active") == 0) v.putInt("active", rec.getInt("uid"));
        HunterData.get(p).markDirty();
        return 1;
    }
}
