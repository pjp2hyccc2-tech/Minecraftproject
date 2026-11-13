package com.eternalrealm.ui.phone;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.client.phone.Camera;
import com.eternalrealm.client.phone.ClientPhone;
import com.eternalrealm.client.phone.PhotoCache;
import com.eternalrealm.client.phone.Uploads;
import com.eternalrealm.client.sound.UiSounds;
import com.eternalrealm.social.phone.PhoneText;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * The 魔導スマホ: a phone of ebony and gold with a crystal screen, held in the middle of the screen while the game goes
 * on. Its apps: フレンド (friends, requests, blocks), メッセージ (conversations with friends, photos included), カメラ
 * (take a photo of the world, post it or send it), つぶやき (the server's board: everyone's, friends' or one's own posts,
 * likes, comments) and お知らせ (likes and comments on one's posts). Everything is laid out in the phone's own pixels
 * ({@link PhoneArt}) and the whole phone is scaled down on small windows (the mouse is mapped back).
 */
public final class PhoneScreen extends Screen {
    private enum Page { HOME, FRIENDS, PICK, MESSAGES, CHAT, CAMERA, FEED, POST, COMPOSE, ACTIVITY }

    private record Hot(float x0, float y0, float x1, float y1, Runnable action) {
        boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private record Place(Page page, String arg) {
    }

    private static final int TEXT = 0xF2ECFF;
    private static final int MUTED = 0x9A90B8;
    private static final int GOLD = 0xF0CC6A;
    private static final int DARK = 0x2A1A08;
    private static final int TOP = PhoneArt.SY0 + 9;      // below the status bar
    private static final int CONTENT = TOP + 15;          // below the header

    private Page page = Page.HOME;
    private String arg = "";
    private final Deque<Place> back = new ArrayDeque<>();
    private String feed = "all";
    private long before;
    private float scale = 1.0F;
    private float originX;
    private float originY;
    private final List<Hot> hots = new ArrayList<>();
    private double scroll;
    private int contentHeight;
    private boolean stickToBottom = true;
    private EditBox input;
    private String keptText = "";
    private Camera.Shot attached;
    private boolean picking;
    private int selectedShot = -1;
    private boolean hideHud = true;
    private boolean saveCopy;
    private String armed = "";
    private long armedAt;
    private String viewer = "";
    private int clipTop;
    private int clipBottom;
    private boolean sending;

    private PhoneScreen() {
        super(Component.translatable("phone.eternalrealm.title"));
    }

    // ------------------------------------------------------------------ opening

    /** Opens the phone at a view ({@code home}, {@code friends}, {@code messages}, {@code chat} + id, ...). */
    public static void open(String view, String id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        PhoneScreen screen = minecraft.screen instanceof PhoneScreen existing ? existing : new PhoneScreen();
        Page target = switch (view) {
            case "friends" -> Page.FRIENDS;
            case "messages" -> Page.MESSAGES;
            case "chat" -> Page.CHAT;
            case "feed" -> Page.FEED;
            case "post" -> Page.POST;
            case "activity" -> Page.ACTIVITY;
            case "camera" -> Page.CAMERA;
            default -> Page.HOME;
        };
        if ((target == Page.CHAT || target == Page.POST) && id.isEmpty()) {
            target = Page.HOME;
        }
        screen.back.clear();
        screen.go(target, id, false);
        if (minecraft.screen != screen) {
            minecraft.setScreen(screen);
            UiSounds.journalOpen();
        }
    }

    /** After the camera: the phone again, on the photo just taken. */
    public static void openCamera(Camera.Shot shot) {
        open("camera", "");
        if (Minecraft.getInstance().screen instanceof PhoneScreen screen && shot != null) {
            screen.selectedShot = shot.number();
        }
    }

    private void go(Page target, String targetArg, boolean remember) {
        if (remember && page != target) {
            back.push(new Place(page, arg));
        }
        keptText = "";
        page = target;
        arg = targetArg;
        scroll = 0;
        stickToBottom = true;
        picking = false;
        viewer = "";
        armed = "";
        if (target != Page.CHAT && target != Page.COMPOSE && target != Page.PICK) {
            attached = null;
        }
        request();
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private void goBack() {
        Place place = back.isEmpty() ? new Place(Page.HOME, "") : back.pop();
        Camera.Shot keep = attached;
        go(place.page(), place.arg(), false);
        if (place.page() == Page.CHAT || place.page() == Page.COMPOSE) {
            attached = keep;
        }
    }

    /** Asks the server for what this page shows. */
    private void request() {
        JsonObject args = new JsonObject();
        switch (page) {
            case FRIENDS, PICK -> ClientPhone.show("friends", args);
            case MESSAGES -> ClientPhone.show("messages", args);
            case CHAT -> {
                args.addProperty("id", arg);
                ClientPhone.show("chat", args);
            }
            case FEED -> {
                args.addProperty("feed", feed);
                args.addProperty("before", before);
                ClientPhone.show("feed", args);
            }
            case POST -> {
                args.addProperty("id", parseLong(arg));
                ClientPhone.show("post", args);
            }
            case ACTIVITY -> ClientPhone.show("activity", args);
            default -> ClientPhone.show("home", args);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ClientPhone.send("phone.close", new JsonObject());
        super.onClose();
    }

    @Override
    protected void init() {
        scale = Math.min(1.0F, Math.min((height - 8) / (float) PhoneArt.BODY_H, (width - 8) / (float) PhoneArt.BODY_W));
        originX = (width - PhoneArt.BODY_W * scale) / 2.0F;
        originY = (height - PhoneArt.BODY_H * scale) / 2.0F;
        input = null;
        switch (page) {
            case FRIENDS -> input = box(17, 51, 92, 16, "phone.eternalrealm.friends.hint");
            case CHAT -> input = box(15, 241, 100, PhoneText.MESSAGE_MAX, "phone.eternalrealm.chat.hint");
            case POST -> input = box(15, 241, 112, PhoneText.COMMENT_MAX, "phone.eternalrealm.post.hint");
            case COMPOSE -> input = box(17, 147, 134, PhoneText.POST_MAX, "phone.eternalrealm.compose.hint");
            default -> {
            }
        }
    }

    private EditBox box(int x, int y, int w, int max, String hint) {
        EditBox box = new EditBox(font, x, y, w, 10, Component.translatable(hint));
        box.setBordered(false);
        box.setMaxLength(max);
        box.setTextColor(TEXT);
        box.setHint(Component.translatable(hint).withStyle(ChatFormatting.DARK_GRAY));
        box.setValue(keptText);
        box.setResponder(value -> keptText = value);
        addRenderableWidget(box);
        setInitialFocus(box);
        return box;
    }

    // ------------------------------------------------------------------ coordinates

    private double phoneX(double x) {
        return (x - originX) / scale;
    }

    private double phoneY(double y) {
        return (y - originY) / scale;
    }

    private void hot(float x0, float y0, float x1, float y1, Runnable action) {
        float top = Math.max(y0, clipTop);
        float bottom = Math.min(y1, clipBottom);
        if (bottom > top) {
            hots.add(new Hot(x0, top, x1, bottom, action));
        }
    }

    /** Clips drawing to phone-space rows {@code y0..y1} of the screen. */
    private void clip(GuiGraphics graphics, int y0, int y1) {
        clipTop = y0;
        clipBottom = y1;
        graphics.enableScissor((int) Math.floor(originX + PhoneArt.SX0 * scale), (int) Math.floor(originY + y0 * scale),
                (int) Math.ceil(originX + PhoneArt.SX1 * scale), (int) Math.ceil(originY + y1 * scale));
    }

    private void unclip(GuiGraphics graphics) {
        graphics.disableScissor();
        clipTop = PhoneArt.SY0;
        clipBottom = PhoneArt.SY1;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = phoneX(mouseX);
        double y = phoneY(mouseY);
        if (!viewer.isEmpty()) {
            viewer = "";
            return true;
        }
        if (Math.hypot(x - PhoneArt.HOME_X, y - PhoneArt.HOME_Y) <= PhoneArt.HOME_R) {
            UiSounds.click();
            back.clear();
            go(Page.HOME, "", false);
            return true;
        }
        for (int i = hots.size() - 1; i >= 0; i--) {
            if (hots.get(i).contains(x, y)) {
                UiSounds.click();
                hots.get(i).action().run();
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(phoneX(mouseX), phoneY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(phoneX(mouseX), phoneY(mouseY), button, dragX / scale, dragY / scale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll -= delta * 14;
        stickToBottom = false;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && input != null && input.isFocused()) {
            submit();
            return true;
        }
        boolean typing = input != null && input.isFocused();
        if (!typing && com.eternalrealm.client.ClientKeyMappings.OPEN_PHONE.matches(key, scanCode)) {
            onClose();
            return true;
        }
        if (!typing && key == GLFW.GLFW_KEY_BACKSPACE && page != Page.HOME) {
            goBack();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void submit() {
        switch (page) {
            case FRIENDS -> request(keptText.strip());
            case CHAT -> sendMessage();
            case POST -> comment();
            case COMPOSE -> post();
            default -> {
            }
        }
    }

    /** Destructive buttons ask twice: the first press arms them for three seconds. */
    private void confirm(String key, Runnable action) {
        long now = System.currentTimeMillis();
        if (armed.equals(key) && now - armedAt < 3000L) {
            armed = "";
            action.run();
        } else {
            armed = key;
            armedAt = now;
            ClientPhone.toast(Component.translatable("phone.eternalrealm.confirm"));
        }
    }

    // ------------------------------------------------------------------ actions

    private void friend(String op, String id) {
        JsonObject args = new JsonObject();
        args.addProperty("op", op);
        args.addProperty("id", id);
        ClientPhone.send("phone.friend", args);
    }

    private void request(String name) {
        if (name.isEmpty()) {
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("op", "request");
        args.addProperty("name", name);
        ClientPhone.send("phone.friend", args);
        keptText = "";
        if (input != null) {
            input.setValue("");
        }
    }

    /**
     * Sends text (and the attached photo, uploaded first if needed) with {@code action}, then runs {@code after}. If
     * the photo is refused nothing is sent and the text stays (a toast says why).
     */
    private void withPhoto(String action, JsonObject args, Runnable after) {
        if (sending) {
            return;
        }
        Camera.Shot shot = attached;
        if (shot == null) {
            ClientPhone.send(action, args);
            clearInput();
            after.run();
            return;
        }
        sending = true;
        Uploads.send(shot, id -> {
            args.addProperty("photo", id);
            ClientPhone.send(action, args);
            shot.used();
            attached = null;
            sending = false;
            clearInput();
            after.run();
        }, () -> sending = false);
    }

    private void clearInput() {
        keptText = "";
        if (input != null) {
            input.setValue("");
        }
    }

    private void sendMessage() {
        String text = keptText.strip();
        if (text.isEmpty() && attached == null) {
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("to", arg);
        args.addProperty("text", text);
        stickToBottom = true;
        withPhoto("phone.send", args, () -> stickToBottom = true);
    }

    private void post() {
        String text = keptText.strip();
        if (text.isEmpty() && attached == null) {
            ClientPhone.toast(Component.translatable("phone.eternalrealm.toast.empty"));
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("text", text);
        withPhoto("phone.post", args, () -> {
            if (page != Page.COMPOSE) {
                return; // the player went elsewhere while the photo was on its way
            }
            feed = "all";
            before = 0;
            back.clear();
            page = Page.FEED;
            arg = "";
            scroll = 0;
            rebuildWidgets();
        });
    }

    private void comment() {
        String text = keptText.strip();
        if (text.isEmpty()) {
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("id", parseLong(arg));
        args.addProperty("text", text);
        ClientPhone.send("phone.comment", args);
        clearInput();
    }

    private void like(long id) {
        JsonObject args = new JsonObject();
        args.addProperty("id", id);
        ClientPhone.send("phone.like", args);
    }

    private void deletePost(long id) {
        confirm("post" + id, () -> {
            JsonObject args = new JsonObject();
            args.addProperty("id", id);
            ClientPhone.send("phone.delete", args);
            if (page == Page.POST) {
                goBack();
            }
        });
    }

    private void uncomment(long post, long comment) {
        confirm("comment" + comment, () -> {
            JsonObject args = new JsonObject();
            args.addProperty("id", post);
            args.addProperty("comment", comment);
            ClientPhone.send("phone.uncomment", args);
        });
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hots.clear();
        double mx = phoneX(mouseX);
        double my = phoneY(mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(originX, originY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.fill(6, 8, PhoneArt.BODY_W + 4, PhoneArt.BODY_H + 5, 0x50000000);
        clip(graphics, PhoneArt.SY0, PhoneArt.SY1);
        if (page == Page.HOME) {
            PhoneArt.wallpaper(graphics);
        } else {
            graphics.fillGradient(PhoneArt.SX0, PhoneArt.SY0, PhoneArt.SX1, PhoneArt.SY1, 0xFF1A1430, 0xFF0C0A18);
        }
        statusBar(graphics);
        switch (page) {
            case HOME -> home(graphics);
            case FRIENDS -> friends(graphics, false);
            case PICK -> friends(graphics, true);
            case MESSAGES -> messages(graphics);
            case CHAT -> chat(graphics);
            case CAMERA -> camera(graphics);
            case FEED -> feed(graphics);
            case POST -> post(graphics);
            case COMPOSE -> compose(graphics);
            case ACTIVITY -> activity(graphics);
        }
        unclip(graphics);
        PhoneArt.body(graphics);
        if (Math.hypot(mx - PhoneArt.HOME_X, my - PhoneArt.HOME_Y) <= PhoneArt.HOME_R) {
            PhoneArt.homeGlow(graphics, 0.55F);
        }
        super.render(graphics, (int) Math.floor(mx), (int) Math.floor(my), partialTick);
        clip(graphics, PhoneArt.SY0, PhoneArt.SY1);
        toast(graphics);
        if (!viewer.isEmpty()) {
            photoViewer(graphics);
        }
        unclip(graphics);
        graphics.pose().popPose();
    }

    private void statusBar(GuiGraphics graphics) {
        graphics.fill(PhoneArt.SX0, PhoneArt.SY0, PhoneArt.SX1, TOP, 0x70000000);
        Minecraft minecraft = Minecraft.getInstance();
        long day = minecraft.level == null ? 0 : minecraft.level.getDayTime();
        long t = (day + 6000) % 24000;
        String clock = String.format(java.util.Locale.ROOT, "%s %d:%02d", t >= 6000 && t < 18000 ? "☀" : "☾", t / 1000,
                t % 1000 * 60 / 1000);
        small(graphics, Component.literal(clock), PhoneArt.SX0 + 4, PhoneArt.SY0 + 2, TEXT, 0.7F);
        // the crystal's charge: four bars
        for (int i = 0; i < 4; i++) {
            int h = 2 + i;
            graphics.fill(PhoneArt.SX1 - 18 + i * 3, TOP - 2 - h, PhoneArt.SX1 - 16 + i * 3, TOP - 2, 0xFF8FF0FF);
        }
    }

    private void header(GuiGraphics graphics, Component title, boolean withBack) {
        graphics.fill(PhoneArt.SX0, TOP, PhoneArt.SX1, CONTENT - 1, 0x60201838);
        graphics.fill(PhoneArt.SX0, CONTENT - 1, PhoneArt.SX1, CONTENT, 0x80A07C3A);
        if (withBack) {
            PhoneArt.icon(graphics, PhoneArt.Icon.BACK, PhoneArt.SX0 + 3, TOP + 2, GOLD);
            hot(PhoneArt.SX0, TOP, PhoneArt.SX0 + 16, CONTENT, this::goBack);
        }
        int w = font.width(title);
        graphics.drawString(font, title, (PhoneArt.SX0 + PhoneArt.SX1 - w) / 2, TOP + 3, TEXT, false);
    }

    /** A right-hand action in the header (an icon). */
    private void headerAction(GuiGraphics graphics, PhoneArt.Icon icon, Runnable action) {
        PhoneArt.icon(graphics, icon, PhoneArt.SX1 - 13, TOP + 2, GOLD);
        hot(PhoneArt.SX1 - 16, TOP, PhoneArt.SX1, CONTENT, action);
    }

    private void small(GuiGraphics graphics, Component text, float x, float y, int color, float size) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(size, size, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void small(GuiGraphics graphics, FormattedCharSequence text, float x, float y, int color, float size) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(size, size, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private List<FormattedCharSequence> wrap(Component text, int width, float size) {
        return font.split(text, Math.max(10, (int) (width / size)));
    }

    /** One line cut to {@code width} (with "…"). */
    private Component fit(String text, int width, float size) {
        int room = (int) (width / size);
        if (font.width(text) <= room) {
            return Component.literal(text);
        }
        return Component.literal(font.plainSubstrByWidth(text, room - font.width("…")) + "…");
    }

    private void button(GuiGraphics graphics, int x, int y, int w, int h, Component label, boolean enabled,
                        Runnable action, double mx, double my) {
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        PhoneArt.piece(graphics, enabled ? (hover ? PhoneArt.Piece.BUTTON_HOVER : PhoneArt.Piece.BUTTON)
                : PhoneArt.Piece.BUTTON_OFF, x, y, w, h);
        float size = font.width(label) > w - 6 ? 0.75F : 1.0F;
        float lw = font.width(label) * size;
        small(graphics, label, x + (w - lw) / 2.0F, y + (h - 8 * size) / 2.0F, enabled ? TEXT : MUTED, size);
        if (enabled) {
            hot(x, y, x + w, y + h, action);
        }
    }

    private void count(GuiGraphics graphics, int x, int y, int n) {
        if (n <= 0) {
            return;
        }
        PhoneArt.badge(graphics, x, y);
        String text = n > 9 ? "9+" : Integer.toString(n);
        small(graphics, Component.literal(text), x + 4.5F - font.width(text) * 0.3F, y + 2.2F, 0xFFFFFF, 0.6F);
    }

    private double mouseX() {
        Minecraft minecraft = Minecraft.getInstance();
        return phoneX(minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth());
    }

    private double mouseY() {
        Minecraft minecraft = Minecraft.getInstance();
        return phoneY(minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight());
    }

    /** The scrolling part of a page: from {@code top} to {@code bottom}; @return the first row's y */
    private int beginScroll(GuiGraphics graphics, int top, int bottom) {
        int view = bottom - top;
        double max = Math.max(0, contentHeight - view);
        if (stickToBottom && page == Page.CHAT) {
            scroll = max;
        }
        scroll = Math.max(0, Math.min(scroll, max));
        clip(graphics, top, bottom);
        return top - (int) scroll;
    }

    private void endScroll(GuiGraphics graphics, int top, int bottom, int endY) {
        contentHeight = endY + (int) scroll - top;
        unclip(graphics); // back to the whole screen (the outer clip is still on the stack)
        int view = bottom - top;
        if (contentHeight > view) {
            float bar = Math.max(10, view * view / (float) contentHeight);
            float at = (float) (top + (view - bar) * (scroll / Math.max(1, contentHeight - view)));
            graphics.fill(PhoneArt.SX1 - 3, (int) at, PhoneArt.SX1 - 1, (int) (at + bar), 0x90A07C3A);
        }
    }

    private void hint(GuiGraphics graphics, String key, int y) {
        for (FormattedCharSequence line : wrap(Component.translatable(key), 128, 0.8F)) {
            float w = font.width(line) * 0.8F;
            small(graphics, line, (PhoneArt.SX0 + PhoneArt.SX1 - w) / 2.0F, y, MUTED, 0.8F);
            y += 9;
        }
    }

    private void loading(GuiGraphics graphics, int y) {
        int dots = (int) (System.currentTimeMillis() / 300 % 4);
        Component text = Component.translatable("phone.eternalrealm.loading").append(".".repeat(dots));
        small(graphics, text, (PhoneArt.SX0 + PhoneArt.SX1) / 2.0F - 20, y, MUTED, 0.8F);
    }

    private void toast(GuiGraphics graphics) {
        Optional<Component> toast = ClientPhone.toast();
        if (toast.isEmpty()) {
            return;
        }
        List<FormattedCharSequence> lines = wrap(toast.get(), 124, 0.75F);
        int h = 6 + lines.size() * 7;
        int y = PhoneArt.SY1 - 26 - h;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        PhoneArt.piece(graphics, PhoneArt.Piece.CARD, PhoneArt.SX0 + 6, y, PhoneArt.SX1 - PhoneArt.SX0 - 12, h);
        int ty = y + 3;
        for (FormattedCharSequence line : lines) {
            float w = font.width(line) * 0.75F;
            small(graphics, line, (PhoneArt.SX0 + PhoneArt.SX1 - w) / 2.0F, ty, TEXT, 0.75F);
            ty += 7;
        }
        graphics.pose().popPose();
    }

    /** A photo from the server, fitted into the box (a placeholder while it comes, or when it is gone). */
    private void photo(GuiGraphics graphics, String id, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF05040A);
        Optional<PhotoCache.Photo> photo = PhotoCache.get(id);
        if (photo.isPresent() && photo.get().ready()) {
            PhotoArtBlit.draw(graphics, photo.get(), x, y, w, h);
        } else if (photo.isPresent() && photo.get().missing()) {
            PhoneArt.noPhoto(graphics, x + (w - 24) / 2, y + (h - 18) / 2 - 3);
            small(graphics, Component.translatable("phone.eternalrealm.photo_gone"), x + 3, y + h - 7, MUTED, 0.6F);
        } else {
            int dots = (int) (System.currentTimeMillis() / 250 % 4);
            small(graphics, Component.literal("◆".repeat(dots)), x + w / 2.0F - 6, y + h / 2.0F - 3, MUTED, 0.7F);
        }
    }

    private void shot(GuiGraphics graphics, Camera.Shot shot, int x, int y, int w, int h) {
        PhotoArtBlit.draw(graphics, shot.preview(), x, y, w, h);
    }

    private void photoViewer(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 350);
        graphics.fill(PhoneArt.SX0, PhoneArt.SY0, PhoneArt.SX1, PhoneArt.SY1, 0xE0000000);
        int w = PhoneArt.SX1 - PhoneArt.SX0 - 6;
        int h = w * 9 / 16;
        int x = PhoneArt.SX0 + 3;
        int y = (PhoneArt.SY0 + PhoneArt.SY1 - h) / 2;
        if (viewer.startsWith("s:")) {
            Camera.shot(Integer.parseInt(viewer.substring(2))).ifPresent(s -> shot(graphics, s, x, y, w, h));
        } else {
            photo(graphics, viewer, x, y, w, h);
        }
        small(graphics, Component.translatable("phone.eternalrealm.close_photo"), x, y + h + 4, MUTED, 0.7F);
        graphics.pose().popPose();
    }

    private static String ago(long time) {
        long diff = Math.max(0, System.currentTimeMillis() - time) / 1000;
        if (diff < 60) {
            return Component.translatable("phone.eternalrealm.time.now").getString();
        }
        if (diff < 3600) {
            return Component.translatable("phone.eternalrealm.time.minutes", diff / 60).getString();
        }
        if (diff < 86400) {
            return Component.translatable("phone.eternalrealm.time.hours", diff / 3600).getString();
        }
        if (diff < 7 * 86400) {
            return Component.translatable("phone.eternalrealm.time.days", diff / 86400).getString();
        }
        ZonedDateTime date = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
        return date.getMonthValue() + "/" + date.getDayOfMonth();
    }

    // ------------------------------------------------------------------ the apps

    private void home(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        long dayTime = minecraft.level == null ? 0 : minecraft.level.getDayTime();
        long t = (dayTime + 6000) % 24000;
        String clock = String.format(java.util.Locale.ROOT, "%d:%02d", t / 1000, t % 1000 * 60 / 1000);
        float size = 2.4F;
        small(graphics, Component.literal(clock), (PhoneArt.SX0 + PhoneArt.SX1) / 2.0F - font.width(clock) * size / 2,
                TOP + 8, 0xFFFFFF, size);
        Component date = Component.translatable("phone.eternalrealm.home.day", dayTime / 24000 + 1);
        small(graphics, date, (PhoneArt.SX0 + PhoneArt.SX1) / 2.0F - font.width(date) * 0.7F / 2, TOP + 30, TEXT, 0.7F);
        ClientPhone.view("home").ifPresent(view -> {
            Component friends = Component.translatable("phone.eternalrealm.home.friends",
                    ClientUiState.intOr(view, "friendsOnline", 0), ClientUiState.intOr(view, "friends", 0));
            small(graphics, friends, (PhoneArt.SX0 + PhoneArt.SX1) / 2.0F - font.width(friends) * 0.65F / 2, TOP + 39,
                    0xC8F0FF, 0.65F);
        });
        double mx = mouseX();
        double my = mouseY();
        int[][] spots = {{26, 112}, {70, 112}, {114, 112}, {48, 160}, {92, 160}};
        PhoneArt.App[] apps = PhoneArt.App.values();
        String[] labels = {"friends", "messages", "camera", "feed", "activity"};
        int[] badges = {ClientPhone.requests(), ClientPhone.unread(), 0, 0, ClientPhone.activity()};
        Page[] pages = {Page.FRIENDS, Page.MESSAGES, Page.CAMERA, Page.FEED, Page.ACTIVITY};
        for (int i = 0; i < apps.length; i++) {
            int x = spots[i][0];
            int y = spots[i][1];
            boolean hover = mx >= x && mx < x + PhoneArt.APP && my >= y && my < y + PhoneArt.APP;
            if (hover) {
                graphics.fill(x - 2, y - 2, x + PhoneArt.APP + 2, y + PhoneArt.APP + 2, 0x40FFFFFF);
            }
            PhoneArt.app(graphics, apps[i], x, y);
            count(graphics, x + PhoneArt.APP - 6, y - 3, badges[i]);
            Component label = Component.translatable("phone.eternalrealm.app." + labels[i]);
            float lw = font.width(label) * 0.65F;
            graphics.pose().pushPose();
            small(graphics, label, x + PhoneArt.APP / 2.0F - lw / 2 + 0.5F, y + PhoneArt.APP + 3.5F, 0x000000, 0.65F);
            small(graphics, label, x + PhoneArt.APP / 2.0F - lw / 2, y + PhoneArt.APP + 3, 0xFFFFFF, 0.65F);
            graphics.pose().popPose();
            Page target = pages[i];
            hot(x - 4, y - 4, x + PhoneArt.APP + 4, y + PhoneArt.APP + 10, () -> go(target, "", true));
        }
    }

    private void friends(GuiGraphics graphics, boolean pick) {
        header(graphics, Component.translatable(pick ? "phone.eternalrealm.pick.title" : "phone.eternalrealm.app.friends"),
                true);
        double mx = mouseX();
        double my = mouseY();
        int top = CONTENT + 2;
        if (!pick) {
            PhoneArt.piece(graphics, PhoneArt.Piece.INPUT, 14, CONTENT + 2, 98, 13);
            button(graphics, 115, CONTENT + 2, 40, 13, Component.translatable("phone.eternalrealm.friends.ask"),
                    !keptText.isBlank(), () -> request(keptText.strip()), mx, my);
            top = CONTENT + 19;
        }
        Optional<JsonObject> data = ClientPhone.view("friends");
        if (data.isEmpty()) {
            loading(graphics, top + 10);
            return;
        }
        JsonObject view = data.get();
        int y = beginScroll(graphics, top, PhoneArt.SY1);
        JsonArray incoming = array(view, "incoming");
        if (!pick && !incoming.isEmpty()) {
            y = section(graphics, "phone.eternalrealm.friends.incoming", incoming.size(), y);
            for (JsonElement e : incoming) {
                JsonObject person = e.getAsJsonObject();
                String id = str(person, "id");
                row(graphics, person, y, 0);
                PhoneArt.icon(graphics, PhoneArt.Icon.CHECK, 128, y + 3, 0x6AF08A);
                hot(126, y, 140, y + 16, () -> friend("accept", id));
                PhoneArt.icon(graphics, PhoneArt.Icon.CROSS, 143, y + 3, 0xFF6A7A);
                hot(141, y, 156, y + 16, () -> friend("decline", id));
                y += 16;
            }
            y += 4;
        }
        JsonArray friends = array(view, "friends");
        y = section(graphics, "phone.eternalrealm.friends.list", friends.size(), y);
        if (friends.isEmpty()) {
            hint(graphics, "phone.eternalrealm.friends.none", y + 2);
            y += 24;
        }
        for (JsonElement e : friends) {
            JsonObject person = e.getAsJsonObject();
            String id = str(person, "id");
            boolean hover = my >= y && my < y + 16 && mx < 124 && my >= clipTop && my < clipBottom;
            if (hover) {
                graphics.fill(PhoneArt.SX0 + 2, y, PhoneArt.SX1 - 2, y + 16, 0x30FFFFFF);
            }
            row(graphics, person, y, ClientUiState.intOr(person, "unread", 0));
            hot(PhoneArt.SX0, y, 124, y + 16, () -> go(Page.CHAT, id, true));
            if (!pick) {
                PhoneArt.icon(graphics, PhoneArt.Icon.SEND, 128, y + 3, GOLD);
                hot(126, y, 140, y + 16, () -> go(Page.CHAT, id, true));
                PhoneArt.icon(graphics, PhoneArt.Icon.TRASH, 143, y + 3, armed.equals("remove" + id) ? 0xFF4A5A
                        : MUTED);
                hot(141, y, 156, y + 16, () -> confirm("remove" + id, () -> friend("remove", id)));
            }
            y += 16;
        }
        if (!pick) {
            JsonArray asked = array(view, "asked");
            if (!asked.isEmpty()) {
                y = section(graphics, "phone.eternalrealm.friends.asked", asked.size(), y + 4);
                for (JsonElement e : asked) {
                    row(graphics, e.getAsJsonObject(), y, 0);
                    small(graphics, Component.translatable("phone.eternalrealm.friends.waiting"), 118, y + 5, MUTED,
                            0.6F);
                    y += 16;
                }
            }
            JsonArray blocked = array(view, "blocked");
            if (!blocked.isEmpty()) {
                y = section(graphics, "phone.eternalrealm.friends.blocked", blocked.size(), y + 4);
                for (JsonElement e : blocked) {
                    JsonObject person = e.getAsJsonObject();
                    String id = str(person, "id");
                    row(graphics, person, y, 0);
                    button(graphics, 120, y + 2, 34, 12, Component.translatable("phone.eternalrealm.friends.unblock"),
                            true, () -> friend("unblock", id), mx, my);
                    y += 16;
                }
            }
        }
        endScroll(graphics, top, PhoneArt.SY1, y + 4);
    }

    private int section(GuiGraphics graphics, String key, int count, int y) {
        small(graphics, Component.translatable(key, count), PhoneArt.SX0 + 4, y + 2, GOLD, 0.7F);
        graphics.fill(PhoneArt.SX0 + 4, y + 9, PhoneArt.SX1 - 4, y + 10, 0x40A07C3A);
        return y + 12;
    }

    /** A person's row: online dot, name, unread badge. */
    private void row(GuiGraphics graphics, JsonObject person, int y, int unread) {
        PhoneArt.dot(graphics, flag(person, "online"), PhoneArt.SX0 + 5, y + 5);
        graphics.drawString(font, fit(str(person, "name"), 90, 1.0F), PhoneArt.SX0 + 14, y + 4, TEXT, false);
        count(graphics, 112, y + 3, unread);
    }

    private void messages(GuiGraphics graphics) {
        header(graphics, Component.translatable("phone.eternalrealm.app.messages"), true);
        headerAction(graphics, PhoneArt.Icon.PLUS, () -> go(Page.PICK, "", true));
        Optional<JsonObject> data = ClientPhone.view("messages");
        if (data.isEmpty()) {
            loading(graphics, CONTENT + 12);
            return;
        }
        JsonArray list = array(data.get(), "conversations");
        int top = CONTENT + 2;
        int y = beginScroll(graphics, top, PhoneArt.SY1);
        if (list.isEmpty()) {
            hint(graphics, "phone.eternalrealm.messages.none", y + 10);
            y += 40;
        }
        double mx = mouseX();
        double my = mouseY();
        for (JsonElement e : list) {
            JsonObject c = e.getAsJsonObject();
            String id = str(c, "id");
            boolean hover = mx >= PhoneArt.SX0 + 2 && mx < PhoneArt.SX1 - 2 && my >= y && my < y + 26
                    && my >= clipTop && my < clipBottom;
            PhoneArt.piece(graphics, PhoneArt.Piece.CARD, PhoneArt.SX0 + 3, y + 1, PhoneArt.SX1 - PhoneArt.SX0 - 6, 24);
            if (hover) {
                graphics.fill(PhoneArt.SX0 + 5, y + 3, PhoneArt.SX1 - 5, y + 23, 0x20FFFFFF);
            }
            PhoneArt.dot(graphics, flag(c, "online"), PhoneArt.SX0 + 7, y + 6);
            graphics.drawString(font, fit(str(c, "name"), 80, 1.0F), PhoneArt.SX0 + 15, y + 4, TEXT, false);
            String when = ago(ClientUiState.longOr(c, "time", 0));
            small(graphics, Component.literal(when), PhoneArt.SX1 - 8 - font.width(when) * 0.6F, y + 5, MUTED, 0.6F);
            String preview = (flag(c, "mine") ? Component.translatable("phone.eternalrealm.messages.you").getString()
                    : "") + (flag(c, "photo") && str(c, "text").isEmpty()
                    ? Component.translatable("phone.eternalrealm.photo").getString() : str(c, "text"));
            small(graphics, fit(preview, 110, 0.7F), PhoneArt.SX0 + 15, y + 15, MUTED, 0.7F);
            count(graphics, PhoneArt.SX1 - 16, y + 13, ClientUiState.intOr(c, "unread", 0));
            hot(PhoneArt.SX0 + 3, y + 1, PhoneArt.SX1 - 3, y + 25, () -> go(Page.CHAT, id, true));
            y += 27;
        }
        endScroll(graphics, top, PhoneArt.SY1, y + 2);
    }

    private void chat(GuiGraphics graphics) {
        Optional<JsonObject> data = ClientPhone.view("chat")
                .filter(view -> arg.equals(str(view.getAsJsonObject("with"), "id")));
        JsonObject with = data.map(view -> view.getAsJsonObject("with")).orElse(new JsonObject());
        header(graphics, Component.literal(data.isPresent() ? str(with, "name") : "…"), true);
        double mx = mouseX();
        double my = mouseY();
        if (data.isPresent() && flag(with, "friend")) {
            String id = arg;
            Component block = Component.translatable("phone.eternalrealm.chat.block");
            float bw = font.width(block) * 0.6F;
            small(graphics, block, PhoneArt.SX1 - 4 - bw, TOP + 4, armed.equals("block" + id) ? 0xFF4A5A : MUTED, 0.6F);
            hot(PhoneArt.SX1 - 6 - bw, TOP, PhoneArt.SX1, CONTENT, () -> confirm("block" + id, () -> {
                friend("block", id);
                goBack();
            }));
        }
        int bottom = PhoneArt.SY1 - 17;
        if (data.isEmpty()) {
            loading(graphics, CONTENT + 12);
        } else {
            int y = beginScroll(graphics, CONTENT + 2, bottom);
            JsonArray messages = array(data.get(), "messages");
            if (messages.isEmpty()) {
                hint(graphics, "phone.eternalrealm.chat.empty", y + 20);
                y += 50;
            }
            for (JsonElement e : messages) {
                y = bubble(graphics, e.getAsJsonObject(), y) + 3;
            }
            endScroll(graphics, CONTENT + 2, bottom, y);
        }
        boolean friend = data.isPresent() && flag(with, "friend");
        footer(graphics, friend, mx, my);
        if (data.isPresent() && !friend) {
            graphics.fill(PhoneArt.SX0, PhoneArt.SY1 - 17, PhoneArt.SX1, PhoneArt.SY1, 0xE0100C1C);
            small(graphics, Component.translatable("phone.eternalrealm.chat.not_friends"), PhoneArt.SX0 + 6,
                    PhoneArt.SY1 - 12, MUTED, 0.7F);
        }
        if (input != null) {
            input.visible = friend;
        }
    }

    /** One message as a bubble (mine on the right in gold, theirs on the left in blue); @return its bottom */
    private int bubble(GuiGraphics graphics, JsonObject message, int y) {
        boolean mine = flag(message, "mine");
        String text = str(message, "text");
        String photo = str(message, "photo");
        int maxW = 104;
        List<FormattedCharSequence> lines = text.isEmpty() ? List.of() : wrap(Component.literal(text), maxW - 8, 0.8F);
        int textW = 0;
        for (FormattedCharSequence line : lines) {
            textW = Math.max(textW, (int) Math.ceil(font.width(line) * 0.8F));
        }
        int w = Math.max(photo.isEmpty() ? 0 : 84, textW + 8);
        int h = 6 + lines.size() * 8 + (photo.isEmpty() ? 0 : 48 + (lines.isEmpty() ? 0 : 3));
        int x = mine ? PhoneArt.SX1 - 6 - w : PhoneArt.SX0 + 6;
        PhoneArt.piece(graphics, mine ? PhoneArt.Piece.BUBBLE_MINE : PhoneArt.Piece.BUBBLE_THEIRS, x, y, w, h);
        int ty = y + 3;
        if (!photo.isEmpty()) {
            photo(graphics, photo, x + 4, ty, w - 8, 45);
            hot(x + 4, ty, x + w - 4, ty + 45, () -> viewer = photo);
            ty += 48;
        }
        for (FormattedCharSequence line : lines) {
            small(graphics, line, x + 4, ty, mine ? DARK : TEXT, 0.8F);
            ty += 8;
        }
        String when = ago(ClientUiState.longOr(message, "time", 0));
        float ww = font.width(when) * 0.5F;
        small(graphics, Component.literal(when), mine ? x - 2 - ww : x + w + 2, y + h - 5, MUTED, 0.5F);
        return y + h;
    }

    /** The typing line with the photo and send buttons (chat and post pages). */
    private void footer(GuiGraphics graphics, boolean enabled, double mx, double my) {
        int y = PhoneArt.SY1 - 16;
        graphics.fill(PhoneArt.SX0, y - 1, PhoneArt.SX1, PhoneArt.SY1, 0xD0120E22);
        boolean chat = page == Page.CHAT;
        int inputW = chat ? 106 : 118;
        PhoneArt.piece(graphics, PhoneArt.Piece.INPUT, 12, y + 1, inputW, 13);
        if (!enabled) {
            return;
        }
        if (chat) {
            PhoneArt.icon(graphics, PhoneArt.Icon.PHOTO, 122, y + 3, attached != null ? GOLD : 0xC8C0E0);
            hot(120, y, 134, PhoneArt.SY1, () -> picking = !picking);
        }
        PhoneArt.icon(graphics, PhoneArt.Icon.SEND, 142, y + 3, sending ? MUTED : GOLD);
        hot(138, y, PhoneArt.SX1, PhoneArt.SY1, this::submit);
        if (chat && (picking || attached != null)) {
            attachStrip(graphics, y - 26, mx, my);
        }
    }

    /** The photos taken (choose one to attach), or the attached one with a cross. */
    private void attachStrip(GuiGraphics graphics, int y, double mx, double my) {
        PhoneArt.piece(graphics, PhoneArt.Piece.CARD, PhoneArt.SX0 + 3, y, PhoneArt.SX1 - PhoneArt.SX0 - 6, 24);
        if (attached != null && !picking) {
            shot(graphics, attached, PhoneArt.SX0 + 6, y + 3, 32, 18);
            small(graphics, Component.translatable("phone.eternalrealm.attached"), PhoneArt.SX0 + 42, y + 9, TEXT, 0.7F);
            PhoneArt.icon(graphics, PhoneArt.Icon.CROSS, PhoneArt.SX1 - 18, y + 7, 0xFF6A7A);
            hot(PhoneArt.SX1 - 20, y, PhoneArt.SX1 - 4, y + 24, () -> attached = null);
            return;
        }
        List<Camera.Shot> shots = Camera.shots();
        if (shots.isEmpty()) {
            small(graphics, Component.translatable("phone.eternalrealm.no_shots"), PhoneArt.SX0 + 8, y + 9, MUTED, 0.7F);
            return;
        }
        int x = PhoneArt.SX0 + 6;
        for (Camera.Shot shot : shots.subList(0, Math.min(4, shots.size()))) {
            shot(graphics, shot, x, y + 3, 32, 18);
            hot(x, y + 3, x + 32, y + 21, () -> {
                attached = shot;
                picking = false;
            });
            x += 34;
        }
    }

    private void camera(GuiGraphics graphics) {
        header(graphics, Component.translatable("phone.eternalrealm.app.camera"), true);
        double mx = mouseX();
        double my = mouseY();
        List<Camera.Shot> shots = Camera.shots();
        Optional<Camera.Shot> selected = Camera.shot(selectedShot);
        if (selected.isEmpty() && !shots.isEmpty()) {
            selected = Optional.of(shots.get(0));
            selectedShot = shots.get(0).number();
        }
        int px = PhoneArt.SX0 + 6;
        int py = CONTENT + 4;
        int pw = PhoneArt.SX1 - PhoneArt.SX0 - 12;
        int ph = pw * 9 / 16;
        graphics.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFFA07C3A);
        graphics.fill(px, py, px + pw, py + ph, 0xFF05040A);
        if (selected.isPresent()) {
            Camera.Shot shot = selected.get();
            shot(graphics, shot, px, py, pw, ph);
            hot(px, py, px + pw, py + ph, () -> viewer = "s:" + shot.number());
        } else {
            PhoneArt.noPhoto(graphics, px + (pw - 24) / 2, py + ph / 2 - 14);
            hint(graphics, "phone.eternalrealm.camera.none", py + ph / 2 + 8);
        }
        int y = py + ph + 6;
        button(graphics, 38, y, 92, 16, Component.translatable("phone.eternalrealm.camera.take"), !Camera.busy(),
                () -> Camera.take(hideHud, saveCopy), mx, my);
        y += 20;
        y = toggle(graphics, y, "phone.eternalrealm.camera.hide_hud", hideHud, () -> hideHud = !hideHud);
        y = toggle(graphics, y, "phone.eternalrealm.camera.save", saveCopy, () -> saveCopy = !saveCopy);
        y += 2;
        int x = PhoneArt.SX0 + 6;
        int row = 0;
        for (Camera.Shot shot : shots) {
            int tx = x + (row % 4) * 34;
            int ty = y + (row / 4) * 20;
            if (shot.number() == selectedShot) {
                graphics.fill(tx - 1, ty - 1, tx + 33, ty + 19, 0xFFF0CC6A);
            }
            shot(graphics, shot, tx, ty, 32, 18);
            hot(tx, ty, tx + 32, ty + 18, () -> selectedShot = shot.number());
            row++;
        }
        boolean has = selected.isPresent();
        Camera.Shot chosen = selected.orElse(null);
        int by = PhoneArt.SY1 - 18;
        button(graphics, PhoneArt.SX0 + 6, by, 66, 14, Component.translatable("phone.eternalrealm.camera.post"), has,
                () -> {
                    go(Page.COMPOSE, "", true);
                    attached = chosen;
                }, mx, my);
        button(graphics, PhoneArt.SX1 - 72, by, 66, 14, Component.translatable("phone.eternalrealm.camera.send"), has,
                () -> {
                    go(Page.PICK, "", true);
                    attached = chosen;
                }, mx, my);
    }

    private int toggle(GuiGraphics graphics, int y, String key, boolean on, Runnable action) {
        int x = PhoneArt.SX0 + 8;
        graphics.fill(x, y + 1, x + 7, y + 8, 0xFFA07C3A);
        graphics.fill(x + 1, y + 2, x + 6, y + 7, on ? 0xFF8FF0FF : 0xFF0C0A18);
        small(graphics, Component.translatable(key), x + 11, y + 2, TEXT, 0.7F);
        hot(x - 2, y, PhoneArt.SX1 - 6, y + 10, action);
        return y + 11;
    }

    private void feed(GuiGraphics graphics) {
        header(graphics, Component.translatable("phone.eternalrealm.app.feed"), true);
        headerAction(graphics, PhoneArt.Icon.PLUS, () -> go(Page.COMPOSE, "", true));
        double mx = mouseX();
        double my = mouseY();
        String[] tabs = {"all", "friends", "mine"};
        for (int i = 0; i < tabs.length; i++) {
            int x = PhoneArt.SX0 + 4 + i * 47;
            boolean on = tabs[i].equals(feed);
            PhoneArt.piece(graphics, on ? PhoneArt.Piece.TAB : PhoneArt.Piece.TAB_OFF, x, CONTENT + 2, 45, 12);
            Component label = Component.translatable("phone.eternalrealm.feed." + tabs[i]);
            small(graphics, label, x + 22.5F - font.width(label) * 0.35F, CONTENT + 4.5F, on ? DARK : TEXT, 0.7F);
            String tab = tabs[i];
            hot(x, CONTENT + 2, x + 45, CONTENT + 14, () -> {
                feed = tab;
                before = 0;
                scroll = 0;
                request();
            });
        }
        int top = CONTENT + 17;
        Optional<JsonObject> data = ClientPhone.view("feed")
                .filter(view -> feed.equals(str(view, "feed")) && ClientUiState.longOr(view, "before", -1) == before);
        if (data.isEmpty()) {
            loading(graphics, top + 10);
            return;
        }
        JsonArray posts = array(data.get(), "posts");
        int y = beginScroll(graphics, top, PhoneArt.SY1);
        if (before > 0) {
            button(graphics, 44, y + 2, 80, 12, Component.translatable("phone.eternalrealm.feed.newest"), true, () -> {
                before = 0;
                scroll = 0;
                request();
            }, mx, my);
            y += 16;
        }
        if (posts.isEmpty()) {
            hint(graphics, "phone.eternalrealm.feed.none", y + 14);
            y += 44;
        }
        long last = 0;
        for (JsonElement e : posts) {
            JsonObject post = e.getAsJsonObject();
            y = card(graphics, post, y, true) + 4;
            last = ClientUiState.longOr(post, "id", 0);
        }
        if (flag(data.get(), "more") && last > 0) {
            long older = last;
            button(graphics, 44, y, 80, 13, Component.translatable("phone.eternalrealm.feed.more"), true, () -> {
                before = older;
                scroll = 0;
                request();
            }, mx, my);
            y += 17;
        }
        endScroll(graphics, top, PhoneArt.SY1, y);
    }

    /** A post: who, when, the text, the photo, likes, comments; @return its bottom */
    private int card(GuiGraphics graphics, JsonObject post, int y, boolean openable) {
        long id = ClientUiState.longOr(post, "id", 0);
        JsonObject author = post.getAsJsonObject("author");
        String text = str(post, "text");
        String photo = str(post, "photo");
        int x = PhoneArt.SX0 + 3;
        int w = PhoneArt.SX1 - PhoneArt.SX0 - 6;
        List<FormattedCharSequence> lines = text.isEmpty() ? List.of() : wrap(Component.literal(text), w - 10, 0.8F);
        int photoH = photo.isEmpty() ? 0 : (w - 8) * 9 / 16;
        int h = 14 + lines.size() * 8 + (photoH > 0 ? photoH + 3 : 0) + 14;
        PhoneArt.piece(graphics, PhoneArt.Piece.CARD, x, y, w, h);
        PhoneArt.dot(graphics, flag(author, "online"), x + 4, y + 5);
        small(graphics, fit(str(author, "name"), 80, 0.85F), x + 12, y + 4, GOLD, 0.85F);
        String when = ago(ClientUiState.longOr(post, "time", 0));
        small(graphics, Component.literal(when), x + w - 5 - font.width(when) * 0.6F, y + 5, MUTED, 0.6F);
        int ty = y + 14;
        for (FormattedCharSequence line : lines) {
            small(graphics, line, x + 5, ty, TEXT, 0.8F);
            ty += 8;
        }
        if (openable) {
            hot(x, y, x + w, ty, () -> go(Page.POST, Long.toString(id), true));
        }
        if (photoH > 0) {
            photo(graphics, photo, x + 4, ty + 1, w - 8, photoH);
            hot(x + 4, ty + 1, x + w - 4, ty + 1 + photoH, () -> viewer = photo);
            ty += photoH + 3;
        }
        int fy = ty + 2;
        boolean liked = flag(post, "liked");
        PhoneArt.heart(graphics, liked, x + 5, fy);
        small(graphics, Component.literal(Integer.toString(ClientUiState.intOr(post, "likes", 0))), x + 17, fy + 2,
                liked ? 0xFF8AA0 : MUTED, 0.75F);
        hot(x + 3, fy - 1, x + 30, fy + 11, () -> like(id));
        PhoneArt.icon(graphics, PhoneArt.Icon.COMMENT, x + 36, fy, 0xC8C0E0);
        small(graphics, Component.literal(Integer.toString(ClientUiState.intOr(post, "comments", 0))), x + 48, fy + 2,
                MUTED, 0.75F);
        if (openable) {
            hot(x + 34, fy - 1, x + 62, fy + 11, () -> go(Page.POST, Long.toString(id), true));
        }
        if (flag(post, "delete")) {
            PhoneArt.icon(graphics, PhoneArt.Icon.TRASH, x + w - 15, fy, armed.equals("post" + id) ? 0xFF4A5A : MUTED);
            hot(x + w - 18, fy - 1, x + w - 2, fy + 11, () -> deletePost(id));
        }
        return y + h;
    }

    private void post(GuiGraphics graphics) {
        header(graphics, Component.translatable("phone.eternalrealm.post.title"), true);
        double mx = mouseX();
        double my = mouseY();
        long id = parseLong(arg);
        Optional<JsonObject> data = ClientPhone.view("post").filter(view -> (view.has("post")
                && ClientUiState.longOr(view.getAsJsonObject("post"), "id", -1) == id)
                || ClientUiState.longOr(view, "gone", -1) == id);
        int bottom = PhoneArt.SY1 - 17;
        if (data.isEmpty()) {
            loading(graphics, CONTENT + 12);
            return;
        }
        if (!data.get().has("post")) {
            hint(graphics, "phone.eternalrealm.post.gone", CONTENT + 20);
            if (input != null) {
                input.visible = false;
            }
            return;
        }
        JsonObject post = data.get().getAsJsonObject("post");
        int y = beginScroll(graphics, CONTENT + 2, bottom);
        y = card(graphics, post, y, false) + 4;
        JsonArray comments = array(post, "commentList");
        y = section(graphics, "phone.eternalrealm.post.comments", comments.size(), y);
        for (JsonElement e : comments) {
            JsonObject comment = e.getAsJsonObject();
            long commentId = ClientUiState.longOr(comment, "id", 0);
            JsonObject author = comment.getAsJsonObject("author");
            small(graphics, fit(str(author, "name"), 80, 0.75F), PhoneArt.SX0 + 6, y + 1, GOLD, 0.75F);
            String when = ago(ClientUiState.longOr(comment, "time", 0));
            small(graphics, Component.literal(when), PhoneArt.SX0 + 12 + Math.min(80, font.width(str(author, "name"))
                    * 0.75F), y + 1.5F, MUTED, 0.55F);
            if (flag(comment, "delete")) {
                PhoneArt.icon(graphics, PhoneArt.Icon.TRASH, PhoneArt.SX1 - 15, y, armed.equals("comment" + commentId)
                        ? 0xFF4A5A : MUTED);
                hot(PhoneArt.SX1 - 18, y - 1, PhoneArt.SX1 - 2, y + 10, () -> uncomment(id, commentId));
            }
            int ty = y + 9;
            for (FormattedCharSequence line : wrap(Component.literal(str(comment, "text")), 124, 0.75F)) {
                small(graphics, line, PhoneArt.SX0 + 8, ty, TEXT, 0.75F);
                ty += 7;
            }
            y = ty + 4;
        }
        if (comments.isEmpty()) {
            hint(graphics, "phone.eternalrealm.post.no_comments", y + 4);
            y += 20;
        }
        endScroll(graphics, CONTENT + 2, bottom, y);
        footer(graphics, true, mx, my);
        if (input != null) {
            input.visible = true;
        }
    }

    private void compose(GuiGraphics graphics) {
        header(graphics, Component.translatable("phone.eternalrealm.compose.title"), true);
        double mx = mouseX();
        double my = mouseY();
        int x = PhoneArt.SX0 + 3;
        int w = PhoneArt.SX1 - PhoneArt.SX0 - 6;
        PhoneArt.piece(graphics, PhoneArt.Piece.CARD, x, CONTENT + 2, w, 74);
        String text = keptText;
        if (text.isBlank()) {
            small(graphics, Component.translatable("phone.eternalrealm.compose.preview"), x + 5, CONTENT + 6, MUTED, 0.8F);
        } else {
            int ty = CONTENT + 6;
            for (FormattedCharSequence line : wrap(Component.literal(text), w - 10, 0.8F)) {
                if (ty > CONTENT + 68) {
                    break;
                }
                small(graphics, line, x + 5, ty, TEXT, 0.8F);
                ty += 8;
            }
        }
        String counter = text.codePointCount(0, text.length()) + "/" + PhoneText.POST_MAX;
        small(graphics, Component.literal(counter), x + w - 4 - font.width(counter) * 0.6F, CONTENT + 69, MUTED, 0.6F);
        PhoneArt.piece(graphics, PhoneArt.Piece.INPUT, 14, 144, 140, 13);
        int y = 162;
        small(graphics, Component.translatable("phone.eternalrealm.compose.photo"), PhoneArt.SX0 + 6, y, GOLD, 0.7F);
        y += 9;
        if (attached != null) {
            shot(graphics, attached, PhoneArt.SX0 + 6, y, 48, 27);
            PhoneArt.icon(graphics, PhoneArt.Icon.CROSS, PhoneArt.SX0 + 58, y + 8, 0xFF6A7A);
            Camera.Shot shot = attached;
            hot(PhoneArt.SX0 + 6, y, PhoneArt.SX0 + 54, y + 27, () -> viewer = "s:" + shot.number());
            hot(PhoneArt.SX0 + 56, y + 6, PhoneArt.SX0 + 70, y + 20, () -> attached = null);
        } else {
            List<Camera.Shot> shots = Camera.shots();
            if (shots.isEmpty()) {
                small(graphics, Component.translatable("phone.eternalrealm.no_shots"), PhoneArt.SX0 + 6, y + 2, MUTED,
                        0.7F);
                button(graphics, PhoneArt.SX0 + 6, y + 11, 60, 13, Component.translatable("phone.eternalrealm.app.camera"),
                        true, () -> go(Page.CAMERA, "", true), mx, my);
            } else {
                int tx = PhoneArt.SX0 + 6;
                for (Camera.Shot shot : shots.subList(0, Math.min(4, shots.size()))) {
                    shot(graphics, shot, tx, y, 32, 18);
                    hot(tx, y, tx + 32, y + 18, () -> attached = shot);
                    tx += 34;
                }
            }
        }
        boolean can = (!text.isBlank() || attached != null) && !sending;
        button(graphics, 38, PhoneArt.SY1 - 20, 92, 15, Component.translatable("phone.eternalrealm.compose.post"), can,
                this::post, mx, my);
    }

    private void activity(GuiGraphics graphics) {
        header(graphics, Component.translatable("phone.eternalrealm.app.activity"), true);
        Optional<JsonObject> data = ClientPhone.view("activity");
        if (data.isEmpty()) {
            loading(graphics, CONTENT + 12);
            return;
        }
        JsonArray items = array(data.get(), "items");
        int top = CONTENT + 2;
        int y = beginScroll(graphics, top, PhoneArt.SY1);
        if (items.isEmpty()) {
            hint(graphics, "phone.eternalrealm.activity.none", y + 14);
            y += 40;
        }
        for (JsonElement e : items) {
            JsonObject item = e.getAsJsonObject();
            boolean like = "like".equals(str(item, "kind"));
            long post = ClientUiState.longOr(item, "post", 0);
            PhoneArt.piece(graphics, PhoneArt.Piece.CARD, PhoneArt.SX0 + 3, y, PhoneArt.SX1 - PhoneArt.SX0 - 6, 20);
            if (like) {
                PhoneArt.heart(graphics, true, PhoneArt.SX0 + 7, y + 5);
            } else {
                PhoneArt.icon(graphics, PhoneArt.Icon.COMMENT, PhoneArt.SX0 + 7, y + 5, 0x8FF0FF);
            }
            Component line = Component.translatable(like ? "phone.eternalrealm.activity.like"
                    : "phone.eternalrealm.activity.comment", str(item, "name"));
            small(graphics, Component.literal(fit(line.getString(), 104, 0.7F).getString()), PhoneArt.SX0 + 21, y + 4,
                    TEXT, 0.7F);
            small(graphics, Component.literal(ago(ClientUiState.longOr(item, "time", 0))), PhoneArt.SX0 + 21, y + 12,
                    MUTED, 0.55F);
            hot(PhoneArt.SX0 + 3, y, PhoneArt.SX1 - 3, y + 20, () -> go(Page.POST, Long.toString(post), true));
            y += 22;
        }
        endScroll(graphics, top, PhoneArt.SY1, y);
    }

    // ------------------------------------------------------------------ json

    private static String str(JsonObject object, String key) {
        return object == null ? "" : ClientUiState.stringOr(object, key, "");
    }

    private static boolean flag(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isBoolean() && object.get(key).getAsBoolean();
    }

    private static JsonArray array(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key)
                : new JsonArray();
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
