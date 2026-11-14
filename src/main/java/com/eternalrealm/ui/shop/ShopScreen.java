package com.eternalrealm.ui.shop;

import com.eternalrealm.client.ClientBagBack;
import com.eternalrealm.client.ClientChannels;
import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.ShopCatalog;
import com.eternalrealm.item.bag.Bag;
import com.eternalrealm.item.forge.ShopService;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.eternalrealm.ui.gate.GateArt;
import com.eternalrealm.ui.map.MapDraw;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The item shop (道具屋マルコの店; owner, 2026-11-14: 「アイテムショップのHUDをファンタジー風にオシャレにして」): a
 * lamplit panel in the gold frame of the gate's screens with the shop's red ribbon. Two tabs: 「買う」 shows the
 * shelves (回復薬・旅の道具・秘薬・鍛冶の素材) with prices and how many one has, 「売る」 what the shop would buy from the
 * front and the back of the bag. The chosen thing is shown large on the right with what it does, the amount (− / + and
 * 1・5・10・最大), the total and the button; a piece of equipment is sold only with a second press. The gold rises and
 * falls with a coin's glint, the shopkeeper thanks. The server ({@link ShopService}) decides everything.
 */
public final class ShopScreen extends Screen {
    private static final int W = 448;
    private static final int H = 262;
    private static final int RIBBON_TOP = -9;
    private static final int TAB_Y = 46;
    private static final int TAB_STEP = 80;
    private static final int LIST_X = 14;
    private static final int LIST_Y = 72;
    private static final int LIST_W = 222;
    private static final int LIST_BOTTOM = H - 22;
    private static final int ROW_H = 20;
    private static final int HEAD_H = 14;
    private static final int PANE_X = 248;
    private static final int PANE_Y = 46;
    private static final int PANE_W = W - PANE_X - 14;
    private static final int PANE_BOTTOM = H - 22;
    private static final int AMOUNT_Y = PANE_Y + 134;
    private static final int TOTAL_Y = PANE_Y + 154;
    private static final int BUTTON_Y = PANE_Y + 166;
    private static final int GOLD_W = 132;

    private static final int TEXT = GateArt.TEXT;
    private static final int MUTED = GateArt.MUTED;
    private static final int GOLD = GateArt.GOLD;
    private static final int GOLD_DIM = GateArt.GOLD_DIM;
    private static final int WARN = GateArt.WARN;
    private static final int GOOD = GateArt.READY;
    /** The shop's panel: lamplight on dark wood. */
    private static final int PANEL_TOP = 0xF42C1E14;
    private static final int PANEL_BOTTOM = 0xF4120B08;
    private static final int TWINKLE = 0xFFE0A8;
    private static final ResourceLocation NPC_ICONS = new ResourceLocation(ModInfo.MOD_ID,
            com.eternalrealm.world.map.NpcMarkers.ATLAS);

    private static final long MESSAGE_MS = 4500;
    private static final long PENDING_MS = 4000;
    private static final long CONFIRM_MS = 3000;
    private static final long FLOAT_MS = 1400;
    private static final int GREETINGS = 4;
    private static final int THANKS = 3;

    private enum Tab {
        BUY, SELL
    }

    private record Ware(String id, ShopCatalog.Section section, long price, int level, int owned, ItemStack stack) {
    }

    private record Sellable(int slot, String id, int count, long each, ShopCatalog.SellKind kind, String uuid,
                            int rarity) {
        String key() {
            return slot + "|" + id + "|" + uuid;
        }
    }

    /** A row of the list: a shelf's heading, a ware or a thing to sell. */
    private record Row(ShopCatalog.Section heading, Ware ware, Sellable sellable) {
        int height() {
            return heading != null ? HEAD_H : ROW_H;
        }
    }

    /** Gold flying up from the purse after a purchase or a sale. */
    private record Floater(long amount, long at) {
    }

    private final long openedAt = Util.getMillis();
    private final List<Ware> wares = new ArrayList<>();
    private final List<Sellable> sellables = new ArrayList<>();
    private final List<Floater> floaters = new ArrayList<>();
    private Tab tab = Tab.BUY;
    private long gold;
    private int level = 1;
    private String chosenWare;
    private String chosenSell;
    private int amount = 1;
    private int scroll;
    private boolean pending;
    private long pendingAt;
    private long confirmUntil;
    private Component message;
    private boolean messageOk;
    private long messageAt;
    private Component line;
    private float scale = 1.0F;
    private float left;
    private float top;

    private ShopScreen(JsonObject data) {
        super(Component.translatable("screen.eternalrealm.shop.title"));
        int greeting = 1 + (int) Math.floorMod(Util.getMillis() / 1000L, GREETINGS);
        line = Component.translatable("screen.eternalrealm.shop.greet." + greeting);
        accept(data);
    }

    /** Hooks the shop's channels on the client. */
    public static void registerChannels() {
        ClientChannels.register(ShopService.CHANNEL, ShopScreen::open);
        ClientChannels.register(ShopService.RESULT_CHANNEL, ShopScreen::onResult);
    }

    /** The shop arrived ({@link ShopService#CHANNEL}): opens it, or refreshes the open one. */
    private static void open(JsonObject data) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ShopScreen screen) {
            screen.accept(data);
        } else if (!bool(data, "refresh")) {
            minecraft.setScreen(new ShopScreen(data));
        }
    }

    /** The answer to a purchase or a sale ({@link ShopService#RESULT_CHANNEL}). */
    private static void onResult(JsonObject data) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean ok = bool(data, "ok");
        Component text = parse(ClientUiState.stringOr(data, "message", ""));
        if (!(minecraft.screen instanceof ShopScreen screen)) {
            if (text != null) {
                minecraft.gui.setOverlayMessage(text, false);
            }
            return;
        }
        screen.pending = false;
        screen.confirmUntil = 0L;
        screen.message = text;
        screen.messageOk = ok;
        screen.messageAt = Util.getMillis();
        if (ok) {
            long delta = ClientUiState.longOr(data, "gold", 0L);
            if (delta != 0L) {
                screen.floaters.add(new Floater(delta, Util.getMillis()));
            }
            int thanks = 1 + (int) Math.floorMod(Util.getMillis() / 700L, THANKS);
            screen.line = Component.translatable("screen.eternalrealm.shop."
                    + ("sell".equals(ClientUiState.stringOr(data, "kind", "")) ? "bought_back." : "thanks.") + thanks);
            play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F);
        } else {
            play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F);
        }
    }

    private static Component parse(String json) {
        if (json.isEmpty()) {
            return null;
        }
        try {
            return Component.Serializer.fromJson(json);
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    private static void play(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }

    private void accept(JsonObject data) {
        gold = Math.max(0L, ClientUiState.longOr(data, "gold", 0L));
        level = Math.max(1, ClientUiState.intOr(data, "level", 1));
        wares.clear();
        if (data.get("wares") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (!(element instanceof JsonObject row)) {
                    continue;
                }
                String id = ClientUiState.stringOr(row, "id", "");
                Item item = item(id);
                ShopCatalog.Section section;
                try {
                    section = ShopCatalog.Section.valueOf(ClientUiState.stringOr(row, "section", "POTIONS"));
                } catch (IllegalArgumentException e) {
                    section = ShopCatalog.Section.POTIONS;
                }
                if (item == null || item == Items.AIR) {
                    continue;
                }
                wares.add(new Ware(id, section, Math.max(0L, ClientUiState.longOr(row, "price", 0L)),
                        ClientUiState.intOr(row, "level", 1), ClientUiState.intOr(row, "owned", 0), new ItemStack(item)));
            }
        }
        sellables.clear();
        if (data.get("sell") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (!(element instanceof JsonObject row)) {
                    continue;
                }
                int slot = ClientUiState.intOr(row, "slot", -1);
                String id = ClientUiState.stringOr(row, "id", "");
                Optional<ShopCatalog.SellKind> kind = ShopCatalog.SellKind.byId(ClientUiState.stringOr(row, "kind", ""));
                if (!Bag.inBag(slot) || item(id) == null || kind.isEmpty()) {
                    continue;
                }
                sellables.add(new Sellable(slot, id, Math.max(1, ClientUiState.intOr(row, "count", 1)),
                        Math.max(0L, ClientUiState.longOr(row, "each", 0L)), kind.get(),
                        ClientUiState.stringOr(row, "uuid", ""), ClientUiState.intOr(row, "rarity", 0)));
            }
            // equipment first (rarer first), then the rest as the bag holds it
            sellables.sort(java.util.Comparator.comparingInt((Sellable s) -> s.kind().ordinal())
                    .thenComparing(java.util.Comparator.comparingInt(Sellable::rarity).reversed())
                    .thenComparingInt(Sellable::slot));
        }
        // keep what was chosen, or the nearest thing
        if (chosenWare == null || wares.stream().noneMatch(w -> w.id().equals(chosenWare))) {
            chosenWare = wares.isEmpty() ? null : wares.get(0).id();
        }
        if (chosenSell != null && sellables.stream().noneMatch(s -> s.key().equals(chosenSell))) {
            Sellable near = null;
            String[] parts = chosenSell.split("\\|", -1);
            for (Sellable sellable : sellables) {
                if (sellable.id().equals(parts[1])) {
                    near = sellable; // the same thing elsewhere in the bag
                    break;
                }
            }
            chosenSell = near != null ? near.key() : sellables.isEmpty() ? null : sellables.get(0).key();
        }
        if (chosenSell == null && !sellables.isEmpty()) {
            chosenSell = sellables.get(0).key();
        }
        amount = Mth.clamp(amount, 1, maxAmount());
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private static Item item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(location);
        return item == Items.AIR ? null : item;
    }

    @Override
    protected void init() {
        float fitW = (width - 8) / (float) W;
        float fitH = (height - 8) / (float) (H - RIBBON_TOP);
        scale = Math.min(1.0F, Math.min(fitW, fitH));
        left = (width - W * scale) / 2.0F;
        top = (height - (H - RIBBON_TOP) * scale) / 2.0F - RIBBON_TOP * scale;
        if (scale >= 1.0F) {
            left = Math.round(left);
            top = Math.round(top);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (pending && Util.getMillis() - pendingAt > PENDING_MS) {
            pending = false;
        }
        floaters.removeIf(floater -> Util.getMillis() - floater.at() > FLOAT_MS);
    }

    // ------------------------------------------------------------------------------------------------ the model

    private Optional<Ware> ware() {
        return wares.stream().filter(ware -> ware.id().equals(chosenWare)).findFirst();
    }

    private Optional<Sellable> sellable() {
        return sellables.stream().filter(sellable -> sellable.key().equals(chosenSell)).findFirst();
    }

    /** The stack of a thing to sell as the client holds it (the shop's own idea of it if the bag moved). */
    private static ItemStack stackOf(Sellable sellable) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack held = ItemStack.EMPTY;
        if (Bag.isBackSlot(sellable.slot())) {
            held = ClientBagBack.at(sellable.slot());
        } else if (minecraft.player != null && Bag.isBagSlot(sellable.slot())) {
            held = minecraft.player.getInventory().items.get(sellable.slot());
        }
        Item item = item(sellable.id());
        if (!held.isEmpty() && held.getItem() == item) {
            return held;
        }
        return item == null ? ItemStack.EMPTY : new ItemStack(item, Math.min(sellable.count(), 64));
    }

    /** The most the counter goes to now. */
    private int maxAmount() {
        if (tab == Tab.BUY) {
            return ware().map(w -> Math.max(1, ShopCatalog.affordable(w.price(), gold))).orElse(1);
        }
        return sellable().map(s -> s.kind() == ShopCatalog.SellKind.EQUIPMENT ? 1 : s.count()).orElse(1);
    }

    private long total() {
        if (tab == Tab.BUY) {
            return ware().map(w -> ShopCatalog.total(w.price(), amount)).orElse(0L);
        }
        return sellable().map(s -> ShopCatalog.sellTotal(s.each(), amount, s.count())).orElse(0L);
    }

    /** Why the button cannot be pressed now ({@code null}: it can). */
    private Component refusal() {
        if (pending) {
            return Component.translatable("screen.eternalrealm.shop.pending");
        }
        if (tab == Tab.BUY) {
            Optional<Ware> ware = ware();
            if (ware.isEmpty()) {
                return Component.translatable("screen.eternalrealm.shop.choose");
            }
            if (ShopCatalog.total(ware.get().price(), amount) > gold) {
                return Component.translatable("screen.eternalrealm.shop.short",
                        ShopCatalog.total(ware.get().price(), amount) - gold);
            }
            return null;
        }
        return sellable().isEmpty() ? Component.translatable("screen.eternalrealm.shop.choose") : null;
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        if (tab == Tab.BUY) {
            ShopCatalog.Section section = null;
            for (Ware ware : wares) {
                if (ware.section() != section) {
                    section = ware.section();
                    rows.add(new Row(section, null, null));
                }
                rows.add(new Row(null, ware, null));
            }
        } else {
            for (Sellable sellable : sellables) {
                rows.add(new Row(null, null, sellable));
            }
        }
        return rows;
    }

    private int maxScroll() {
        List<Row> rows = rows();
        int room = LIST_BOTTOM - LIST_Y;
        int used = 0;
        for (int i = rows.size() - 1; i >= 0; i--) {
            used += rows.get(i).height();
            if (used > room) {
                return Math.min(rows.size() - 1, i + 1);
            }
        }
        return 0;
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

    private int tabAt(double x, double y) {
        for (int i = 0; i < 2; i++) {
            if (inside(x, y, LIST_X + i * TAB_STEP, TAB_Y, 74, 20)) {
                return i;
            }
        }
        return -1;
    }

    /** The row under ({@code x}, {@code y}) and its top, or {@code null}. */
    private Row rowAt(double x, double y) {
        if (!inside(x, y, LIST_X, LIST_Y, LIST_W, LIST_BOTTOM - LIST_Y)) {
            return null;
        }
        int rowTop = LIST_Y;
        List<Row> rows = rows();
        for (int i = scroll; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (rowTop + row.height() > LIST_BOTTOM) {
                break;
            }
            if (y >= rowTop && y < rowTop + row.height()) {
                return row.heading() != null ? null : row;
            }
            rowTop += row.height();
        }
        return null;
    }

    private int buttonX() {
        return PANE_X + (PANE_W - 96) / 2;
    }

    private boolean onButton(double x, double y) {
        return inside(x, y, buttonX(), BUTTON_Y, 96, 24);
    }

    /** The counter's parts: 0 −, 1 +, 2.. the quick amounts (the last is 最大／全部). */
    private int counterAt(double x, double y) {
        if (inside(x, y, PANE_X + 6, AMOUNT_Y, 14, 14)) {
            return 0;
        }
        if (inside(x, y, PANE_X + 52, AMOUNT_Y, 14, 14)) {
            return 1;
        }
        for (int i = 0; i <= ShopCatalog.STEPS.size(); i++) {
            if (inside(x, y, quickX(i), AMOUNT_Y, 24, 14)) {
                return 2 + i;
            }
        }
        return -1;
    }

    private int quickX(int index) {
        return PANE_X + 72 + index * 26;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double x = localX(mouseX);
        double y = localY(mouseY);
        int tabHit = tabAt(x, y);
        if (tabHit >= 0) {
            Tab next = Tab.values()[tabHit];
            if (next != tab) {
                tab = next;
                scroll = 0;
                amount = 1;
                confirmUntil = 0L;
                play(SoundEvents.BOOK_PAGE_TURN, 1.1F);
            }
            return true;
        }
        Row row = rowAt(x, y);
        if (row != null) {
            if (row.ware() != null && !row.ware().id().equals(chosenWare)) {
                chosenWare = row.ware().id();
                amount = 1;
            } else if (row.sellable() != null && !row.sellable().key().equals(chosenSell)) {
                chosenSell = row.sellable().key();
                amount = row.sellable().kind() == ShopCatalog.SellKind.EQUIPMENT ? 1 : row.sellable().count();
            }
            confirmUntil = 0L;
            play(SoundEvents.UI_BUTTON_CLICK.value(), 1.3F);
            return true;
        }
        int counter = counterAt(x, y);
        if (counter >= 0) {
            int max = maxAmount();
            int step = hasShiftDown() ? 10 : 1;
            if (counter == 0) {
                amount = ShopCatalog.amount(amount, -step, max);
            } else if (counter == 1) {
                amount = ShopCatalog.amount(amount, step, max);
            } else if (counter - 2 < ShopCatalog.STEPS.size()) {
                amount = ShopCatalog.amount(ShopCatalog.STEPS.get(counter - 2), 0, max);
            } else {
                amount = max;
            }
            confirmUntil = 0L;
            play(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F);
            return true;
        }
        if (onButton(x, y)) {
            act();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The button: buy, or sell (a piece of equipment asks once more). */
    private void act() {
        if (refusal() != null) {
            play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8F);
            return;
        }
        JsonObject args = new JsonObject();
        if (tab == Tab.BUY) {
            Ware ware = ware().orElseThrow();
            args.addProperty("id", ware.id());
            args.addProperty("count", amount);
            ERNetwork.sendToServer(new UiActionPacket(ShopService.BUY, args));
        } else {
            Sellable sellable = sellable().orElseThrow();
            if (sellable.kind() == ShopCatalog.SellKind.EQUIPMENT && Util.getMillis() > confirmUntil) {
                confirmUntil = Util.getMillis() + CONFIRM_MS;
                play(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8F);
                return;
            }
            args.addProperty("slot", sellable.slot());
            args.addProperty("id", sellable.id());
            args.addProperty("count", Math.min(amount, sellable.count()));
            if (!sellable.uuid().isEmpty()) {
                args.addProperty("uuid", sellable.uuid());
            }
            ERNetwork.sendToServer(new UiActionPacket(ShopService.SELL, args));
        }
        pending = true;
        pendingAt = Util.getMillis();
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double x = localX(mouseX);
        double y = localY(mouseY);
        if (inside(x, y, LIST_X, LIST_Y, LIST_W, LIST_BOTTOM - LIST_Y)) {
            scroll = Mth.clamp(scroll + (delta > 0 ? -2 : 2), 0, maxScroll());
            return true;
        }
        if (inside(x, y, PANE_X, AMOUNT_Y - 4, PANE_W, 22)) {
            amount = ShopCatalog.amount(amount, delta > 0 ? 1 : -1, maxAmount());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                act();
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                move(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                amount = ShopCatalog.amount(amount, keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1, maxAmount());
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    /** Up / down: the previous or next thing of the list. */
    private void move(int step) {
        if (tab == Tab.BUY) {
            int index = wares.indexOf(ware().orElse(null));
            if (!wares.isEmpty()) {
                chosenWare = wares.get(Mth.clamp(index + step, 0, wares.size() - 1)).id();
            }
        } else {
            int index = sellables.indexOf(sellable().orElse(null));
            if (!sellables.isEmpty()) {
                chosenSell = sellables.get(Mth.clamp(index + step, 0, sellables.size() - 1)).key();
            }
        }
        amount = 1;
        confirmUntil = 0L;
    }

    // ------------------------------------------------------------------------------------------------ drawing

    private float seconds() {
        return (Util.getMillis() - openedAt) / 1000.0F;
    }

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
        GateArt.panel(graphics, W, H, t, 0, PANEL_TOP, PANEL_BOTTOM, TWINKLE);
        GateArt.ribbon(graphics, font, Component.translatable("screen.eternalrealm.shop.sign"), W, RIBBON_TOP);
        drawKeeper(graphics);
        drawPurse(graphics, t);
        drawTabs(graphics, mx, my);
        drawList(graphics, mx, my);
        drawPane(graphics, mx, my, t);
        GateArt.text(graphics, font, Component.translatable("screen.eternalrealm.shop.hint"), LIST_X, H - 15, MUTED,
                0.75F);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
        drawTooltips(graphics, mx, my, mouseX, mouseY);
        RenderSystem.disableBlend();
    }

    /** The shopkeeper's icon and what he says. */
    private void drawKeeper(GuiGraphics graphics) {
        int icon = com.eternalrealm.world.map.NpcMarkers.icon("item_shop");
        float u0 = icon / (float) com.eternalrealm.world.map.NpcMarkers.roles().size();
        float u1 = (icon + 1) / (float) com.eternalrealm.world.map.NpcMarkers.roles().size();
        graphics.fill(LIST_X, 18, LIST_X + 22, 40, 0x90000000);
        graphics.renderOutline(LIST_X, 18, 22, 22, 0xC0B89A5A);
        MapDraw.texturedRect(graphics, NPC_ICONS, LIST_X + 2, 20, LIST_X + 20, 38, u0, 0.0F, u1, 1.0F);
        Component name = Component.translatable("screen.eternalrealm.shop.keeper");
        GateArt.text(graphics, font, name, LIST_X + 28, 19, GOLD, 0.85F);
        if (line != null) {
            List<FormattedCharSequence> lines = font.split(line, (int) ((W - GOLD_W - 40 - LIST_X - 28) / 0.9F));
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                GateArt.textSeq(graphics, font, lines.get(i), LIST_X + 28, 29 + i * 9, TEXT, 0.9F);
            }
        }
    }

    /** The purse at the top right: a coin and the gold, and the gold flying off it after a deal. */
    private void drawPurse(GuiGraphics graphics, float t) {
        int x = W - 14 - GOLD_W;
        int y = 18;
        graphics.fill(x, y, x + GOLD_W, y + 22, 0xA0100A06);
        graphics.renderOutline(x, y, GOLD_W, 22, 0xC0B89A5A);
        coin(graphics, x + 12, y + 11, t);
        GateArt.text(graphics, font, Component.translatable("screen.eternalrealm.shop.purse"), x + 22, y + 3, MUTED,
                0.7F);
        Component amountText = Component.literal(String.format(Locale.ROOT, "%,d G", gold));
        float size = Math.min(1.2F, (GOLD_W - 26) / (float) Math.max(1, font.width(amountText)));
        GateArt.text(graphics, font, amountText, x + GOLD_W - 4 - font.width(amountText) * size, y + 10, GOLD, size);
        long now = Util.getMillis();
        for (Floater floater : floaters) {
            float age = (now - floater.at()) / (float) FLOAT_MS;
            if (age < 0.0F || age > 1.0F) {
                continue;
            }
            Component text = Component.literal(String.format(Locale.ROOT, "%+,d G", floater.amount()));
            int color = floater.amount() >= 0 ? GOOD : WARN;
            float alpha = age < 0.75F ? 1.0F : (1.0F - age) / 0.25F;
            GateArt.text(graphics, font, text, x + GOLD_W - 6 - font.width(text), y + 24 + 6 * (1.0F - age) - 10 * age,
                    GateArt.argb(color & 0xFFFFFF, alpha), 0.95F);
        }
    }

    /** A gold coin that glints now and then. */
    private static void coin(GuiGraphics graphics, float cx, float cy, float t) {
        MapDraw.disc(graphics, cx, cy, 6.5F, 0xFF3A2406);
        MapDraw.disc(graphics, cx, cy, 5.5F, 0xFFB07C24);
        MapDraw.disc(graphics, cx - 0.4F, cy - 0.4F, 4.2F, 0xFFE6BC58);
        MapDraw.disc(graphics, cx - 1.6F, cy - 1.6F, 1.4F, 0xFFFFF0B0);
        float glint = (t * 0.6F) % 3.0F;
        if (glint < 0.35F) {
            float a = 1.0F - Math.abs(glint - 0.175F) / 0.175F;
            graphics.fill(Math.round(cx + 2), Math.round(cy - 6), Math.round(cx + 3), Math.round(cy - 2),
                    GateArt.argb(0xFFFFFF, a));
            graphics.fill(Math.round(cx), Math.round(cy - 4), Math.round(cx + 5), Math.round(cy - 3),
                    GateArt.argb(0xFFFFFF, a));
        }
    }

    private void drawTabs(GuiGraphics graphics, double mx, double my) {
        int hover = tabAt(mx, my);
        for (int i = 0; i < 2; i++) {
            int x = LIST_X + i * TAB_STEP;
            boolean chosen = tab.ordinal() == i;
            GateArt.atlas(graphics, x, TAB_Y, GateArt.PILL_U, GateArt.BUTTON_V + (chosen ? 40 : i == hover ? 20 : 0),
                    74, 20);
            GateArt.atlas(graphics, x + 2, TAB_Y, GateArt.GEM_U + (i * 2 + (chosen || i == hover ? 1 : 0)) * 20, 0,
                    20, 20);
            Component label = Component.translatable(i == 0 ? "screen.eternalrealm.shop.tab.buy"
                    : "screen.eternalrealm.shop.tab.sell");
            GateArt.fitted(graphics, font, label, x + 25, TAB_Y + 6, 45, chosen ? GOLD : MUTED, 1.0F, 0.7F);
        }
        Component count = tab == Tab.BUY
                ? Component.translatable("screen.eternalrealm.shop.wares", wares.size())
                : Component.translatable("screen.eternalrealm.shop.sellables", sellables.size());
        GateArt.text(graphics, font, count, LIST_X + LIST_W - font.width(count) * 0.75F, TAB_Y + 12, MUTED, 0.75F);
    }

    private void drawList(GuiGraphics graphics, double mx, double my) {
        graphics.fill(LIST_X - 2, LIST_Y - 2, LIST_X + LIST_W + 2, LIST_BOTTOM + 2, 0x50000000);
        graphics.renderOutline(LIST_X - 2, LIST_Y - 2, LIST_W + 4, LIST_BOTTOM - LIST_Y + 4, 0x60B89A5A);
        List<Row> rows = rows();
        if (rows.isEmpty()) {
            GateArt.centered(graphics, font, Component.translatable(tab == Tab.BUY ? "screen.eternalrealm.shop.empty"
                    : "screen.eternalrealm.shop.nothing_to_sell"), LIST_X + LIST_W / 2.0F, LIST_Y + 40, MUTED, 0.9F);
            return;
        }
        Row hover = rowAt(mx, my);
        int y = LIST_Y;
        for (int i = scroll; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (y + row.height() > LIST_BOTTOM) {
                break;
            }
            if (row.heading() != null) {
                GateArt.text(graphics, font, Component.translatable(row.heading().translationKey()), LIST_X + 4, y + 4,
                        GOLD_DIM, 0.8F);
                graphics.fill(LIST_X + 4, y + 12, LIST_X + LIST_W - 4, y + 13, 0x50B89A5A);
            } else {
                boolean chosen = row.ware() != null ? row.ware().id().equals(chosenWare)
                        : row.sellable().key().equals(chosenSell);
                boolean lit = hover != null && hover.equals(row);
                if (chosen) {
                    graphics.fillGradient(LIST_X, y, LIST_X + LIST_W, y + ROW_H, 0x70E8C66A, 0x40B07C24);
                    graphics.renderOutline(LIST_X, y, LIST_W, ROW_H, 0xC0E6BC58);
                    GateArt.atlas(graphics, LIST_X - 10, y + 5, GateArt.POINTER_U, GateArt.EMBLEM_V, 8, 10);
                } else if (lit) {
                    graphics.fill(LIST_X, y, LIST_X + LIST_W, y + ROW_H, 0x28FFFFFF);
                }
                if (row.ware() != null) {
                    drawWareRow(graphics, row.ware(), y);
                } else {
                    drawSellRow(graphics, row.sellable(), y);
                }
            }
            y += row.height();
        }
        if (scroll > 0) {
            GateArt.centered(graphics, font, Component.literal("▲"), LIST_X + LIST_W - 6, LIST_Y - 1, GOLD_DIM, 0.7F);
        }
        if (scroll < maxScroll()) {
            GateArt.centered(graphics, font, Component.literal("▼"), LIST_X + LIST_W - 6, LIST_BOTTOM - 6, GOLD_DIM,
                    0.7F);
        }
    }

    private void drawWareRow(GuiGraphics graphics, Ware ware, int y) {
        graphics.renderItem(ware.stack(), LIST_X + 3, y + 2);
        boolean young = level < ware.level();
        GateArt.fitted(graphics, font, ware.stack().getHoverName(), LIST_X + 24, y + 3, LIST_W - 100,
                young ? 0xFFC8B8A0 : TEXT, 1.0F, 0.7F);
        Component sub = young ? Component.translatable("screen.eternalrealm.shop.from_level", ware.level())
                : Component.translatable("screen.eternalrealm.shop.owned", ware.owned());
        GateArt.text(graphics, font, sub, LIST_X + 24, y + 12, young ? WARN : MUTED, 0.65F);
        Component price = Component.literal(String.format(Locale.ROOT, "%,d G", ware.price()));
        GateArt.text(graphics, font, price, LIST_X + LIST_W - 6 - font.width(price), y + 6,
                ware.price() > gold ? 0xFFB06A50 : GOLD, 1.0F);
    }

    private void drawSellRow(GuiGraphics graphics, Sellable sellable, int y) {
        ItemStack stack = stackOf(sellable);
        graphics.renderItem(stack, LIST_X + 3, y + 2);
        graphics.renderItemDecorations(font, stack, LIST_X + 3, y + 2);
        GateArt.fitted(graphics, font, stack.getHoverName(), LIST_X + 24, y + 3, LIST_W - 100, TEXT, 1.0F, 0.7F);
        Component sub = Component.translatable("screen.eternalrealm.shop.kind." + sellable.kind().id())
                .append(Bag.isBackSlot(sellable.slot())
                        ? Component.translatable("screen.eternalrealm.shop.in_back") : Component.empty());
        GateArt.text(graphics, font, sub, LIST_X + 24, y + 12, MUTED, 0.65F);
        long whole = ShopCatalog.sellTotal(sellable.each(), sellable.count(), sellable.count());
        Component price = Component.literal(String.format(Locale.ROOT, "%,d G", whole));
        GateArt.text(graphics, font, price, LIST_X + LIST_W - 6 - font.width(price), y + 6, GOLD, 1.0F);
    }

    /** The chosen thing, large, with what it does, the counter, the total and the button. */
    private void drawPane(GuiGraphics graphics, double mx, double my, float t) {
        graphics.fill(PANE_X - 2, PANE_Y - 2, PANE_X + PANE_W + 2, PANE_BOTTOM + 2, 0x50000000);
        graphics.renderOutline(PANE_X - 2, PANE_Y - 2, PANE_W + 4, PANE_BOTTOM - PANE_Y + 4, 0x60B89A5A);
        ItemStack stack;
        Component sub;
        long each;
        Component priceLabel;
        if (tab == Tab.BUY) {
            Optional<Ware> ware = ware();
            if (ware.isEmpty()) {
                GateArt.centered(graphics, font, Component.translatable("screen.eternalrealm.shop.choose"),
                        PANE_X + PANE_W / 2.0F, PANE_Y + 60, MUTED, 0.9F);
                return;
            }
            stack = ware.get().stack();
            boolean young = level < ware.get().level();
            sub = Component.translatable(ware.get().section().translationKey())
                    .append(ware.get().level() > 1 ? Component.literal("  ").append(Component.translatable(
                            "screen.eternalrealm.shop.from_level", ware.get().level())
                            .withStyle(young ? ChatFormatting.RED : ChatFormatting.GRAY)) : Component.empty());
            each = ware.get().price();
            priceLabel = Component.translatable("screen.eternalrealm.shop.each_buy", String.format(Locale.ROOT, "%,d", each),
                    ware.get().owned());
        } else {
            Optional<Sellable> sellable = sellable();
            if (sellable.isEmpty()) {
                GateArt.centered(graphics, font, Component.translatable(sellables.isEmpty()
                                ? "screen.eternalrealm.shop.nothing_to_sell" : "screen.eternalrealm.shop.choose"),
                        PANE_X + PANE_W / 2.0F, PANE_Y + 60, MUTED, 0.9F);
                return;
            }
            stack = stackOf(sellable.get());
            sub = Component.translatable("screen.eternalrealm.shop.kind." + sellable.get().kind().id())
                    .append(Bag.isBackSlot(sellable.get().slot())
                            ? Component.translatable("screen.eternalrealm.shop.in_back") : Component.empty());
            each = sellable.get().each();
            priceLabel = Component.translatable("screen.eternalrealm.shop.each_sell",
                    String.format(Locale.ROOT, "%,d", each), sellable.get().count());
        }
        // the thing, large, in a medal with a slow halo
        float pulse = 0.35F + 0.15F * Mth.sin(t * 2.4F);
        graphics.setColor(1.0F, 0.85F, 0.5F, pulse);
        GateArt.atlas(graphics, PANE_X + 2, PANE_Y + 2, GateArt.HALO_U, 0, 32, 32);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(PANE_X + 4, PANE_Y + 4, 0.0F);
        pose.scale(1.4F, 1.4F, 1.0F);
        GateArt.atlas(graphics, 0, 0, 20, GateArt.MEDAL_V, 20, 20);
        pose.popPose();
        pose.pushPose();
        pose.translate(PANE_X + 10, PANE_Y + 10, 0.0F);
        pose.scale(1.0F, 1.0F, 1.0F);
        graphics.renderItem(stack, 0, 0);
        pose.popPose();
        GateArt.fitted(graphics, font, stack.getHoverName(), PANE_X + 38, PANE_Y + 6, PANE_W - 42, GOLD, 1.1F, 0.7F);
        GateArt.fitted(graphics, font, sub, PANE_X + 38, PANE_Y + 19, PANE_W - 42, MUTED, 0.8F, 0.6F);
        // what it does: the item's own tooltip without its name
        List<Component> tooltip = new ArrayList<>();
        LocalPlayer player = Minecraft.getInstance().player;
        try {
            tooltip.addAll(stack.getTooltipLines(player, TooltipFlag.NORMAL));
        } catch (RuntimeException e) {
            tooltip.clear();
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (int i = 1; i < tooltip.size(); i++) {
            lines.addAll(font.split(tooltip.get(i), (int) ((PANE_W - 8) / 0.8F)));
        }
        int maxLines = (AMOUNT_Y - 26 - (PANE_Y + 36)) / 9;
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            GateArt.textSeq(graphics, font, lines.get(i), PANE_X + 4, PANE_Y + 36 + i * 9, 0xFFD8CCB4, 0.8F);
        }
        if (lines.size() > maxLines) {
            GateArt.text(graphics, font, Component.literal("…"), PANE_X + PANE_W - 10, AMOUNT_Y - 30, MUTED, 0.8F);
        }
        graphics.fill(PANE_X + 4, AMOUNT_Y - 21, PANE_X + PANE_W - 4, AMOUNT_Y - 20, 0x50B89A5A);
        GateArt.fitted(graphics, font, priceLabel, PANE_X + 4, AMOUNT_Y - 15, PANE_W - 8, TEXT, 0.85F, 0.6F);
        drawCounter(graphics, mx, my);
        // the total and the button
        long total = total();
        boolean buying = tab == Tab.BUY;
        Component totalText = Component.translatable(buying ? "screen.eternalrealm.shop.total_buy"
                : "screen.eternalrealm.shop.total_sell", String.format(Locale.ROOT, "%,d", total));
        GateArt.text(graphics, font, totalText, PANE_X + 4, TOTAL_Y, buying && total > gold ? WARN : GOLD, 1.0F);
        Component refusal = refusal();
        boolean enabled = refusal == null;
        boolean over = onButton(mx, my);
        int bx = buttonX();
        GateArt.atlas(graphics, bx, BUTTON_Y, 0, GateArt.BUTTON_V + (!enabled ? 48 : over ? 24 : 0), 96, 24);
        boolean confirming = !buying && Util.getMillis() <= confirmUntil;
        Component label = pending ? Component.translatable("screen.eternalrealm.shop.pending")
                : buying ? Component.translatable("screen.eternalrealm.shop.buy")
                : confirming ? Component.translatable("screen.eternalrealm.shop.sell_confirm")
                : Component.translatable("screen.eternalrealm.shop.sell");
        float size = Math.min(1.0F, 84.0F / Math.max(1, font.width(label)));
        GateArt.centered(graphics, font, label, bx + 48, BUTTON_Y + 12 - 4 * size,
                !enabled ? 0xFF9A9AA2 : confirming ? 0xFFFFB090 : 0xFFFFF0C8, size);
        // the last answer of the counter, or why the button waits
        long age = Util.getMillis() - messageAt;
        if (message != null && age < MESSAGE_MS) {
            float alpha = age < MESSAGE_MS - 600 ? 1.0F : (MESSAGE_MS - age) / 600.0F;
            int color = messageOk ? GOOD : WARN;
            GateArt.fitted(graphics, font, message, PANE_X + 4, BUTTON_Y + 28, PANE_W - 8,
                    GateArt.argb(color & 0xFFFFFF, Math.max(0.05F, alpha)), 0.8F, 0.55F);
        } else if (refusal != null && !pending) {
            GateArt.fitted(graphics, font, refusal, PANE_X + 4, BUTTON_Y + 28, PANE_W - 8, 0xFFB8A890, 0.8F, 0.55F);
        } else if (confirming) {
            GateArt.fitted(graphics, font, Component.translatable("screen.eternalrealm.shop.sell_confirm_hint"),
                    PANE_X + 4, BUTTON_Y + 28, PANE_W - 8, 0xFFFFB090, 0.8F, 0.55F);
        }
    }

    /** − amount + and the quick amounts. */
    private void drawCounter(GuiGraphics graphics, double mx, double my) {
        int hover = counterAt(mx, my);
        int max = maxAmount();
        boolean equipment = tab == Tab.SELL && sellable().map(s -> s.kind() == ShopCatalog.SellKind.EQUIPMENT)
                .orElse(false);
        smallButton(graphics, PANE_X + 6, AMOUNT_Y, 14, Component.literal("−"), hover == 0, amount > 1);
        graphics.fill(PANE_X + 22, AMOUNT_Y, PANE_X + 50, AMOUNT_Y + 14, 0xC0000000);
        graphics.renderOutline(PANE_X + 22, AMOUNT_Y, 28, 14, 0xC0E6BC58);
        GateArt.centered(graphics, font, Component.literal(Integer.toString(amount)), PANE_X + 36, AMOUNT_Y + 3,
                GOLD, 1.0F);
        smallButton(graphics, PANE_X + 52, AMOUNT_Y, 14, Component.literal("+"), hover == 1, amount < max);
        if (equipment) {
            return; // a piece of equipment is sold alone
        }
        for (int i = 0; i <= ShopCatalog.STEPS.size(); i++) {
            boolean last = i == ShopCatalog.STEPS.size();
            Component text = last ? Component.translatable(tab == Tab.BUY ? "screen.eternalrealm.shop.max"
                    : "screen.eternalrealm.shop.all") : Component.literal(Integer.toString(ShopCatalog.STEPS.get(i)));
            int value = last ? max : ShopCatalog.STEPS.get(i);
            smallButton(graphics, quickX(i), AMOUNT_Y, 24, text, hover == 2 + i, value <= max || last);
        }
    }

    private void smallButton(GuiGraphics graphics, int x, int y, int w, Component text, boolean hover,
                             boolean enabled) {
        int top = !enabled ? 0xC0302A26 : hover ? 0xE0705020 : 0xE04A3418;
        int bottom = !enabled ? 0xC0201A16 : hover ? 0xE0402810 : 0xE02A1A0A;
        graphics.fillGradient(x, y, x + w, y + 14, top, bottom);
        graphics.renderOutline(x, y, w, 14, enabled ? (hover ? 0xFFFFD866 : 0xC0B89A5A) : 0x80605040);
        float size = Math.min(0.85F, (w - 4) / (float) Math.max(1, font.width(text)));
        GateArt.centered(graphics, font, text, x + w / 2.0F, y + 7 - 4 * size, enabled ? 0xFFFFF0C8 : 0xFF8A8278,
                size);
    }

    private void drawTooltips(GuiGraphics graphics, double mx, double my, int mouseX, int mouseY) {
        Row row = rowAt(mx, my);
        if (row != null) {
            ItemStack stack = row.ware() != null ? row.ware().stack() : stackOf(row.sellable());
            if (!stack.isEmpty()) {
                graphics.renderTooltip(font, stack, mouseX, mouseY);
            }
            return;
        }
        if (inside(mx, my, PANE_X, PANE_Y, 34, 34)) {
            ItemStack stack = tab == Tab.BUY ? ware().map(Ware::stack).orElse(ItemStack.EMPTY)
                    : sellable().map(ShopScreen::stackOf).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                graphics.renderTooltip(font, stack, mouseX, mouseY);
            }
            return;
        }
        if (onButton(mx, my)) {
            List<Component> tip = new ArrayList<>();
            Component refusal = refusal();
            if (refusal != null) {
                tip.add(refusal.copy().withStyle(ChatFormatting.RED));
            } else {
                tip.add(Component.translatable(tab == Tab.BUY ? "screen.eternalrealm.shop.buy_tip"
                        : "screen.eternalrealm.shop.sell_tip"));
            }
            graphics.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }
}
