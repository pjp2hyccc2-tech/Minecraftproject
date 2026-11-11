package com.eternalrealm.ui.estate;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.forge.InnService;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The innkeeper's screen (星灯りの館): the five rooms with a picture, what they hold and what they cost; renting one,
 * moving to another with everything stored, going to one's room, the keepsafe and giving the room back. Spending
 * gold takes a second click. Everything is decided by the server ({@link InnService}).
 */
public final class InnScreen extends Screen {
    private static final int WIDTH = 420;
    private static final int HEIGHT = 262;
    private static final int BUTTON_W = 86;
    private static final int LIST_W = 168;
    private static final int ROW_H = 38;
    private static final int PICTURE_W = 228;
    private static final int PICTURE_H = 96;
    private static final int CONFIRM_TICKS = 70;
    private static final String[] PIECES = {"chest", "barrel", "shelf", "stand", "frame"};

    private int left;
    private int top;
    private long seenRevision = -1;
    private JsonObject data = new JsonObject();
    private String selected = "";
    private String confirming = "";
    private int confirmTicks;
    private int ticks;

    private Button actionButton;
    private Button visitButton;
    private Button keepsafeButton;
    private Button giveUpButton;

    private InnScreen() {
        super(Component.translatable("screen.eternalrealm.inn.title"));
    }

    /** New inn data: opens the screen when the server says so; an open screen refreshes itself. */
    public static void accept(JsonObject json) {
        Minecraft minecraft = Minecraft.getInstance();
        if (EstateUi.bool(json, "open") && !(minecraft.screen instanceof InnScreen)) {
            minecraft.setScreen(new InnScreen());
        }
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int px = left + LIST_W + 16;
        actionButton = addRenderableWidget(Button.builder(Component.empty(), button -> act())
                .bounds(px, top + 208, PICTURE_W, 18).build());
        int by = top + HEIGHT - 22;
        int bx = left + WIDTH - 8 - BUTTON_W;
        giveUpButton = addRenderableWidget(Button.builder(Component.empty(), button -> giveUp())
                .bounds(bx, by, BUTTON_W, 16).build());
        keepsafeButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> EstateUi.send(InnService.KEEPSAFE, new JsonObject()))
                .bounds(bx - BUTTON_W - 4, by, BUTTON_W, 16).build());
        visitButton = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.inn.visit"),
                button -> {
                    EstateUi.send(InnService.VISIT, new JsonObject());
                    onClose();
                }).bounds(bx - 2 * (BUTTON_W + 4), by, BUTTON_W, 16).build());
        refresh();
        update();
    }

    @Override
    public void tick() {
        ticks++;
        if (confirmTicks > 0 && --confirmTicks == 0) {
            confirming = "";
        }
        String phase = EstateUi.str(data, "phase");
        if (ticks % 20 == 0 && ("building".equals(phase) || "rebuilding".equals(phase))) {
            EstateUi.send(InnService.VIEW, new JsonObject());
        }
        refresh();
        update();
    }

    private void refresh() {
        if (ClientUiState.revision() == seenRevision) {
            return;
        }
        seenRevision = ClientUiState.revision();
        JsonObject next = ClientUiState.get(InnService.CHANNEL).orElse(null);
        if (next == null || next == data) {
            return;
        }
        boolean first = !data.has("designs");
        data = next;
        if (first || selected.isEmpty()) {
            String room = EstateUi.str(data, "room");
            selected = room.isEmpty() ? "garret" : room;
        }
        confirming = "";
    }

    private JsonObject design(String id) {
        for (JsonObject design : EstateUi.objects(data, "designs")) {
            if (id.equals(EstateUi.str(design, "id"))) {
                return design;
            }
        }
        return new JsonObject();
    }

    private void update() {
        String room = EstateUi.str(data, "room");
        boolean hasRoom = !room.isEmpty();
        boolean atInn = EstateUi.bool(data, "atInn");
        boolean ready = "ready".equals(EstateUi.str(data, "phase"));
        JsonObject design = design(selected);
        long gold = EstateUi.num(data, "gold");
        actionButton.visible = design.has("id");
        if (!hasRoom) {
            long price = EstateUi.num(design, "price");
            actionButton.active = atInn && gold >= price;
            actionButton.setMessage(confirming.equals("rent:" + selected)
                    ? Component.translatable("screen.eternalrealm.inn.confirm")
                    : Component.translatable("screen.eternalrealm.inn.rent", EstateUi.gold(price)));
            actionButton.setTooltip(gold < price ? Tooltip.create(Component.translatable(
                    "screen.eternalrealm.inn.short", EstateUi.gold(price - gold))) : null);
        } else if (room.equals(selected)) {
            actionButton.active = false;
            actionButton.setMessage(Component.translatable("screen.eternalrealm.inn.current"));
            actionButton.setTooltip(null);
        } else {
            long cost = EstateUi.num(design, "cost");
            actionButton.active = atInn && ready && gold >= Math.max(0L, cost);
            Component label = cost >= 0 ? Component.translatable("screen.eternalrealm.inn.move_pay", EstateUi.gold(cost))
                    : Component.translatable("screen.eternalrealm.inn.move_back", EstateUi.gold(-cost));
            actionButton.setMessage(confirming.equals("change:" + selected)
                    ? Component.translatable("screen.eternalrealm.inn.confirm") : label);
            long kept = EstateUi.num(design, "keepsafe");
            long rehomed = EstateUi.num(design, "rehome");
            actionButton.setTooltip(Tooltip.create(kept > 0
                    ? Component.translatable("screen.eternalrealm.inn.move_keepsafe", rehomed, kept)
                    : rehomed > 0 ? Component.translatable("screen.eternalrealm.inn.move_rehome", rehomed)
                    : Component.translatable("screen.eternalrealm.inn.move_all")));
        }
        visitButton.visible = hasRoom;
        visitButton.active = atInn && ready;
        long keepsafe = EstateUi.num(data, "keepsafe");
        keepsafeButton.visible = keepsafe > 0;
        keepsafeButton.active = atInn;
        keepsafeButton.setMessage(Component.translatable("screen.eternalrealm.inn.keepsafe", keepsafe));
        giveUpButton.visible = hasRoom;
        boolean empty = EstateUi.bool(data, "empty");
        giveUpButton.active = atInn && ready && empty;
        giveUpButton.setMessage(confirming.equals("give_up") ? Component.translatable("screen.eternalrealm.inn.confirm")
                : Component.translatable("screen.eternalrealm.inn.give_up"));
        giveUpButton.setTooltip(Tooltip.create(empty
                ? Component.translatable("screen.eternalrealm.inn.give_up_tip", EstateUi.gold(EstateUi.num(data, "refund")))
                : Component.translatable("screen.eternalrealm.inn.give_up_full", EstateUi.num(data, "stored"))));
    }

    private void act() {
        String room = EstateUi.str(data, "room");
        String key = (room.isEmpty() ? "rent:" : "change:") + selected;
        if (!confirming.equals(key)) {
            confirming = key;
            confirmTicks = CONFIRM_TICKS;
            return;
        }
        confirming = "";
        JsonObject args = new JsonObject();
        args.addProperty("design", selected);
        EstateUi.send(room.isEmpty() ? InnService.RENT : InnService.CHANGE, args);
    }

    private void giveUp() {
        if (!confirming.equals("give_up")) {
            confirming = "give_up";
            confirmTicks = CONFIRM_TICKS;
            return;
        }
        confirming = "";
        EstateUi.send(InnService.GIVE_UP, new JsonObject());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<JsonObject> designs = EstateUi.objects(data, "designs");
        for (int i = 0; i < designs.size(); i++) {
            if (EstateUi.inside(mouseX, mouseY, left + 8, top + 26 + i * (ROW_H + 2), LIST_W, ROW_H)) {
                String id = EstateUi.str(designs.get(i), "id");
                if (!id.equals(selected)) {
                    selected = id;
                    confirming = "";
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.player.playSound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 0.5F, 1.2F);
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        EstateUi.panel(graphics, left, top, WIDTH, HEIGHT);
        EstateUi.title(graphics, font, title, left + WIDTH / 2, top + 6, WIDTH - 40);
        String room = EstateUi.str(data, "room");
        List<JsonObject> designs = EstateUi.objects(data, "designs");
        for (int i = 0; i < designs.size(); i++) {
            JsonObject design = designs.get(i);
            String id = EstateUi.str(design, "id");
            int x = left + 8;
            int y = top + 26 + i * (ROW_H + 2);
            boolean hover = EstateUi.inside(mouseX, mouseY, x, y, LIST_W, ROW_H);
            graphics.fill(x, y, x + LIST_W, y + ROW_H, id.equals(selected) ? EstateUi.ROW_SELECTED
                    : hover ? EstateUi.ROW_HOVER : EstateUi.ROW);
            if (id.equals(selected)) {
                graphics.fill(x, y, x + 2, y + ROW_H, EstateUi.BRASS);
            }
            // a little brass key tag with the room's number
            graphics.fill(x + 6, y + 8, x + 22, y + 30, EstateUi.BRASS_DARK);
            graphics.fill(x + 7, y + 9, x + 21, y + 29, EstateUi.BRASS);
            graphics.drawCenteredString(font, Integer.toString(i + 1), x + 14, y + 15, 0xFF3A2410);
            graphics.drawString(font, Component.translatable("estate.eternalrealm.room." + id), x + 28, y + 6,
                    EstateUi.TEXT);
            graphics.drawString(font, EstateUi.gold(EstateUi.num(design, "price")) + " G", x + 28, y + 20,
                    EstateUi.GOLD);
            if (id.equals(room)) {
                Component badge = Component.translatable("screen.eternalrealm.inn.yours");
                int bw = font.width(badge) + 6;
                graphics.fill(x + LIST_W - bw - 4, y + 18, x + LIST_W - 4, y + 30, 0xFF2E5A30);
                graphics.drawString(font, badge, x + LIST_W - bw - 1, y + 20, EstateUi.GOOD);
            }
        }
        renderDetails(graphics);
        renderFooter(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderDetails(GuiGraphics graphics) {
        int x = left + LIST_W + 16;
        int y = top + 26;
        JsonObject design = design(selected);
        if (!design.has("id")) {
            return;
        }
        EstateUi.inset(graphics, x, y, PICTURE_W, PICTURE_H);
        ResourceLocation picture = new ResourceLocation(ModInfo.MOD_ID, "textures/gui/inn/" + selected + ".png");
        graphics.blit(picture, x, y, 0.0F, 0.0F, PICTURE_W, PICTURE_H, PICTURE_W, PICTURE_H);
        y += PICTURE_H + 5;
        graphics.drawString(font, Component.translatable("estate.eternalrealm.room." + selected), x, y,
                EstateUi.GOLD);
        y += 12;
        for (FormattedCharSequence line : font.split(Component.translatable("estate.eternalrealm.room." + selected
                + ".description"), PICTURE_W)) {
            if (y > top + 26 + PICTURE_H + 5 + 12 + 3 * 10) {
                break;
            }
            graphics.drawString(font, line, x, y, EstateUi.MUTED);
            y += 10;
        }
        JsonObject storage = EstateUi.object(design, "storage");
        MutableComponent pieces = Component.empty();
        boolean firstPiece = true;
        for (String piece : PIECES) {
            long count = EstateUi.num(storage, piece);
            if (count <= 0) {
                continue;
            }
            if (!firstPiece) {
                pieces.append(Component.literal("  "));
            }
            firstPiece = false;
            pieces.append(Component.translatable("screen.eternalrealm.inn.piece." + piece, count));
        }
        graphics.drawString(font, pieces, x, top + 184, EstateUi.TEXT);
    }

    private void renderFooter(GuiGraphics graphics) {
        int y = top + HEIGHT - 28;
        graphics.fill(left + 6, y, left + WIDTH - 6, y + 1, EstateUi.BRASS_DARK);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.inn.gold",
                EstateUi.gold(EstateUi.num(data, "gold"))), left + 10, y + 5, EstateUi.GOLD);
        String room = EstateUi.str(data, "room");
        String phase = EstateUi.str(data, "phase");
        Component status;
        int color = EstateUi.MUTED;
        if (!EstateUi.bool(data, "available")) {
            status = Component.translatable("screen.eternalrealm.inn.unavailable");
            color = EstateUi.BAD;
        } else if (room.isEmpty()) {
            status = Component.translatable("screen.eternalrealm.inn.no_room");
        } else if ("ready".equals(phase)) {
            status = Component.translatable("screen.eternalrealm.inn.status_ready",
                    Component.translatable("estate.eternalrealm.room." + room), EstateUi.num(data, "stored"));
            color = EstateUi.GOOD;
        } else {
            status = Component.translatable("screen.eternalrealm.inn.status_building", EstateUi.num(data, "percent"));
            color = EstateUi.AMBER;
        }
        int free = WIDTH - 20 - 3 * (BUTTON_W + 4);
        graphics.drawString(font, EstateUi.ellipsize(font, status.getString(), free), left + 10, y + 16, color);
        if (!EstateUi.bool(data, "atInn")) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.inn.away"), left + LIST_W + 16,
                    top + 197, EstateUi.BAD);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
