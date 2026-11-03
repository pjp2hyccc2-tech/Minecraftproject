package com.hitori.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.hitori.entity.OniDoll;
import com.hitori.entity.Woman;
import com.hitori.game.Apt;
import com.hitori.game.Cameras;
import com.hitori.game.Clock;
import com.hitori.game.Director;
import com.hitori.game.Doll;
import com.hitori.game.Endings;
import com.hitori.game.GameData;
import com.hitori.game.GameManager;
import com.hitori.game.Hiding;
import com.hitori.game.Phone;
import com.hitori.game.PlayerState;
import com.hitori.game.Recording;
import com.hitori.game.Ritual;
import com.hitori.game.Step;
import com.hitori.item.GameItem;
import com.hitori.registry.ModItems;
import com.hitori.world.Layout;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /hitori start | leave | reset | status | debug ... — debug: phase and step jumps, the clock, the doll (spawn, pose,
 * state, suspicion, wake), the woman, the director's events, the endings, cameras at the suggested spots, hiding,
 * the mouth, the fixtures.
 */
public final class HitoriCommand {
    private static final SuggestionProvider<CommandSourceStack> STEPS = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Step.ALL).map(s -> s.id), b);
    private static final SuggestionProvider<CommandSourceStack> EVENTS = (c, b) -> SharedSuggestionProvider.suggest(Director.EVENTS, b);
    private static final SuggestionProvider<CommandSourceStack> STATES = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(OniDoll.State.values()).map(s -> s.name().toLowerCase(Locale.ROOT)), b);
    private static final SuggestionProvider<CommandSourceStack> ENDINGS = (c, b) ->
            SharedSuggestionProvider.suggest(List.of("good", "oni", "soto", "mikan"), b);
    private static final SuggestionProvider<CommandSourceStack> PHASES = (c, b) ->
            SharedSuggestionProvider.suggest(List.of("prep", "ritual", "hiding", "finish"), b);

    private HitoriCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("hitori")
                .then(Commands.literal("start").executes(c -> {
                    GameManager.startGame(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("leave").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (p.getPersistentData().contains("hitori_return")) GameManager.leaveGame(p);
                    return 1;
                }))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(c -> {
                    var server = c.getSource().getServer();
                    GameData data = GameData.get(server);
                    data.resetProgress();
                    ServerLevel level = GameManager.level(server);
                    if (level != null) {
                        Apt.resetAll(level);
                        Doll.removeAll(level);
                        Cameras.removeAll(level);
                        Ritual.resetWorld(level);
                        Woman w = Director.womanEntity(level);
                        if (w != null) w.discard();
                    }
                    Director.reset();
                    Endings.reset();
                    c.getSource().sendSuccess(() -> Component.literal("hitori: the night is reset"), true);
                    return 1;
                }))
                .then(Commands.literal("status").executes(c -> {
                    GameData data = GameData.get(c.getSource().getServer());
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    PlayerState ps = GameManager.state(p);
                    ServerLevel level = GameManager.level(c.getSource().getServer());
                    OniDoll doll = level == null ? null : Doll.get(level);
                    String s = String.format(Locale.ROOT, "started=%s phase=%d clock=%s hud=%d steps=%s name=%s broken=%s cams=%d fear=%.2f danger=%.2f mouth=%.2f hide=%s app=%d room=%s doll=%s",
                            data.started, data.phase, Clock.hhmm(data.clock), Step.hudLine(data), Long.toBinaryString(data.steps), data.dollName,
                            data.broken, data.cameras.size(), ps.fear, ps.danger, ps.mouth, ps.hideSpot, ps.appCamera, ps.room,
                            doll == null ? "-" : doll.describe());
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
                            for (RegistryObject<Item> it : ModItems.GAME_ITEMS) p.getInventory().add(new ItemStack(it.get()));
                            return 1;
                        }))
                        .then(Commands.literal("mouth").then(Commands.argument("v", FloatArgumentType.floatArg(0, 1)).executes(c -> {
                            GameManager.state(c.getSource().getPlayerOrException()).mouth = FloatArgumentType.getFloat(c, "v");
                            return 1;
                        })))
                        .then(Commands.literal("clock").then(Commands.argument("hhmm", StringArgumentType.word()).executes(c -> {
                            String[] hm = StringArgumentType.getString(c, "hhmm").split(":");
                            GameData data = GameData.get(c.getSource().getServer());
                            data.clock = Integer.parseInt(hm[0]) * 3600 + (hm.length > 1 ? Integer.parseInt(hm[1]) * 60 : 0);
                            data.setDirty();
                            return 1;
                        })))
                        .then(Commands.literal("phase").then(Commands.argument("phase", StringArgumentType.word()).suggests(PHASES)
                                .executes(c -> phase(c, StringArgumentType.getString(c, "phase")))))
                        .then(Commands.literal("step").then(Commands.argument("step", StringArgumentType.word()).suggests(STEPS)
                                .executes(c -> step(c, StringArgumentType.getString(c, "step")))))
                        .then(Commands.literal("cameras").executes(HitoriCommand::cameras))
                        .then(Commands.literal("doll")
                                .then(Commands.literal("spawn").executes(c -> dollSpawn(c, "here"))
                                        .then(Commands.argument("where", StringArgumentType.word()).executes(c -> dollSpawn(c, StringArgumentType.getString(c, "where")))))
                                .then(Commands.literal("pose").then(Commands.argument("pose", IntegerArgumentType.integer(0, 9)).executes(c -> {
                                    OniDoll doll = doll(c);
                                    if (doll != null) {
                                        doll.setState(OniDoll.State.SCRIPTED);
                                        doll.setAnimPose(IntegerArgumentType.getInteger(c, "pose"));
                                    }
                                    return 1;
                                })))
                                .then(Commands.literal("state").then(Commands.argument("state", StringArgumentType.word()).suggests(STATES).executes(c -> {
                                    OniDoll doll = doll(c);
                                    if (doll == null) return 0;
                                    OniDoll.State st = OniDoll.State.valueOf(StringArgumentType.getString(c, "state").toUpperCase(Locale.ROOT));
                                    switch (st) {
                                        case TUB -> doll.wake();
                                        case WAIT -> doll.startWaiting();
                                        case HUNT -> doll.startHunting();
                                        default -> doll.setState(st);
                                    }
                                    return 1;
                                })))
                                .then(Commands.literal("still").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                                    OniDoll doll = doll(c);
                                    if (doll != null) doll.debugStill = BoolArgumentType.getBool(c, "on");
                                    return 1;
                                })))
                                .then(Commands.literal("suspicion").then(Commands.argument("v", FloatArgumentType.floatArg(0, 1)).executes(c -> {
                                    OniDoll doll = doll(c);
                                    if (doll != null) doll.suspicion = FloatArgumentType.getFloat(c, "v");
                                    return 1;
                                })))
                                .then(Commands.literal("face").executes(c -> {
                                    OniDoll doll = doll(c);
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    if (doll != null) {
                                        float yaw = OniDoll.yawTo(doll.position(), p.position());
                                        doll.setYRot(yaw);
                                        doll.yBodyRot = yaw;
                                        doll.setYHeadRot(yaw);
                                    }
                                    return 1;
                                }))
                                .then(Commands.literal("tub").executes(c -> {
                                    OniDoll doll = doll(c);
                                    if (doll != null) doll.wake();
                                    return 1;
                                }))
                                .then(Commands.literal("remove").executes(c -> {
                                    ServerLevel level = GameManager.level(c.getSource().getServer());
                                    if (level != null) Doll.removeAll(level);
                                    Doll.forget(GameData.get(c.getSource().getServer()));
                                    return 1;
                                })))
                        .then(Commands.literal("woman")
                                .then(Commands.argument("camera", IntegerArgumentType.integer(1, 4)).executes(c -> {
                                    ServerLevel level = GameManager.level(c.getSource().getServer());
                                    if (level == null) return 0;
                                    return Director.debugWoman(level, c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "camera")) ? 1 : 0;
                                }))
                                .then(Commands.literal("closer").executes(c -> {
                                    ServerLevel level = GameManager.level(c.getSource().getServer());
                                    if (level == null) return 0;
                                    Woman w = Director.womanEntity(level);
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    if (w != null) Director.onCameraShown(level, p, GameManager.state(p), 99, w.haunts());
                                    return 1;
                                }))
                                .then(Commands.literal("remove").executes(c -> {
                                    ServerLevel level = GameManager.level(c.getSource().getServer());
                                    Woman w = level == null ? null : Director.womanEntity(level);
                                    if (w != null) w.discard();
                                    return 1;
                                })))
                        .then(Commands.literal("event").then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS).executes(c -> {
                            ServerLevel level = GameManager.level(c.getSource().getServer());
                            if (level == null) return 0;
                            return Director.run(level, c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "event")) ? 1 : 0;
                        })))
                        .then(Commands.literal("director").then(Commands.argument("paused", BoolArgumentType.bool()).executes(c -> {
                            Director.paused = BoolArgumentType.getBool(c, "paused");
                            return 1;
                        })))
                        .then(Commands.literal("ending").then(Commands.argument("ending", StringArgumentType.word()).suggests(ENDINGS).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            int k = switch (StringArgumentType.getString(c, "ending")) {
                                case "good" -> GameData.ENDING_GOOD;
                                case "oni" -> GameData.ENDING_ONI;
                                case "soto" -> GameData.ENDING_SOTO;
                                default -> GameData.ENDING_MIKAN;
                            };
                            if (k == GameData.ENDING_ONI) {
                                ServerLevel level = p.serverLevel();
                                GameManager.caughtByDoll(p, Doll.get(level));
                            } else {
                                Endings.begin(p.serverLevel(), p, k);
                            }
                            return 1;
                        })))
                        .then(Commands.literal("hide").executes(c -> hide(c, "oshiire"))
                                .then(Commands.argument("where", StringArgumentType.word()).executes(c -> hide(c, StringArgumentType.getString(c, "where")))))
                        .then(Commands.literal("unhide").executes(c -> {
                            Hiding.exit(c.getSource().getPlayerOrException(), true);
                            return 1;
                        }))
                        .then(Commands.literal("peek").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            Hiding.peek(c.getSource().getPlayerOrException(), BoolArgumentType.getBool(c, "on"));
                            return 1;
                        })))
                        .then(Commands.literal("call").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            PlayerState ps = GameManager.state(p);
                            ps.ringTicks = 0;
                            GameManager.event(p, GameManager.EV_CALL, 1, "");
                            return 1;
                        }))
                        .then(Commands.literal("message").then(Commands.argument("n", IntegerArgumentType.integer(1, 8)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            GameManager.event(p, GameManager.EV_MESSAGE, 0, "msg.hitori." + IntegerArgumentType.getInteger(c, "n"));
                            return 1;
                        })))
                        .then(Commands.literal("light").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Phone.toggleLight(p, GameManager.state(p));
                            return 1;
                        }))
                        .then(Commands.literal("fixture")
                                .then(Commands.literal("tv").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                                    ServerLevel level = GameManager.level(c.getSource().getServer());
                                    if (level != null) Apt.setTv(level, BoolArgumentType.getBool(c, "on"));
                                    return 1;
                                })))
                                .then(Commands.literal("lights").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                                    ServerLevel level = GameManager.level(c.getSource().getServer());
                                    if (level != null) Apt.allLights(level, BoolArgumentType.getBool(c, "on"));
                                    return 1;
                                })))
                                .then(Commands.literal("bath").then(Commands.argument("water", IntegerArgumentType.integer(0, 2)).executes(c -> {
                                    ServerLevel level = GameManager.level(c.getSource().getServer());
                                    if (level != null) Apt.setBathWater(level, IntegerArgumentType.getInteger(c, "water"));
                                    return 1;
                                })))
                                .then(Commands.literal("fusuma").then(Commands.argument("open", IntegerArgumentType.integer(0, 2)).executes(c -> {
                                    ServerLevel level = GameManager.level(c.getSource().getServer());
                                    if (level != null) Apt.setClosetFusuma(level, IntegerArgumentType.getInteger(c, "open"));
                                    return 1;
                                }))))
                        .then(Commands.literal("log").executes(c -> {
                            String s = "director: " + String.join(", ", Director.LOG) + " | recording: " + String.join(", ", Recording.LOG);
                            c.getSource().sendSuccess(() -> Component.literal(s), false);
                            return 1;
                        }))));
    }

    private static OniDoll doll(CommandContext<CommandSourceStack> c) {
        ServerLevel level = GameManager.level(c.getSource().getServer());
        return level == null ? null : Doll.get(level);
    }

    private static int dollSpawn(CommandContext<CommandSourceStack> c, String where) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = GameManager.level(c.getSource().getServer());
        if (level == null) return 0;
        GameData data = GameData.get(c.getSource().getServer());
        OniDoll doll;
        if (where.equals("tub")) {
            doll = Doll.spawnInTub(level, data);
        } else if (where.startsWith("cam")) {
            // on the floor in front of camera n, facing it (in its feed)
            int n = where.length() > 3 ? Integer.parseInt(where.substring(3)) : 1;
            com.hitori.entity.FixedCamera cam = Cameras.entity(level, n);
            if (cam == null) return 0;
            Vec3 at = inView(level, cam);
            doll = at == null ? null : Doll.spawnAt(level, data, at, OniDoll.yawTo(at, cam.position()));
            if (doll != null) {
                doll.setKnife(true);
                doll.setState(OniDoll.State.SCRIPTED);
                doll.setAnimPose(OniDoll.STAND);
            }
        } else if (where.startsWith("hide")) {
            int i = where.length() > 4 ? Integer.parseInt(where.substring(4)) : 0;
            Layout.Marker m = Layout.markers("doll_hides").get(Math.min(i, Layout.markers("doll_hides").size() - 1));
            doll = Doll.spawnAt(level, data, m.center(), m.yaw());
            if (doll != null) doll.setState(OniDoll.State.SCRIPTED);
        } else {
            Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5));
            doll = Doll.spawnAt(level, data, new Vec3(at.x, p.getY(), at.z), OniDoll.yawTo(at, p.position()));
            if (doll != null) {
                doll.setKnife(true);
                doll.setState(OniDoll.State.SCRIPTED);
                doll.setAnimPose(OniDoll.STAND);
            }
        }
        return doll != null ? 1 : 0;
    }

    /** A floor spot 1.6-3 blocks out along the camera's view that it sees, or null. */
    @javax.annotation.Nullable
    private static Vec3 inView(ServerLevel level, com.hitori.entity.FixedCamera cam) {
        Vec3 eye = cam.getEyePosition();
        Vec3 f = Vec3.directionFromRotation(0.0f, cam.getYRot());
        for (double d = 3.0; d >= 1.6; d -= 0.35) {
            Vec3 p = eye.add(f.scale(d));
            net.minecraft.core.BlockPos b = net.minecraft.core.BlockPos.containing(p.x, Layout.FEET_Y + 1, p.z);
            for (int i = 0; i < 5 && level.getBlockState(b.below()).getCollisionShape(level, b.below()).isEmpty(); i++) b = b.below();
            if (!level.getBlockState(b).getCollisionShape(level, b).isEmpty()) continue;
            net.minecraft.core.BlockPos under = b.below();
            double floor = under.getY() + level.getBlockState(under).getCollisionShape(level, under).max(net.minecraft.core.Direction.Axis.Y);
            Vec3 at = new Vec3(p.x, floor, p.z);
            if (com.hitori.game.Watch.cameraSees(cam, at.add(0.0, 0.3, 0.0))) return at;
        }
        return null;
    }

    private static int hide(CommandContext<CommandSourceStack> c, String where) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        boolean western = where.equals("western");
        return Hiding.enter(p, Layout.marker(western ? "western_closet" : "closet_hide").pos(), western ? "western" : "oshiire") ? 1 : 0;
    }

    /** Four cameras at the suggested spots (their facing). */
    private static int cameras(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerLevel level = GameManager.level(c.getSource().getServer());
        if (level == null) return 0;
        GameData data = GameData.get(c.getSource().getServer());
        Cameras.removeAll(level);
        data.cameras.clear();
        int n = 1;
        for (Layout.Marker m : Layout.markers("cam_suggest")) {
            if (n > Cameras.MAX) break;
            Vec3 at = m.center();
            data.cameras.add(new GameData.CamSpot(n++, at.x, at.y, at.z, m.yaw(), 26.0f, false));
        }
        data.flag("app_opened");
        data.setDirty();
        Cameras.ensure(level, data);
        return 1;
    }

    /** Jumps to the start of a phase (the steps before it done; the doll where it would be). */
    private static int phase(CommandContext<CommandSourceStack> c, String phase) throws CommandSyntaxException {
        Step target = switch (phase) {
            case "ritual" -> Step.NAME;
            case "hiding" -> Step.HIDE;
            case "finish" -> Step.FIND;
            default -> Step.PLUSH;
        };
        return step(c, target.id);
    }

    /** Everything before this step done; the night set up as it would be. */
    private static int step(CommandContext<CommandSourceStack> c, String id) throws CommandSyntaxException {
        Step target = Step.byId(id);
        if (target == null) return 0;
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = GameManager.level(c.getSource().getServer());
        if (level == null) return 0;
        GameData data = GameData.get(c.getSource().getServer());
        if (!data.started) GameManager.startGame(p);
        data.steps = 0;
        for (Step s : Step.ALL) {
            if (s.ordinal() >= target.ordinal()) break;
            data.setDone(s, true);
        }
        data.flag("plush_taken");
        // the world as the steps leave it
        boolean afterPrep = target.ordinal() >= Step.NAME.ordinal();
        data.phase = target.phase == GameData.PHASE_PREP ? GameData.PHASE_PREP
                : target.ordinal() > Step.NEXT_ONI.ordinal() && target.phase == GameData.PHASE_RITUAL ? GameData.PHASE_HIDING : target.phase;
        if (afterPrep && data.clock < Clock.THREE) data.clock = Clock.THREE + 30;
        if (afterPrep) {
            data.ritualStartTick = data.playTicks;
            Apt.setBathWater(level, 2);
            Apt.setTv(level, true);
            if (data.cameras.isEmpty()) cameras(c);
        }
        if (target.ordinal() > Step.LIGHTS.ordinal()) Apt.allLights(level, false);
        // items in hand for the step
        p.getInventory().clearContent();
        p.getInventory().setItem(8, new ItemStack(ModItems.PHONE.get()));
        switch (target) {
            case CUT -> {
                p.getInventory().setItem(0, new ItemStack(ModItems.SCISSORS.get()));
                p.getInventory().setItem(1, new ItemStack(ModItems.PLUSH.get()));
            }
            case RICE -> p.getInventory().setItem(0, new ItemStack(ModItems.PLUSH_OPEN.get()));
            case NAILS -> {
                p.getInventory().setItem(0, new ItemStack(ModItems.NAIL_CLIPPER.get()));
                p.getInventory().setItem(1, new ItemStack(ModItems.PLUSH_RICE.get()));
            }
            case SEW -> {
                ItemStack rice = new ItemStack(ModItems.PLUSH_RICE.get());
                GameItem.setNails(rice);
                p.getInventory().setItem(0, new ItemStack(ModItems.RED_THREAD.get()));
                p.getInventory().setItem(1, rice);
            }
            case NAME, FIRST_ONI, TUB -> p.getInventory().setItem(0, new ItemStack(ModItems.RITUAL_DOLL.get()));
            default -> {
            }
        }
        if (target.ordinal() >= Step.TUB.ordinal() && target.phase != GameData.PHASE_PREP) {
            ItemStack water = new ItemStack(ModItems.SALT_WATER.get());
            GameItem.setSips(water, GameItem.CUP_SIPS);
            p.getInventory().setItem(2, water);
            p.getInventory().setItem(3, new ItemStack(ModItems.LAPTOP.get()));
        }
        if (target.ordinal() > Step.TUB.ordinal() && target.phase != GameData.PHASE_PREP) {
            OniDoll doll = Doll.get(level);
            if (doll == null) doll = Doll.spawnInTub(level, data);
            if (target == Step.KNIFE || target == Step.STAB) p.getInventory().setItem(0, new ItemStack(ModItems.KNIFE.get()));
            if (doll != null && target.ordinal() > Step.NEXT_ONI.ordinal()) {
                if (data.hideStartTick < 0) data.hideStartTick = data.playTicks;
                doll.wake();
                if (target.phase == GameData.PHASE_FINISH) {
                    doll.setState(OniDoll.State.SEARCH);
                    doll.startWaiting();
                    data.finishStartTick = data.playTicks;
                    data.hideStartTick = data.playTicks - 20 * 60 * 15;
                }
            }
        }
        if (target.phase == GameData.PHASE_FINISH) {
            GameManager.state(p).mouth = 1.0f;
            p.getInventory().setItem(4, new ItemStack(ModItems.LIGHTER.get()));
            if (data.clock < Clock.FOUR) data.clock = Clock.FOUR + 60;
        }
        data.setDirty();
        GameManager.syncState(p, GameManager.state(p), data);
        return 1;
    }
}
