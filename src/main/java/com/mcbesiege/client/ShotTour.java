package com.mcbesiege.client;

import com.mcbesiege.MCBesiege;
import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.entity.MachineEntity;
import com.mcbesiege.machine.LevelPhysics;
import com.mcbesiege.machine.Machine;
import com.mcbesiege.machine.MachineManager;
import com.mcbesiege.mission.Arena;
import com.mcbesiege.mission.MissionManager;
import com.mcbesiege.mission.Missions;
import com.mcbesiege.net.Net;
import com.mcbesiege.part.Keys;
import com.mcbesiege.part.PartType;
import com.mcbesiege.registry.ModBlocks;
import com.mcbesiege.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** CI only (-Dmcbesiege.shots=true): builds a showcase, photographs build mode, GUIs, a running machine and a mission. */
public final class ShotTour {
    private ShotTour() {}

    private static final boolean ENABLED = Boolean.getBoolean("mcbesiege.shots");
    private static boolean worldRequested;
    private static int stage = 0, wait = 0, total = 0, guard = 0;
    private static final BlockPos G = new BlockPos(3000, MissionManager.GROUND, 3000);
    private static BlockPos carStart;
    private static int carId = -1;

    public static void onScreen(Screen s) {
        if (!ENABLED || worldRequested || !(s instanceof TitleScreen)) return;
        worldRequested = true;
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            try {
                LevelSettings settings = new LevelSettings("mcbesiege_shots", GameType.CREATIVE, false, Difficulty.EASY, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("mcbesiege_shots_" + System.currentTimeMillis(), settings,
                        new WorldOptions(424242L, false, false), WorldPresets::createNormalWorldDimensions);
            } catch (Throwable t) {
                MCBesiege.LOGGER.error("[mcbesiege-shots] could not create world", t);
                mc.stop();
            }
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, String.format("%02d_%s.png", total++, name), mc.getMainRenderTarget(),
                c -> MCBesiege.LOGGER.info("[mcbesiege-shots] {}", c.getString()));
    }

    private static ServerPlayer sp(Minecraft mc) {
        IntegratedServer s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void server(Minecraft mc, Runnable r) {
        IntegratedServer s = mc.getSingleplayerServer();
        if (s != null) s.execute(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                MCBesiege.LOGGER.error("[mcbesiege-shots] server task failed", t);
            }
        });
    }

    private static void view(Minecraft mc, double x, double y, double z, float yaw, float pitch, GameType mode) {
        server(mc, () -> {
            ServerPlayer p = sp(mc);
            ServerLevel dim = p.server.getLevel(MissionManager.DIM);
            p.setGameMode(mode);
            p.teleportTo(dim, x, y, z, yaw, pitch);
        });
    }

    public static void tick(Minecraft mc) {
        if (!ENABLED || mc.player == null || mc.level == null) return;
        if (++guard > 30000) {
            MCBesiege.LOGGER.error("[mcbesiege-shots] timeout");
            mc.stop();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (stage) {
            case 0 -> {
                mc.options.hideGui = false;
                server(mc, () -> {
                    ServerPlayer p = sp(mc);
                    p.server.getCommands().performPrefixedCommand(p.server.createCommandSourceStack(), "gamerule doDaylightCycle false");
                    p.server.getCommands().performPrefixedCommand(p.server.createCommandSourceStack(), "gamerule doWeatherCycle false");
                    p.server.getCommands().performPrefixedCommand(p.server.createCommandSourceStack(), "gamerule doMobSpawning false");
                    p.server.getCommands().performPrefixedCommand(p.server.createCommandSourceStack(), "time set 5000");
                    ServerLevel dim = p.server.getLevel(MissionManager.DIM);
                    p.teleportTo(dim, G.getX() + 0.5, G.getY() + 2, G.getZ() - 12.5, 0, 20);
                });
                wait = 120;
                stage = 1;
            }
            case 1 -> {
                server(mc, () -> buildShowcase(sp(mc).server.getLevel(MissionManager.DIM)));
                wait = 100;
                stage = 2;
            }
            case 2 -> { // gallery of every part
                view(mc, G.getX() + 13, G.getY() + 6, G.getZ() - 9, 0, 32, GameType.SPECTATOR);
                wait = 80;
                stage = 3;
            }
            case 3 -> {
                shot(mc, "parts_gallery");
                view(mc, G.getX() + 5, G.getY() + 4, G.getZ() - 3, 20, 28, GameType.SPECTATOR);
                wait = 60;
                stage = 4;
            }
            case 4 -> {
                shot(mc, "parts_closeup");
                view(mc, G.getX() + 40, G.getY() + 9, G.getZ() + 6, 135, 25, GameType.SPECTATOR);
                wait = 80;
                stage = 5;
            }
            case 5 -> {
                shot(mc, "showcase_build");
                // GUIs
                view(mc, G.getX() + 32, G.getY() + 3, G.getZ() + 18, 180, 30, GameType.CREATIVE);
                wait = 40;
                stage = 6;
            }
            case 6 -> {
                mc.player.getInventory().setItem(0, new ItemStack(ModItems.WRENCH.get()));
                mc.player.getInventory().selected = 0;
                mc.setScreen(new BuilderScreen());
                wait = 30;
                stage = 7;
            }
            case 7 -> {
                shot(mc, "ui_builder");
                ClientHooks.openPartConfig(carStart.offset(2, 0, 2));
                wait = 30;
                stage = 8;
            }
            case 8 -> {
                shot(mc, "ui_part_config");
                mc.setScreen(null);
                server(mc, () -> MissionManager.openTable(sp(mc)));
                wait = 40;
                stage = 9;
            }
            case 9 -> {
                shot(mc, "ui_missions");
                mc.setScreen(null);
                wait = 20;
                stage = 10;
            }
            case 10 -> { // simulation: start the car
                server(mc, () -> {
                    ServerPlayer p = sp(mc);
                    MachineEntity e = MachineManager.startSimulation(p.serverLevel(), carStart, p);
                    if (e != null) carId = e.getId();
                    MCBesiege.LOGGER.info("[mcbesiege-shots] car simulation {}", e == null ? "FAILED" : "started");
                });
                wait = 60;
                stage = 11;
            }
            case 11 -> {
                shot(mc, "sim_start");
                if (carId >= 0) Net.sendKeys(carId, new int[]{Keys.UP});
                wait = 50;
                stage = 12;
            }
            case 12 -> {
                shot(mc, "sim_drive");
                if (carId >= 0) Net.sendKeys(carId, new int[]{Keys.UP, Keys.LEFT});
                wait = 30;
                stage = 13;
            }
            case 13 -> {
                shot(mc, "sim_steer");
                if (carId >= 0) Net.sendKeys(carId, new int[]{Keys.C});
                wait = 6;
                stage = 14;
            }
            case 14 -> {
                shot(mc, "sim_cannon");
                if (carId >= 0) Net.sendKeys(carId, new int[0]);
                wait = 50;
                stage = 15;
            }
            case 15 -> {
                shot(mc, "sim_after");
                logMachines(mc);
                OrbitCamera.dist = 22;
                OrbitCamera.pitch = 35;
                wait = 10;
                stage = 16;
            }
            case 16 -> { // bomb run at the castle wall
                mc.setScreen(null);
                server(mc, () -> {
                    ServerPlayer p = sp(mc);
                    MachineEntity own = MachineManager.ownedMachine(p);
                    if (own != null) MachineManager.reset(own, true);
                    ServerLevel dim = p.serverLevel();
                    BlockPos b = G.offset(60, 0, 40);
                    BlockState st = ModBlocks.part(PartType.STARTING_BLOCK).defaultBlockState().setValue(MachinePartBlock.FACING, Direction.SOUTH);
                    dim.setBlock(b, st, 3);
                    dim.setBlock(b.south(), ModBlocks.part(PartType.POWDER_BARREL).defaultBlockState(), 3);
                    MachineEntity e = MachineManager.startSimulation(dim, b, null);
                    if (e != null && e.machine != null) {
                        for (var part : e.machine.parts) if (part.type == PartType.POWDER_BARREL) part.fuse = 30;
                    }
                });
                view(mc, G.getX() + 60, G.getY() + 6, G.getZ() + 30, 0, 25, GameType.SPECTATOR);
                wait = 34;
                stage = 17;
            }
            case 17 -> {
                shot(mc, "explosion");
                wait = 40;
                stage = 18;
            }
            case 18 -> {
                shot(mc, "explosion_after");
                server(mc, () -> {
                    ServerPlayer p = sp(mc);
                    p.setGameMode(GameType.CREATIVE);
                    MissionManager.start(p, "castle_gate");
                });
                wait = 60;
                stage = 19;
            }
            case 19 -> {
                if (!readyOnServer(mc)) {
                    wait = 20;
                    return;
                }
                wait = 80;
                stage = 20;
            }
            case 20 -> {
                shot(mc, "mission_castle_gate");
                server(mc, () -> {
                    ServerPlayer p = sp(mc);
                    p.setGameMode(GameType.SPECTATOR);
                    p.teleportTo(p.serverLevel(), p.getX(), p.getY() + 10, p.getZ() + 4, 0, 30);
                });
                wait = 60;
                stage = 21;
            }
            case 21 -> {
                shot(mc, "mission_castle_overview");
                server(mc, () -> MissionManager.start(sp(mc), "windmill"));
                wait = 60;
                stage = 22;
            }
            case 22 -> {
                if (!readyOnServer(mc)) {
                    wait = 20;
                    return;
                }
                server(mc, () -> {
                    ServerPlayer p = sp(mc);
                    p.setGameMode(GameType.SPECTATOR);
                    p.teleportTo(p.serverLevel(), p.getX() + 6, p.getY() + 7, p.getZ() + 6, 10, 18);
                });
                wait = 80;
                stage = 23;
            }
            case 23 -> {
                shot(mc, "mission_windmill");
                wait = 10;
                stage = 24;
            }
            case 24 -> {
                MCBesiege.LOGGER.info("[mcbesiege-shots] done, {} screenshots", total);
                mc.stop();
                stage = 25;
            }
            default -> {}
        }
    }

    private static volatile boolean ready;

    private static boolean readyOnServer(Minecraft mc) {
        server(mc, () -> ready = MissionManager.isReady(sp(mc)));
        return ready;
    }

    private static void logMachines(Minecraft mc) {
        server(mc, () -> {
            ServerLevel l = sp(mc).serverLevel();
            LevelPhysics lp = LevelPhysics.peek(l);
            if (lp == null) return;
            for (Machine m : lp.machines) {
                MCBesiege.LOGGER.info("[mcbesiege-shots] machine parts={} alive={} bodies={} core={}", m.parts.size(), m.aliveCount(), m.bodies.size(), m.corePos());
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ showcase

    private static void part(ServerLevel l, BlockPos p, PartType t, Direction f) {
        l.setBlock(p, ModBlocks.part(t).defaultBlockState().setValue(MachinePartBlock.FACING, f), 3);
    }

    static void buildShowcase(ServerLevel l) {
        // flatten
        for (int x = -10; x <= 90; x++) {
            for (int z = -20; z <= 80; z++) {
                l.setBlock(G.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int y = 0; y < 14; y++) l.setBlock(G.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // gallery: every part on a pedestal, 12 per row
        PartType[] all = PartType.values();
        for (int i = 0; i < all.length; i++) {
            int x = (i % 12) * 2, z = (i / 12) * 3;
            BlockPos ped = G.offset(x, 0, z);
            l.setBlock(ped, Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
            PartType t = all[i];
            Direction f = switch (t) {
                case WHEEL, IRON_WHEEL, LARGE_TIRE, SMALL_WHEEL, SAW_BLADE, DRILL, SENSOR, FLYING_BLOCK, CANNON, CROSSBOW, FLAMETHROWER, ROCKET -> Direction.NORTH;
                case STARTING_BLOCK, SEAT -> Direction.NORTH;
                default -> Direction.UP;
            };
            part(l, ped.above(), t, f);
        }
        // the car (forward = south), large tyres
        carStart = G.offset(32, 1, 20);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -2; dz <= 2; dz++) part(l, carStart.offset(dx, 0, dz), PartType.WOODEN_BLOCK, Direction.UP);
        part(l, carStart, PartType.STARTING_BLOCK, Direction.SOUTH);
        part(l, carStart.offset(0, 0, 2), PartType.REINFORCED_WOOD, Direction.UP);
        for (int dz = -2; dz <= 2; dz += 4) {
            part(l, carStart.offset(-2, 0, dz), PartType.WHEEL, Direction.WEST);
            part(l, carStart.offset(2, 0, dz), PartType.WHEEL, Direction.EAST);
        }
        part(l, carStart.offset(0, 1, 1), PartType.CANNON, Direction.SOUTH);
        part(l, carStart.offset(0, 1, 0), PartType.IRON_BLOCK, Direction.UP);
        part(l, carStart.offset(-1, 1, -2), PartType.IRON_PLATE, Direction.UP);
        part(l, carStart.offset(1, 1, -2), PartType.IRON_PLATE, Direction.UP);
        part(l, carStart.offset(0, 0, 3), PartType.SPIKE, Direction.SOUTH);
        part(l, carStart.offset(0, 1, -1), PartType.SEAT, Direction.UP);
        // castle wall ahead of the car
        Arena a = new Arena(l, G.offset(32, 0, 0));
        a.wallX(-14, 14, 44, 7);
        a.tower(-5, 45, 2, 10, Arena.bricksState());
        a.tower(5, 45, 2, 10, Arena.bricksState());
        a.fill(-2, 0, 44, 2, 6, 45, Blocks.AIR.defaultBlockState());
        a.gate(-2, 2, 44, 5);
        a.banner(-5, 7, 42, Direction.NORTH, true);
        a.banner(5, 7, 42, Direction.NORTH, false);
        a.apply();
        // a catapult on display
        BlockPos c = G.offset(48, 1, 18);
        part(l, c, PartType.STARTING_BLOCK, Direction.SOUTH);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) part(l, c.offset(dx, 0, dz), PartType.REINFORCED_WOOD, Direction.UP);
        part(l, c.offset(0, 1, 0), PartType.HINGE, Direction.UP);
        part(l, c.offset(0, 2, 0), PartType.WOODEN_POLE, Direction.UP);
        part(l, c.offset(0, 3, 0), PartType.WOODEN_POLE, Direction.UP);
        part(l, c.offset(0, 4, 0), PartType.WOODEN_BLOCK, Direction.UP);
        part(l, c.offset(0, 5, 0), PartType.BOULDER, Direction.UP);
        for (int dz = -1; dz <= 1; dz += 2) {
            part(l, c.offset(-2, 0, dz), PartType.IRON_WHEEL, Direction.WEST);
            part(l, c.offset(2, 0, dz), PartType.IRON_WHEEL, Direction.EAST);
        }
        // a little flying machine
        BlockPos f = G.offset(60, 1, 16);
        part(l, f, PartType.STARTING_BLOCK, Direction.SOUTH);
        part(l, f.west(), PartType.WING, Direction.WEST);
        part(l, f.east(), PartType.WING, Direction.EAST);
        part(l, f.above(), PartType.SPINNING_BLOCK, Direction.UP);
        part(l, f.above(2), PartType.PROPELLER, Direction.UP);
        part(l, f.north(), PartType.FLYING_BLOCK, Direction.NORTH);
        part(l, f.south(), PartType.BALLOON, Direction.SOUTH);
        MCBesiege.LOGGER.info("[mcbesiege-shots] showcase built, missions: {}", Missions.ALL.size());
    }
}
