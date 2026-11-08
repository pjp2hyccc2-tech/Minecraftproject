package com.eternalrealm.ui.map;

import com.eternalrealm.client.map.ClientMapState;
import com.eternalrealm.client.map.MapKeys;
import com.eternalrealm.dungeon.hazard.HazardKind;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.gen.AstoriaAreas;
import com.eternalrealm.world.gen.AstoriaLayout;
import com.eternalrealm.world.map.AtlasPainter;
import com.eternalrealm.world.map.MapMarkers;
import com.eternalrealm.world.map.MapMath;
import com.eternalrealm.world.map.MapView;
import com.eternalrealm.world.map.MapViewport;
import com.google.gson.JsonParseException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The world map (M, or the map button of the journal): a stylised atlas of アストリア painted from the pure layout
 * ({@link AtlasTexture}) with the area rings, their names and level ranges, sealed areas greyed out by the server's
 * world progress, the capital, the Tower of the End and every dungeon entrance with its recommended level, the town
 * NPCs near the player, the party members and the player. Drag to pan, scroll to zoom, hover for details. Inside a
 * dungeon instance it shows the instance's layout instead (rooms, current room, boss arena, traps).
 */
public final class WorldMapScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int HEADER = 24;
    private static final int FOOTER = 16;
    private static final int FRAME = 0xFF5A4A2A;
    private static final int PANEL = 0xF0101018;
    private static final int LABEL_BACKGROUND = 0xA0101018;
    private static final int HOVER_PIXELS = 7;
    /** The zoom is kept between openings; the view always starts on the player. */
    private static double realmZoom = 4.0;

    private enum Mode {
        REALM, DUNGEON
    }

    /** Something under the mouse: where it is drawn and what the tooltip says. */
    private record Hover(float x, float y, List<Component> lines) {
    }

    private final List<Hover> hovers = new ArrayList<>();
    private Mode mode = Mode.REALM;
    private MapViewport viewport;
    private boolean dragging;

    private WorldMapScreen() {
        super(Component.translatable("screen.eternalrealm.map.title"));
    }

    public static void open() {
        com.eternalrealm.client.sound.UiSounds.journalOpen();
        Minecraft.getInstance().setScreen(new WorldMapScreen());
    }

    // ------------------------------------------------------------------ layout

    private int frameLeft() {
        return MARGIN;
    }

    private int frameTop() {
        return HEADER;
    }

    private int frameRight() {
        return width - MARGIN;
    }

    private int frameBottom() {
        return height - FOOTER;
    }

    private float centerX() {
        return (frameLeft() + frameRight()) / 2.0F;
    }

    private float centerY() {
        return (frameTop() + frameBottom()) / 2.0F;
    }

    @Override
    protected void init() {
        Minecraft minecraft = Minecraft.getInstance();
        MapView view = ClientMapState.view();
        LocalPlayer player = minecraft.player;
        if (viewport == null) {
            if (player != null && minecraft.level != null && ClientMapState.inDungeonDimension(minecraft.level)
                    && view.dungeon().isPresent()) {
                mode = Mode.DUNGEON;
                MapView.Dungeon dungeon = view.dungeon().get();
                double fit = 280.0 / Math.max(80, Math.min(frameRight() - frameLeft(), frameBottom() - frameTop()));
                viewport = new MapViewport(player.getX() - dungeon.originX(), player.getZ() - dungeon.originZ(), fit);
            } else {
                mode = Mode.REALM;
                viewport = new MapViewport(0.0, 0.0, realmZoom);
                centerOnPlayer();
            }
        }
        int right = width - MARGIN;
        addRenderableWidget(Button.builder(Component.literal("×"), b -> onClose())
                .bounds(right - 18, 4, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("−"), b -> zoomButton(1.5))
                .bounds(right - 40, 4, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoomButton(1.0 / 1.5))
                .bounds(right - 60, 4, 18, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.map.center"),
                b -> centerOnPlayer()).bounds(right - 128, 4, 66, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.map.journal"),
                b -> com.eternalrealm.ui.screen.JournalScreen.open()).bounds(MARGIN, 4, 70, 16).build());
    }

    private void zoomButton(double factor) {
        viewport.zoom(factor, centerX(), centerY(), centerX(), centerY());
        rememberZoom();
    }

    private void rememberZoom() {
        if (mode == Mode.REALM) {
            realmZoom = viewport.blocksPerPixel();
        }
    }

    private void centerOnPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        MapView view = ClientMapState.view();
        if (player == null || minecraft.level == null) {
            return;
        }
        if (mode == Mode.DUNGEON) {
            view.dungeon().ifPresent(d -> viewport.centerOn(player.getX() - d.originX(), player.getZ() - d.originZ()));
        } else if (ClientMapState.inRealm(minecraft.level)) {
            viewport.centerOn(player.getX() - view.realm().centerX(), player.getZ() - view.realm().centerZ());
        } else {
            viewport.centerOn(0.0, 0.0);
        }
    }

    // ------------------------------------------------------------------ input

    private boolean insideFrame(double x, double y) {
        return x >= frameLeft() && x < frameRight() && y >= frameTop() && y < frameBottom();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && insideFrame(mouseX, mouseY)) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            viewport.drag(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0.0) {
            viewport.zoom(delta > 0.0 ? 1.0 / 1.25 : 1.25, mouseX, mouseY, centerX(), centerY());
            rememberZoom();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MapKeys.WORLD_MAP.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        double step = 40.0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> viewport.drag(step, 0.0);
            case GLFW.GLFW_KEY_RIGHT -> viewport.drag(-step, 0.0);
            case GLFW.GLFW_KEY_UP -> viewport.drag(0.0, step);
            case GLFW.GLFW_KEY_DOWN -> viewport.drag(0.0, -step);
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        hovers.clear();
        int x0 = frameLeft();
        int y0 = frameTop();
        int x1 = frameRight();
        int y1 = frameBottom();
        graphics.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, FRAME);
        graphics.fill(x0, y0, x1, y1, PANEL);
        graphics.enableScissor(x0, y0, x1, y1);
        if (mode == Mode.DUNGEON && ClientMapState.view().dungeon().isPresent()) {
            drawDungeon(graphics, ClientMapState.view().dungeon().get(), partialTick);
        } else {
            drawRealm(graphics, partialTick);
        }
        graphics.disableScissor();
        graphics.drawString(font, title, MARGIN + 76, 8, 0xFFE8C66A);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.map.hint",
                MapKeys.WORLD_MAP.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY), MARGIN, height - 12,
                0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (insideFrame(mouseX, mouseY) && !dragging) {
            tooltip(graphics, mouseX, mouseY);
        }
    }

    private void drawRealm(GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        MapView view = ClientMapState.view();
        boolean astoria = view.realm().astoria();
        float cx = centerX();
        float cy = centerY();
        double ppb = viewport.pixelsPerBlock();
        float originX = (float) viewport.screenX(0.0, cx);
        float originY = (float) viewport.screenY(0.0, cy);
        net.minecraft.resources.ResourceLocation atlas = AtlasTexture.get(astoria);
        if (atlas != null) {
            int extent = AtlasPainter.EXTENT;
            MapDraw.texturedRect(graphics, atlas, (float) viewport.screenX(-extent, cx), (float) viewport.screenY(-extent, cy),
                    (float) viewport.screenX(extent, cx), (float) viewport.screenY(extent, cy), 0.0F, 0.0F, 1.0F, 1.0F);
        } else {
            for (int id = Areas.LAST_ID; id >= Areas.FIRST_ID; id--) {
                AreaDefinition area = Areas.require(id);
                double outer = area.isUnbounded() ? AtlasPainter.LAND_RADIUS : area.outerRadius();
                MapDraw.disc(graphics, originX, originY, (float) (outer * ppb), MapMath.opaque(area.color()));
            }
            graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.map.painting"),
                    Math.round(cx), frameTop() + 6, 0xFFE8C66A);
        }
        // sealed areas: greyed out
        for (AreaDefinition area : Areas.all()) {
            if (view.isUnlocked(area.id())) {
                continue;
            }
            double outer = area.isUnbounded() ? AtlasPainter.EXTENT * 1.5 : area.outerRadius();
            MapDraw.ring(graphics, originX, originY, (float) (area.innerRadius() * ppb), (float) (outer * ppb), 0xB0282830);
        }
        // area names along the north-east diagonal (hidden when zoomed out so far that they would overlap)
        for (AreaDefinition area : viewport.blocksPerPixel() <= 12.0 ? Areas.all() : List.<AreaDefinition>of()) {
            double middle = area.isUnbounded() ? area.innerRadius() + 160.0 : (area.innerRadius() + area.outerRadius()) / 2.0;
            if (area.id() == 1) {
                middle = 190.0;
            }
            double angle = Math.toRadians(-40.0);
            float lx = (float) viewport.screenX(Math.cos(angle) * middle, cx);
            float ly = (float) viewport.screenY(Math.sin(angle) * middle, cy);
            boolean open = view.isUnlocked(area.id());
            MutableComponent name = Component.translatable(area.translationKey());
            name.withStyle(style -> style.withColor(open ? MapMath.mix(area.color(), 0xFFFFFF, 0.45) : 0x9A9A9A));
            Component sub = open ? Component.translatable("map.eternalrealm.level_range", area.minLevel(), area.maxLevel())
                    : Component.translatable("map.eternalrealm.sealed");
            label(graphics, name, lx, ly - 5.0F);
            label(graphics, sub.copy().withStyle(open ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY), lx, ly + 5.0F);
        }
        // the fixed places
        if (astoria) {
            for (MapMarkers.Marker place : MapMarkers.astoria()) {
                drawPlace(graphics, view, place, cx, cy, ppb);
            }
        }
        if (minecraft.level == null || !ClientMapState.inRealm(minecraft.level)) {
            graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.map.elsewhere"),
                    Math.round(cx), frameBottom() - 14, 0xFFFF8060);
            return;
        }
        double realmX = view.realm().centerX();
        double realmZ = view.realm().centerZ();
        if (ppb >= 0.6) {
            for (MapView.Npc npc : view.npcs()) {
                drawNpc(graphics, npc, (float) viewport.screenX(npc.x() - realmX, cx),
                        (float) viewport.screenY(npc.z() - realmZ, cy));
            }
        }
        drawMembersAndPlayer(graphics, view, realmX, realmZ, cx, cy, partialTick);
    }

    private void drawPlace(GuiGraphics graphics, MapView view, MapMarkers.Marker place, float cx, float cy, double ppb) {
        float sx = (float) viewport.screenX(place.x(), cx);
        float sy = (float) viewport.screenY(place.z(), cy);
        boolean open = view.isUnlocked(place.areaId());
        List<Component> lines = new ArrayList<>();
        switch (place.kind()) {
            case CAPITAL -> {
                MapDraw.rect(graphics, sx - 6.0F, sy - 6.0F, sx + 6.0F, sy + 6.0F, 0xFF000000);
                MapDraw.rect(graphics, sx - 5.0F, sy - 5.0F, sx + 5.0F, sy + 5.0F, 0xFFE8C66A);
                MapDraw.rect(graphics, sx - 2.0F, sy - 5.0F, sx + 2.0F, sy - 1.0F, 0xFF8A6A2A);
                lines.add(Component.translatable(MapMarkers.CAPITAL_KEY).withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("map.eternalrealm.capital.desc").withStyle(ChatFormatting.GRAY));
                label(graphics, Component.translatable(MapMarkers.CAPITAL_KEY).withStyle(ChatFormatting.GOLD), sx,
                        sy + 12.0F);
            }
            case TOWER -> {
                MapDraw.triangle(graphics, sx, sy - 10.0F, sx - 6.0F, sy + 6.0F, sx + 6.0F, sy + 6.0F, 0xFF000000);
                MapDraw.triangle(graphics, sx, sy - 8.0F, sx - 4.5F, sy + 5.0F, sx + 4.5F, sy + 5.0F,
                        open ? 0xFFB060F0 : 0xFF6A6A6A);
                lines.add(Component.translatable(MapMarkers.TOWER_KEY).withStyle(ChatFormatting.LIGHT_PURPLE));
                lines.add(Component.translatable("map.eternalrealm.dungeon_level", place.level())
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(view.finalBossUnlocked() ? "map.eternalrealm.tower.final_open"
                        : "map.eternalrealm.tower.desc").withStyle(ChatFormatting.DARK_PURPLE));
                label(graphics, Component.translatable(MapMarkers.TOWER_KEY).withStyle(ChatFormatting.LIGHT_PURPLE), sx,
                        sy + 13.0F);
            }
            case DUNGEON -> {
                MapDraw.diamond(graphics, sx, sy, 6.0F, 0xFF000000);
                MapDraw.diamond(graphics, sx, sy, 4.5F, open ? 0xFF4FD0C0 : 0xFF7A7A7A);
                Component name = Component.translatable(place.nameKey());
                lines.add(name.copy().withStyle(open ? ChatFormatting.AQUA : ChatFormatting.GRAY));
                lines.add(Component.translatable("map.eternalrealm.dungeon_level", place.level())
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(Areas.require(place.areaId()).translationKey())
                        .withStyle(ChatFormatting.DARK_GRAY));
                if (!open) {
                    lines.add(Component.translatable("map.eternalrealm.sealed").withStyle(ChatFormatting.RED));
                }
                if (ppb >= 0.1) {
                    label(graphics, Component.translatable("map.eternalrealm.dungeon_label", name, place.level())
                            .withStyle(open ? ChatFormatting.AQUA : ChatFormatting.GRAY), sx, sy + 11.0F);
                }
            }
        }
        hovers.add(new Hover(sx, sy, lines));
    }

    private void drawNpc(GuiGraphics graphics, MapView.Npc npc, float sx, float sy) {
        MapDraw.disc(graphics, sx, sy, 3.5F, 0xFF000000);
        MapDraw.disc(graphics, sx, sy, 2.6F, "story".equals(npc.role()) ? 0xFF7FE0A0 : 0xFFF0D070);
        List<Component> lines = new ArrayList<>();
        lines.add(npcName(npc));
        if (npc.mark() != MapView.Mark.NONE) {
            String symbol = npc.mark().symbol();
            graphics.drawString(font, symbol, Math.round(sx - font.width(symbol) / 2.0F), Math.round(sy - 13.0F),
                    npc.mark() == MapView.Mark.READY ? 0xFFFFC040 : 0xFFFFFF40, true);
            lines.add(Component.translatable(npc.mark() == MapView.Mark.READY ? "map.eternalrealm.npc.ready"
                    : "map.eternalrealm.npc.offer").withStyle(ChatFormatting.YELLOW));
        }
        hovers.add(new Hover(sx, sy, lines));
    }

    private static Component npcName(MapView.Npc npc) {
        try {
            MutableComponent name = Component.Serializer.fromJson(npc.name());
            if (name != null) {
                return name;
            }
        } catch (JsonParseException | IllegalStateException e) {
            // fall through to the role name
        }
        return Component.translatable("npc.eternalrealm." + npc.role());
    }

    /** Party members and the player at map positions relative to ({@code baseX}, {@code baseZ}). */
    private void drawMembersAndPlayer(GuiGraphics graphics, MapView view, double baseX, double baseZ, float cx, float cy,
                                      float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        String here = ClientMapState.dimensionOf(minecraft.level);
        for (MapView.Member member : view.party()) {
            if (!member.dimension().equals(here)) {
                continue;
            }
            Player body = minecraft.level.getPlayerByUUID(member.id());
            double x = body != null ? body.getX() : member.x();
            double z = body != null ? body.getZ() : member.z();
            float sx = (float) viewport.screenX(x - baseX, cx);
            float sy = (float) viewport.screenY(z - baseZ, cy);
            MapDraw.disc(graphics, sx, sy, 4.0F, 0xFF000000);
            MapDraw.disc(graphics, sx, sy, 3.0F, member.leader() ? 0xFFFFD447 : 0xFF55E0FF);
            label(graphics, Component.literal(member.name()).withStyle(ChatFormatting.AQUA), sx, sy + 9.0F);
            hovers.add(new Hover(sx, sy, List.of(Component.literal(member.name()).withStyle(ChatFormatting.AQUA),
                    Component.translatable("map.eternalrealm.member.health",
                            Math.round(member.health() * 100.0F)).withStyle(ChatFormatting.GRAY))));
        }
        double px = net.minecraft.util.Mth.lerp(partialTick, player.xo, player.getX()) - baseX;
        double pz = net.minecraft.util.Mth.lerp(partialTick, player.zo, player.getZ()) - baseZ;
        float sx = (float) viewport.screenX(px, cx);
        float sy = (float) viewport.screenY(pz, cy);
        float yaw = net.minecraft.util.Mth.lerp(partialTick, player.yRotO, player.getYRot());
        MapDraw.arrow(graphics, sx, sy, 6.0F, MapMath.arrowAngle(yaw, 0.0), 0xFFFFFFFF, 0xFF000000);
        hovers.add(new Hover(sx, sy, List.of(Component.translatable("map.eternalrealm.you").withStyle(ChatFormatting.WHITE),
                Component.literal(String.format(Locale.ROOT, "%d, %d, %d", player.getBlockX(), player.getBlockY(),
                        player.getBlockZ())).withStyle(ChatFormatting.GRAY))));
    }

    private void drawDungeon(GuiGraphics graphics, MapView.Dungeon dungeon, float partialTick) {
        float cx = centerX();
        float cy = centerY();
        for (MapView.Rect corridor : dungeon.corridors()) {
            block(graphics, corridor, 0xFF4A4A52, cx, cy);
        }
        block(graphics, dungeon.hall(), 0xFF5A6A8A, cx, cy);
        tag(graphics, dungeon.hall(), Component.translatable("map.eternalrealm.entrance_hall"), cx, cy);
        for (int i = 0; i < dungeon.rooms().size(); i++) {
            MapView.Rect room = dungeon.rooms().get(i);
            int color = i < dungeon.currentRoom() ? 0xFF4F7A4F : i == dungeon.currentRoom() ? 0xFFC9A55A : 0xFF6A5A4A;
            block(graphics, room, color, cx, cy);
            tag(graphics, room, Component.translatable("map.eternalrealm.room_short", i + 1), cx, cy);
        }
        long time = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        int arena = dungeon.bossFight() && (time / 10) % 2 == 0 ? 0xFFB03030 : 0xFF7A2A2A;
        block(graphics, dungeon.arena(), arena, cx, cy);
        tag(graphics, dungeon.arena(), Component.translatable("map.eternalrealm.boss_room"), cx, cy);
        for (MapView.Trap trap : dungeon.traps()) {
            float sx = (float) viewport.screenX(trap.x() - dungeon.originX(), cx);
            float sy = (float) viewport.screenY(trap.z() - dungeon.originZ(), cy);
            MapDraw.triangle(graphics, sx, sy - 4.0F, sx - 4.0F, sy + 3.0F, sx + 4.0F, sy + 3.0F, 0xFF000000);
            MapDraw.triangle(graphics, sx, sy - 2.8F, sx - 2.8F, sy + 2.2F, sx + 2.8F, sy + 2.2F, 0xFFFF4030);
            List<Component> lines = new ArrayList<>();
            for (HazardKind kind : HazardKind.values()) {
                if (kind.id().equals(trap.kind())) {
                    lines.add(Component.translatable(kind.translationKey()).withStyle(ChatFormatting.RED));
                }
            }
            lines.add(Component.translatable("map.eternalrealm.trap").withStyle(ChatFormatting.GRAY));
            hovers.add(new Hover(sx, sy, lines));
        }
        graphics.drawCenteredString(font, Component.translatable(dungeon.nameKey()).withStyle(ChatFormatting.GOLD),
                Math.round(cx), frameTop() + 4, 0xFFFFFF);
        graphics.drawCenteredString(font, MinimapOverlay.roomLabel(dungeon), Math.round(cx), frameTop() + 15, 0xFFFFFF);
        drawMembersAndPlayer(graphics, ClientMapState.view(), dungeon.originX(), dungeon.originZ(), cx, cy, partialTick);
    }

    private void block(GuiGraphics graphics, MapView.Rect rect, int color, float cx, float cy) {
        float x0 = (float) viewport.screenX(rect.minX(), cx);
        float y0 = (float) viewport.screenY(rect.minZ(), cy);
        float x1 = (float) viewport.screenX(rect.maxX() + 1.0, cx);
        float y1 = (float) viewport.screenY(rect.maxZ() + 1.0, cy);
        MapDraw.rect(graphics, x0, y0, x1, y1, 0xFF000000);
        float inset = (float) Math.min(1.0, (x1 - x0) / 4.0);
        MapDraw.rect(graphics, x0 + inset, y0 + inset, x1 - inset, y1 - inset, color);
    }

    private void tag(GuiGraphics graphics, MapView.Rect rect, Component text, float cx, float cy) {
        float x = (float) viewport.screenX((rect.minX() + rect.maxX() + 1) / 2.0, cx);
        float y = (float) viewport.screenY((rect.minZ() + rect.maxZ() + 1) / 2.0, cy);
        label(graphics, text, x, y - 4.0F);
    }

    /** Text centred on (x, y) on a dark plate. */
    private void label(GuiGraphics graphics, Component text, float x, float y) {
        int width = font.width(text);
        int left = Math.round(x - width / 2.0F);
        int top = Math.round(y - 4.0F);
        graphics.fill(left - 2, top - 1, left + width + 2, top + 9, LABEL_BACKGROUND);
        graphics.drawString(font, text, left, top, 0xFFFFFF, false);
    }

    // ------------------------------------------------------------------ tooltips

    private void tooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Hover best = null;
        double bestDistance = HOVER_PIXELS * HOVER_PIXELS;
        for (Hover hover : hovers) {
            double dx = hover.x() - mouseX;
            double dy = hover.y() - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance <= bestDistance && !hover.lines().isEmpty()) {
                best = hover;
                bestDistance = distance;
            }
        }
        if (best != null) {
            graphics.renderComponentTooltip(font, best.lines(), mouseX, mouseY);
            return;
        }
        if (mode == Mode.REALM) {
            graphics.renderComponentTooltip(font, areaLines(mouseX, mouseY), mouseX, mouseY);
        }
    }

    /** The area under the mouse: name, level range, sealed or open, visited or not, biome and coordinates. */
    private List<Component> areaLines(int mouseX, int mouseY) {
        MapView view = ClientMapState.view();
        double x = viewport.worldX(mouseX, centerX());
        double z = viewport.worldZ(mouseY, centerY());
        AreaDefinition area = Areas.require(AstoriaAreas.areaAt(x, z));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(area.translationKey())
                .withStyle(style -> style.withColor(MapMath.mix(area.color(), 0xFFFFFF, 0.45))));
        lines.add(Component.translatable("map.eternalrealm.level_range", area.minLevel(), area.maxLevel())
                .withStyle(ChatFormatting.GRAY));
        if (!view.isUnlocked(area.id())) {
            lines.add(Component.translatable("map.eternalrealm.sealed.desc").withStyle(ChatFormatting.RED));
        } else if (!view.isDiscovered(area.id())) {
            lines.add(Component.translatable("map.eternalrealm.undiscovered").withStyle(ChatFormatting.YELLOW));
        }
        if (view.realm().astoria() && Math.sqrt(x * x + z * z) < AtlasPainter.LAND_RADIUS) {
            lines.add(Component.translatable(AstoriaLayout.biomeAt((int) Math.floor(x), (int) Math.floor(z))
                    .translationKey()).withStyle(ChatFormatting.DARK_GREEN));
        }
        lines.add(Component.literal(String.format(Locale.ROOT, "%d, %d", Math.round(x + view.realm().centerX()),
                Math.round(z + view.realm().centerZ()))).withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }
}
