package com.eternalrealm.ui.map;

import com.eternalrealm.client.guide.GuideClient;
import com.eternalrealm.client.map.ClientMapState;
import com.eternalrealm.client.map.MapKeys;
import com.eternalrealm.client.sound.UiSounds;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.map.CapitalMap;
import com.eternalrealm.world.map.LabelLayout;
import com.eternalrealm.world.map.MapMath;
import com.eternalrealm.world.map.MapView;
import com.eternalrealm.world.map.MapViewport;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The map of the capital (王都の地図; owner, 2026-11-14: 「王国に入るとまた王国専用のマップを開けるようにして、どこに何が
 * あるかみやすくして」). Opened by the map key inside the capital (or from the realm map's footer): the city painted
 * from above as it is built, every place on it with its icon and name (names that would cover each other give way,
 * the chosen and pointed-at ones always show), the districts' names, the quests waiting at the town's NPCs ("!" and
 * "?"), the story characters about, the party and the player. Beside it the list of the places by group: pointing at
 * one lights it on the map, clicking it goes there and tells what one does there, who works there and which way it is
 * from the player; 「ここへ案内」 lays the yellow dots of the quest guide to it ({@link GuideClient#guideTo}). Tab or
 * the header's button opens the realm map, the map key closes.
 */
public final class CapitalMapScreen extends Screen {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int MARGIN = 8;
    private static final int HEADER = 24;
    private static final int FOOTER = 16;
    /** The list of places beside the map (shown when the window is wide enough). */
    private static final int PANEL = 158;
    private static final int PANEL_MIN_WIDTH = 400;
    private static final int ROW = 15;
    private static final int GROUP_ROW = 14;
    /** The chosen place's box at the bottom of the list. */
    private static final int DETAILS = 96;
    private static final int FRAME = 0xFF5A4A2A;
    private static final int FRAME_LIGHT = 0xFFB89A5A;
    private static final int PANEL_BACKGROUND = 0xF0141016;
    private static final int PARCHMENT = 0xFFDECDA4;
    private static final int LABEL_BACKGROUND = 0xB8141016;
    private static final int DISTRICT_BACKGROUND = 0x70201810;
    private static final int TITLE = 0xFFE8C66A;
    private static final int HOVER_PIXELS = 8;
    private static final int CLICK_SLOP = 4;
    /** Closest zoom: five GUI pixels per block. */
    private static final double MIN_BLOCKS_PER_PIXEL = 0.2;
    /** A place chosen from the list is shown at least this close (blocks per pixel). */
    private static final double FOCUS_BLOCKS_PER_PIXEL = 0.5;
    /** Closer than this (blocks) to a place: "you are here". */
    private static final double HERE = 6.0;
    /** A town NPC this close to its place (blocks) shows its quests there. */
    private static final double NPC_AT_PLACE = 24.0;
    private static final int RANK_STORY = 4;
    private static final int RANK_DISTRICT = 6;

    private static final ResourceLocation TEXTURE = new ResourceLocation(ModInfo.MOD_ID, CapitalMap.TEXTURE);
    private static final ResourceLocation ICONS = new ResourceLocation(ModInfo.MOD_ID, CapitalMap.ICON_ATLAS);
    private static CapitalMap.Data data;
    private static boolean loaded;

    /** A place drawn on the map: where, how big, which. */
    private record Spot(float x, float y, float radius, CapitalMap.Place place) {
    }

    /** Something else under the mouse with a tooltip. */
    private record Hover(float x, float y, List<Component> lines) {
    }

    /** A row of the list: a group's heading ({@code place == null}) or a place. */
    private record Row(CapitalMap.Group group, CapitalMap.Place place) {
        int height() {
            return place == null ? GROUP_ROW : ROW;
        }
    }

    private final List<Spot> spots = new ArrayList<>();
    private final List<Hover> hovers = new ArrayList<>();
    private final List<LabelLayout.Box> plateBoxes = new ArrayList<>();
    private final List<Component> plateTexts = new ArrayList<>();
    private final List<Integer> plateBackgrounds = new ArrayList<>();
    private final Map<String, MapView.Mark> marks = new HashMap<>();
    private MapViewport viewport;
    private String selected;
    /** The place pointed at on the map or in the list (lit on the map). */
    private String pointed;
    private boolean dragging;
    private boolean pressed;
    private double pressX;
    private double pressY;
    /** The list's first row shown (it scrolls with the wheel). */
    private int listScroll;
    private Button guideButton;
    /** What the guide button last showed (it is set again only when that changes). */
    private String guideState = "";

    private CapitalMapScreen() {
        super(Component.translatable("screen.eternalrealm.capital_map.title"));
    }

    /** Opens the capital's map. */
    public static void open() {
        if (data().isEmpty()) {
            WorldMapScreen.open();
            return;
        }
        UiSounds.journalOpen();
        Minecraft.getInstance().setScreen(new CapitalMapScreen());
    }

    /** The map key and the journal: the capital's map inside the capital, the realm map elsewhere. */
    public static void openForPlayer() {
        if (inCapital() && data().isPresent()) {
            open();
        } else {
            WorldMapScreen.open();
        }
    }

    /** Whether the player stands inside the capital of アストリア. */
    public static boolean inCapital() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !ClientMapState.inAstoria(minecraft.level)) {
            return false;
        }
        MapView.Realm realm = ClientMapState.view().realm();
        return FieldLayout.inCapital(player.getX() - realm.centerX(), player.getZ() - realm.centerZ());
    }

    /** The places and the painting's bounds, read once from the mod's assets ({@link CapitalMap#RESOURCE}). */
    static Optional<CapitalMap.Data> data() {
        if (!loaded) {
            loaded = true;
            ResourceLocation location = new ResourceLocation(ModInfo.MOD_ID, CapitalMap.RESOURCE);
            try {
                Optional<net.minecraft.server.packs.resources.Resource> resource = Minecraft.getInstance()
                        .getResourceManager().getResource(location);
                if (resource.isPresent()) {
                    try (Reader reader = resource.get().openAsReader()) {
                        data = CapitalMap.parse(JsonParser.parseReader(reader).getAsJsonObject());
                    }
                } else {
                    LOG.warn("The capital's map {} is missing", location);
                }
            } catch (IOException | JsonParseException | IllegalArgumentException | IllegalStateException e) {
                LOG.warn("The capital's map {} could not be read", location, e);
                data = null;
            }
        }
        return Optional.ofNullable(data);
    }

    // ------------------------------------------------------------------ layout

    private boolean panelShown() {
        return width >= PANEL_MIN_WIDTH;
    }

    private int frameLeft() {
        return MARGIN;
    }

    private int frameTop() {
        return HEADER;
    }

    private int frameRight() {
        return width - MARGIN - (panelShown() ? PANEL + 6 : 0);
    }

    private int frameBottom() {
        return height - FOOTER;
    }

    private int panelLeft() {
        return width - MARGIN - PANEL;
    }

    private float centerX() {
        return (frameLeft() + frameRight()) / 2.0F;
    }

    private float centerY() {
        return (frameTop() + frameBottom()) / 2.0F;
    }

    private int listTop() {
        return frameTop() + 16;
    }

    private int listBottom() {
        return frameBottom() - (selectedPlace().isPresent() ? DETAILS : 0) - 2;
    }

    @Override
    protected void init() {
        CapitalMap.Data map = data().orElse(null);
        if (map == null) {
            return;
        }
        double w = Math.max(40, frameRight() - frameLeft());
        double h = Math.max(40, frameBottom() - frameTop());
        double fit = Math.max(map.width() / w, map.height() / h) * 1.04;
        double max = Math.max(fit, MIN_BLOCKS_PER_PIXEL);
        if (viewport == null) {
            viewport = new MapViewport(map.centerX(), map.centerZ(), fit, MIN_BLOCKS_PER_PIXEL, max, map.x0(), map.z0(),
                    map.x0() + map.width(), map.z0() + map.height());
        } else {
            // the window was resized: keep the view, within the new limits
            MapViewport before = viewport;
            viewport = new MapViewport(before.centerX(), before.centerZ(), before.blocksPerPixel(), MIN_BLOCKS_PER_PIXEL,
                    max, map.x0(), map.z0(), map.x0() + map.width(), map.z0() + map.height());
        }
        int right = frameRight();
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.capital_map.field_map"),
                        b -> WorldMapScreen.open()).bounds(MARGIN, 4, 92, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("screen.eternalrealm.capital_map.field_map_tip"))).build());
        addRenderableWidget(Button.builder(Component.literal("×"), b -> onClose())
                .bounds(width - MARGIN - 18, 4, 18, 16).build());
        int zoomRight = panelShown() ? right : width - MARGIN - 22;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> zoomButton(1.5))
                .bounds(zoomRight - 20, 4, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoomButton(1.0 / 1.5))
                .bounds(zoomRight - 40, 4, 18, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.map.center"),
                b -> centerOnPlayer()).bounds(zoomRight - 108, 4, 66, 16).build());
        if (panelShown()) {
            guideButton = addRenderableWidget(Button.builder(Component.empty(), b -> guide())
                    .bounds(panelLeft() + 6, frameBottom() - 20, PANEL - 12, 16).build());
            guideState = "";
            refreshGuideButton();
        }
    }

    private void zoomButton(double factor) {
        viewport.zoom(factor, centerX(), centerY(), centerX(), centerY());
    }

    private void centerOnPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null && minecraft.level != null && ClientMapState.inAstoria(minecraft.level)) {
            MapView.Realm realm = ClientMapState.view().realm();
            viewport.centerOn(player.getX() - realm.centerX(), player.getZ() - realm.centerZ());
        }
    }

    private Optional<CapitalMap.Place> selectedPlace() {
        return selected == null ? Optional.empty() : data().flatMap(map -> map.place(selected));
    }

    /** Chooses a place: lit on the map, told about in the list's box. */
    private void select(CapitalMap.Place place, boolean focus) {
        selected = place.id();
        if (focus) {
            if (viewport.blocksPerPixel() > FOCUS_BLOCKS_PER_PIXEL) {
                viewport.zoomTo(FOCUS_BLOCKS_PER_PIXEL);
            }
            viewport.centerOn(place.x(), place.z());
        }
        UiSounds.click();
        refreshGuideButton();
    }

    // ------------------------------------------------------------------ the guide

    /** The player is in アストリア (the capital's world), so the guide can lead there. */
    private static boolean inAstoria() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.level != null && ClientMapState.inAstoria(minecraft.level);
    }

    /** Whether the guide leads to this place now. */
    private static boolean guiding(CapitalMap.Place place) {
        MapView.Realm realm = ClientMapState.view().realm();
        return GuideClient.picked().map(at -> Math.abs(at[0] - (place.x() + realm.centerX() + 0.5)) < 0.01
                && Math.abs(at[1] - (place.z() + realm.centerZ() + 0.5)) < 0.01).orElse(false);
    }

    private void refreshGuideButton() {
        if (guideButton == null) {
            return;
        }
        Optional<CapitalMap.Place> place = selectedPlace();
        boolean on = place.map(CapitalMapScreen::guiding).orElse(false);
        String state = place.map(CapitalMap.Place::id).orElse("") + "|" + inAstoria() + "|" + on;
        if (state.equals(guideState)) {
            return;
        }
        guideState = state;
        guideButton.visible = place.isPresent();
        guideButton.active = place.isPresent() && inAstoria();
        guideButton.setMessage(Component.translatable(on ? "screen.eternalrealm.capital_map.guide_stop"
                : "screen.eternalrealm.capital_map.guide"));
        guideButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                !inAstoria() ? "screen.eternalrealm.capital_map.guide_elsewhere"
                        : on ? "screen.eternalrealm.capital_map.guide_stop_tip"
                        : "screen.eternalrealm.capital_map.guide_tip")));
    }

    /** 「ここへ案内」: the yellow dots of the guide lead to the chosen place (again: they stop). */
    private void guide() {
        Optional<CapitalMap.Place> chosen = selectedPlace();
        if (chosen.isEmpty() || !inAstoria()) {
            return;
        }
        CapitalMap.Place place = chosen.get();
        if (guiding(place)) {
            GuideClient.stopGuiding();
            UiSounds.click();
            refreshGuideButton();
            return;
        }
        MapView.Realm realm = ClientMapState.view().realm();
        GuideClient.guideTo(realm.dimension(), place.x() + realm.centerX() + 0.5, place.z() + realm.centerZ() + 0.5,
                Component.translatable(place.nameKey()));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("map.eternalrealm.capital.guide.started",
                    Component.translatable(place.nameKey())).withStyle(ChatFormatting.YELLOW), true);
        }
        UiSounds.journalOpen();
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        refreshGuideButton();
    }

    // ------------------------------------------------------------------ input

    private boolean insideFrame(double x, double y) {
        return x >= frameLeft() && x < frameRight() && y >= frameTop() && y < frameBottom();
    }

    private boolean insideList(double x, double y) {
        return panelShown() && x >= panelLeft() && x < width - MARGIN && y >= listTop() && y < listBottom();
    }

    /** The place drawn under the mouse, if any (the nearest). */
    private Optional<CapitalMap.Place> spotAt(double x, double y) {
        Spot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Spot spot : spots) {
            double dx = spot.x() - x;
            double dy = spot.y() - y;
            double distance = dx * dx + dy * dy;
            double reach = Math.max(HOVER_PIXELS, spot.radius() + 2.0);
            if (distance <= reach * reach && distance < bestDistance) {
                best = spot;
                bestDistance = distance;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.place());
    }

    /** The place of the list's row under the mouse, if any. */
    private Optional<CapitalMap.Place> rowAt(double x, double y) {
        if (!insideList(x, y)) {
            return Optional.empty();
        }
        int top = listTop();
        List<Row> rows = rows();
        for (int i = listScroll; i < rows.size() && top < listBottom(); i++) {
            Row row = rows.get(i);
            if (y >= top && y < top + row.height()) {
                return Optional.ofNullable(row.place());
            }
            top += row.height();
        }
        return Optional.empty();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || viewport == null) {
            return false;
        }
        Optional<CapitalMap.Place> row = rowAt(mouseX, mouseY);
        if (row.isPresent()) {
            select(row.get(), true);
            return true;
        }
        if (insideFrame(mouseX, mouseY)) {
            Optional<CapitalMap.Place> spot = spotAt(mouseX, mouseY);
            if (spot.isPresent()) {
                select(spot.get(), false);
                return true;
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
        if (click && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && selected != null) {
            selected = null; // a click on the open map lets the chosen place go
            refreshGuideButton();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && viewport != null) {
            viewport.drag(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (viewport == null || delta == 0.0) {
            return true;
        }
        if (panelShown() && mouseX >= panelLeft()) {
            listScroll = Mth.clamp(listScroll + (delta > 0.0 ? -2 : 2), 0, maxScroll());
            return true;
        }
        viewport.zoom(delta > 0.0 ? 1.0 / 1.25 : 1.25, mouseX, mouseY, centerX(), centerY());
        return true;
    }

    private int maxScroll() {
        List<Row> rows = rows();
        int room = listBottom() - listTop();
        int used = 0;
        for (int i = rows.size() - 1; i >= 0; i--) {
            used += rows.get(i).height();
            if (used > room) {
                return Math.min(rows.size() - 1, i + 1);
            }
        }
        return 0;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MapKeys.WORLD_MAP.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            WorldMapScreen.open();
            return true;
        }
        if (viewport == null) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        double step = 40.0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> viewport.drag(step, 0.0);
            case GLFW.GLFW_KEY_RIGHT -> viewport.drag(-step, 0.0);
            case GLFW.GLFW_KEY_UP -> viewport.drag(0.0, step);
            case GLFW.GLFW_KEY_DOWN -> viewport.drag(0.0, -step);
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> zoomButton(1.0 / 1.5);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> zoomButton(1.5);
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
        CapitalMap.Data map = data().orElse(null);
        if (map == null || viewport == null) {
            graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.capital_map.missing"),
                    width / 2, height / 2, 0xFFFF8060);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        // what the mouse points at: a row of the list, or a place where it was drawn last frame
        pointed = dragging ? null : rowAt(mouseX, mouseY).or(() -> insideFrame(mouseX, mouseY)
                ? spotAt(mouseX, mouseY) : Optional.empty()).map(CapitalMap.Place::id).orElse(null);
        spots.clear();
        hovers.clear();
        plateBoxes.clear();
        plateTexts.clear();
        plateBackgrounds.clear();
        int x0 = frameLeft();
        int y0 = frameTop();
        int x1 = frameRight();
        int y1 = frameBottom();
        graphics.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, FRAME);
        graphics.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, FRAME_LIGHT);
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, FRAME);
        graphics.fill(x0, y0, x1, y1, PARCHMENT);
        graphics.enableScissor(x0, y0, x1, y1);
        drawCity(graphics, map, partialTick);
        graphics.disableScissor();
        if (panelShown()) {
            drawPanel(graphics, map, mouseX, mouseY);
        }
        graphics.drawString(font, title, MARGIN + 98, 8, TITLE);
        Component hint = Component.translatable("screen.eternalrealm.capital_map.hint",
                MapKeys.WORLD_MAP.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY);
        List<FormattedCharSequence> lines = font.split(hint, Math.max(40, width - MARGIN * 2));
        if (!lines.isEmpty()) {
            graphics.drawString(font, lines.get(0), MARGIN, height - 12, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (insideFrame(mouseX, mouseY) && !dragging) {
            tooltip(graphics, map, mouseX, mouseY);
        }
    }

    private void drawCity(GuiGraphics graphics, CapitalMap.Data map, float partialTick) {
        float cx = centerX();
        float cy = centerY();
        double ppb = viewport.pixelsPerBlock();
        // the painting: two texels a block; smooth when they are smaller than a few screen pixels, crisp when larger
        double screenPixelsPerTexel = ppb * Minecraft.getInstance().getWindow().getGuiScale() / 2.0;
        Minecraft.getInstance().getTextureManager().getTexture(TEXTURE).setFilter(screenPixelsPerTexel < 3.0, false);
        MapDraw.texturedRect(graphics, TEXTURE, (float) viewport.screenX(map.x0(), cx),
                (float) viewport.screenY(map.z0(), cy), (float) viewport.screenX(map.x0() + map.width(), cx),
                (float) viewport.screenY(map.z0() + map.height(), cy), 0.0F, 0.0F, 1.0F, 1.0F);
        // the districts' names, behind everything else
        for (CapitalMap.District district : map.districts()) {
            MutableComponent name = Component.translatable(district.nameKey())
                    .withStyle(style -> style.withItalic(true).withColor(0xF6EBCF));
            plate(name, (float) viewport.screenX(district.x(), cx), (float) viewport.screenY(district.z(), cy),
                    RANK_DISTRICT, false, DISTRICT_BACKGROUND);
        }
        marks.clear();
        List<MapView.Npc> stories = townNpcs(map);
        // the places
        long now = System.currentTimeMillis();
        float pulse = 1.0F + 0.5F * (float) Math.sin(now / 160.0);
        for (CapitalMap.Place place : map.places()) {
            drawPlace(graphics, place, cx, cy, ppb, pulse);
        }
        for (MapView.Npc npc : stories) {
            drawStoryNpc(graphics, npc, cx, cy, ppb);
        }
        drawGuideTarget(graphics, cx, cy, pulse);
        drawPlates(graphics);
        drawMembersAndPlayer(graphics, cx, cy, partialTick);
    }

    /**
     * The quests waiting at the town's NPCs, put on their places ({@link #marks}); the story characters about (who
     * have no place of their own) are returned to be drawn where they stand.
     */
    private List<MapView.Npc> townNpcs(CapitalMap.Data map) {
        List<MapView.Npc> stories = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !ClientMapState.inAstoria(minecraft.level)) {
            return stories;
        }
        MapView view = ClientMapState.view();
        MapView.Realm realm = view.realm();
        for (MapView.Npc npc : view.npcs()) {
            double x = npc.x() - realm.centerX();
            double z = npc.z() - realm.centerZ();
            if (!map.contains(x, z)) {
                continue;
            }
            CapitalMap.Place home = null;
            for (CapitalMap.Place place : map.places()) {
                if (place.npc().equals(npc.role()) && Math.hypot(place.x() - x, place.z() - z) < NPC_AT_PLACE) {
                    home = place;
                    break;
                }
            }
            if (home != null) {
                if (npc.mark() != MapView.Mark.NONE) {
                    marks.merge(home.id(), npc.mark(), (a, b) -> a == MapView.Mark.READY || b == MapView.Mark.READY
                            ? MapView.Mark.READY : MapView.Mark.OFFER);
                }
            } else if ("story".equals(npc.role()) || npc.mark() != MapView.Mark.NONE) {
                stories.add(npc);
            }
        }
        return stories;
    }

    private void drawPlace(GuiGraphics graphics, CapitalMap.Place place, float cx, float cy, double ppb,
                           float pulse) {
        float sx = (float) viewport.screenX(place.x() + 0.5, cx);
        float sy = (float) viewport.screenY(place.z() + 0.5, cy);
        boolean lit = place.id().equals(selected) || place.id().equals(pointed);
        float icon = lit ? 12.0F : (float) Mth.clamp((int) Math.round(10.0 * ppb), 8, 12);
        float radius = icon / 2.0F + 2.0F;
        int color = place.kind().color();
        if (sx < frameLeft() - 20 || sx > frameRight() + 20 || sy < frameTop() - 20 || sy > frameBottom() + 20) {
            return;
        }
        if (lit) {
            MapDraw.ring(graphics, sx, sy, radius + 1.5F + pulse, radius + 3.0F + pulse,
                    place.id().equals(selected) ? 0xE0FFE070 : 0xB0FFFFFF);
        }
        MapDraw.disc(graphics, sx, sy, radius + 1.0F, 0xFF000000);
        MapDraw.disc(graphics, sx, sy, radius, 0xFF000000 | MapMath.mix(color, 0x141016, 0.72));
        MapDraw.ring(graphics, sx, sy, radius - 1.3F, radius, 0xFF000000 | color);
        icon(graphics, place.icon(), sx, sy, icon);
        MapView.Mark mark = marks.getOrDefault(place.id(), MapView.Mark.NONE);
        if (mark != MapView.Mark.NONE) {
            questMark(graphics, mark, sx + radius - 1.0F, sy - radius - 4.0F);
        }
        spots.add(new Spot(sx, sy, radius, place));
        plate(Component.translatable(place.nameKey()).withStyle(style -> style.withColor(MapMath.mix(color, 0xFFFFFF,
                0.45))), sx, sy + radius + 6.0F, place.kind().rank(), lit, LABEL_BACKGROUND);
    }

    /** A story character about the town (or a townsman with a quest): a speech bubble where they stand. */
    private void drawStoryNpc(GuiGraphics graphics, MapView.Npc npc, float cx, float cy, double ppb) {
        MapView.Realm realm = ClientMapState.view().realm();
        float sx = (float) viewport.screenX(npc.x() - realm.centerX(), cx);
        float sy = (float) viewport.screenY(npc.z() - realm.centerZ(), cy);
        float icon = (float) Mth.clamp((int) Math.round(8.0 * ppb), 7, 10);
        MapDraw.disc(graphics, sx, sy, icon / 2.0F + 2.0F, 0xC0000000);
        icon(graphics, CapitalMap.icon(CapitalMap.STORY), sx, sy, icon);
        if (npc.mark() != MapView.Mark.NONE) {
            questMark(graphics, npc.mark(), sx + icon / 2.0F, sy - icon / 2.0F - 6.0F);
        }
        Component name = npcName(npc);
        List<Component> lines = new ArrayList<>();
        lines.add(name.copy().withStyle(ChatFormatting.GREEN));
        if (npc.mark() != MapView.Mark.NONE) {
            lines.add(Component.translatable(npc.mark() == MapView.Mark.READY ? "map.eternalrealm.npc.ready"
                    : "map.eternalrealm.npc.offer").withStyle(ChatFormatting.YELLOW));
        }
        hovers.add(new Hover(sx, sy, lines));
        if (ppb >= 1.2) {
            plate(name.copy().withStyle(ChatFormatting.GREEN), sx, sy + icon / 2.0F + 6.0F, RANK_STORY, false,
                    LABEL_BACKGROUND);
        }
    }

    /** Where the guide leads (a picked place or the quest's), if it is in the capital. */
    private void drawGuideTarget(GuiGraphics graphics, float cx, float cy, float pulse) {
        Optional<double[]> target = GuideClient.target();
        if (target.isEmpty()) {
            return;
        }
        MapView.Realm realm = ClientMapState.view().realm();
        float sx = (float) viewport.screenX(target.get()[0] - realm.centerX(), cx);
        float sy = (float) viewport.screenY(target.get()[1] - realm.centerZ(), cy);
        MapDraw.ring(graphics, sx, sy, 7.0F + pulse, 8.5F + pulse, 0xE0FFD040);
        MapDraw.diamond(graphics, sx, sy - 12.0F, 5.0F, 0xFF000000);
        MapDraw.diamond(graphics, sx, sy - 12.0F, 3.6F, 0xFFFFD040);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.eternalrealm.capital_map.guide_target").withStyle(ChatFormatting.GOLD));
        GuideClient.label().ifPresent(label -> lines.add(label.copy().withStyle(ChatFormatting.YELLOW)));
        hovers.add(new Hover(sx, sy - 12.0F, lines));
    }

    private void icon(GuiGraphics graphics, int index, float x, float y, float size) {
        float u0 = index / (float) CapitalMap.icons().size();
        float u1 = (index + 1) / (float) CapitalMap.icons().size();
        MapDraw.texturedRect(graphics, ICONS, x - size / 2.0F, y - size / 2.0F, x + size / 2.0F, y + size / 2.0F, u0,
                0.0F, u1, 1.0F);
    }

    private void questMark(GuiGraphics graphics, MapView.Mark mark, float x, float y) {
        String symbol = mark.symbol();
        int w = font.width(symbol);
        graphics.fill(Math.round(x - w / 2.0F) - 2, Math.round(y) - 1, Math.round(x + w / 2.0F) + 2, Math.round(y) + 9,
                0xD0000000);
        graphics.drawString(font, symbol, Math.round(x - w / 2.0F), Math.round(y),
                mark == MapView.Mark.READY ? 0xFFFFC040 : 0xFFFFFF40, false);
    }

    /** Party members and the player. */
    private void drawMembersAndPlayer(GuiGraphics graphics, float cx, float cy, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !ClientMapState.inAstoria(minecraft.level)) {
            graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.capital_map.elsewhere"),
                    Math.round(cx), frameBottom() - 14, 0xFFFF8060);
            return;
        }
        MapView view = ClientMapState.view();
        MapView.Realm realm = view.realm();
        String here = ClientMapState.dimensionOf(minecraft.level);
        for (MapView.Member member : view.party()) {
            if (!member.dimension().equals(here)) {
                continue;
            }
            Player body = minecraft.level.getPlayerByUUID(member.id());
            double x = (body != null ? body.getX() : member.x()) - realm.centerX();
            double z = (body != null ? body.getZ() : member.z()) - realm.centerZ();
            float sx = (float) viewport.screenX(x, cx);
            float sy = (float) viewport.screenY(z, cy);
            MapDraw.disc(graphics, sx, sy, 4.0F, 0xFF000000);
            MapDraw.disc(graphics, sx, sy, 3.0F, member.leader() ? 0xFFFFD447 : 0xFF55E0FF);
            Component name = Component.literal(member.name()).withStyle(ChatFormatting.AQUA);
            int w = font.width(name);
            graphics.fill(Math.round(sx - w / 2.0F) - 2, Math.round(sy + 6.0F), Math.round(sx + w / 2.0F) + 2,
                    Math.round(sy + 16.0F), LABEL_BACKGROUND);
            graphics.drawString(font, name, Math.round(sx - w / 2.0F), Math.round(sy + 7.0F), 0xFFFFFF, false);
            hovers.add(new Hover(sx, sy, List.of(name, Component.translatable("map.eternalrealm.member.health",
                    Math.round(member.health() * 100.0F)).withStyle(ChatFormatting.GRAY))));
        }
        double px = Mth.lerp(partialTick, player.xo, player.getX()) - realm.centerX();
        double pz = Mth.lerp(partialTick, player.zo, player.getZ()) - realm.centerZ();
        float sx = (float) viewport.screenX(px, cx);
        float sy = (float) viewport.screenY(pz, cy);
        float yaw = Mth.lerp(partialTick, player.yRotO, player.getYRot());
        MapDraw.disc(graphics, sx, sy, 9.0F, 0x50FFFFFF);
        MapDraw.arrow(graphics, sx, sy, 7.0F, MapMath.arrowAngle(yaw, 0.0), 0xFFFFFFFF, 0xFF000000);
        hovers.add(new Hover(sx, sy, List.of(Component.translatable("map.eternalrealm.you").withStyle(ChatFormatting.WHITE),
                Component.literal(String.format(Locale.ROOT, "%d, %d, %d", player.getBlockX(), player.getBlockY(),
                        player.getBlockZ())).withStyle(ChatFormatting.GRAY))));
    }

    // ------------------------------------------------------------------ name plates

    private void plate(Component text, float x, float y, int rank, boolean always, int background) {
        int width = font.width(text);
        int left = Math.round(x - width / 2.0F);
        int top = Math.round(y - 4.0F);
        plateBoxes.add(new LabelLayout.Box(left - 2, top - 1, left + width + 2, top + 9, rank, always));
        plateTexts.add(text);
        plateBackgrounds.add(background);
    }

    private void drawPlates(GuiGraphics graphics) {
        for (int index : LabelLayout.place(plateBoxes, frameLeft(), frameTop(), frameRight(), frameBottom())) {
            LabelLayout.Box box = plateBoxes.get(index);
            int left = Math.round(box.x0()) + 2;
            int top = Math.round(box.y0()) + 1;
            graphics.fill(left - 2, top - 1, Math.round(box.x1()), top + 9, plateBackgrounds.get(index));
            graphics.drawString(font, plateTexts.get(index), left, top, 0xFFFFFF,
                    plateBackgrounds.get(index) == DISTRICT_BACKGROUND);
        }
    }

    // ------------------------------------------------------------------ the list

    /** The list's rows: each group's heading, then its places. */
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        CapitalMap.Data map = data().orElse(null);
        if (map == null) {
            return rows;
        }
        Map<CapitalMap.Group, List<CapitalMap.Place>> byGroup = new EnumMap<>(CapitalMap.Group.class);
        for (CapitalMap.Place place : CapitalMap.legend(map)) {
            byGroup.computeIfAbsent(place.kind().group(), group -> new ArrayList<>()).add(place);
        }
        for (Map.Entry<CapitalMap.Group, List<CapitalMap.Place>> entry : byGroup.entrySet()) {
            rows.add(new Row(entry.getKey(), null));
            for (CapitalMap.Place place : entry.getValue()) {
                rows.add(new Row(entry.getKey(), place));
            }
        }
        return rows;
    }

    private void drawPanel(GuiGraphics graphics, CapitalMap.Data map, int mouseX, int mouseY) {
        int left = panelLeft();
        int right = width - MARGIN;
        int top = frameTop();
        int bottom = frameBottom();
        graphics.fill(left - 3, top - 3, right + 3, bottom + 3, FRAME);
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, FRAME_LIGHT);
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, FRAME);
        graphics.fill(left, top, right, bottom, PANEL_BACKGROUND);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.capital_map.places"), left + 6, top + 4,
                TITLE, false);
        graphics.fill(left + 4, top + 13, right - 4, top + 14, 0x80B89A5A);
        listScroll = Mth.clamp(listScroll, 0, maxScroll());
        List<Row> rows = rows();
        int y = listTop();
        graphics.enableScissor(left, listTop(), right, listBottom());
        for (int i = listScroll; i < rows.size() && y < listBottom(); i++) {
            Row row = rows.get(i);
            if (row.place() == null) {
                graphics.drawString(font, Component.translatable(row.group().translationKey())
                        .withStyle(ChatFormatting.GRAY), left + 5, y + 3, 0xFFFFFF, false);
                graphics.fill(left + 5, y + 12, right - 5, y + 13, 0x40FFFFFF);
            } else {
                CapitalMap.Place place = row.place();
                boolean chosen = place.id().equals(selected);
                boolean lit = place.id().equals(pointed);
                if (chosen || lit) {
                    graphics.fill(left + 2, y, right - 2, y + ROW, chosen ? 0x60E8C66A : 0x30FFFFFF);
                }
                MapDraw.disc(graphics, left + 13, y + 7.5F, 7.0F, 0xFF000000 | MapMath.mix(place.kind().color(),
                        0x141016, 0.72));
                icon(graphics, place.icon(), left + 13, y + 7.5F, 10.0F);
                MapView.Mark mark = marks.getOrDefault(place.id(), MapView.Mark.NONE);
                int textRight = right - 6 - (mark != MapView.Mark.NONE ? 10 : 0) - (guiding(place) ? 10 : 0);
                Component name = Component.translatable(place.nameKey());
                String clipped = font.plainSubstrByWidth(name.getString(), textRight - (left + 24));
                graphics.drawString(font, clipped, left + 24, y + 4,
                        chosen ? 0xFFFFE8A0 : 0xFF000000 | MapMath.mix(place.kind().color(), 0xFFFFFF, 0.55), false);
                int markX = right - 9;
                if (mark != MapView.Mark.NONE) {
                    graphics.drawString(font, mark.symbol(), markX - font.width(mark.symbol()) / 2, y + 4,
                            mark == MapView.Mark.READY ? 0xFFFFC040 : 0xFFFFFF40, false);
                    markX -= 10;
                }
                if (guiding(place)) {
                    MapDraw.diamond(graphics, markX, y + 7.5F, 3.5F, 0xFFFFD040);
                }
            }
            y += row.height();
        }
        graphics.disableScissor();
        if (listScroll > 0) {
            graphics.drawCenteredString(font, "▲", right - 8, listTop() - 1, 0xFFB89A5A);
        }
        if (listScroll < maxScroll()) {
            graphics.drawCenteredString(font, "▼", right - 8, listBottom() - 8, 0xFFB89A5A);
        }
        selectedPlace().ifPresent(place -> drawDetails(graphics, place, left, right, bottom));
    }

    /** The chosen place: name, who works there, what one does there, which way it is. */
    private void drawDetails(GuiGraphics graphics, CapitalMap.Place place, int left, int right, int bottom) {
        int top = bottom - DETAILS;
        graphics.fill(left + 3, top, right - 3, top + 1, 0x80B89A5A);
        int y = top + 4;
        MapDraw.disc(graphics, left + 13, y + 6.0F, 8.0F, 0xFF000000 | MapMath.mix(place.kind().color(), 0x141016, 0.72));
        icon(graphics, place.icon(), left + 13, y + 6.0F, 12.0F);
        int textLeft = left + 25;
        int textWidth = right - 6 - textLeft;
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(place.nameKey()).getString(), textWidth),
                textLeft, y + 2, TITLE, false);
        y += 14;
        if (place.hasNpc()) {
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("npc.eternalrealm." + place.npc())
                    .getString(), right - 6 - (left + 6)), left + 6, y, 0xFFF4E8C8, false);
            y += 10;
        }
        for (FormattedCharSequence line : font.split(Component.translatable(place.descKey())
                .withStyle(ChatFormatting.GRAY), right - 6 - (left + 6))) {
            if (y > bottom - 34) {
                break;
            }
            graphics.drawString(font, line, left + 6, y, 0xFFFFFF, false);
            y += 10;
        }
        Component way = way(place);
        if (way != null) {
            graphics.drawString(font, font.plainSubstrByWidth(way.getString(), right - 6 - (left + 6)), left + 6,
                    bottom - 32, 0xFF7FD8FF, false);
        }
    }

    /** "北東へ 45 ブロック" from the player to the place, or "ここ" when there; {@code null} outside アストリア. */
    private static Component way(CapitalMap.Place place) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !ClientMapState.inAstoria(minecraft.level)) {
            return null;
        }
        MapView.Realm realm = ClientMapState.view().realm();
        double x = player.getX() - realm.centerX();
        double z = player.getZ() - realm.centerZ();
        String direction = CapitalMap.direction(x, z, place.x() + 0.5, place.z() + 0.5, HERE);
        if (direction.endsWith(".here")) {
            return Component.translatable(direction);
        }
        long blocks = Math.round(Math.hypot(place.x() + 0.5 - x, place.z() + 0.5 - z));
        return Component.translatable("map.eternalrealm.capital.way", Component.translatable(direction), blocks);
    }

    // ------------------------------------------------------------------ tooltips

    private void tooltip(GuiGraphics graphics, CapitalMap.Data map, int mouseX, int mouseY) {
        Optional<CapitalMap.Place> place = spotAt(mouseX, mouseY);
        if (place.isPresent()) {
            graphics.renderComponentTooltip(font, placeLines(place.get()), mouseX, mouseY);
            return;
        }
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
        }
    }

    private List<Component> placeLines(CapitalMap.Place place) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(place.nameKey()).withStyle(style -> style.withColor(
                MapMath.mix(place.kind().color(), 0xFFFFFF, 0.35))));
        if (place.hasNpc()) {
            lines.add(Component.translatable("npc.eternalrealm." + place.npc()).withStyle(ChatFormatting.WHITE));
        }
        lines.add(Component.translatable(place.descKey()).withStyle(ChatFormatting.GRAY));
        MapView.Mark mark = marks.getOrDefault(place.id(), MapView.Mark.NONE);
        if (mark != MapView.Mark.NONE) {
            lines.add(Component.translatable(mark == MapView.Mark.READY ? "map.eternalrealm.npc.ready"
                    : "map.eternalrealm.npc.offer").withStyle(ChatFormatting.YELLOW));
        }
        Component way = way(place);
        if (way != null) {
            lines.add(way.copy().withStyle(ChatFormatting.AQUA));
        }
        lines.add(Component.translatable("screen.eternalrealm.capital_map.click").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private static Component npcName(MapView.Npc npc) {
        try {
            MutableComponent name = Component.Serializer.fromJson(npc.name());
            if (name != null) {
                return name;
            }
        } catch (JsonParseException | IllegalStateException e) {
            // fall through to the role's name
        }
        return Component.translatable("npc.eternalrealm." + npc.role());
    }
}
