package com.eternalrealm.ui.map;

import com.eternalrealm.client.map.ClientMapState;
import com.eternalrealm.client.map.MapKeys;
import com.eternalrealm.dungeon.hazard.HazardKind;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.Waystones;
import com.eternalrealm.world.gen.AstoriaAreas;
import com.eternalrealm.world.gen.AstoriaLayout;
import com.eternalrealm.world.map.AtlasPainter;
import com.eternalrealm.world.map.FieldMaskPainter;
import com.eternalrealm.world.map.MapMarkers;
import com.eternalrealm.world.map.MapMath;
import com.eternalrealm.world.map.MapView;
import com.eternalrealm.world.map.MapViewport;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The world map (M, or the map button of the journal), with two tabs:
 * <ul>
 *   <li><b>フィールドマップ</b> (first press of M): the field the player is in, zoomed to fit, everything else veiled and
 *   the field's border drawn in its colour ({@link FieldMaskTexture}), with a panel: name, recommended level, what it
 *   is like, its waystone and its dungeons;</li>
 *   <li><b>全域マップ</b> (M again, or Tab): the whole kingdom — every field with its name and level range, sealed ones in
 *   grey, the capital, the Tower of the End, every dungeon entrance and every waystone.</li>
 * </ul>
 * Clicking a field (without dragging) opens its field map. Waystones show whether the player has recorded them.
 * Opened at a waystone ({@link #openTravel}) the screen is the travel menu: a list of all waystones next to the map;
 * recorded ones can be chosen on the list or the map and the server checks and performs the travel. Inside a dungeon
 * instance it shows the instance's layout instead (rooms, current room, boss arena, traps).
 */
public final class WorldMapScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int HEADER = 24;
    private static final int FOOTER = 16;
    private static final int FRAME = 0xFF5A4A2A;
    private static final int PANEL = 0xF0101018;
    private static final int LABEL_BACKGROUND = 0xA0101018;
    private static final int HOVER_PIXELS = 7;
    private static final int TRAVEL_PANEL = 168;
    private static final int ROW = 22;
    private static final int CLICK_SLOP = 4;

    private enum Mode {
        REALM, DUNGEON
    }

    /** The two realm views. */
    public enum Tab {
        FIELD, WHOLE
    }

    /** Something under the mouse: where it is drawn and what the tooltip says. */
    private record Hover(float x, float y, List<Component> lines) {
    }

    /** A waystone drawn on the map (clickable in travel mode). */
    private record StoneSpot(float x, float y, String id) {
    }

    /** Opened at a waystone: where the player stands, when travel is possible again, what the server says is known. */
    private record Travel(String from, long readyAtMillis, Set<String> recorded) {
    }

    private final List<Hover> hovers = new ArrayList<>();
    private final List<StoneSpot> stoneSpots = new ArrayList<>();
    private final Travel travel;
    private Mode mode = Mode.REALM;
    private Tab tab;
    private int field = FieldLayout.MEADOW;
    private MapViewport viewport;
    private boolean dragging;
    private boolean pressed;
    private double pressX;
    private double pressY;
    private String selected;
    private Button travelButton;

    private WorldMapScreen(Tab tab, Travel travel) {
        super(Component.translatable(travel != null ? "screen.eternalrealm.waystone.title"
                : "screen.eternalrealm.map.title"));
        this.tab = tab;
        this.travel = travel;
    }

    /** M or the journal: the field map first. */
    public static void open() {
        com.eternalrealm.client.sound.UiSounds.journalOpen();
        Minecraft.getInstance().setScreen(new WorldMapScreen(Tab.FIELD, null));
    }

    /**
     * The server's answer to touching a waystone ({@link Waystones#UI_CHANNEL}):
     * {@code {"at": id, "cooldown": seconds, "recorded": [ids]}}.
     */
    public static void openTravel(JsonObject data) {
        String at = data.has("at") && data.get("at").isJsonPrimitive() ? data.get("at").getAsString() : "";
        if (Waystones.byId(at).isEmpty()) {
            return;
        }
        double cooldown = 0.0;
        if (data.has("cooldown") && data.get("cooldown").isJsonPrimitive()) {
            try {
                cooldown = Math.max(0.0, Math.min(600.0, data.get("cooldown").getAsDouble()));
            } catch (NumberFormatException | UnsupportedOperationException e) {
                cooldown = 0.0;
            }
        }
        Set<String> recorded = new HashSet<>();
        if (data.has("recorded") && data.get("recorded").isJsonArray()) {
            JsonArray array = data.getAsJsonArray("recorded");
            for (JsonElement element : array) {
                if (element.isJsonPrimitive() && Waystones.byId(element.getAsString()).isPresent()) {
                    recorded.add(element.getAsString());
                }
            }
        }
        com.eternalrealm.client.sound.UiSounds.journalOpen();
        Minecraft.getInstance().setScreen(new WorldMapScreen(Tab.WHOLE,
                new Travel(at, System.currentTimeMillis() + Math.round(cooldown * 1000.0), Set.copyOf(recorded))));
    }

    private boolean recorded(String waystone) {
        return ClientMapState.view().hasWaystone(waystone) || (travel != null && travel.recorded().contains(waystone));
    }

    // ------------------------------------------------------------------ layout

    private int frameLeft() {
        return MARGIN;
    }

    private int frameTop() {
        return HEADER;
    }

    private int frameRight() {
        return width - MARGIN - (travel != null ? TRAVEL_PANEL + 6 : 0);
    }

    private int panelLeft() {
        return width - MARGIN - TRAVEL_PANEL;
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
            if (travel == null && player != null && minecraft.level != null
                    && ClientMapState.inDungeonDimension(minecraft.level) && view.dungeon().isPresent()) {
                mode = Mode.DUNGEON;
                MapView.Dungeon dungeon = view.dungeon().get();
                double fit = 280.0 / Math.max(80, Math.min(frameRight() - frameLeft(), frameBottom() - frameTop()));
                viewport = new MapViewport(player.getX() - dungeon.originX(), player.getZ() - dungeon.originZ(), fit);
            } else {
                mode = Mode.REALM;
                field = playerField();
                if (travel != null) {
                    field = Waystones.byId(travel.from()).map(FieldLayout.Waystone::field).orElse(field);
                }
                fit();
            }
        } else if (mode == Mode.REALM) {
            fit(); // the window was resized
        }
        int right = frameRight();
        addRenderableWidget(Button.builder(Component.literal("×"), b -> onClose())
                .bounds(width - MARGIN - 18, 4, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("−"), b -> zoomButton(1.5))
                .bounds(right - 40, 4, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoomButton(1.0 / 1.5))
                .bounds(right - 60, 4, 18, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.map.center"),
                b -> centerOnPlayer()).bounds(right - 128, 4, 66, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.map.journal"),
                b -> com.eternalrealm.ui.screen.JournalScreen.open()).bounds(MARGIN, 4, 70, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("threat.eternalrealm.board.open"),
                b -> ThreatBoardScreen.open()).bounds(MARGIN + 74, 4, 78, 16).build());
        if (mode == Mode.REALM) {
            int mid = (frameLeft() + right) / 2;
            addRenderableWidget(Button.builder(tabLabel(Tab.FIELD), b -> showTab(Tab.FIELD, playerField()))
                    .bounds(mid - 96, 4, 94, 16).build());
            addRenderableWidget(Button.builder(tabLabel(Tab.WHOLE), b -> showTab(Tab.WHOLE, field))
                    .bounds(mid + 2, 4, 94, 16).build());
        }
        if (travel != null) {
            travelButton = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.waystone.go"),
                    b -> travel()).bounds(panelLeft() + 8, frameBottom() - 22, TRAVEL_PANEL - 16, 18).build());
            travelButton.active = false;
        }
    }

    private Component tabLabel(Tab which) {
        MutableComponent label = Component.translatable(which == Tab.FIELD ? "screen.eternalrealm.map.tab.field"
                : "screen.eternalrealm.map.tab.whole");
        return which == tab ? Component.literal("» ").append(label.withStyle(ChatFormatting.GOLD)).append(" «")
                : label;
    }

    /** Field (area id) the player stands in; the meadow when not in the realm. */
    private static int playerField() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !ClientMapState.inRealm(minecraft.level)) {
            return FieldLayout.MEADOW;
        }
        MapView view = ClientMapState.view();
        return AstoriaAreas.areaAt(player.getX() - view.realm().centerX(), player.getZ() - view.realm().centerZ());
    }

    private void showTab(Tab which, int newField) {
        tab = which;
        field = Math.max(1, Math.min(FieldLayout.FIELD_COUNT, newField));
        com.eternalrealm.client.sound.UiSounds.journalOpen();
        rebuildWidgets();
        fit();
    }

    /** Fits the view to the tab: the whole kingdom, or the field's region with a little of its neighbours. */
    private void fit() {
        double w = Math.max(40, frameRight() - frameLeft());
        double h = Math.max(40, frameBottom() - frameTop());
        if (tab == Tab.WHOLE) {
            double minX = FieldLayout.MIN_X - 60;
            double maxX = FieldLayout.MAX_X + 60;
            double minZ = FieldLayout.MIN_Z - 60;
            double maxZ = FieldLayout.MAX_Z + 60;
            viewport = new MapViewport((minX + maxX) / 2.0, (minZ + maxZ) / 2.0,
                    Math.max((maxX - minX) / w, (maxZ - minZ) / h));
        } else {
            int[] b = FieldLayout.regionBounds(field);
            double minX = b[0] - 40;
            double maxX = b[2] + 56;
            double minZ = b[1] - 40;
            double maxZ = b[3] + 56;
            viewport = new MapViewport((minX + maxX) / 2.0, (minZ + maxZ) / 2.0,
                    Math.max((maxX - minX) / w, (maxZ - minZ) / h) * 1.08);
        }
    }

    private void zoomButton(double factor) {
        viewport.zoom(factor, centerX(), centerY(), centerX(), centerY());
    }

    /** Travel mode: asks the server to travel to the selected waystone (the server checks everything). */
    private void travel() {
        if (travel == null || selected == null || !canChoose(selected)) {
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("to", selected);
        ERNetwork.sendToServer(new UiActionPacket(Waystones.TRAVEL_ACTION, args));
        onClose();
    }

    /** Whether the player may pick {@code id} as the destination (the server checks again). */
    private boolean canChoose(String id) {
        if (travel == null || id.equals(travel.from()) || !recorded(id)) {
            return false;
        }
        return Waystones.byId(id).map(w -> ClientMapState.view().isUnlocked(w.field())).orElse(false);
    }

    private void select(String id) {
        if (canChoose(id)) {
            selected = id;
            com.eternalrealm.client.sound.UiSounds.journalOpen();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (travelButton != null) {
            travelButton.active = selected != null && canChoose(selected)
                    && System.currentTimeMillis() >= travel.readyAtMillis();
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
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (travel != null && mouseX >= panelLeft() && mouseX < width - MARGIN) {
            int index = (int) Math.floor((mouseY - (frameTop() + 18)) / ROW);
            List<FieldLayout.Waystone> all = Waystones.all();
            if (mouseY >= frameTop() + 18 && index >= 0 && index < all.size()) {
                select(all.get(index).id());
                return true;
            }
            return false;
        }
        if (insideFrame(mouseX, mouseY)) {
            if (travel != null) {
                for (StoneSpot spot : stoneSpots) {
                    double dx = spot.x() - mouseX;
                    double dy = spot.y() - mouseY;
                    if (dx * dx + dy * dy <= 81.0 && canChoose(spot.id())) {
                        select(spot.id());
                        return true;
                    }
                }
            }
            dragging = true;
            pressed = true;
            pressX = mouseX;
            pressY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean click = pressed && Math.abs(mouseX - pressX) <= CLICK_SLOP && Math.abs(mouseY - pressY) <= CLICK_SLOP;
        dragging = false;
        pressed = false;
        if (click && mode == Mode.REALM && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // a click (not a drag) on a field opens its field map
            double x = viewport.worldX(mouseX, centerX());
            double z = viewport.worldZ(mouseY, centerY());
            if (FieldLayout.inBounds(x, z)) {
                int clicked = AstoriaAreas.areaAt(x, z);
                if (tab == Tab.WHOLE || clicked != field) {
                    showTab(Tab.FIELD, clicked);
                    return true;
                }
            }
        }
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
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MapKeys.WORLD_MAP.matches(keyCode, scanCode)) {
            if (mode == Mode.REALM && tab == Tab.FIELD && travel == null) {
                showTab(Tab.WHOLE, field);
            } else {
                onClose();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && mode == Mode.REALM) {
            showTab(tab == Tab.FIELD ? Tab.WHOLE : Tab.FIELD, tab == Tab.FIELD ? field : playerField());
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
        stoneSpots.clear();
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
        if (travel != null) {
            drawTravelPanel(graphics, mouseX, mouseY);
        }
        graphics.drawString(font, title, MARGIN + 76, 8, 0xFFE8C66A);
        Component hint = mode == Mode.REALM
                ? Component.translatable(travel != null ? "screen.eternalrealm.waystone.hint"
                : "screen.eternalrealm.map.hint_tabs", MapKeys.WORLD_MAP.getTranslatedKeyMessage())
                : Component.translatable("screen.eternalrealm.map.hint", MapKeys.WORLD_MAP.getTranslatedKeyMessage());
        graphics.drawString(font, hint.copy().withStyle(ChatFormatting.GRAY), MARGIN, height - 12, 0xFFFFFF);
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
        net.minecraft.resources.ResourceLocation atlas = AtlasTexture.get(astoria);
        if (atlas != null) {
            int extent = AtlasPainter.EXTENT;
            MapDraw.texturedRect(graphics, atlas, (float) viewport.screenX(-extent, cx), (float) viewport.screenY(-extent, cy),
                    (float) viewport.screenX(extent, cx), (float) viewport.screenY(extent, cy), 0.0F, 0.0F, 1.0F, 1.0F);
        } else {
            int extent = AtlasPainter.EXTENT;
            graphics.fill(Math.round((float) viewport.screenX(-extent, cx)), Math.round((float) viewport.screenY(-extent, cy)),
                    Math.round((float) viewport.screenX(extent, cx)), Math.round((float) viewport.screenY(extent, cy)),
                    0xFFE8DCB5);
            graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.map.painting"),
                    Math.round(cx), frameTop() + 6, 0xFFE8C66A);
        }
        if (tab == Tab.FIELD && astoria) {
            drawFieldMask(graphics, cx, cy);
        }
        // field names with their recommended levels (sealed fields in grey)
        for (AreaDefinition area : viewport.blocksPerPixel() <= 14.0 ? Areas.all() : List.<AreaDefinition>of()) {
            if (tab == Tab.FIELD && area.id() == field) {
                continue; // the panel names it
            }
            double[] at = com.eternalrealm.world.field.FieldLayout.labelPoint(area.id());
            float lx = (float) viewport.screenX(at[0], cx);
            float ly = (float) viewport.screenY(at[1], cy);
            boolean open = view.isUnlocked(area.id());
            MutableComponent name = Component.translatable(area.translationKey());
            name.withStyle(style -> style.withColor(open ? MapMath.mix(area.color(), 0xFFFFFF, 0.45) : 0x9A9A9A));
            Component sub = open ? levelRange(area) : Component.translatable("map.eternalrealm.sealed");
            label(graphics, name, lx, ly - 5.0F);
            label(graphics, sub.copy().withStyle(open ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY), lx, ly + 5.0F);
        }
        // the fixed places
        if (astoria) {
            for (MapMarkers.Marker place : MapMarkers.astoria()) {
                drawPlace(graphics, view, place, cx, cy, ppb);
            }
            drawThreats(graphics, cx, cy);
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
        if (tab == Tab.FIELD && astoria) {
            drawFieldPanel(graphics, view);
        }
    }

    /** Veils everything but the field and draws its border (see {@link FieldMaskPainter}). */
    private void drawFieldMask(GuiGraphics graphics, float cx, float cy) {
        int[] box = FieldMaskPainter.box(field);
        float x0 = (float) viewport.screenX(box[0], cx);
        float y0 = (float) viewport.screenY(box[1], cy);
        float x1 = (float) viewport.screenX(box[2], cx);
        float y1 = (float) viewport.screenY(box[3], cy);
        net.minecraft.resources.ResourceLocation mask = FieldMaskTexture.get(field);
        if (mask != null) {
            MapDraw.texturedRect(graphics, mask, x0, y0, x1, y1, 0.0F, 0.0F, 1.0F, 1.0F);
        }
        int veil = FieldMaskPainter.VEIL;
        float l = frameLeft();
        float t = frameTop();
        float r = frameRight();
        float b = frameBottom();
        MapDraw.rect(graphics, l, t, r, Math.max(t, y0), veil);
        MapDraw.rect(graphics, l, Math.min(b, y1), r, b, veil);
        MapDraw.rect(graphics, l, Math.max(t, y0), Math.max(l, x0), Math.min(b, y1), veil);
        MapDraw.rect(graphics, Math.min(r, x1), Math.max(t, y0), r, Math.min(b, y1), veil);
    }

    /** Field tab: name, level, description, its waystone and its dungeons, top left of the map. */
    private void drawFieldPanel(GuiGraphics graphics, MapView view) {
        AreaDefinition area = Areas.require(field);
        boolean open = view.isUnlocked(field);
        List<Component> lines = new ArrayList<>();
        List<FormattedCharSequence> desc = font.split(Component.translatable(area.descriptionKey())
                .withStyle(ChatFormatting.GRAY), 176);
        for (FieldLayout.Waystone waystone : Waystones.all()) {
            if (waystone.field() == field) {
                boolean known = recorded(waystone.id());
                lines.add(Component.translatable(known ? "screen.eternalrealm.map.field.waystone_known"
                        : "screen.eternalrealm.map.field.waystone_unknown",
                        Component.translatable(Waystones.translationKey(waystone.id())))
                        .withStyle(known ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
            }
        }
        for (MapMarkers.Marker place : MapMarkers.astoria()) {
            if (place.areaId() == field && (place.kind() == MapMarkers.Kind.DUNGEON
                    || place.kind() == MapMarkers.Kind.TOWER)) {
                lines.add(Component.translatable("screen.eternalrealm.map.field.dungeon",
                        Component.translatable(place.kind() == MapMarkers.Kind.TOWER ? MapMarkers.TOWER_KEY
                                : place.nameKey()), place.level()).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        if (!open) {
            lines.add(Component.translatable("map.eternalrealm.sealed.desc").withStyle(ChatFormatting.RED));
        } else if (!view.isDiscovered(field)) {
            lines.add(Component.translatable("map.eternalrealm.undiscovered").withStyle(ChatFormatting.YELLOW));
        }
        int x = frameLeft() + 6;
        int y = frameTop() + 6;
        int w = 184;
        int h = 8 + 12 + 11 + desc.size() * 10 + 4 + lines.size() * 10 + 4;
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF5A4A2A);
        graphics.fill(x, y, x + w, y + h, 0xE0101018);
        graphics.fill(x, y, x + 3, y + h, 0xFF000000 | area.color());
        MutableComponent name = Component.translatable(area.translationKey());
        name.withStyle(style -> style.withColor(open ? MapMath.mix(area.color(), 0xFFFFFF, 0.5) : 0x9A9A9A)
                .withBold(true));
        graphics.drawString(font, name, x + 8, y + 5, 0xFFFFFF);
        graphics.drawString(font, levelRange(area).copy().withStyle(open ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY),
                x + 8, y + 17, 0xFFFFFF);
        int ly = y + 30;
        for (FormattedCharSequence line : desc) {
            graphics.drawString(font, line, x + 8, ly, 0xFFFFFF, false);
            ly += 10;
        }
        ly += 4;
        for (Component line : lines) {
            graphics.drawString(font, line, x + 8, ly, 0xFFFFFF, false);
            ly += 10;
        }
    }

    /** Travel mode: every waystone with its state; recorded and open ones can be chosen. */
    private void drawTravelPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = panelLeft();
        int y0 = frameTop();
        int x1 = width - MARGIN;
        int y1 = frameBottom();
        graphics.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, FRAME);
        graphics.fill(x0, y0, x1, y1, PANEL);
        graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.waystone.choose")
                .withStyle(ChatFormatting.GOLD), (x0 + x1) / 2, y0 + 5, 0xFFFFFF);
        MapView view = ClientMapState.view();
        List<FieldLayout.Waystone> all = Waystones.all();
        for (int i = 0; i < all.size(); i++) {
            FieldLayout.Waystone waystone = all.get(i);
            int top = y0 + 18 + i * ROW;
            if (top + ROW > y1 - 28) {
                break;
            }
            boolean here = waystone.id().equals(travel.from());
            boolean known = recorded(waystone.id());
            boolean open = view.isUnlocked(waystone.field());
            boolean chosen = waystone.id().equals(selected);
            boolean hovered = mouseX >= x0 && mouseX < x1 && mouseY >= top && mouseY < top + ROW;
            if (chosen) {
                graphics.fill(x0 + 2, top, x1 - 2, top + ROW - 2, 0x806A5A20);
            } else if (hovered && canChoose(waystone.id())) {
                graphics.fill(x0 + 2, top, x1 - 2, top + ROW - 2, 0x40FFFFFF);
            }
            MapDraw.waystone(graphics, x0 + 11.0F, top + 12.0F, 7.0F, known);
            ChatFormatting color = here ? ChatFormatting.GREEN : !known ? ChatFormatting.DARK_GRAY
                    : !open ? ChatFormatting.RED : ChatFormatting.AQUA;
            graphics.drawString(font, Component.translatable(Waystones.translationKey(waystone.id()))
                    .withStyle(color), x0 + 22, top + 2, 0xFFFFFF, false);
            Component sub = here ? Component.translatable("screen.eternalrealm.waystone.here")
                    : !known ? Component.translatable("screen.eternalrealm.waystone.unknown")
                    : !open ? Component.translatable("map.eternalrealm.sealed")
                    : levelRange(Areas.require(waystone.field()));
            graphics.drawString(font, sub.copy().withStyle(ChatFormatting.GRAY), x0 + 22, top + 11, 0xFFFFFF, false);
        }
        long wait = travel.readyAtMillis() - System.currentTimeMillis();
        if (wait > 0L) {
            graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.waystone.cooldown",
                    (wait + 999L) / 1000L).withStyle(ChatFormatting.RED), (x0 + x1) / 2, y1 - 34, 0xFFFFFF);
        } else if (selected == null) {
            graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.waystone.pick")
                    .withStyle(ChatFormatting.GRAY), (x0 + x1) / 2, y1 - 34, 0xFFFFFF);
        }
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
            case WAYSTONE -> {
                String id = place.dungeonId();
                boolean known = recorded(id);
                boolean here = travel != null && travel.from().equals(id);
                boolean chosen = id.equals(selected);
                float size = here || chosen ? 9.0F : 7.0F;
                if (here || chosen) {
                    MapDraw.ring(graphics, sx, sy - 2.0F, size + 2.0F, size + 3.5F, chosen ? 0xFFFFE070 : 0xFF70FFB0);
                }
                MapDraw.waystone(graphics, sx, sy, size, known);
                Component name = Component.translatable(place.nameKey());
                lines.add(name.copy().withStyle(known ? ChatFormatting.AQUA : ChatFormatting.GRAY));
                lines.add(Component.translatable(Areas.require(place.areaId()).translationKey())
                        .withStyle(ChatFormatting.DARK_GRAY));
                if (here) {
                    lines.add(Component.translatable("screen.eternalrealm.waystone.here").withStyle(ChatFormatting.GREEN));
                } else if (!known) {
                    lines.add(Component.translatable("map.eternalrealm.waystone.unknown")
                            .withStyle(ChatFormatting.DARK_GRAY));
                } else if (!open) {
                    lines.add(Component.translatable("map.eternalrealm.sealed").withStyle(ChatFormatting.RED));
                } else {
                    lines.add(Component.translatable(travel != null ? "map.eternalrealm.waystone.choose"
                            : "map.eternalrealm.waystone.known").withStyle(ChatFormatting.YELLOW));
                }
                if (ppb >= 0.15 || travel != null || here) {
                    label(graphics, name.copy().withStyle(known ? ChatFormatting.AQUA : ChatFormatting.GRAY), sx,
                            sy + 11.0F);
                }
                stoneSpots.add(new StoneSpot(sx, sy - 3.0F, id));
            }
        }
        hovers.add(new Hover(sx, sy, lines));
    }

    /**
     * The field threats on the map: a calamity's lair (a red-black sun while it is out, a dim mark while it sleeps),
     * every lord that is out (a violet crown where it was seen last) and every wanderer that is out (an amber sun
     * with a pulsing ring, red while it is fought).
     */
    private void drawThreats(GuiGraphics graphics, float cx, float cy) {
        com.google.gson.JsonObject board = com.eternalrealm.client.map.ThreatClient.board().orElse(null);
        if (board == null || !board.has("fields")) {
            return;
        }
        long nanos = System.nanoTime();
        for (com.google.gson.JsonElement element : board.getAsJsonArray("fields")) {
            com.google.gson.JsonObject entry = element.getAsJsonObject();
            String phase = com.eternalrealm.client.ClientUiState.stringOr(entry, "phase", "calm");
            if (entry.has("calamity")) {
                com.google.gson.JsonObject calamity = entry.getAsJsonObject("calamity");
                boolean risen = calamity.has("risen") && calamity.get("risen").getAsBoolean();
                boolean awake = risen || "manifest".equals(phase) || "omen".equals(phase);
                double x = calamity.has("bx") ? calamity.get("bx").getAsDouble() : calamity.get("x").getAsDouble();
                double z = calamity.has("bz") ? calamity.get("bz").getAsDouble() : calamity.get("z").getAsDouble();
                float sx = (float) viewport.screenX(x, cx);
                float sy = (float) viewport.screenY(z, cy);
                float pulse = awake ? 1.0F + 0.25F * (float) Math.sin(nanos / 1.5E8) : 1.0F;
                MapDraw.disc(graphics, sx, sy, 6.5F * pulse, 0xFF000000);
                MapDraw.disc(graphics, sx, sy, 5.0F * pulse, awake ? 0xFFC0182A : 0xFF4A3A44);
                MapDraw.disc(graphics, sx, sy, 2.2F, awake ? 0xFF1A0008 : 0xFF2A2028);
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("threat.eternalrealm.board.calamity",
                        Component.translatable(com.eternalrealm.client.ClientUiState.stringOr(calamity, "name", "")),
                        com.eternalrealm.client.ClientUiState.intOr(calamity, "level", 0))
                        .withStyle(ChatFormatting.DARK_RED));
                lines.add(Component.translatable(risen ? "threat.eternalrealm.board.risen"
                        : awake ? "threat.eternalrealm.board.waiting" : "threat.eternalrealm.board.sleeping")
                        .withStyle(awake ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
                if (awake) {
                    label(graphics, Component.translatable("threat.eternalrealm.map.calamity").withStyle(ChatFormatting.RED),
                            sx, sy + 10.0F);
                }
                hovers.add(new Hover(sx, sy, lines));
            }
            if (entry.has("lords")) {
                for (com.google.gson.JsonElement lordElement : entry.getAsJsonArray("lords")) {
                    com.google.gson.JsonObject lord = lordElement.getAsJsonObject();
                    if (!lord.has("present") || !lord.get("present").getAsBoolean()) {
                        continue;
                    }
                    float sx = (float) viewport.screenX(lord.has("bx") ? lord.get("bx").getAsDouble()
                            : lord.get("x").getAsDouble(), cx);
                    float sy = (float) viewport.screenY(lord.has("bz") ? lord.get("bz").getAsDouble()
                            : lord.get("z").getAsDouble(), cy);
                    MapDraw.diamond(graphics, sx, sy, 5.5F, 0xFF000000);
                    MapDraw.diamond(graphics, sx, sy, 4.0F, 0xFFC070F0);
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.translatable("threat.eternalrealm.board.lord",
                            Component.translatable(com.eternalrealm.client.ClientUiState.stringOr(lord, "name", "")),
                            com.eternalrealm.client.ClientUiState.intOr(lord, "level", 0))
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                    lines.add(Component.translatable("threat.eternalrealm.board.lord_out").withStyle(ChatFormatting.GOLD));
                    hovers.add(new Hover(sx, sy, lines));
                }
            }
            if (entry.has("wanderers")) {
                double age = com.eternalrealm.client.map.ThreatClient.boardAge();
                for (com.google.gson.JsonElement wandererElement : entry.getAsJsonArray("wanderers")) {
                    com.google.gson.JsonObject wanderer = wandererElement.getAsJsonObject();
                    if (!wanderer.has("out") || !wanderer.get("out").getAsBoolean()) {
                        continue;
                    }
                    boolean fighting = wanderer.has("fighting") && wanderer.get("fighting").getAsBoolean();
                    float sx = (float) viewport.screenX(wanderer.has("bx") ? wanderer.get("bx").getAsDouble()
                            : wanderer.get("x").getAsDouble(), cx);
                    float sy = (float) viewport.screenY(wanderer.has("bz") ? wanderer.get("bz").getAsDouble()
                            : wanderer.get("z").getAsDouble(), cy);
                    float pulse = 1.0F + 0.2F * (float) Math.sin(nanos / 2.0E8);
                    MapDraw.ring(graphics, sx, sy, 6.0F * pulse, 7.5F * pulse, fighting ? 0xC0FF3010 : 0xA0FFC040);
                    MapDraw.disc(graphics, sx, sy, 5.0F, 0xFF000000);
                    MapDraw.disc(graphics, sx, sy, 3.8F, fighting ? 0xFFFF5A1E : 0xFFFFB02E);
                    MapDraw.disc(graphics, sx, sy, 1.4F, 0xFF3A1A00);
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.translatable(com.eternalrealm.world.field.Wanderers.BOARD,
                            Component.translatable(com.eternalrealm.client.ClientUiState.stringOr(wanderer, "name", "")),
                            com.eternalrealm.client.ClientUiState.intOr(wanderer, "level", 0))
                            .withStyle(ChatFormatting.GOLD));
                    double leaves = Math.max(0.0, (wanderer.has("leaves") ? wanderer.get("leaves").getAsDouble() : 0.0)
                            - age);
                    lines.add(fighting ? Component.translatable(com.eternalrealm.world.field.Wanderers.BOARD_FIGHTING)
                            .withStyle(ChatFormatting.RED)
                            : Component.translatable(com.eternalrealm.world.field.Wanderers.BOARD_OUT,
                            ThreatOverlay.clock(leaves)).withStyle(ChatFormatting.YELLOW));
                    label(graphics, Component.translatable(com.eternalrealm.world.field.Wanderers.MAP)
                            .withStyle(ChatFormatting.GOLD), sx, sy + 10.0F);
                    hovers.add(new Hover(sx, sy, lines));
                }
            }
        }
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
        lines.add(levelRange(area).copy().withStyle(ChatFormatting.GRAY));
        if (!view.isUnlocked(area.id())) {
            lines.add(Component.translatable("map.eternalrealm.sealed.desc").withStyle(ChatFormatting.RED));
        } else if (!view.isDiscovered(area.id())) {
            lines.add(Component.translatable("map.eternalrealm.undiscovered").withStyle(ChatFormatting.YELLOW));
        }
        if (view.realm().astoria() && com.eternalrealm.world.field.FieldLayout.inBounds(x, z)) {
            lines.add(Component.translatable(AstoriaLayout.biomeAt((int) Math.floor(x), (int) Math.floor(z))
                    .translationKey()).withStyle(ChatFormatting.DARK_GREEN));
        }
        lines.add(Component.literal(String.format(Locale.ROOT, "%d, %d", Math.round(x + view.realm().centerX()),
                Math.round(z + view.realm().centerZ()))).withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** "推奨Lv.10〜20", or "推奨Lv.40〜" for the open-ended high fields. */
    static Component levelRange(AreaDefinition area) {
        return area.openEnded() ? Component.translatable("map.eternalrealm.level_from", area.minLevel())
                : Component.translatable("map.eternalrealm.level_range", area.minLevel(), area.maxLevel());
    }
}
