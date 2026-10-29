package com.huntcraft.gear.armor.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.entity.misc.MonsterProjectile;
import com.huntcraft.entity.misc.TrapEntity;
import com.huntcraft.gear.armor.ArmorSet;
import com.huntcraft.gear.armor.ArmorSets;
import com.huntcraft.registry.HCBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Development-only visual check of the armor module (active only with -Dhuntcraft.autotest=true, i.e. the CI client
 * run). Uses the warm-up window before the core autotest's first step (120 ticks after the world loads) to photograph
 * armor stands wearing all 8 series (two close-ups, front, back; with both traps, both barrel bombs and every monster
 * projectile sprite in the scene) and a wall of every HuntCraft item icon, then cleans up.
 * Screenshots: run/screenshots/armor_*.png, item_icons.png.
 */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ArmorAutoTest {
    private static final boolean ENABLED = Boolean.getBoolean("huntcraft.autotest");
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final double SPACING = 1.6;
    private static final double ROW_DIST = 7.0;
    /** Last tick (since the world loaded) we may act on: the core autotest runs its first step at tick ~121. */
    private static final int DEADLINE = 114;
    /** Ticks since the world loaded (-1 = not loaded yet). */
    private static int tick = -1;
    private static int start = -1;
    private static int next;
    private static boolean done;
    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<Entity> SPAWNED = new ArrayList<>();
    private static final List<BlockPos> BLOCKS = new ArrayList<>();
    private static Vec3 home;
    private static float homeYaw, homePitch;
    private static int groundY;

    private record Step(int at, Consumer<Minecraft> action) {
    }

    private ArmorAutoTest() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        tick = Math.max(1, tick + 1);
        try {
            if (start < 0) {
                if (mc.screen != null && tick < 50) return; // wait for the "loading terrain" screen to close
                start = tick;
                buildSteps();
            }
            int t = tick - start;
            while (next < STEPS.size() && STEPS.get(next).at() <= t && tick < DEADLINE) STEPS.get(next++).action().accept(mc);
            if (next >= STEPS.size() || tick >= DEADLINE) finish(mc);
        } catch (Exception e) {
            HuntCraft.LOGGER.error("[AUTOTEST] armor autotest failed", e);
            finish(mc);
        }
    }

    /** Shots in priority order (later ones are dropped if the world took long to load). */
    private static void buildSteps() {
        STEPS.clear();
        int n = ArmorSets.ALL.size();
        double half = (n / 2 - 1) / 2.0 * SPACING + SPACING / 2.0; // centre of each half of the row
        STEPS.add(new Step(1, mc -> {
            mc.options.hideGui = true;
            server(mc, sp -> {
                spawnStands(sp);
                view(sp, -half + 1.0, -ROW_DIST + 3.7, 165.0F, 12.0F);
            });
        }));
        STEPS.add(new Step(15, mc -> shot(mc, "armor_closeup_a")));
        STEPS.add(new Step(17, mc -> server(mc, sp -> view(sp, half - 1.0, -ROW_DIST + 3.7, 195.0F, 12.0F))));
        STEPS.add(new Step(31, mc -> shot(mc, "armor_closeup_b")));
        STEPS.add(new Step(33, mc -> mc.setScreen(new IconWall())));
        STEPS.add(new Step(43, mc -> shot(mc, "item_icons")));
        STEPS.add(new Step(45, mc -> {
            mc.setScreen(null);
            server(mc, sp -> view(sp, 0.0, 0.0, 180.0F, 4.0F));
        }));
        STEPS.add(new Step(59, mc -> shot(mc, "armor_front")));
        STEPS.add(new Step(61, mc -> server(mc, sp -> view(sp, 0.0, -2 * ROW_DIST, 0.0F, 4.0F))));
        STEPS.add(new Step(75, mc -> shot(mc, "armor_back")));
    }

    private static void spawnStands(ServerPlayer sp) {
        ServerLevel lvl = sp.serverLevel();
        home = sp.position();
        homeYaw = sp.getYRot();
        homePitch = sp.getXRot();
        groundY = lvl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(home.x, 0, home.z - ROW_DIST)).getY();
        int n = ArmorSets.ALL.size();
        for (int k = 0; k < n; k++) {
            ArmorSet set = ArmorSets.ALL.get(k);
            double x = home.x + (k - (n - 1) / 2.0) * SPACING;
            ArmorStand st = new ArmorStand(lvl, x, groundY, home.z - ROW_DIST);
            CompoundTag tag = new CompoundTag();
            st.addAdditionalSaveData(tag);
            tag.putBoolean("ShowArms", true);
            tag.putBoolean("NoBasePlate", true);
            st.readAdditionalSaveData(tag);
            st.moveTo(x, groundY, home.z - ROW_DIST, 0.0F, 0.0F);
            st.setYBodyRot(0.0F);
            st.setYHeadRot(0.0F);
            for (int i = 0; i < 4; i++) st.setItemSlot(SLOTS[i], new ItemStack(set.item(i).get()));
            lvl.addFreshEntity(st);
            SPAWNED.add(st);
        }
        double rowZ = home.z - ROW_DIST;
        // barrel bombs at both ends of the row, both traps in front of it
        for (int side = 0; side < 2; side++) {
            BlockPos b = BlockPos.containing(home.x + (side == 0 ? -1 : 1) * (n / 2.0 + 0.6) * SPACING, groundY, rowZ);
            lvl.setBlock(b, (side == 0 ? HCBlocks.BARREL_BOMB : HCBlocks.BARREL_BOMB_PLUS).get().defaultBlockState(), 3);
            BLOCKS.add(b);
        }
        for (TrapEntity.TrapKind kind : TrapEntity.TrapKind.values()) {
            TrapEntity trap = TrapEntity.create(lvl, kind, home.x + (kind.ordinal() == 0 ? -1.7 : 1.7), groundY, rowZ + 2.6);
            lvl.addFreshEntity(trap);
            SPAWNED.add(trap);
        }
        // every monster projectile sprite, hovering in two rows above the armor stands
        MonsterProjectile.Kind[] kinds = MonsterProjectile.Kind.values();
        int perRow = (kinds.length + 1) / 2;
        for (int k = 0; k < kinds.length; k++) {
            int row = k / perRow;
            int idx = k % perRow;
            int cnt = row == 0 ? perRow : kinds.length - perRow;
            double px = home.x + (idx - (cnt - 1) / 2.0) * 1.7;
            double py = groundY + 3.3 + row * 1.6;
            CompoundTag pt = new CompoundTag();
            pt.putString("id", HuntCraft.id("monster_projectile").toString());
            pt.putInt("Kind", k);
            pt.putInt("Life", 600);
            pt.putFloat("Damage", 0.0F);
            Entity e = EntityType.loadEntityRecursive(pt, lvl, ent -> {
                ent.moveTo(px, py, rowZ, 0.0F, 0.0F);
                return ent;
            });
            if (e != null) {
                e.setNoGravity(true);
                lvl.addFreshEntity(e);
                SPAWNED.add(e);
            }
        }
        sp.getAbilities().flying = true;
        sp.onUpdateAbilities();
    }

    /** Puts the camera at an offset from the original spawn point (z relative to the player, row at -ROW_DIST). */
    private static void view(ServerPlayer sp, double dx, double dz, float yaw, float pitch) {
        if (home == null) return;
        double dy = Math.abs(dz + ROW_DIST) < ROW_DIST * 0.75 ? 0.2 : 0.5; // a bit lower for close-ups
        sp.teleportTo(sp.serverLevel(), home.x + dx, groundY + dy, home.z + dz, yaw, pitch);
    }

    private static void finish(Minecraft mc) {
        done = true;
        if (mc.screen instanceof IconWall) mc.setScreen(null);
        mc.options.hideGui = false;
        server(mc, sp -> {
            for (Entity e : SPAWNED) e.discard();
            SPAWNED.clear();
            for (BlockPos b : BLOCKS) sp.serverLevel().setBlock(b, Blocks.AIR.defaultBlockState(), 3);
            BLOCKS.clear();
            if (home != null) sp.teleportTo(sp.serverLevel(), home.x, home.y, home.z, homeYaw, homePitch);
        });
        HuntCraft.LOGGER.info("[AUTOTEST] armor autotest finished at tick {}", tick);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {
        });
        HuntCraft.LOGGER.info("[AUTOTEST] armor shot {}", name);
    }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null) return;
            try {
                action.accept(sp);
            } catch (Exception e) {
                HuntCraft.LOGGER.error("[AUTOTEST] armor autotest server action failed", e);
            }
        });
    }

    /** Every HuntCraft item in a grid (verifies that each item model / texture resolves in game). */
    private static final class IconWall extends Screen {
        private final List<ItemStack> stacks = new ArrayList<>();

        IconWall() {
            super(Component.literal("HuntCraft items"));
            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
                if (key != null && HuntCraft.MODID.equals(key.getNamespace())) stacks.add(new ItemStack(item));
            }
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, this.width, this.height, 0xFF2E323A);
            int cols = Math.max(1, (this.width - 4) / 18);
            int rows = (stacks.size() + cols - 1) / cols;
            float scale = Math.min(1.0F, (this.height - 4) / (rows * 18.0F));
            if (scale < 1.0F) cols = Math.max(1, (int) ((this.width - 4) / (18 * scale)));
            g.pose().pushPose();
            g.pose().scale(scale, scale, 1.0F);
            for (int i = 0; i < stacks.size(); i++) {
                int x = 3 + (i % cols) * 18;
                int y = 3 + (i / cols) * 18;
                g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF464C58);
                g.renderItem(stacks.get(i), x, y);
            }
            g.pose().popPose();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
