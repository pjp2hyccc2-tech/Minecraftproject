package com.reisatsu.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.reisatsu.entity.Spirit;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.game.Camera;
import com.reisatsu.game.Director;
import com.reisatsu.game.GameData;
import com.reisatsu.game.GameManager;
import com.reisatsu.game.Nights;
import com.reisatsu.game.NoiseSystem;
import com.reisatsu.game.PlayerState;
import com.reisatsu.game.Spirits;
import com.reisatsu.game.Story;
import com.reisatsu.game.Week;
import com.reisatsu.item.GameItem;
import com.reisatsu.registry.ModItems;
import com.reisatsu.world.Circuit;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Locale;

/** /reisatsu start | leave | reset | status | debug ... (debug night n: jump to the start of night n of the week) */
public final class ReisatsuCommand {
    private static final SuggestionProvider<CommandSourceStack> SPIRITS = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(SpiritKind.ALL).map(k -> k.id), b);
    private static final SuggestionProvider<CommandSourceStack> MARKERS = (c, b) ->
            SharedSuggestionProvider.suggest(Layout.all().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> EVENTS = (c, b) ->
            SharedSuggestionProvider.suggest(Director.EVENTS, b);
    private static final SuggestionProvider<CommandSourceStack> CIRCUITS = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Circuit.values()).map(Circuit::key), b);

    private ReisatsuCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("reisatsu")
                .then(Commands.literal("start").executes(c -> {
                    GameManager.startGame(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("leave").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (p.getPersistentData().contains("reisatsu_return")) GameManager.leaveGame(p);
                    return 1;
                }))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(c -> {
                    var server = c.getSource().getServer();
                    GameData data = GameData.get(server);
                    data.resetProgress();
                    ServerLevel level = GameManager.level(server);
                    if (level != null) {
                        Fixtures.resetAll(level);
                        Spirits.removeAll(level);
                        Story.resetWorld(level);
                    }
                    Director.reset();
                    Nights.reset();
                    c.getSource().sendSuccess(() -> Component.literal("reisatsu: the week is reset"), true);
                    return 1;
                }))
                .then(Commands.literal("status").executes(c -> {
                    GameData data = GameData.get(c.getSource().getServer());
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    PlayerState ps = GameManager.state(p);
                    String s = String.format(Locale.ROOT, "started=%s night=%d stage=%d phase=%d signs=%s inside=%ds captured=%s submitted=%s ofudaUsed=%d reishou=%d fear=%.2f danger=%.2f aware=%.2f room=%s photos=%d",
                            data.started, data.night, data.nightStage, data.phase, data.signs, data.enteredAt < 0 ? -1 : (data.playTicks - data.enteredAt) / 20,
                            Integer.toBinaryString(data.captured), Integer.toBinaryString(data.submitted),
                            data.ofudaUsed, data.reishou, ps.fear, ps.danger, ps.aware, ps.room, data.photos.size());
                    c.getSource().sendSuccess(() -> Component.literal(s), false);
                    return 1;
                }))
                .then(Commands.literal("debug").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("peaceful").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            GameManager.debugPeaceful = BoolArgumentType.getBool(c, "on");
                            return 1;
                        })))
                        .then(Commands.literal("fear").then(Commands.argument("fear", FloatArgumentType.floatArg(0, 1))
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
                        .then(Commands.literal("film").then(Commands.argument("n", IntegerArgumentType.integer(0, 99)).executes(c -> {
                            ItemStack cam = GameManager.camera(c.getSource().getPlayerOrException());
                            if (!cam.isEmpty()) GameItem.setFilm(cam, IntegerArgumentType.getInteger(c, "n"));
                            return 1;
                        })))
                        .then(Commands.literal("spawn").then(Commands.argument("spirit", StringArgumentType.word()).suggests(SPIRITS)
                                .executes(c -> spawn(c, 4.0f))
                                .then(Commands.argument("distance", FloatArgumentType.floatArg(0.5f, 30.0f))
                                        .executes(c -> spawn(c, FloatArgumentType.getFloat(c, "distance"))))))
                        .then(Commands.literal("pose").then(Commands.argument("spirit", StringArgumentType.word()).suggests(SPIRITS)
                                .then(Commands.argument("pose", IntegerArgumentType.integer(0, 9)).executes(c -> {
                                    Spirit s = nearest(c);
                                    if (s == null) return 0;
                                    s.freeze(IntegerArgumentType.getInteger(c, "pose"));
                                    return 1;
                                }))))
                        .then(Commands.literal("face").then(Commands.argument("spirit", StringArgumentType.word()).suggests(SPIRITS)
                                .then(Commands.argument("yaw", FloatArgumentType.floatArg(-720, 720)).executes(c -> {
                                    Spirit s = nearest(c);
                                    if (s == null) return 0;
                                    s.faceYaw(FloatArgumentType.getFloat(c, "yaw"));
                                    return 1;
                                }))
                                .executes(c -> {
                                    Spirit s = nearest(c);
                                    if (s == null) return 0;
                                    s.faceTowards(c.getSource().getPlayerOrException().getEyePosition());
                                    return 1;
                                })))
                        .then(Commands.literal("release").then(Commands.argument("spirit", StringArgumentType.word()).suggests(SPIRITS).executes(c -> {
                            Spirit s = nearest(c);
                            if (s == null) return 0;
                            s.unfreeze();
                            return 1;
                        })))
                        .then(Commands.literal("shown").then(Commands.argument("spirit", StringArgumentType.word()).suggests(SPIRITS)
                                .then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                                    Spirit s = nearest(c);
                                    if (s == null) return 0;
                                    s.debugShown = BoolArgumentType.getBool(c, "on");
                                    return 1;
                                }))))
                        .then(Commands.literal("awareness").then(Commands.argument("spirit", StringArgumentType.word()).suggests(SPIRITS)
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0, 1)).executes(c -> {
                                    Spirit s = nearest(c);
                                    if (s == null) return 0;
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    float v = FloatArgumentType.getFloat(c, "value");
                                    if (v >= 1.0f) s.raise(p, 1.0f);
                                    else s.setAwareness(p, v);
                                    return 1;
                                }))))
                        .then(Commands.literal("validate").then(Commands.argument("spirit", StringArgumentType.word()).suggests(SPIRITS).executes(c -> {
                            Spirit s = nearest(c);
                            if (s == null) return 0;
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Vec3 eye = p.getEyePosition();
                            Vec3 h = Camera.head(s);
                            float yaw = (float) (Mth.atan2(h.z - eye.z, h.x - eye.x) * Mth.RAD_TO_DEG) - 90.0f;
                            float pitch = (float) -(Mth.atan2(h.y - eye.y, Math.sqrt((h.x - eye.x) * (h.x - eye.x) + (h.z - eye.z) * (h.z - eye.z))) * Mth.RAD_TO_DEG);
                            Camera.Shot shot = Camera.validate(p.serverLevel(), p, eye, yaw, pitch, 70.0f, 0.0f);
                            c.getSource().sendSuccess(() -> Component.literal("verdict: " + shot.result() + " " + shot.reason() + " ★" + shot.stars()), false);
                            return 1;
                        })))
                        .then(Commands.literal("capture").then(Commands.argument("spirit", StringArgumentType.word()).suggests(SPIRITS).executes(c -> {
                            Spirit s = nearest(c);
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            if (s == null) return 0;
                            Camera.take(p.serverLevel(), p, "debug" + System.currentTimeMillis(), new Camera.Shot(GameData.RESULT_CAPTURED, s, 3, "debug"));
                            return 1;
                        })))
                        .then(Commands.literal("caught").then(Commands.argument("spirit", StringArgumentType.word()).suggests(SPIRITS).executes(c -> {
                            SpiritKind k = SpiritKind.byId(StringArgumentType.getString(c, "spirit"));
                            if (k == null) return 0;
                            GameManager.caughtBy(c.getSource().getPlayerOrException(), k);
                            return 1;
                        })))
                        .then(Commands.literal("circuit").then(Commands.argument("circuit", StringArgumentType.word()).suggests(CIRCUITS)
                                .then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                                    ServerLevel level = GameManager.level(c.getSource().getServer());
                                    if (level == null) return 0;
                                    for (Circuit ci : Circuit.values()) {
                                        if (ci.key().equals(StringArgumentType.getString(c, "circuit"))) {
                                            com.reisatsu.game.Breaker.switchCircuit(level, ci, BoolArgumentType.getBool(c, "on"), null);
                                            return 1;
                                        }
                                    }
                                    return 0;
                                }))))
                        .then(Commands.literal("tp").then(Commands.argument("marker", StringArgumentType.word()).suggests(MARKERS)
                                .executes(c -> tp(c, 0))
                                .then(Commands.argument("index", IntegerArgumentType.integer(0)).executes(c -> tp(c, IntegerArgumentType.getInteger(c, "index"))))))
                        .then(Commands.literal("event").then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            boolean ok = Director.run(p.serverLevel(), p, StringArgumentType.getString(c, "event"));
                            c.getSource().sendSuccess(() -> Component.literal("event: " + ok), false);
                            return ok ? 1 : 0;
                        })))
                        .then(Commands.literal("noise").then(Commands.argument("radius", FloatArgumentType.floatArg(0, 64)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            NoiseSystem.emit(p.serverLevel(), p.position(), FloatArgumentType.getFloat(c, "radius"), "debug", p);
                            return 1;
                        })))
                        .then(Commands.literal("night").then(Commands.argument("night", IntegerArgumentType.integer(1, Week.NIGHTS)).executes(c -> {
                            ServerLevel level = GameManager.level(c.getSource().getServer());
                            if (level == null) return 0;
                            Nights.jump(level, GameData.get(c.getSource().getServer()), IntegerArgumentType.getInteger(c, "night"));
                            return 1;
                        })))
                        .then(Commands.literal("manifest").executes(c -> {
                            ServerLevel level = GameManager.level(c.getSource().getServer());
                            if (level == null) return 0;
                            Nights.manifest(level, GameData.get(c.getSource().getServer()));
                            return 1;
                        }))
                        .then(Commands.literal("talk").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ServerLevel level = GameManager.level(c.getSource().getServer());
                            var m = level == null ? null : Spirits.medium(level);
                            if (m == null) return 0;
                            Story.skipDialogue(p);
                            Story.talk(p, m);
                            return 1;
                        }))
                        .then(Commands.literal("closing").executes(c -> {
                            // the last night's closing time, right here (the five before it done)
                            ServerLevel level = GameManager.level(c.getSource().getServer());
                            if (level == null) return 0;
                            Nights.debugClosing(level, GameData.get(c.getSource().getServer()));
                            return 1;
                        }))
                        .then(Commands.literal("exorcism").executes(c -> {
                            ServerLevel level = GameManager.level(c.getSource().getServer());
                            if (level == null) return 0;
                            GameData data = GameData.get(c.getSource().getServer());
                            for (SpiritKind k : SpiritKind.ALL) {
                                data.captured |= k.bit();
                                data.submitted |= k.bit();
                            }
                            for (Spirit s : Spirits.all(level)) s.discard();
                            Nights.debugLastNight(data);
                            Story.startExorcism(level, data);
                            return 1;
                        }))
                        .then(Commands.literal("ending").then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"good", "bad"}, b)).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    GameData data = GameData.get(c.getSource().getServer());
                                    PlayerState ps = GameManager.state(p);
                                    if (StringArgumentType.getString(c, "kind").equals("bad")) {
                                        Story.startBadEnding(p.serverLevel(), p, ps, data);
                                    } else {
                                        data.phase = GameData.PHASE_ENDING;
                                        data.ending = GameData.ENDING_GOOD;
                                        ps.endingTicks = 0;
                                        GameManager.event(p, GameManager.EV_ENDING, GameData.ENDING_GOOD, String.valueOf(data.ofudaUsed));
                                    }
                                    return 1;
                                })))
                        .then(Commands.literal("skipintro").executes(c -> {
                            // the intro, or the medium's lines at the start / end of a night
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            GameData data = GameData.get(c.getSource().getServer());
                            if (Nights.inOpening() || Nights.inEnding()) {
                                Nights.skip(p, true);
                                return 1;
                            }
                            GameManager.event(p, GameManager.EV_INTRO, -1, "");
                            Story.finishIntro(p, data);
                            return 1;
                        }))
                ));
    }

    @Nullable
    private static Spirit nearest(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        SpiritKind k = SpiritKind.byId(StringArgumentType.getString(c, "spirit"));
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = GameManager.level(c.getSource().getServer());
        if (k == null || level == null) return null;
        Spirit best = null;
        double bd = Double.MAX_VALUE;
        for (Spirit s : Spirits.all(level)) {
            if (s.kind() != k) continue;
            double d = s.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        if (best == null) c.getSource().sendFailure(Component.literal("no " + k.id + " here"));
        return best;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, float distance) throws CommandSyntaxException {
        SpiritKind k = SpiritKind.byId(StringArgumentType.getString(c, "spirit"));
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (k == null) return 0;
        Vec3 look = p.getViewVector(1.0f).multiply(1, 0, 1).normalize();
        Vec3 at = p.position().add(look.scale(distance));
        Spirit s = Spirits.spawnAt(p.serverLevel(), k, new Vec3(at.x, Layout.FEET_Y, at.z), p.getYRot() + 180.0f);
        if (s == null) return 0;
        s.faceTowards(p.getEyePosition());
        s.freeze(0);
        return 1;
    }

    private static int tp(CommandContext<CommandSourceStack> c, int index) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "marker");
        if (!Layout.has(name)) {
            c.getSource().sendFailure(Component.literal("unknown marker " + name));
            return 0;
        }
        var list = Layout.markers(name);
        Layout.Marker m = list.get(Math.min(index, list.size() - 1));
        ServerLevel level = GameManager.level(c.getSource().getServer());
        if (level == null) return 0;
        GameManager.teleport(p, level, m.center(), m.yaw(), 0.0f);
        return 1;
    }
}
