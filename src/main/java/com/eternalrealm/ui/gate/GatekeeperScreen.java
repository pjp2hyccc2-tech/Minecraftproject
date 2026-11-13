package com.eternalrealm.ui.gate;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.forge.GatekeeperService;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.eternalrealm.ui.GoldFrame;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 転移門: the gatekeeper's screen. The eight dungeons and the final raid on the left (emblem, name, level, sealed or
 * conquered), the chosen one on the right: its picture (gently drifting, with the theme's motes over it; chains and a
 * padlock while sealed), boss, party size, time limit, a few lines about it, the three difficulties with what each
 * does to the boss and pays, the player's best time, and the button that sends the party off. A slowly turning magic
 * circle, drifting motes and a gold frame around it all; the whole panel shrinks to fit small windows.
 *
 * <p>Keys: up/down choose, left/right difficulty, Enter sets out. The server decides everything
 * ({@link GatekeeperService}); this screen only shows its board and asks. Art: tools/art/gate_screen.py.</p>
 */
public final class GatekeeperScreen extends Screen {
    // layout, in GUI units from the panel's top-left corner
    private static final int W = 420;
    private static final int H = 248;
    private static final int RIBBON_TOP = -9;
    private static final int LIST_X = 14;
    private static final int LIST_Y = 30;
    private static final int ROW_W = 150;
    private static final int ROW_H = 20;
    private static final int ROW_STEP = 22;
    private static final int DIVIDER_Y = LIST_Y + 8 * ROW_STEP + 1;
    private static final int RAID_Y = LIST_Y + 8 * ROW_STEP + 14;
    private static final int PANE_X = 172;
    private static final int PANE_W = 236;
    private static final int PIC_Y = 30;
    private static final int PIC_H = 92;
    private static final int BOSS_Y = 126;
    private static final int FACTS_Y = 137;
    private static final int LORE_Y = 150;
    private static final int LORE_LINES = 3;
    private static final int PILL_Y = 181;
    private static final int PILL_STEP = 81;
    private static final int DETAIL_Y = 205;
    private static final int BUTTON_Y = 216;

    // textures (tools/art/gate_screen.py ATLAS)
    private static final ResourceLocation ATLAS = new ResourceLocation(ModInfo.MOD_ID, "textures/gui/gate/atlas.png");
    private static final ResourceLocation CIRCLE = new ResourceLocation(ModInfo.MOD_ID, "textures/gui/gate/circle.png");
    private static final ResourceLocation SEALED = new ResourceLocation(ModInfo.MOD_ID, "textures/gui/gate/sealed.png");
    private static final Map<String, ResourceLocation> PICTURES = new HashMap<>();
    private static final int TEX = 256;
    private static final int PIC_TEX_W = 472;
    private static final int PIC_TEX_H = 200;
    private static final int PIC_SAMPLE_W = 456;
    private static final int PIC_SAMPLE_H = 178;
    private static final int CORNER_V = 0;
    private static final int GEM_U = 96;
    private static final int HALO_U = 216;
    private static final int EMBLEM_V = 24;
    private static final int LOCK_U = 144;
    private static final int LAUREL_U = 156;
    private static final int POINTER_U = 172;
    private static final int ICON_U = 180;
    private static final int MEDAL_V = 40;
    private static final int BADGE_U = 80;
    private static final int STUD_U = 114;
    private static final int DIVIDER_U = 121;
    private static final int RIBBON_V = 60;
    private static final int BUTTON_V = 90;
    private static final int PILL_U = 96;
    private static final int ROW_V = 162;
    private static final String[] EMBLEM_ORDER = {"d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "final"};

    // colours
    private static final int TEXT = 0xFFEDE3CC;
    private static final int MUTED = 0xFFA89A86;
    private static final int GOLD = 0xFFFFD866;
    private static final int GOLD_DIM = 0xFFD2AE5A;
    private static final int SEALED_TEXT = 0xFF7E7470;
    private static final int WARN = 0xFFFF8A6A;
    private static final int LORE = 0xFFC4B69C;
    private static final int[] DIFF_TEXT = {0xFF8CE08C, 0xFFFF8A6A, 0xFFD6A0FF};
    private static final float[][] DIFF_GLOW = {{0.45F, 1.0F, 0.55F}, {1.0F, 0.45F, 0.35F}, {0.78F, 0.48F, 1.0F}};
    private static final String[] DIFF_IDS = {"normal", "hard", "ex"};

    private static final long ERROR_MS = 4500;
    private static final long PENDING_MS = 6000;
    private static final long FADE_MS = 260;
    /** The difficulty last chosen (kept while the game runs). */
    private static int lastDifficulty;

    private record Reward(String rarity, int color, int stones) {
    }

    private record Entry(String id, String name, String area, String boss, int level, int min, int max, int minutes,
                         int rooms, boolean raid, boolean open, boolean conquered, String unlock, String refusal,
                         double[] hp, double[] damage, long[] best) {
    }

    private final long openedAt = Util.getMillis();
    private final List<Entry> entries = new ArrayList<>();
    private final List<Reward> rewards = new ArrayList<>();
    private int level = 1;
    private boolean inParty;
    private boolean leader = true;
    private int partySize = 1;
    private int conquered;
    private int dungeons = 8;
    private int selected;
    private int previous = -1;
    private long switchedAt;
    private int difficulty = lastDifficulty;
    private String pending;
    private long pendingAt;
    private Component error;
    private long errorAt;

    private float scale = 1.0F;
    private float left;
    private float top;

    private GatekeeperScreen(JsonObject data) {
        super(Component.translatable("screen.eternalrealm.gate.title"));
        accept(data, true);
    }

    /** The board arrived ({@link GatekeeperService#CHANNEL}): opens the screen, or refreshes an open one. */
    public static void open(JsonObject data) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof GatekeeperScreen screen) {
            screen.accept(data, false);
        } else {
            minecraft.setScreen(new GatekeeperScreen(data));
        }
    }

    /** The answer to setting out ({@link GatekeeperService#RESULT_CHANNEL}). */
    public static void onResult(JsonObject data) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean ok = data.has("ok") && data.get("ok").isJsonPrimitive() && data.get("ok").getAsBoolean();
        Component message = parse(ClientUiState.stringOr(data, "message", ""));
        if (minecraft.screen instanceof GatekeeperScreen screen) {
            screen.pending = null;
            if (ok) {
                play(SoundEvents.ENCHANTMENT_TABLE_USE, 1.15F);
                screen.onClose();
            } else {
                screen.error = message;
                screen.errorAt = Util.getMillis();
                play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F);
            }
        } else if (!ok && message != null) {
            minecraft.gui.setOverlayMessage(message, false);
        }
    }

    private static Component parse(String json) {
        if (json.isEmpty()) {
            return null;
        }
        try {
            MutableComponent component = Component.Serializer.fromJson(json);
            return component;
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }

    private void accept(JsonObject data, boolean first) {
        String keep = entries.isEmpty() ? null : entries.get(selected).id();
        entries.clear();
        rewards.clear();
        level = ClientUiState.intOr(data, "level", 1);
        inParty = bool(data, "inParty");
        leader = !data.has("leader") || bool(data, "leader");
        partySize = Math.max(1, ClientUiState.intOr(data, "partySize", 1));
        conquered = ClientUiState.intOr(data, "conquered", 0);
        dungeons = Math.max(1, ClientUiState.intOr(data, "dungeons", 8));
        if (data.get("rewards") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element instanceof JsonObject reward) {
                    rewards.add(new Reward(ClientUiState.stringOr(reward, "rarity", ""),
                            ClientUiState.intOr(reward, "color", 0xFFFFFF), ClientUiState.intOr(reward, "stones", 0)));
                }
            }
        }
        if (data.get("entries") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element instanceof JsonObject row) {
                    String id = ClientUiState.stringOr(row, "id", "");
                    if (!id.matches("[a-z0-9_]{1,16}")) {
                        continue;
                    }
                    entries.add(new Entry(id, ClientUiState.stringOr(row, "name", ""),
                            ClientUiState.stringOr(row, "area", ""), ClientUiState.stringOr(row, "boss", ""),
                            ClientUiState.intOr(row, "level", 1), ClientUiState.intOr(row, "min", 1),
                            ClientUiState.intOr(row, "max", 1), ClientUiState.intOr(row, "minutes", 0),
                            ClientUiState.intOr(row, "rooms", 0), bool(row, "raid"), bool(row, "open"),
                            bool(row, "conquered"), ClientUiState.stringOr(row, "unlock", ""),
                            ClientUiState.stringOr(row, "refusal", ""), doubles(row, "hp"), doubles(row, "dmg"),
                            longs(row, "best")));
                }
            }
        }
        if (entries.isEmpty()) {
            entries.add(new Entry("d1", "dungeon.eternalrealm.d1", "", "", 1, 1, 1, 0, 0, false, false, false, "",
                    "sealed", new double[]{1, 1, 1}, new double[]{1, 1, 1}, new long[]{-1, -1, -1}));
        }
        String want = first ? ClientUiState.stringOr(data, "focus", "") : keep;
        selected = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(want)) {
                selected = i;
            }
        }
        previous = -1;
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    private static double[] doubles(JsonObject object, String key) {
        double[] out = {1, 1, 1};
        if (object.get(key) instanceof JsonArray array) {
            for (int i = 0; i < Math.min(3, array.size()); i++) {
                if (array.get(i).isJsonPrimitive() && array.get(i).getAsJsonPrimitive().isNumber()) {
                    out[i] = array.get(i).getAsDouble();
                }
            }
        }
        return out;
    }

    private static long[] longs(JsonObject object, String key) {
        long[] out = {-1, -1, -1};
        if (object.get(key) instanceof JsonArray array) {
            for (int i = 0; i < Math.min(3, array.size()); i++) {
                if (array.get(i).isJsonPrimitive() && array.get(i).getAsJsonPrimitive().isNumber()) {
                    out[i] = array.get(i).getAsLong();
                }
            }
        }
        return out;
    }

    @Override
    protected void init() {
        float fitW = (width - 8) / (float) W;
        float fitH = (height - 8) / (float) (H - RIBBON_TOP);
        scale = Math.min(1.0F, Math.min(fitW, fitH));
        if (scale >= 1.0F) {
            left = Math.round((width - W) / 2.0F);
            top = Math.round((height - (H - RIBBON_TOP)) / 2.0F) - RIBBON_TOP;
        } else {
            left = (width - W * scale) / 2.0F;
            top = (height - (H - RIBBON_TOP) * scale) / 2.0F - RIBBON_TOP * scale;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (pending != null && Util.getMillis() - pendingAt > PENDING_MS) {
            pending = null;
        }
    }

    private Entry current() {
        return entries.get(selected);
    }

    private float seconds() {
        return (Util.getMillis() - openedAt) / 1000.0F;
    }

    private boolean canDepart(Entry entry) {
        return entry.open() && entry.refusal().isEmpty() && pending == null;
    }

    // ------------------------------------------------------------------------------------------------ input

    private double localX(double mouseX) {
        return (mouseX - left) / scale;
    }

    private double localY(double mouseY) {
        return (mouseY - top) / scale;
    }

    private static boolean inside(double x, double y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    private int rowY(int index) {
        return entries.get(index).raid() ? RAID_Y : LIST_Y + index * ROW_STEP;
    }

    private int rowAt(double x, double y) {
        for (int i = 0; i < entries.size(); i++) {
            if (inside(x, y, LIST_X, rowY(i), ROW_W, ROW_H)) {
                return i;
            }
        }
        return -1;
    }

    private int pillAt(double x, double y) {
        for (int i = 0; i < 3; i++) {
            if (inside(x, y, PANE_X + i * PILL_STEP, PILL_Y, 74, 20)) {
                return i;
            }
        }
        return -1;
    }

    private boolean onButton(double x, double y) {
        return inside(x, y, PANE_X + PANE_W - 96, BUTTON_Y, 96, 24);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double x = localX(mouseX);
            double y = localY(mouseY);
            int row = rowAt(x, y);
            if (row >= 0) {
                select(row);
                return true;
            }
            int pill = pillAt(x, y);
            if (pill >= 0) {
                choose(pill);
                return true;
            }
            if (onButton(x, y)) {
                depart();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double x = localX(mouseX);
        if (x < PANE_X - 4) {
            select(Mth.clamp(selected - (int) Math.signum(delta), 0, entries.size() - 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                select(Math.max(0, selected - 1));
                return true;
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                select(Math.min(entries.size() - 1, selected + 1));
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
                choose(Math.max(0, difficulty - 1));
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> {
                choose(Math.min(2, difficulty + 1));
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                depart();
                return true;
            }
            default -> {
                if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
                    onClose();
                    return true;
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private void select(int index) {
        if (index == selected || index < 0 || index >= entries.size()) {
            return;
        }
        previous = selected;
        selected = index;
        switchedAt = Util.getMillis();
        error = null;
        play(SoundEvents.BOOK_PAGE_TURN, 1.0F + index * 0.03F);
    }

    private void choose(int index) {
        if (index == difficulty) {
            return;
        }
        difficulty = index;
        lastDifficulty = index;
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F + index * 0.25F);
    }

    private void depart() {
        Entry entry = current();
        if (!canDepart(entry)) {
            play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F);
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("id", entry.id());
        args.addProperty("difficulty", DIFF_IDS[difficulty]);
        ERNetwork.sendToServer(new UiActionPacket(GatekeeperService.START, args));
        pending = entry.id();
        pendingAt = Util.getMillis();
        error = null;
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
    }

    private static void play(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        double mx = localX(mouseX);
        double my = localY(mouseY);
        float t = seconds();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(left, top, 0.0F);
        pose.scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawPanel(graphics, t);
        drawList(graphics, mx, my, t);
        drawPane(graphics, mx, my, t);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
        drawTooltips(graphics, mx, my, mouseX, mouseY);
        RenderSystem.disableBlend();
    }

    private void atlas(GuiGraphics graphics, int x, int y, int u, int v, int w, int h) {
        graphics.blit(ATLAS, x, y, u, v, w, h, TEX, TEX);
    }

    private void text(GuiGraphics graphics, Component text, float x, float y, int color, float size) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(size, size, 1.0F);
        graphics.drawString(font, text, 0, 0, color, true);
        pose.popPose();
    }

    private void textSeq(GuiGraphics graphics, FormattedCharSequence text, float x, float y, int color, float size) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(size, size, 1.0F);
        graphics.drawString(font, text, 0, 0, color, true);
        pose.popPose();
    }

    /** Draws {@code text} at up to {@code size}, shrunk (down to {@code min}) or shortened to fit {@code maxWidth}. */
    private float fitted(GuiGraphics graphics, Component text, float x, float y, float maxWidth, int color, float size,
                         float min) {
        int width = font.width(text);
        float s = width <= 0 ? size : Math.min(size, maxWidth / width);
        if (s >= min) {
            text(graphics, text, x, y, color, s);
            return width * s;
        }
        String plain = text.getString();
        String cut = font.plainSubstrByWidth(plain, Math.max(0, (int) (maxWidth / min) - font.width("…"))) + "…";
        text(graphics, Component.literal(cut).withStyle(text.getStyle()), x, y, color, min);
        return font.width(cut) * min;
    }

    private void centered(GuiGraphics graphics, Component text, float cx, float y, int color, float size) {
        text(graphics, text, cx - font.width(text) * size / 2.0F, y, color, size);
    }

    private static float rand(int seed) {
        int h = seed * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA77;
        h ^= h >>> 13;
        h *= 0xC2B2AE3D;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static int argb(int rgb, float alpha) {
        return (Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private void dot(GuiGraphics graphics, float x, float y, int rgb, float alpha, boolean glow) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        if (glow) {
            graphics.fill(-1, -1, 2, 2, argb(rgb, alpha * 0.22F));
        }
        graphics.fill(0, 0, 1, 1, argb(rgb, alpha));
        pose.popPose();
    }

    private void drawPanel(GuiGraphics graphics, float t) {
        graphics.fill(-3, -1, W + 5, H + 5, 0x70000000);
        graphics.fillGradient(0, 0, W, H, 0xF4161C34, 0xF40A0C1A);
        // the night inside the frame
        for (int i = 0; i < 46; i++) {
            float x = 6 + rand(i * 7 + 1) * (W - 12);
            float y = 6 + rand(i * 7 + 2) * (H - 12);
            float twinkle = 0.5F + 0.5F * Mth.sin(t * (0.8F + rand(i * 7 + 3) * 1.6F) + i);
            dot(graphics, x, y, 0xE8E4FF, 0.12F + 0.4F * twinkle * rand(i * 7 + 4), false);
        }
        // the magic circle, turning slowly, and a smaller one against it
        circle(graphics, W / 2.0F, H / 2.0F + 6, 236, t * 5.0F, 0.95F, 0.78F, 0.4F, 0.085F);
        circle(graphics, W / 2.0F, H / 2.0F + 6, 150, -t * 8.0F, 0.95F, 0.78F, 0.4F, 0.06F);
        // motes rising through the panel
        for (int i = 0; i < 22; i++) {
            float speed = 4.0F + rand(i * 11 + 1) * 7.0F;
            float x = 10 + rand(i * 11 + 2) * (W - 20) + Mth.sin(t * 0.7F + i) * 4.0F;
            float y = H - 6 - ((rand(i * 11 + 3) * H + t * speed) % (H - 12));
            float edge = Math.min(1.0F, Math.min((y - 6) / 24.0F, (H - 6 - y) / 24.0F));
            dot(graphics, x, y, 0xFFD866, 0.45F * edge * (0.6F + 0.4F * Mth.sin(t * 2.0F + i)), true);
        }
        // the frame
        graphics.renderOutline(-1, -1, W + 2, H + 2, 0xFF2A1606);
        GoldFrame.draw(graphics, 1, 1, W - 2, H - 2, (long) (t * 20.0F));
        graphics.renderOutline(4, 4, W - 8, H - 8, 0x8A8A6420);
        atlas(graphics, -1, -1, 0, CORNER_V, 24, 24);
        atlas(graphics, W - 23, -1, 24, CORNER_V, 24, 24);
        atlas(graphics, -1, H - 23, 48, CORNER_V, 24, 24);
        atlas(graphics, W - 23, H - 23, 72, CORNER_V, 24, 24);
        atlas(graphics, -3, H / 2 - 3, STUD_U, MEDAL_V, 7, 7);
        atlas(graphics, W - 4, H / 2 - 3, STUD_U, MEDAL_V, 7, 7);
        atlas(graphics, W / 2 - 3, H - 4, STUD_U, MEDAL_V, 7, 7);
        // a rule between the list and the picture
        graphics.fillGradient(PANE_X - 5, LIST_Y, PANE_X - 4, LIST_Y + 100, 0x00D8A84A, 0x80D8A84A);
        graphics.fillGradient(PANE_X - 5, LIST_Y + 100, PANE_X - 4, H - 8, 0x80D8A84A, 0x00D8A84A);
        // the title ribbon
        atlas(graphics, W / 2 - 100, RIBBON_TOP, 0, RIBBON_V, 200, 30);
        centered(graphics, title, W / 2.0F, RIBBON_TOP + 9.5F, 0xFFFFE9B0, 1.25F);
        // conquered dungeons (left) and the party (right)
        atlas(graphics, 12, 5, LAUREL_U, EMBLEM_V, 16, 16);
        text(graphics, Component.translatable("screen.eternalrealm.gate.conquered_count", conquered, dungeons), 30, 9,
                GOLD_DIM, 1.0F);
        Component party = !inParty ? Component.translatable("screen.eternalrealm.gate.solo")
                : Component.translatable(leader ? "screen.eternalrealm.gate.party_leader"
                : "screen.eternalrealm.gate.party_member", partySize);
        // right of the ribbon's tail (it ends at W / 2 + 100), the icon included
        float room = W - 14 - (W / 2.0F + 104) - 12;
        float ps = Math.min(1.0F, room / Math.max(1, font.width(party)));
        float pw = font.width(party) * ps;
        text(graphics, party, W - 14 - pw, 9 + (1.0F - ps) * 4.0F, leader ? GOLD_DIM : WARN, ps);
        atlas(graphics, Math.round(W - 14 - pw - 12), 9, ICON_U + 9, EMBLEM_V, 9, 9);
    }

    private void circle(GuiGraphics graphics, float cx, float cy, int size, float degrees, float r, float g, float b,
                        float alpha) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(degrees));
        graphics.setColor(r, g, b, alpha);
        graphics.blit(CIRCLE, -size / 2, -size / 2, size, size, 0.0F, 0.0F, 256, 256, 256, 256);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }

    private void drawList(GuiGraphics graphics, double mx, double my, float t) {
        int hover = rowAt(mx, my);
        atlas(graphics, LIST_X + ROW_W / 2 - 64, DIVIDER_Y + 2, DIVIDER_U, MEDAL_V, 128, 9);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int x = LIST_X;
            int y = rowY(i);
            boolean chosen = i == selected;
            graphics.fill(x, y, x + ROW_W, y + ROW_H, entry.raid() ? 0x50401060 : 0x38000000);
            graphics.fill(x + 22, y + ROW_H - 1, x + ROW_W, y + ROW_H, 0x30D8A84A);
            if (chosen) {
                atlas(graphics, x, y, 0, ROW_V, ROW_W, ROW_H);
                float bob = Mth.sin(t * 5.0F) * 1.5F;
                PoseStack pose = graphics.pose();
                pose.pushPose();
                pose.translate(bob, 0.0F, 0.0F);
                atlas(graphics, x - 10, y + 5, POINTER_U, EMBLEM_V, 8, 10);
                pose.popPose();
            } else if (i == hover) {
                atlas(graphics, x, y, 0, ROW_V + ROW_H, ROW_W, ROW_H);
            }
            int medal = !entry.open() ? 2 : entry.raid() ? 3 : chosen ? 1 : 0;
            atlas(graphics, x, y, medal * 20, MEDAL_V, 20, 20);
            if (entry.open()) {
                atlas(graphics, x + 2, y + 2, emblemIndex(entry.id()) * 16, EMBLEM_V, 16, 16);
            } else {
                atlas(graphics, x + 4, y + 3, LOCK_U, EMBLEM_V, 12, 14);
            }
            int nameColor = !entry.open() ? SEALED_TEXT : chosen ? GOLD : TEXT;
            float room = ROW_W - 26 - (entry.conquered() ? 18 : 2);
            fitted(graphics, Component.translatable(entry.name()), x + 24, y + 2, room, nameColor, 1.0F, 0.7F);
            Component sub;
            int subColor;
            if (!entry.open()) {
                sub = Component.translatable("screen.eternalrealm.gate.sealed");
                subColor = 0xFFB0645A;
            } else {
                sub = Component.translatable("screen.eternalrealm.gate.level_short", entry.level());
                subColor = level + 3 < entry.level() ? WARN : MUTED;
            }
            text(graphics, sub, x + 24, y + 12, subColor, 0.75F);
            if (entry.conquered()) {
                atlas(graphics, x + ROW_W - 18, y + 2, LAUREL_U, EMBLEM_V, 16, 16);
            }
        }
    }

    private static int emblemIndex(String id) {
        for (int i = 0; i < EMBLEM_ORDER.length; i++) {
            if (EMBLEM_ORDER[i].equals(id)) {
                return i;
            }
        }
        return 0;
    }

    private static ResourceLocation picture(String id) {
        return PICTURES.computeIfAbsent(id, key -> new ResourceLocation(ModInfo.MOD_ID,
                "textures/gui/gate/" + key + ".png"));
    }

    private void drawPicture(GuiGraphics graphics, Entry entry, float t, float alpha) {
        float u = 8.0F + 8.0F * Mth.sin(t * (float) (Math.PI * 2.0 / 26.0));
        float v = 11.0F + 6.0F * Mth.sin(t * (float) (Math.PI * 2.0 / 33.0) + 1.0F);
        float shade = entry.open() ? 1.0F : 0.34F;
        graphics.setColor(shade, shade * 0.95F, shade * 1.08F, alpha);
        graphics.blit(picture(entry.id()), PANE_X, PIC_Y, PANE_W, PIC_H, u, v, PIC_SAMPLE_W, PIC_SAMPLE_H, PIC_TEX_W,
                PIC_TEX_H);
        if (!entry.open()) {
            graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            graphics.blit(SEALED, PANE_X, PIC_Y, PANE_W, PIC_H, u, v, PIC_SAMPLE_W, PIC_SAMPLE_H, PIC_TEX_W, PIC_TEX_H);
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawPane(GuiGraphics graphics, double mx, double my, float t) {
        Entry entry = current();
        long now = Util.getMillis();
        // the picture, fading over the one before it
        GoldFrame.draw(graphics, PANE_X, PIC_Y, PANE_W, PIC_H, (long) (t * 20.0F));
        float fade = previous >= 0 ? Mth.clamp((now - switchedAt) / (float) FADE_MS, 0.0F, 1.0F) : 1.0F;
        if (fade < 1.0F) {
            drawPicture(graphics, entries.get(previous), t, 1.0F);
        }
        drawPicture(graphics, entry, t, fade);
        if (entry.open()) {
            motes(graphics, entry.id(), t);
        }
        graphics.fillGradient(PANE_X, PIC_Y + PIC_H - 36, PANE_X + PANE_W, PIC_Y + PIC_H, 0x00000000, 0xD8000000);
        if (!entry.area().isEmpty()) {
            fitted(graphics, Component.translatable(entry.area()), PANE_X + 6, PIC_Y + PIC_H - 30, PANE_W - 12,
                    0xFFE4CC92, 0.75F, 0.6F);
        }
        fitted(graphics, Component.translatable(entry.name()), PANE_X + 6, PIC_Y + PIC_H - 22, PANE_W - 12,
                entry.open() ? GOLD : 0xFFB8AC9C, 1.5F, 0.9F);
        atlas(graphics, PANE_X + 4, PIC_Y + 4, BADGE_U, MEDAL_V, 34, 14);
        centered(graphics, Component.translatable("screen.eternalrealm.gate.level_badge", entry.level()),
                PANE_X + 21, PIC_Y + 7, level + 3 < entry.level() ? WARN : GOLD, 1.0F);
        if (entry.conquered()) {
            atlas(graphics, PANE_X + PANE_W - 20, PIC_Y + 3, LAUREL_U, EMBLEM_V, 16, 16);
        }
        if (error != null && now - errorAt < ERROR_MS) {
            float a = Mth.clamp((ERROR_MS - (now - errorAt)) / 500.0F, 0.0F, 1.0F);
            graphics.fill(PANE_X, PIC_Y + PIC_H - 15, PANE_X + PANE_W, PIC_Y + PIC_H, argb(0x5A0A14, 0.9F * a));
            graphics.fill(PANE_X, PIC_Y + PIC_H - 15, PANE_X + PANE_W, PIC_Y + PIC_H - 14, argb(0xE8A04A, a));
            fitted(graphics, error, PANE_X + 6, PIC_Y + PIC_H - 11, PANE_W - 12, argb(0xFFD8CC, a), 1.0F, 0.7F);
        }

        // boss, party size, time limit, rooms
        atlas(graphics, PANE_X, BOSS_Y, ICON_U, EMBLEM_V, 9, 9);
        Component bossLabel = Component.translatable("screen.eternalrealm.gate.boss");
        text(graphics, bossLabel, PANE_X + 12, BOSS_Y + 1, MUTED, 1.0F);
        fitted(graphics, Component.translatable(entry.boss()), PANE_X + 16 + font.width(bossLabel), BOSS_Y + 1,
                PANE_W - 18 - font.width(bossLabel), 0xFFF0D8A8, 1.0F, 0.7F);
        float fx = PANE_X;
        fx = fact(graphics, fx, 9, Component.translatable("screen.eternalrealm.gate.party", entry.min(), entry.max()));
        fx = fact(graphics, fx + 10, 18, Component.translatable("screen.eternalrealm.gate.time", entry.minutes()));
        fact(graphics, fx + 10, 27, entry.raid() ? Component.translatable("screen.eternalrealm.gate.arena")
                : Component.translatable("screen.eternalrealm.gate.rooms", entry.rooms()));

        // a few lines about it (and what breaks the seal)
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (!entry.open()) {
            Component hint = entry.raid()
                    ? Component.translatable("screen.eternalrealm.gate.unlock_final", conquered, dungeons)
                    : entry.unlock().isEmpty() ? Component.translatable("screen.eternalrealm.gate.sealed")
                    : Component.translatable("screen.eternalrealm.gate.unlock", Component.translatable(entry.unlock()));
            lines.addAll(font.split(hint, PANE_W));
        }
        int hintLines = lines.size();
        lines.addAll(font.split(Component.translatable("screen.eternalrealm.gate.lore." + entry.id()), PANE_W));
        for (int i = 0; i < Math.min(LORE_LINES, lines.size()); i++) {
            textSeq(graphics, lines.get(i), PANE_X, LORE_Y + i * 10, i < hintLines ? 0xFFE89A84 : LORE, 1.0F);
        }

        // the three difficulties
        int hoverPill = pillAt(mx, my);
        for (int i = 0; i < 3; i++) {
            int x = PANE_X + i * PILL_STEP;
            boolean chosen = i == difficulty;
            atlas(graphics, x, PILL_Y, PILL_U, BUTTON_V + (chosen ? 40 : i == hoverPill ? 20 : 0), 74, 20);
            if (chosen) {
                float pulse = 0.55F + 0.25F * Mth.sin(t * 3.2F);
                graphics.setColor(DIFF_GLOW[i][0], DIFF_GLOW[i][1], DIFF_GLOW[i][2], pulse);
                atlas(graphics, x - 4, PILL_Y - 6, HALO_U, 0, 32, 32);
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
            atlas(graphics, x + 2, PILL_Y, GEM_U + (i * 2 + (chosen || i == hoverPill ? 1 : 0)) * 20, 0, 20, 20);
            fitted(graphics, Component.translatable("difficulty.eternalrealm." + DIFF_IDS[i]), x + 25, PILL_Y + 6, 45,
                    chosen ? DIFF_TEXT[i] : MUTED, 1.0F, 0.7F);
        }
        // what the chosen difficulty does to the boss and pays
        MutableComponent detail = bossNumbers(entry);
        if (difficulty < rewards.size()) {
            Reward reward = rewards.get(difficulty);
            detail.append("  ").append(Component.translatable("screen.eternalrealm.gate.reward",
                    Component.translatable(reward.rarity()).withStyle(style -> style.withColor(reward.color())),
                    reward.stones()));
        }
        fitted(graphics, detail, PANE_X, DETAIL_Y, PANE_W, 0xFFCFC2A8, 1.0F, 0.62F);

        // personal best and the button
        long best = entry.best()[difficulty];
        Component bestText = best >= 0
                ? Component.translatable("screen.eternalrealm.gate.best", clock(best))
                : Component.translatable("screen.eternalrealm.gate.best_none");
        fitted(graphics, bestText, PANE_X, BUTTON_Y + 8, PANE_W - 104, MUTED, 1.0F, 0.7F);
        boolean enabled = canDepart(entry);
        int bx = PANE_X + PANE_W - 96;
        boolean over = onButton(mx, my);
        atlas(graphics, bx, BUTTON_Y, 0, BUTTON_V + (!enabled ? 48 : over ? 24 : 0), 96, 24);
        Component label = pending != null ? Component.translatable("screen.eternalrealm.gate.departing")
                : !entry.open() ? Component.translatable("screen.eternalrealm.gate.refusal.sealed.short")
                : !entry.refusal().isEmpty()
                ? Component.translatable("screen.eternalrealm.gate.refusal." + entry.refusal() + ".short")
                : Component.translatable("screen.eternalrealm.gate.depart");
        float size = Math.min(1.0F, 84.0F / Math.max(1, font.width(label)));
        centered(graphics, label, bx + 48, BUTTON_Y + 12 - 4 * size, enabled ? 0xFFFFF0C8 : 0xFF9A9AA2, size);
    }

    private float fact(GuiGraphics graphics, float x, int iconOffset, Component text) {
        atlas(graphics, (int) x, FACTS_Y, ICON_U + iconOffset, EMBLEM_V, 9, 9);
        text(graphics, text, x + 11, FACTS_Y + 1, TEXT, 1.0F);
        return x + 11 + font.width(text);
    }

    private MutableComponent bossNumbers(Entry entry) {
        double hp = entry.hp()[difficulty];
        double damage = entry.damage()[difficulty];
        if (Math.abs(hp - 1.0) < 1e-6 && Math.abs(damage - 1.0) < 1e-6) {
            return Component.translatable("screen.eternalrealm.gate.boss_base");
        }
        return Component.translatable("screen.eternalrealm.gate.boss_stats", number(hp), number(damage));
    }

    private static String number(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static String clock(long ticks) {
        long tenths = ticks / 2;
        long minutes = tenths / 600;
        long seconds = (tenths / 10) % 60;
        return String.format(Locale.ROOT, "%d:%02d.%d", minutes, seconds, tenths % 10);
    }

    /** The theme's motes drifting over the picture (clipped to it). */
    private void motes(GuiGraphics graphics, String id, float t) {
        int count;
        int rgb;
        float vx;
        float vy;
        float sway;
        float twinkle;
        boolean glow;
        switch (id) {
            case "d1" -> { count = 24; rgb = 0xFFD8A0; vx = 1.5F; vy = -2.5F; sway = 3; twinkle = 1.5F; glow = true; }
            case "d2" -> { count = 18; rgb = 0xD8FF70; vx = 0.0F; vy = -1.5F; sway = 7; twinkle = 3.0F; glow = true; }
            case "d3" -> { count = 40; rgb = 0xFFD8A0; vx = 16.0F; vy = 1.0F; sway = 1; twinkle = 1.0F; glow = false; }
            case "d4" -> { count = 30; rgb = 0xE8F8FF; vx = -1.0F; vy = 4.0F; sway = 2; twinkle = 1.0F; glow = false; }
            case "d5" -> { count = 46; rgb = 0xFFFFFF; vx = 2.0F; vy = 9.0F; sway = 4; twinkle = 0.5F; glow = false; }
            case "d6" -> { count = 36; rgb = 0xFFB040; vx = 1.0F; vy = -11.0F; sway = 3; twinkle = 2.5F; glow = true; }
            case "d7" -> { count = 20; rgb = 0xE0FDFF; vx = 0.0F; vy = -3.0F; sway = 2; twinkle = 3.0F; glow = true; }
            case "d8" -> { count = 28; rgb = 0xE070FF; vx = 0.0F; vy = -5.0F; sway = 3; twinkle = 2.0F; glow = true; }
            default -> { count = 30; rgb = 0xD080FF; vx = 0.0F; vy = -4.0F; sway = 4; twinkle = 2.0F; glow = true; }
        }
        int x0 = Math.round(left + PANE_X * scale);
        int y0 = Math.round(top + PIC_Y * scale);
        int x1 = Math.round(left + (PANE_X + PANE_W) * scale);
        int y1 = Math.round(top + (PIC_Y + PIC_H) * scale);
        graphics.enableScissor(x0, y0, x1, y1);
        int seed = id.hashCode();
        for (int i = 0; i < count; i++) {
            float rx = rand(seed + i * 13 + 1);
            float ry = rand(seed + i * 13 + 2);
            float speed = 0.6F + 0.8F * rand(seed + i * 13 + 3);
            float x = Mth.positiveModulo(rx * PANE_W + vx * speed * t + Mth.sin(t * 0.9F + i) * sway, PANE_W);
            float y = Mth.positiveModulo(ry * PIC_H + vy * speed * t, PIC_H);
            float a = 0.35F + 0.65F * (0.5F + 0.5F * Mth.sin(t * twinkle + i * 1.7F));
            dot(graphics, PANE_X + x, PIC_Y + y, rgb, a * 0.85F, glow);
        }
        graphics.disableScissor();
    }

    private void drawTooltips(GuiGraphics graphics, double mx, double my, int mouseX, int mouseY) {
        Entry entry = current();
        List<Component> tip = new ArrayList<>();
        int row = rowAt(mx, my);
        if (inside(mx, my, 12, 5, 16 + 6 + font.width(Component.translatable(
                "screen.eternalrealm.gate.conquered_count", conquered, dungeons)), 16)) {
            tip.add(Component.translatable("screen.eternalrealm.gate.conquered_help", conquered, dungeons));
        } else if (row >= 0 && entries.get(row).conquered() && inside(mx, my, LIST_X + ROW_W - 18, rowY(row) + 2, 16,
                16)) {
            tip.add(Component.translatable("screen.eternalrealm.gate.conquered"));
        } else if (entry.conquered() && inside(mx, my, PANE_X + PANE_W - 20, PIC_Y + 3, 16, 16)) {
            tip.add(Component.translatable("screen.eternalrealm.gate.conquered"));
        } else if (pillAt(mx, my) >= 0) {
            int pill = pillAt(mx, my);
            tip.add(Component.translatable("difficulty.eternalrealm." + DIFF_IDS[pill])
                    .withStyle(style -> style.withColor(DIFF_TEXT[pill] & 0xFFFFFF)));
            tip.add(Component.translatable("screen.eternalrealm.gate.diff." + DIFF_IDS[pill]));
        } else if (onButton(mx, my) && pending == null) {
            if (!entry.open()) {
                tip.add(Component.translatable("screen.eternalrealm.gate.refusal.sealed.long"));
            } else if ("party_size".equals(entry.refusal())) {
                tip.add(Component.translatable("screen.eternalrealm.gate.refusal.party_size.long", entry.min(),
                        entry.max(), partySize));
            } else if (!entry.refusal().isEmpty()) {
                tip.add(Component.translatable("screen.eternalrealm.gate.refusal." + entry.refusal() + ".long"));
            } else {
                tip.add(Component.translatable("screen.eternalrealm.gate.depart_help"));
            }
        }
        if (!tip.isEmpty()) {
            graphics.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }
}
