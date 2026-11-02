package com.meikon.command;

import com.meikon.entity.Hanayome;
import com.meikon.entity.Ichimatsu;
import com.meikon.entity.Kageboshi;
import com.meikon.game.Closet;
import com.meikon.game.Director;
import com.meikon.game.GameData;
import com.meikon.game.GameManager;
import com.meikon.game.Mansion;
import com.meikon.game.PlayerState;
import com.meikon.game.Room;
import com.meikon.item.LanternItem;
import com.meikon.registry.ModEntities;
import com.meikon.registry.ModItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** /meikon start | leave | reset | debug ... */
public final class MeikonCommand {
    private MeikonCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("meikon")
                .then(Commands.literal("start").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!GameManager.inGame(p)) GameManager.startGame(p);
                    return 1;
                }))
                .then(Commands.literal("leave").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (GameManager.inGame(p) || p.getPersistentData().contains("meikon_return")) GameManager.leaveGame(p, false);
                    return 1;
                }))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerLevel level = GameManager.kakuriyo(c.getSource().getServer());
                    if (level == null) return 0;
                    GameData data = GameData.get(c.getSource().getServer());
                    GameManager.newMansion(level, data, level.getRandom().nextLong());
                    for (ServerPlayer p : level.players()) {
                        GameManager.stateOrCreate(p).resetAfterDeath();
                        GameManager.toSpawn(p);
                    }
                    c.getSource().sendSuccess(() -> Component.literal("mansion rebuilt (seed " + data.seed + ")"), true);
                    return 1;
                }))
                .then(Commands.literal("debug").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("peaceful").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            GameManager.debugPeaceful = BoolArgumentType.getBool(c, "on");
                            return 1;
                        })))
                        .then(Commands.literal("bells").then(Commands.argument("n", IntegerArgumentType.integer(0, 7)).executes(c -> {
                            GameData data = GameData.get(c.getSource().getServer());
                            data.bells = IntegerArgumentType.getInteger(c, "n");
                            data.setDirty();
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            int have = 0;
                            for (ItemStack s : p.getInventory().items) if (s.is(ModItems.SUZU.get())) have += s.getCount();
                            if (have < data.bells) p.getInventory().add(new ItemStack(ModItems.SUZU.get(), data.bells - have));
                            return 1;
                        })))
                        .then(Commands.literal("fear").then(Commands.argument("fear", FloatArgumentType.floatArg(0, 100))
                                .then(Commands.argument("danger", FloatArgumentType.floatArg(0, 1))
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 20 * 600)).executes(c -> {
                                            PlayerState ps = GameManager.stateOrCreate(c.getSource().getPlayerOrException());
                                            ps.forceFear = FloatArgumentType.getFloat(c, "fear");
                                            ps.forceDanger = FloatArgumentType.getFloat(c, "danger");
                                            ps.forceTicks = IntegerArgumentType.getInteger(c, "ticks");
                                            ps.fear = ps.forceFear;
                                            ps.danger = ps.forceDanger;
                                            return 1;
                                        })))))
                        .then(Commands.literal("spawn").then(Commands.argument("what", StringArgumentType.word()).executes(MeikonCommand::spawn)))
                        .then(Commands.literal("event").then(Commands.argument("type", IntegerArgumentType.integer(0, 99))
                                .then(Commands.argument("arg", IntegerArgumentType.integer(-1, 99)).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    GameManager.event(p, IntegerArgumentType.getInteger(c, "type"), IntegerArgumentType.getInteger(c, "arg"),
                                            p.getGameProfile().getName());
                                    return 1;
                                }))))
                        .then(Commands.literal("note").then(Commands.argument("id", IntegerArgumentType.integer(1, 12)).executes(c -> {
                            GameManager.openNote(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "id"));
                            return 1;
                        })))
                        .then(Commands.literal("tp").then(Commands.argument("where", StringArgumentType.word()).executes(MeikonCommand::tp)))
                        .then(Commands.literal("bride").then(Commands.argument("state", StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Hanayome b = GameManager.anyBride(p.serverLevel());
                            if (b == null) return 0;
                            b.force(Hanayome.State.valueOf(StringArgumentType.getString(c, "state").toUpperCase(java.util.Locale.ROOT)), p.position());
                            return 1;
                        })))
                        .then(Commands.literal("lantern").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ItemStack s = new ItemStack(ModItems.CHOCHIN_LIT.get());
                            LanternItem.setOil(s, LanternItem.MAX_OIL);
                            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, s);
                            return 1;
                        }))
                        .then(Commands.literal("clear").executes(c -> {
                            ServerLevel level = c.getSource().getPlayerOrException().serverLevel();
                            for (Kageboshi k : level.getEntitiesOfClass(Kageboshi.class, new net.minecraft.world.phys.AABB(-80, 0, -80, 80, 200, 80))) k.discard();
                            for (var m : level.getEntitiesOfClass(com.meikon.entity.Maboroshi.class, new net.minecraft.world.phys.AABB(-80, 0, -80, 80, 200, 80))) m.discard();
                            return 1;
                        }))
                        .then(Commands.literal("state").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            PlayerState ps = GameManager.state(p);
                            GameData data = GameData.get(p.server);
                            Hanayome b = GameManager.anyBride(p.serverLevel());
                            String s = "active=" + data.active + " bells=" + data.bells + " phase=" + GameManager.phase()
                                    + (ps == null ? " (no state)" : String.format(" fear=%.1f stamina=%.0f danger=%.2f closet=%d safe=%s",
                                    ps.fear, ps.stamina, ps.danger, ps.closet, ps.inSafeRoom))
                                    + (b == null ? " bride=none" : " bride=" + b.state() + " @" + b.blockPosition().toShortString());
                            c.getSource().sendSuccess(() -> Component.literal(s), false);
                            return 1;
                        }))));
    }

    private static int spawn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        String what = StringArgumentType.getString(c, "what");
        Vec3 look = p.getViewVector(1.0f);
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        switch (what) {
            case "bride", "bride_far" -> {
                double dist = what.equals("bride") ? 3.2 : 12.0;
                Hanayome b = GameManager.anyBride(level);
                if (b == null) {
                    b = ModEntities.HANAYOME.get().create(level);
                    if (b == null) return 0;
                    level.addFreshEntity(b);
                }
                Vec3 at = p.position().add(flat.scale(dist));
                float yaw = p.getYRot() + 180.0f;
                b.moveTo(at.x, p.getY(), at.z, yaw, 0);
                b.setYHeadRot(yaw);
                b.setYBodyRot(yaw);
                b.force(Hanayome.State.IDLE, null);
            }
            case "doll" -> {
                Ichimatsu d = ModEntities.ICHIMATSU.get().create(level);
                if (d == null) return 0;
                Vec3 at = p.position().add(flat.scale(2.5));
                d.moveTo(at.x, p.getY(), at.z, p.getYRot() + 180.0f, 0);
                d.setYHeadRot(p.getYRot() + 180.0f);
                d.setHome(BlockPos.containing(at), true);
                level.addFreshEntity(d);
            }
            case "shadow" -> {
                Kageboshi k = ModEntities.KAGEBOSHI.get().create(level);
                if (k == null) return 0;
                Vec3 at = p.position().add(flat.scale(5));
                k.moveTo(at.x, p.getY(), at.z, p.getYRot() + 180.0f, 0);
                level.addFreshEntity(k);
            }
            case "phantom" -> Director.spawnFar(level, p);
            case "peek" -> Director.spawnPeek(level, p);
            default -> {
                return 0;
            }
        }
        return 1;
    }

    private static int tp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Mansion m = GameManager.mansion();
        if (m == null) return 0;
        String where = StringArgumentType.getString(c, "where");
        BlockPos target = null;
        switch (where) {
            case "spawn", "safe" -> target = m.spawn;
            case "outside" -> target = m.safeOutside;
            case "courtyard" -> target = new BlockPos(0, 65, 9);
            case "torii" -> target = m.toriiCenter.south(3);
            case "kura" -> target = m.kuraDoor == null ? null : m.kuraDoor.south(3);
            case "well" -> target = m.well == null ? null : m.well.offset(0, 1, 3);
            case "closet" -> {
                if (!m.closets.isEmpty()) {
                    Closet cl = m.closets.get(0);
                    target = cl.inside();
                }
            }
            default -> {
                for (Room r : m.rooms) {
                    if (r.key().equals(where)) {
                        target = r.center();
                        break;
                    }
                }
            }
        }
        if (target == null) return 0;
        p.teleportTo(p.serverLevel(), target.getX() + 0.5, target.getY(), target.getZ() + 0.5, p.getYRot(), p.getXRot());
        return 1;
    }
}
