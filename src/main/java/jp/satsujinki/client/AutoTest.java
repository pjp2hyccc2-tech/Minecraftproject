package jp.satsujinki.client;

import jp.satsujinki.Satsujinki;
import jp.satsujinki.block.entity.GuillotineBlockEntity;
import jp.satsujinki.block.entity.MixerBlockEntity;
import jp.satsujinki.block.entity.PressBlockEntity;
import jp.satsujinki.block.entity.SawBlockEntity;
import jp.satsujinki.client.gui.HostTerminalScreen;
import jp.satsujinki.client.gui.R18WarningScreen;
import jp.satsujinki.entity.ContestantEntity;
import jp.satsujinki.game.ContestantType;
import jp.satsujinki.game.DeathGameManager;
import jp.satsujinki.game.Structures;
import jp.satsujinki.item.HostHandItem;
import jp.satsujinki.registry.ModBlocks;
import jp.satsujinki.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * CI-only visual test (-Dsatsujinki.autotest=true under Xvfb): accepts the R18 gate, creates a flat world,
 * builds an arena with every trap, executes contestants and photographs everything, then quits.
 */
@Mod.EventBusSubscriber(modid = Satsujinki.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AutoTest {
    public static final boolean ENABLED = Boolean.getBoolean("satsujinki.autotest");
    private static int stage;
    private static int wait;
    private static int totalTicks;
    private static int stepIndex;
    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> LOG = new ArrayList<>();

    // scene layout (absolute)
    private static BlockPos center = BlockPos.ZERO;
    private static BlockPos mixer = BlockPos.ZERO;
    private static BlockPos press = BlockPos.ZERO;
    private static BlockPos saw = BlockPos.ZERO;
    private static BlockPos guillotine = BlockPos.ZERO;

    private record Step(String name, Consumer<Minecraft> action, int waitAfter, boolean screenshot) {
    }

    private AutoTest() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (++totalTicks > 20 * 60 * 16) {
            log("global timeout");
            finish(mc);
            return;
        }
        if (stage == 0) {
            mc.options.pauseOnLostFocus = false;
            if (mc.screen instanceof R18WarningScreen r18) {
                wait++;
                if (wait == 60) grab(mc, "00_r18_warning");
                if (wait >= 66) {
                    r18.accept();
                    wait = 0;
                    stage = 1;
                }
            } else if (mc.screen instanceof TitleScreen && totalTicks > 400) {
                log("no R18 screen seen");
                stage = 1;
            }
            return;
        }
        if (stage == 1) {
            if (mc.screen instanceof TitleScreen && ++wait > 30) {
                createWorld(mc);
                stage = 2;
                wait = 0;
            }
            return;
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (stage == 2) {
            stage = 3;
            wait = 120;
            buildSteps();
            log("world loaded, " + STEPS.size() + " steps");
            return;
        }
        if (stage != 3) return;
        if (mc.player.isDeadOrDying()) {
            mc.player.respawn();
            wait = 40;
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (stepIndex >= STEPS.size()) {
            finish(mc);
            return;
        }
        Step s = STEPS.get(stepIndex++);
        if (s.screenshot) {
            grab(mc, s.name);
            wait = 3;
            return;
        }
        try {
            s.action.accept(mc);
            log("step " + s.name);
        } catch (Exception e) {
            log("step failed " + s.name + ": " + e);
            Satsujinki.LOGGER.error("[AUTOTEST] step {} failed", s.name, e);
        }
        wait = s.waitAfter;
    }

    private static void createWorld(Minecraft mc) {
        try {
            String name = "satsujinki_autotest";
            GameRules rules = new GameRules();
            LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
            WorldOptions options = new WorldOptions(20261104L, false, false);
            mc.createWorldOpenFlows().createFreshLevel(name, settings, options,
                    ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            log("world creation requested");
        } catch (Exception e) {
            log("world creation failed: " + e);
            Satsujinki.LOGGER.error("[AUTOTEST] world creation failed", e);
            finish(mc);
        }
    }

    // ------------------------------------------------------------------ the scenario

    private static void buildSteps() {
        STEPS.clear();
        add("setup", mc -> server(mc, sp -> {
            cmd(sp, "time set 6000");
            cmd(sp, "gamerule doDaylightCycle false");
            cmd(sp, "gamerule doMobSpawning false");
            cmd(sp, "gamerule doWeatherCycle false");
            cmd(sp, "weather clear");
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            sp.setNoGravity(true);
            ServerLevel lvl = sp.serverLevel();
            int gy = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            center = new BlockPos(0, gy, 0);
            Structures.build(lvl, Structures.arena(center, Direction.SOUTH), sp);
            mixer = center.offset(-4, -1, 3);
            press = center.offset(4, -1, 3);
            saw = center.offset(-3, -1, -4);
            guillotine = center.offset(3, 0, -4);
            Structures.build(lvl, Structures.mixer(mixer), sp);
            Structures.build(lvl, Structures.press(press), sp);
            lvl.setBlock(saw, ModBlocks.SAW.get().defaultBlockState(), 3);
            lvl.setBlock(guillotine, ModBlocks.GUILLOTINE.get().defaultBlockState(), 3);
            for (int dx = -1; dx <= 1; dx++) lvl.setBlock(center.offset(dx - 5, -1, -1), ModBlocks.SPIKE_BED.get().defaultBlockState(), 3);
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HOST_HAND.get()));
            look(sp, new Vec3(center.getX() + 0.5D, center.getY() + 9.0D, center.getZ() - 12.5D), Vec3.atCenterOf(center));
        }), 100);
        shot("01_arena_overview", true);

        add("spawn_crowd", mc -> server(mc, sp -> {
            ServerLevel lvl = sp.serverLevel();
            ContestantType[] types = ContestantType.values();
            for (int i = 0; i < types.length; i++) {
                double a = i / (double) types.length * Math.PI * 2.0D;
                ContestantEntity.spawnAt(lvl, new Vec3(center.getX() + 0.5D + Math.cos(a) * 2.6D, center.getY(), center.getZ() - 1.5D + Math.sin(a) * 1.6D), types[i]);
            }
            look(sp, new Vec3(center.getX() + 0.5D, center.getY() + 2.4D, center.getZ() - 6.5D), new Vec3(center.getX() + 0.5D, center.getY() + 1.2D, center.getZ() - 1.5D));
        }), 70);
        shot("02_contestants", false);

        add("grab", mc -> server(mc, sp -> {
            ContestantEntity c = nearest(sp);
            if (c != null) HostHandItem.hold(sp, c);
        }), 30);
        shot("03_grabbed", true);

        // Hover over the jar looking 50 degrees down, so the hand (eye + look * 2.4) is right above its middle.
        add("carry_to_mixer", mc -> server(mc, sp -> {
            Vec3 eye = new Vec3(mixer.getX() + 0.5D, mixer.getY() + 7.92D, mixer.getZ() + 0.5D - 1.54D);
            double pitch = Math.toRadians(50.0D);
            look(sp, eye, eye.add(0.0D, -Math.sin(pitch) * 10.0D, Math.cos(pitch) * 10.0D));
            if (sp.serverLevel().getBlockEntity(mixer) instanceof MixerBlockEntity be) be.setRunning(true, sp);
        }), 30);
        add("drop_into_mixer", mc -> server(mc, sp -> {
            HostHandItem.letGo(sp, 0.0F);
            look(sp, new Vec3(mixer.getX() + 0.5D, mixer.getY() + 4.5D, mixer.getZ() - 5.5D), new Vec3(mixer.getX() + 0.5D, mixer.getY() + 1.5D, mixer.getZ() + 0.5D));
        }), 14);
        shot("04_mixer_grinding", true);
        add("wait_mixer", mc -> {
        }, 18);
        shot("05_mixer_kill", true);
        add("feed_mixer", mc -> server(mc, sp -> {
            for (int i = 0; i < 4; i++) {
                ContestantEntity.spawnAt(sp.serverLevel(), new Vec3(mixer.getX() + 0.5D + (i % 2) - 0.5D, mixer.getY() + 2.0D + i * 0.4D, mixer.getZ() + 0.5D), null);
            }
        }), 70);
        shot("06_mixer_soup", true);

        add("press_victims", mc -> server(mc, sp -> {
            ServerLevel lvl = sp.serverLevel();
            ContestantEntity.spawnAt(lvl, new Vec3(press.getX() + 0.5D, press.getY() + 0.2D, press.getZ() + 0.5D), ContestantType.OJISAN);
            ContestantEntity.spawnAt(lvl, new Vec3(press.getX() - 0.4D, press.getY() + 0.2D, press.getZ() + 1.2D), ContestantType.GYARU);
            look(sp, new Vec3(press.getX() + 0.5D, press.getY() + 3.0D, press.getZ() - 5.0D), new Vec3(press.getX() + 0.5D, press.getY() + 1.5D, press.getZ() + 0.5D));
        }), 40);
        shot("07_press_victims", false);
        add("press_fire", mc -> server(mc, sp -> {
            if (sp.serverLevel().getBlockEntity(press.above(5)) instanceof PressBlockEntity be) be.trigger(sp);
        }), 33);
        shot("08_press_slam", true);
        add("press_wait", mc -> {
        }, 90);
        shot("09_press_after", false);

        add("saw_victim", mc -> server(mc, sp -> {
            ServerLevel lvl = sp.serverLevel();
            if (lvl.getBlockEntity(saw) instanceof SawBlockEntity be) be.setRunning(true, sp);
            ContestantEntity.spawnAt(lvl, Vec3.atBottomCenterOf(saw.above()), ContestantType.OTAKU);
            look(sp, new Vec3(saw.getX() + 4.0D, saw.getY() + 2.6D, saw.getZ() + 2.5D), new Vec3(saw.getX() + 0.5D, saw.getY() + 1.4D, saw.getZ() + 0.5D));
        }), 9);
        shot("10_saw_cutting", true);
        add("saw_wait", mc -> {
        }, 14);
        shot("11_saw_halves", true);

        add("guillotine_victim", mc -> server(mc, sp -> {
            ContestantEntity.spawnAt(sp.serverLevel(), Vec3.atBottomCenterOf(guillotine), ContestantType.PRESIDENT);
            look(sp, new Vec3(guillotine.getX() + 3.0D, guillotine.getY() + 2.0D, guillotine.getZ() + 3.5D), new Vec3(guillotine.getX() + 0.5D, guillotine.getY() + 1.6D, guillotine.getZ() + 0.5D));
        }), 40);
        shot("12_guillotine_locked", false);
        add("guillotine_fire", mc -> server(mc, sp -> {
            if (sp.serverLevel().getBlockEntity(guillotine) instanceof GuillotineBlockEntity be) be.trigger(sp);
        }), 7);
        shot("13_guillotine_chop", true);
        add("guillotine_wait", mc -> {
        }, 20);
        shot("14_guillotine_after", true);

        add("spike_throw", mc -> server(mc, sp -> {
            BlockPos sp0 = center.offset(-5, -1, -1);
            ContestantEntity.spawnAt(sp.serverLevel(), new Vec3(sp0.getX() + 0.5D, sp0.getY() + 5.0D, sp0.getZ() + 0.5D), ContestantType.YANKEE);
            look(sp, new Vec3(sp0.getX() + 0.5D, sp0.getY() + 3.0D, sp0.getZ() - 4.0D), new Vec3(sp0.getX() + 0.5D, sp0.getY() + 1.0D, sp0.getZ() + 0.5D));
        }), 40);
        shot("15_spikes", true);

        add("round_start", mc -> server(mc, sp -> {
            look(sp, new Vec3(center.getX() + 2.5D, center.getY() + 8.5D, center.getZ() - 11.0D), Vec3.atCenterOf(center));
            DeathGameManager.start(sp, center, 6);
        }), 20);
        shot("16_round_intro", true);
        add("round_wait1", mc -> {
        }, 50);
        shot("17_round_countdown", true);
        add("round_wait2", mc -> {
        }, 60);
        shot("18_round_running", true);

        add("open_terminal", mc -> mc.setScreen(new HostTerminalScreen(center)), 15);
        shot("19_terminal_game", true);
        add("terminal_shop", mc -> {
            HostTerminalScreen.selectTab(1);
            mc.setScreen(new HostTerminalScreen(center));
        }, 15);
        shot("20_terminal_shop", true);
        add("terminal_records", mc -> {
            HostTerminalScreen.selectTab(2);
            mc.setScreen(new HostTerminalScreen(center));
        }, 15);
        shot("21_terminal_records", true);
        add("close_terminal", mc -> mc.setScreen(null), 10);

        add("aftermath", mc -> server(mc, sp -> {
            DeathGameManager.stop(sp);
            look(sp, new Vec3(center.getX() - 9.0D, center.getY() + 7.0D, center.getZ() - 9.0D), new Vec3(center.getX() + 0.5D, center.getY(), center.getZ() + 1.0D));
        }), 60);
        shot("22_aftermath", false);
        add("night", mc -> server(mc, sp -> cmd(sp, "time set 14500")), 40);
        shot("23_night", true);
    }

    // ------------------------------------------------------------------ helpers

    private static ContestantEntity nearest(ServerPlayer sp) {
        ContestantEntity best = null;
        double bd = Double.MAX_VALUE;
        for (ContestantEntity c : sp.serverLevel().getEntitiesOfClass(ContestantEntity.class, sp.getBoundingBox().inflate(16.0D))) {
            double d = c.distanceToSqr(sp);
            if (d < bd && c.isAlive()) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    private static void look(ServerPlayer sp, Vec3 from, Vec3 at) {
        double dx = at.x - from.x, dy = at.y - from.y, dz = at.z - from.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        sp.teleportTo(sp.serverLevel(), from.x, from.y - sp.getEyeHeight(), from.z, yaw, pitch);
    }

    private static void cmd(ServerPlayer sp, String command) {
        sp.server.getCommands().performPrefixedCommand(sp.server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null) return;
            try {
                action.accept(sp);
            } catch (Exception e) {
                log("server action failed: " + e);
                Satsujinki.LOGGER.error("[AUTOTEST] server action failed", e);
            }
        });
    }

    private static void add(String name, Consumer<Minecraft> action, int waitAfter) {
        STEPS.add(new Step(name, action, waitAfter, false));
    }

    /** The GUI flag must be set one step before the screenshot (grab reads the last rendered frame). */
    private static void shot(String name, boolean hud) {
        STEPS.add(new Step("hud_" + name, mc -> mc.options.hideGui = !hud, 3, false));
        STEPS.add(new Step(name, mc -> {
        }, 0, true));
    }

    private static void grab(Minecraft mc, String name) {
        try {
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {
            });
            log("shot " + name);
        } catch (Exception e) {
            log("screenshot failed " + name + ": " + e);
        }
    }

    private static synchronized void log(String s) {
        LOG.add(s);
        Satsujinki.LOGGER.info("[AUTOTEST] {}", s);
    }

    private static void finish(Minecraft mc) {
        stage = 99;
        try {
            File dir = new File(mc.gameDirectory, "satsujinki_autotest");
            dir.mkdirs();
            try (FileWriter w = new FileWriter(new File(dir, "autotest_log.txt"))) {
                for (String s : LOG) w.write(s + "\n");
            }
        } catch (Exception ignored) {
        }
        Satsujinki.LOGGER.info("[AUTOTEST] finished, stopping client");
        mc.stop();
    }
}
