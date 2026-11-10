package com.eternalrealm.ui.map;

import com.eternalrealm.client.map.ClientMapState;
import com.eternalrealm.client.map.Minimap;
import com.eternalrealm.config.ERClientConfig;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.gen.AstoriaAreas;
import com.eternalrealm.world.map.MapMarkers;
import com.eternalrealm.world.map.MapMath;
import com.eternalrealm.world.map.MapView;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * The minimap in the top right corner: a round map of the surroundings drawn from the loaded chunks
 * ({@link Minimap}), the player's arrow, north, party members (pinned to the rim when far away), town NPCs with their
 * quest marks, dungeon entrances, the capital and the tower, dungeon traps; below it the area (or dungeon room) and the
 * coordinates. It moves down when status effect icons are shown.
 */
public final class MinimapOverlay implements IGuiOverlay {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int MARGIN = 4;
    private static final int BACKGROUND = 0xC0141018;
    private static final int RIM = 0xFFC9A55A;
    private static final int RIM_DARK = 0xFF2A2016;
    private static final int PARTY = 0xFF55E0FF;
    private static final int PLAYER = 0xFFFFFFFF;
    private boolean broken;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        LocalPlayer player = minecraft.player;
        if (broken || minecraft.options.hideGui || minecraft.options.renderDebug || player == null
                || minecraft.level == null || !Minimap.visible()) {
            return;
        }
        try {
            draw(minecraft, player, graphics, partialTick, screenWidth);
        } catch (RuntimeException e) {
            broken = true;
            LOG.error("Minimap overlay disabled after a rendering error", e);
        }
    }

    private static int size() {
        try {
            return ERClientConfig.MINIMAP_SIZE.get();
        } catch (IllegalStateException e) {
            return 96;
        }
    }

    private static boolean rotate() {
        try {
            return ERClientConfig.MINIMAP_ROTATE.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static boolean coordinates() {
        try {
            return ERClientConfig.MINIMAP_COORDINATES.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** Below the status effect icons when there are any (vanilla draws them in the top right corner). */
    private static int effectsOffset(LocalPlayer player) {
        boolean beneficial = false;
        boolean harmful = false;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (!effect.showIcon()) {
                continue;
            }
            if (effect.getEffect().isBeneficial()) {
                beneficial = true;
            } else {
                harmful = true;
            }
        }
        return harmful ? 52 : beneficial ? 26 : 0;
    }

    private void draw(Minecraft minecraft, LocalPlayer player, GuiGraphics graphics, float partialTick, int screenWidth) {
        Font font = minecraft.font;
        float radius = size() / 2.0F;
        float cx = screenWidth - MARGIN - radius;
        float cy = MARGIN + effectsOffset(player) + radius;
        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());
        float yaw = Mth.lerp(partialTick, player.yRotO, player.getYRot());
        boolean rotate = rotate() && !Minimap.caveMode();
        int blocks = Minimap.radiusBlocks();
        double pixelsPerBlock = radius / blocks;
        double mapRotation = rotate ? yaw + 180.0 : 0.0;
        MapView view = ClientMapState.view();

        MapDraw.disc(graphics, cx, cy, radius + 2.0F, RIM_DARK);
        MapDraw.disc(graphics, cx, cy, radius, BACKGROUND);
        if (Minimap.ready()) {
            float u = (float) ((px - Minimap.originX()) / Minimap.SIZE);
            float v = (float) ((pz - Minimap.originZ()) / Minimap.SIZE);
            MapDraw.texturedDisc(graphics, Minimap.TEXTURE, cx, cy, radius, u, v, (float) blocks / Minimap.SIZE,
                    Math.toRadians(-mapRotation));
        }

        Marker marker = new Marker(graphics, cx, cy, radius, px, pz, yaw, rotate, pixelsPerBlock);
        boolean dungeon = ClientMapState.inDungeonDimension(minecraft.level);
        if (dungeon) {
            view.dungeon().ifPresent(d -> {
                for (MapView.Trap trap : d.traps()) {
                    marker.trap(trap.x(), trap.z());
                }
            });
        }
        if (ClientMapState.inRealm(minecraft.level)) {
            for (MapView.Npc npc : view.npcs()) {
                marker.npc(font, npc);
            }
            if (view.realm().astoria()) {
                for (MapMarkers.Marker place : MapMarkers.astoria()) {
                    marker.place(place, view.isUnlocked(place.areaId()), view.hasWaystone(place.dungeonId()));
                }
            }
        }
        String here = ClientMapState.dimensionOf(minecraft.level);
        for (MapView.Member member : view.party()) {
            if (!member.dimension().equals(here)) {
                continue;
            }
            Player body = minecraft.level.getPlayerByUUID(member.id());
            double mx = body != null ? Mth.lerp(partialTick, body.xo, body.getX()) : member.x();
            double mz = body != null ? Mth.lerp(partialTick, body.zo, body.getZ()) : member.z();
            marker.member(mx, mz, member.leader());
        }

        // rim, north and the player
        MapDraw.ring(graphics, cx, cy, radius, radius + 1.5F, RIM);
        double[] north = MapMath.toMinimap(0.0, -1.0, yaw, rotate, 1.0);
        float nx = cx + (float) (north[0] * radius);
        float ny = cy + (float) (north[1] * radius);
        MapDraw.disc(graphics, nx, ny, 5.0F, RIM_DARK);
        graphics.drawString(font, "N", Math.round(nx - font.width("N") / 2.0F), Math.round(ny - 3.5F), 0xFFE8C66A,
                false);
        MapDraw.arrow(graphics, cx, cy, 4.5F, MapMath.arrowAngle(yaw, mapRotation), PLAYER, 0xFF000000);

        labels(minecraft, graphics, font, view, cx, cy + radius + 4.0F, screenWidth, player);
    }

    private static void labels(Minecraft minecraft, GuiGraphics graphics, Font font, MapView view, float cx, float top,
                               int screenWidth, LocalPlayer player) {
        int y = Math.round(top);
        Component title = null;
        if (ClientMapState.inDungeonDimension(minecraft.level) && view.dungeon().isPresent()) {
            MapView.Dungeon dungeon = view.dungeon().get();
            title = Component.translatable(dungeon.nameKey()).withStyle(ChatFormatting.GOLD);
            centered(graphics, font, title, cx, y, screenWidth);
            y += font.lineHeight + 1;
            centered(graphics, font, roomLabel(dungeon), cx, y, screenWidth);
            y += font.lineHeight + 1;
        } else if (ClientMapState.inRealm(minecraft.level)) {
            int area = AstoriaAreas.areaAt(player.getX() - view.realm().centerX(), player.getZ() - view.realm().centerZ());
            AreaDefinition definition = Areas.require(area);
            title = Component.translatable(definition.translationKey())
                    .withStyle(style -> style.withColor(MapMath.mix(definition.color(), 0xFFFFFF, 0.35)));
            centered(graphics, font, title, cx, y, screenWidth);
            y += font.lineHeight + 1;
            centered(graphics, font, WorldMapScreen.levelRange(definition).copy().withStyle(ChatFormatting.GRAY), cx, y,
                    screenWidth);
            y += font.lineHeight + 1;
        }
        if (coordinates()) {
            String text = String.format(Locale.ROOT, "%d, %d, %d", player.getBlockX(), player.getBlockY(),
                    player.getBlockZ());
            centered(graphics, font, Component.literal(text).withStyle(ChatFormatting.GRAY), cx, y, screenWidth);
        }
    }

    /** "部屋 2/4", the boss room, or the entrance hall before the start. */
    static Component roomLabel(MapView.Dungeon dungeon) {
        int rooms = dungeon.rooms().size();
        if (dungeon.currentRoom() < 0) {
            return Component.translatable("map.eternalrealm.entrance_hall").withStyle(ChatFormatting.GRAY);
        }
        if (dungeon.currentRoom() >= rooms) {
            return Component.translatable("map.eternalrealm.boss_room").withStyle(ChatFormatting.RED);
        }
        return Component.translatable("map.eternalrealm.room", dungeon.currentRoom() + 1, rooms)
                .withStyle(ChatFormatting.GRAY);
    }

    private static void centered(GuiGraphics graphics, Font font, Component text, float cx, int y, int screenWidth) {
        int width = font.width(text);
        int x = Math.min(Math.round(cx - width / 2.0F), screenWidth - 2 - width);
        graphics.drawString(font, text, x, y, 0xFFFFFF);
    }

    /** Places markers on the round map. */
    private static final class Marker {
        private final GuiGraphics graphics;
        private final float cx;
        private final float cy;
        private final float radius;
        private final double px;
        private final double pz;
        private final double yaw;
        private final boolean rotate;
        private final double pixelsPerBlock;

        Marker(GuiGraphics graphics, float cx, float cy, float radius, double px, double pz, double yaw, boolean rotate,
               double pixelsPerBlock) {
            this.graphics = graphics;
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.px = px;
            this.pz = pz;
            this.yaw = yaw;
            this.rotate = rotate;
            this.pixelsPerBlock = pixelsPerBlock;
        }

        /** Screen offset of a world point, or null if it is outside the map (minus {@code inset}). */
        private double[] at(double x, double z, float inset) {
            double[] offset = MapMath.toMinimap(x - px, z - pz, yaw, rotate, pixelsPerBlock);
            return offset[0] * offset[0] + offset[1] * offset[1] <= (radius - inset) * (radius - inset) ? offset : null;
        }

        void trap(double x, double z) {
            double[] p = at(x, z, 3.0F);
            if (p != null) {
                float sx = cx + (float) p[0];
                float sy = cy + (float) p[1];
                MapDraw.triangle(graphics, sx, sy - 2.5F, sx - 2.5F, sy + 2.0F, sx + 2.5F, sy + 2.0F, 0xE0FF3020);
            }
        }

        void npc(Font font, MapView.Npc npc) {
            double[] p = at(npc.x(), npc.z(), 3.0F);
            if (p == null) {
                return;
            }
            float sx = cx + (float) p[0];
            float sy = cy + (float) p[1];
            boolean story = "story".equals(npc.role());
            MapDraw.disc(graphics, sx, sy, 2.5F, 0xFF000000);
            MapDraw.disc(graphics, sx, sy, 1.8F, story ? 0xFF7FE0A0 : 0xFFF0D070);
            if (npc.mark() != MapView.Mark.NONE) {
                String mark = npc.mark().symbol();
                graphics.drawString(font, mark, Math.round(sx - font.width(mark) / 2.0F), Math.round(sy - 11.0F),
                        npc.mark() == MapView.Mark.READY ? 0xFFFFC040 : 0xFFFFFF40, true);
            }
        }

        void place(MapMarkers.Marker place, boolean unlocked, boolean recorded) {
            double[] p = at(place.x(), place.z(), 4.0F);
            if (p == null) {
                return;
            }
            float sx = cx + (float) p[0];
            float sy = cy + (float) p[1];
            switch (place.kind()) {
                case CAPITAL -> {
                    MapDraw.rect(graphics, sx - 4.0F, sy - 4.0F, sx + 4.0F, sy + 4.0F, 0xFF000000);
                    MapDraw.rect(graphics, sx - 3.0F, sy - 3.0F, sx + 3.0F, sy + 3.0F, 0xFFE8C66A);
                }
                case TOWER -> {
                    MapDraw.triangle(graphics, sx, sy - 6.0F, sx - 4.5F, sy + 4.0F, sx + 4.5F, sy + 4.0F, 0xFF000000);
                    MapDraw.triangle(graphics, sx, sy - 4.5F, sx - 3.2F, sy + 3.0F, sx + 3.2F, sy + 3.0F, 0xFF9A4ACF);
                }
                case DUNGEON -> {
                    MapDraw.diamond(graphics, sx, sy, 4.5F, 0xFF000000);
                    MapDraw.diamond(graphics, sx, sy, 3.2F, unlocked ? 0xFF4FD0C0 : 0xFF808080);
                }
                case WAYSTONE -> MapDraw.waystone(graphics, sx, sy, 6.0F, recorded);
            }
        }

        void member(double x, double z, boolean leader) {
            double[] offset = MapMath.toMinimap(x - px, z - pz, yaw, rotate, pixelsPerBlock);
            double[] p = MapMath.clampToCircle(offset[0], offset[1], radius - 3.0);
            float sx = cx + (float) p[0];
            float sy = cy + (float) p[1];
            MapDraw.disc(graphics, sx, sy, p[2] > 0.0 ? 3.5F : 3.0F, 0xFF000000);
            MapDraw.disc(graphics, sx, sy, p[2] > 0.0 ? 2.5F : 2.2F, leader ? 0xFFFFD447 : PARTY);
        }
    }
}
