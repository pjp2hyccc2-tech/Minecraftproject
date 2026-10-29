package com.huntcraft.gear.armor.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.gear.armor.ArmorSet;
import com.huntcraft.gear.armor.ArmorSets;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 * armor stands wearing all 8 series (front, back, close-up) and a wall of every HuntCraft item icon, then cleans up.
 * Screenshots: run/screenshots/armor_*.png, item_icons.png.
 */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ArmorAutoTest {
    private static final boolean ENABLED = Boolean.getBoolean("huntcraft.autotest");
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final double SPACING = 1.6;
    private static final double ROW_DIST = 7.0;
    /** Ticks since the world loaded (-1 = not loaded yet). The core autotest starts its own steps at tick 121. */
    private static int tick = -1;
    private static int start = -1;
    private static boolean done;
    private static final List<ArmorStand> STANDS = new ArrayList<>();
    private static Vec3 home;
    private static float homeYaw, homePitch;
    private static int groundY;

    private ArmorAutoTest() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        tick++;
        if (tick == 0) tick = 1;
        try {
            if (start < 0) {
                if (mc.screen != null && tick < 40) return; // wait for the "loading terrain" screen to close
                start = tick;
            }
            int t = tick - start;
            boolean closeup = start <= 24;
            if (t == 1) {
                mc.options.hideGui = true;
                server(mc, ArmorAutoTest::spawnStands);
            } else if (t == 30) {
                shot(mc, "armor_front");
            } else if (t == 32) {
                server(mc, sp -> view(sp, 0.0, -2 * ROW_DIST, 0.0F, 9.0F));
            } else if (t == 54) {
                shot(mc, "armor_back");
            } else if (t == 56 && closeup) {
                // three-quarter close-up of the four large-monster series on the right half of the row
                server(mc, sp -> view(sp, 0.9, -ROW_DIST + 4.3, 208.0F, 12.0F));
            } else if (t == 78 && closeup) {
                shot(mc, "armor_closeup");
            } else if (t == (closeup ? 80 : 56)) {
                mc.setScreen(new IconWall());
            } else if (t == (closeup ? 92 : 70)) {
                shot(mc, "item_icons");
            } else if (t == (closeup ? 94 : 72)) {
                finish(mc);
            }
            if (tick > 116 && !done) finish(mc); // never overlap the core autotest
        } catch (Exception e) {
            HuntCraft.LOGGER.error("[AUTOTEST] armor autotest failed", e);
            finish(mc);
        }
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
            STANDS.add(st);
        }
        sp.getAbilities().flying = true;
        sp.onUpdateAbilities();
        view(sp, 0.0, 0.0, 180.0F, 9.0F);
    }

    /** Puts the camera at an offset from the original spawn point (z relative to the player, row at -ROW_DIST). */
    private static void view(ServerPlayer sp, double dx, double dz, float yaw, float pitch) {
        if (home == null) return;
        sp.teleportTo(sp.serverLevel(), home.x + dx, groundY + 0.5, home.z + dz, yaw, pitch);
    }

    private static void finish(Minecraft mc) {
        done = true;
        if (mc.screen instanceof IconWall) mc.setScreen(null);
        mc.options.hideGui = false;
        server(mc, sp -> {
            for (ArmorStand st : STANDS) st.discard();
            STANDS.clear();
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
