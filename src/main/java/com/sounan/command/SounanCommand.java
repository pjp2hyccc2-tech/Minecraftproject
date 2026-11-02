package com.sounan.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sounan.game.Director;
import com.sounan.game.GameData;
import com.sounan.game.GameManager;
import com.sounan.game.PlayerState;
import com.sounan.registry.ModEntities;
import com.sounan.registry.ModItems;
import com.sounan.world.Pois;
import com.sounan.world.TerrainData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/** /sounan start | leave | reset | tp &lt;poi&gt; | status | debug ... */
public final class SounanCommand {
    private SounanCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("sounan")
                .then(Commands.literal("start").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    GameManager.startGame(p);
                    return 1;
                }))
                .then(Commands.literal("leave").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (GameManager.inGame(p) || p.getPersistentData().contains("sounan_return")) GameManager.leaveGame(p);
                    return 1;
                }))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(c -> {
                    GameData.get(c.getSource().getServer()).reset();
                    c.getSource().sendSuccess(() -> Component.literal("progress reset (the mountain itself keeps its changes)"), true);
                    return 1;
                }))
                .then(Commands.literal("status").executes(c -> {
                    GameData data = GameData.get(c.getSource().getServer());
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    PlayerState ps = GameManager.state(p);
                    String s = String.format("started=%s bag=%s roped=%s omiki=%s offered=%s key=%s gate=%s signal=%s captures=%d cp=%s "
                                    + "fear=%.0f danger=%.2f flash=%.0f phone=%.0f area=%s",
                            data.started, data.bagOpened, data.roped, data.omikiTaken, data.offered, data.keyTaken, data.gateUnlocked,
                            data.signal, data.captures, data.checkpoint, ps.fear, ps.danger, ps.flash, ps.phone,
                            TerrainData.areaKey(Math.max(0, ps.area)));
                    c.getSource().sendSuccess(() -> Component.literal(s), false);
                    return 1;
                }))
                .then(Commands.literal("tp").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("poi", StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            String id = StringArgumentType.getString(c, "poi");
                            Pois pois = Pois.get();
                            Pois.Point pt = pois.points.get(id);
                            if (pt == null) pt = pois.checkpoint(id);
                            if (pt == null) {
                                c.getSource().sendFailure(Component.literal("unknown poi; known: " + String.join(", ", pois.points.keySet())));
                                return 0;
                            }
                            ServerLevel level = GameManager.mountain(c.getSource().getServer());
                            if (level == null) return 0;
                            GameManager.teleport(p, level, pt.center(), pt.facing().toYRot(), 0.0f);
                            return 1;
                        })))
                .then(Commands.literal("debug").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("peaceful").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            GameManager.debugPeaceful = BoolArgumentType.getBool(c, "on");
                            if (GameManager.debugPeaceful) Director.clearAll();
                            return 1;
                        })))
                        .then(Commands.literal("cinematic").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            GameManager.cinematic = BoolArgumentType.getBool(c, "on");
                            return 1;
                        })))
                        .then(Commands.literal("fear").then(Commands.argument("fear", FloatArgumentType.floatArg(0, 100))
                                .then(Commands.argument("danger", FloatArgumentType.floatArg(0, 1))
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0)).executes(c -> {
                                            PlayerState ps = GameManager.state(c.getSource().getPlayerOrException());
                                            ps.forceFear = FloatArgumentType.getFloat(c, "fear");
                                            ps.forceDanger = FloatArgumentType.getFloat(c, "danger");
                                            ps.forceTicks = IntegerArgumentType.getInteger(c, "ticks");
                                            return 1;
                                        })))))
                        .then(Commands.literal("give").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            for (RegistryObject<net.minecraft.world.item.Item> it : ModItems.GAME_ITEMS) p.getInventory().add(new ItemStack(it.get()));
                            return 1;
                        }))
                        .then(Commands.literal("flag").then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                                    GameData data = GameData.get(c.getSource().getServer());
                                    boolean on = BoolArgumentType.getBool(c, "on");
                                    switch (StringArgumentType.getString(c, "name")) {
                                        case "bag" -> data.bagOpened = on;
                                        case "roped" -> data.roped = on;
                                        case "omiki" -> data.omikiTaken = on;
                                        case "offered" -> data.offered = on;
                                        case "key" -> data.keyTaken = on;
                                        case "gate" -> data.gateUnlocked = on;
                                        case "signal" -> data.signal = on;
                                        case "climax" -> data.climax = on;
                                        case "started" -> data.started = on;
                                        default -> {
                                            return 0;
                                        }
                                    }
                                    data.setDirty();
                                    return 1;
                                }))))
                        .then(Commands.literal("ending").then(Commands.argument("kind", IntegerArgumentType.integer(1, 2)).executes(c -> {
                            GameManager.startEnding(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "kind"));
                            return 1;
                        })))
                        .then(Commands.literal("capture").then(Commands.argument("kind", IntegerArgumentType.integer(0, 4)).executes(c -> {
                            GameManager.beginCapture(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "kind"), null);
                            return 1;
                        })))
                        .then(Commands.literal("scene").then(Commands.argument("monster", StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            boolean ok = Director.scene(p.serverLevel(), p, StringArgumentType.getString(c, "monster"));
                            c.getSource().sendSuccess(() -> Component.literal("scene: " + ok), false);
                            return ok ? 1 : 0;
                        })))
                        .then(Commands.literal("spawn").then(Commands.argument("monster", StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            EntityType<? extends Mob> type = switch (StringArgumentType.getString(c, "monster")) {
                                case "nozoki" -> ModEntities.NOZOKI.get();
                                case "yobigoe" -> ModEntities.YOBIGOE.get();
                                case "ushiro" -> ModEntities.USHIRO.get();
                                case "koyanushi" -> ModEntities.KOYANUSHI.get();
                                case "oyamasama" -> ModEntities.OYAMASAMA.get();
                                case "utsurumono" -> ModEntities.UTSURUMONO.get();
                                default -> null;
                            };
                            if (type == null) return 0;
                            Mob m = type.create(p.level());
                            if (m == null) return 0;
                            Vec3 at = p.position().add(p.getViewVector(1.0f).multiply(1, 0, 1).normalize().scale(5));
                            m.moveTo(at.x, p.getY(), at.z, p.getYRot() + 180.0f, 0.0f);
                            p.level().addFreshEntity(m);
                            return 1;
                        }))))
        );
    }
}
