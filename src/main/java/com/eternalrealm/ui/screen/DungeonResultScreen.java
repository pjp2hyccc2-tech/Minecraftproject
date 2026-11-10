package com.eternalrealm.ui.screen;

import com.eternalrealm.client.ClientChannels;
import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The clear result (報酬画面): "DUNGEON CLEAR" and the grade stamped next to it, the clear time and
 * the falls, the party's contributions with the hero of the fight (殊勲) crowned, the rewards popping into their slots one by one, and a
 * bonus chest of three face-down cards — pick one and all three turn over. The instance sends everyone out when the
 * countdown ends; the leave button goes at once.
 */
public final class DungeonResultScreen extends Screen {
    public static final String CHANNEL = "dungeon_result";
    public static final String BONUS_CHANNEL = "dungeon_bonus";
    private static final int GOLD = 0xFFE8C66A;
    private static final int BORDER = 0xFF7A5A26;
    private static final int PANEL = 0xD8141218;
    private static final int SLOT = 18;
    private static final int CARD_W = 34;
    private static final int CARD_H = 42;

    private record Row(String name, String job, int level, long damage, double share, long taken, int falls,
                       boolean hero, boolean me) {
    }

    private record Card(ItemStack stack, long gold) {
    }

    private final JsonObject data;
    private final long openedAt = System.nanoTime();
    private final List<Row> party = new ArrayList<>();
    private final List<ItemStack> rewards = new ArrayList<>();
    private final String rank;
    private final Component dungeon;
    private final Component difficulty;
    private final int cardCount;
    /** A field calamity was subdued (no time limit, no bonus chest, nothing to leave). */
    private final boolean calamity;
    private List<Card> revealed;
    private int picked = -1;
    private long revealedAt;
    private boolean pickSent;
    private boolean stamped;
    private int popped;
    private long exitAt;

    public static void registerChannels() {
        ClientChannels.register(CHANNEL, DungeonResultScreen::open);
        ClientChannels.register(BONUS_CHANNEL, DungeonResultScreen::onBonus);
    }

    private static void open(JsonObject data) {
        Minecraft.getInstance().setScreen(new DungeonResultScreen(data));
    }

    private static void onBonus(JsonObject bonus) {
        if (Minecraft.getInstance().screen instanceof DungeonResultScreen screen) {
            screen.reveal(bonus);
        }
    }

    private DungeonResultScreen(JsonObject data) {
        super(Component.translatable("result.eternalrealm.title"));
        this.data = data;
        this.rank = ClientUiState.stringOr(data, "rank", "C");
        this.dungeon = Component.translatable(ClientUiState.stringOr(data, "dungeon", ""));
        this.difficulty = Component.translatable(ClientUiState.stringOr(data, "difficultyKey", ""));
        this.cardCount = Mth.clamp(ClientUiState.intOr(data, "cards", 3), 0, 5);
        this.calamity = "calamity".equals(ClientUiState.stringOr(data, "kind", ""));
        this.exitAt = System.nanoTime() + (long) (number(data, "exit") * 1.0E9);
        String me = ClientUiState.stringOr(data, "me", "");
        if (data.has("party") && data.get("party").isJsonArray()) {
            for (JsonElement element : data.getAsJsonArray("party")) {
                JsonObject row = element.getAsJsonObject();
                party.add(new Row(ClientUiState.stringOr(row, "name", "?"), ClientUiState.stringOr(row, "job", "none"),
                        ClientUiState.intOr(row, "level", 0), ClientUiState.longOr(row, "damage", 0L),
                        number(row, "share"), ClientUiState.longOr(row, "taken", 0L),
                        ClientUiState.intOr(row, "falls", 0), row.has("hero") && row.get("hero").getAsBoolean(),
                        me.equals(ClientUiState.stringOr(row, "id", ""))));
            }
            party.sort((a, b) -> Long.compare(b.damage(), a.damage()));
        }
        if (data.has("rewards") && data.get("rewards").isJsonArray()) {
            for (JsonElement element : data.getAsJsonArray("rewards")) {
                ItemStack stack = parse(element.getAsString());
                if (!stack.isEmpty()) {
                    rewards.add(stack);
                }
            }
        }
    }

    private static ItemStack parse(String snbt) {
        try {
            CompoundTag tag = TagParser.parseTag(snbt);
            return ItemStack.of(tag);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static double number(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsDouble() : 0.0;
    }

    private void reveal(JsonObject bonus) {
        List<Card> cards = new ArrayList<>();
        if (bonus.has("cards") && bonus.get("cards").isJsonArray()) {
            for (JsonElement element : bonus.getAsJsonArray("cards")) {
                JsonObject card = element.getAsJsonObject();
                cards.add(card.has("item") ? new Card(parse(card.get("item").getAsString()), 0L)
                        : new Card(ItemStack.EMPTY, ClientUiState.longOr(card, "gold", 0L)));
            }
        }
        revealed = cards;
        picked = ClientUiState.intOr(bonus, "picked", -1);
        revealedAt = System.nanoTime();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double age() {
        return (System.nanoTime() - openedAt) / 1.0E9;
    }

    // ------------------------------------------------------------------ layout

    private int middleTop() {
        return 58;
    }

    private int middleBottom() {
        return height - (cardCount > 0 ? 80 : 26);
    }

    private int columnWidth() {
        return Math.min(220, (width - 30) / 2);
    }

    private int partyLeft() {
        return width / 2 - 5 - columnWidth();
    }

    private int rewardsLeft() {
        return width / 2 + 5;
    }

    private int cardsTop() {
        return height - 66;
    }

    private int cardLeft(int index) {
        int total = cardCount * CARD_W + (cardCount - 1) * 12;
        return width / 2 - total / 2 + index * (CARD_W + 12);
    }

    private int leaveLeft() {
        return width / 2 - 60;
    }

    private int leaveTop() {
        return height - 20;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (!pickSent && revealed == null && age() > 1.6) {
                for (int i = 0; i < cardCount; i++) {
                    if (inside(mouseX, mouseY, cardLeft(i), cardsTop(), CARD_W, CARD_H)) {
                        pickSent = true;
                        JsonObject args = new JsonObject();
                        args.addProperty("card", i);
                        ERNetwork.sendToServer(new UiActionPacket(com.eternalrealm.dungeon.forge.DungeonResults.BONUS_ACTION,
                                args));
                        play(SoundEvents.CHEST_OPEN, 1.0F);
                        return true;
                    }
                }
            }
            if (inside(mouseX, mouseY, leaveLeft(), leaveTop(), 120, 14)) {
                if (!calamity) {
                    ERNetwork.sendToServer(new UiActionPacket(com.eternalrealm.dungeon.forge.DungeonResults.EXIT_ACTION,
                            new JsonObject()));
                }
                play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static void play(net.minecraft.sounds.SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics graphics) {
        float fade = (float) Mth.clamp(age() / 0.4, 0.0, 1.0);
        int alpha = (int) (0xB0 * fade);
        graphics.fillGradient(0, 0, width, height, (alpha << 24) | 0x0A0806, (Math.min(255, alpha + 0x20) << 24) | 0x040302);
        // soft golden rays behind the title
        double t = age();
        for (int i = 0; i < 7; i++) {
            double angle = t * 0.15 + i * Math.PI / 3.5;
            int spread = 14 + (int) (6 * Math.sin(t * 1.3 + i));
            int x = width / 2 + (int) (Math.cos(angle) * 90);
            graphics.fillGradient(x - spread, 0, x + spread, 70, (int) (0x22 * fade) << 24 | 0xFFD878, 0x00FFD878);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        double t = age();
        // ---- DUNGEON CLEAR
        float grow = (float) Mth.clamp(t / 0.35, 0.0, 1.0);
        float scale = 2.6F - 0.6F * (1.0F - (1.0F - grow) * (1.0F - grow));
        Component clear = Component.translatable(calamity ? "result.eternalrealm.calamity_clear"
                : "result.eternalrealm.clear").withStyle(ChatFormatting.BOLD);
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, 10.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        int alpha = (int) (255 * grow);
        graphics.drawString(font, clear, -font.width(clear) / 2, 0, (Math.max(8, alpha) << 24) | 0xFFD86A, true);
        graphics.pose().popPose();
        graphics.drawCenteredString(font, dungeon.copy().append(Component.literal("  ")).append(difficulty.copy()
                .withStyle(ChatFormatting.GRAY)), width / 2, 32, 0xFFE8E0D0);
        double time = number(data, "time");
        double limit = number(data, "limit");
        Component stats = (limit > 0.0 ? Component.translatable("result.eternalrealm.time", clock(time), clock(limit))
                : Component.translatable("result.eternalrealm.time_only", clock(time)))
                .append(Component.literal("    "))
                .append(Component.translatable("result.eternalrealm.falls", ClientUiState.intOr(data, "falls", 0)));
        graphics.drawCenteredString(font, stats.copy().withStyle(ChatFormatting.GRAY), width / 2, 44, 0xFFFFFFFF);

        // ---- the grade, stamped
        drawRank(graphics, t);

        // ---- the party
        int top = middleTop();
        int bottom = middleBottom();
        int left = partyLeft();
        int colWidth = columnWidth();
        panel(graphics, left, top, left + colWidth, bottom);
        graphics.drawString(font, Component.translatable("result.eternalrealm.party").withStyle(ChatFormatting.BOLD),
                left + 4, top + 3, GOLD);
        Component headDamage = Component.translatable("result.eternalrealm.damage");
        Component headTaken = Component.translatable("result.eternalrealm.taken");
        int damageRight = left + colWidth - 46;
        int takenRight = left + colWidth - 4;
        graphics.drawString(font, headDamage, damageRight - font.width(headDamage), top + 3, 0xFF9A9088);
        graphics.drawString(font, headTaken, takenRight - font.width(headTaken), top + 3, 0xFF9A9088);
        int rowHeight = 11;
        int maxRows = Math.max(1, (bottom - top - 16) / rowHeight);
        int y = top + 15;
        for (int i = 0; i < Math.min(maxRows, party.size()); i++) {
            Row row = party.get(i);
            double appear = t - 0.9 - i * 0.12;
            if (appear < 0.0) {
                break;
            }
            int slide = (int) (Math.max(0.0, 1.0 - appear / 0.2) * 12);
            if (row.me()) {
                graphics.fill(left + 1, y - 1, left + colWidth - 1, y + rowHeight - 1, 0x40806020);
            }
            int x = left + 4 + slide;
            if (row.hero()) {
                graphics.drawString(font, "♛", x, y, 0xFFFFD34A);
            }
            String label = row.name() + (row.level() > 0 ? " Lv" + row.level() : "");
            int nameWidth = damageRight - 50 - (x + 10);
            graphics.drawString(font, font.plainSubstrByWidth(label, Math.max(20, nameWidth)), x + 10, y,
                    jobColor(row.job()));
            String damage = String.format(Locale.ROOT, "%,d (%.0f%%)", row.damage(), row.share() * 100.0);
            graphics.drawString(font, damage, damageRight - font.width(damage), y, row.hero() ? 0xFFFFE08A : 0xFFE0D8D0);
            String taken = String.format(Locale.ROOT, "%,d", row.taken());
            graphics.drawString(font, taken, takenRight - font.width(taken), y, 0xFFB0A8A0);
            y += rowHeight;
        }
        if (data.has("heroMe") && data.get("heroMe").getAsBoolean() && t > 1.2) {
            graphics.drawString(font, Component.translatable("result.eternalrealm.hero_you").withStyle(ChatFormatting.GOLD),
                    left + 4, bottom - 11, 0xFFFFFFFF);
        }

        // ---- the rewards
        int rLeft = rewardsLeft();
        panel(graphics, rLeft, top, rLeft + colWidth, bottom);
        graphics.drawString(font, Component.translatable("result.eternalrealm.rewards").withStyle(ChatFormatting.BOLD),
                rLeft + 4, top + 3, GOLD);
        if (t > 1.0) {
            String exp = String.format(Locale.ROOT, "EXP +%,d", ClientUiState.longOr(data, "exp", 0L));
            String gold = String.format(Locale.ROOT, "%,d G", ClientUiState.longOr(data, "gold", 0L));
            graphics.drawString(font, exp, rLeft + 4, top + 15, 0xFF8FE07A);
            graphics.drawString(font, "+" + gold, rLeft + colWidth - 4 - font.width("+" + gold), top + 15, 0xFFFFD34A);
        }
        int perRow = Math.max(1, (colWidth - 8) / (SLOT + 2));
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < rewards.size(); i++) {
            double appear = t - 1.2 - i * 0.08;
            if (appear < 0.0) {
                break;
            }
            if (i >= popped) {
                popped = i + 1;
                play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F + 0.05F * i);
            }
            int sx = rLeft + 4 + (i % perRow) * (SLOT + 2);
            int sy = top + 28 + (i / perRow) * (SLOT + 2);
            if (sy + SLOT > bottom - 2) {
                break;
            }
            ItemStack stack = rewards.get(i);
            int frame = rarityColor(stack);
            graphics.fill(sx - 1, sy - 1, sx + SLOT - 1, sy + SLOT - 1, frame);
            graphics.fill(sx, sy, sx + SLOT - 2, sy + SLOT - 2, 0xFF1A1620);
            float pop = (float) Mth.clamp(appear / 0.15, 0.0, 1.0);
            graphics.pose().pushPose();
            graphics.pose().translate(sx + 8, sy + 8, 0);
            graphics.pose().scale(0.6F + 0.4F * pop, 0.6F + 0.4F * pop, 1.0F);
            graphics.renderItem(stack, -8, -8);
            graphics.renderItemDecorations(font, stack, -8, -8);
            graphics.pose().popPose();
            if (inside(mouseX, mouseY, sx, sy, SLOT - 2, SLOT - 2)) {
                hovered = stack;
            }
        }

        // ---- the bonus chest
        if (cardCount > 0 && t > 1.6) {
            Component prompt = revealed == null
                    ? Component.translatable("result.eternalrealm.bonus_pick")
                    : Component.translatable("result.eternalrealm.bonus_got");
            graphics.drawCenteredString(font, prompt.copy().withStyle(ChatFormatting.YELLOW), width / 2, cardsTop() - 11,
                    0xFFFFFFFF);
            for (int i = 0; i < cardCount; i++) {
                ItemStack shown = drawCard(graphics, i, mouseX, mouseY);
                if (!shown.isEmpty() && inside(mouseX, mouseY, cardLeft(i), cardsTop(), CARD_W, CARD_H)) {
                    hovered = shown;
                }
            }
        }

        // ---- leave
        long left0 = Math.max(0L, (exitAt - System.nanoTime()) / 1_000_000_000L);
        boolean overLeave = inside(mouseX, mouseY, leaveLeft(), leaveTop(), 120, 14);
        graphics.fill(leaveLeft() - 1, leaveTop() - 1, leaveLeft() + 121, leaveTop() + 15, overLeave ? GOLD : BORDER);
        graphics.fill(leaveLeft(), leaveTop(), leaveLeft() + 120, leaveTop() + 14, overLeave ? 0xF0302418 : PANEL);
        graphics.drawCenteredString(font, Component.translatable(calamity ? "result.eternalrealm.close"
                : "result.eternalrealm.leave"), width / 2, leaveTop() + 3, 0xFFFFFFFF);
        if (!calamity) {
            Component countdown = Component.translatable("result.eternalrealm.exit_in", left0);
            graphics.drawString(font, countdown.copy().withStyle(ChatFormatting.DARK_GRAY), leaveLeft() + 126,
                    leaveTop() + 3, 0xFFFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private void drawRank(GuiGraphics graphics, double t) {
        double since = t - 0.65;
        if (since < 0.0) {
            return;
        }
        if (!stamped) {
            stamped = true;
            play(SoundEvents.ANVIL_LAND, 0.7F);
        }
        float s = (float) (1.0 + 2.0 * Math.max(0.0, 1.0 - since / 0.18));
        int color = switch (rank) {
            case "S" -> 0xFFFFD34A;
            case "A" -> 0xFFE0E4F0;
            case "B" -> 0xFFD8945A;
            default -> 0xFF9A9090;
        };
        int cx = width / 2 + Math.min(150, width / 2 - 40);
        int cy = 22;
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0.0F);
        graphics.pose().scale(s, s, 1.0F);
        // a diamond frame
        for (int i = 0; i < 14; i++) {
            graphics.fill(-i, -14 + i, i + 1, -13 + i, 0x60000000 | (color & 0xFFFFFF));
            graphics.fill(-i, 14 - i, i + 1, 15 - i, 0x60000000 | (color & 0xFFFFFF));
        }
        graphics.pose().scale(2.4F, 2.4F, 1.0F);
        Component letter = Component.literal(rank).withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, letter, -font.width(letter) / 2, -4, color, true);
        graphics.pose().popPose();
        graphics.drawCenteredString(font, Component.translatable("result.eternalrealm.rank").withStyle(ChatFormatting.GRAY),
                cx, cy + 18, 0xFFFFFFFF);
    }

    /** One bonus card; returns the stack shown on it once revealed (for the tooltip). */
    private ItemStack drawCard(GuiGraphics graphics, int index, int mouseX, int mouseY) {
        int x = cardLeft(index);
        int y = cardsTop();
        boolean hover = revealed == null && !pickSent && inside(mouseX, mouseY, x, y, CARD_W, CARD_H);
        double flip = revealed == null ? 0.0 : Mth.clamp((System.nanoTime() - revealedAt) / 1.0E9 / 0.3
                - (index == picked ? 0.0 : 0.5), 0.0, 1.0);
        boolean front = flip >= 0.5;
        float squeeze = (float) Math.abs(Math.cos(flip * Math.PI));
        int lift = hover ? 3 : 0;
        graphics.pose().pushPose();
        graphics.pose().translate(x + CARD_W / 2.0F, y + CARD_H / 2.0F - lift, 0.0F);
        graphics.pose().scale(Math.max(0.05F, squeeze), 1.0F, 1.0F);
        int half = CARD_W / 2;
        int halfH = CARD_H / 2;
        boolean chosen = front && index == picked;
        int border = chosen ? 0xFFFFD34A : hover ? 0xFFFFE8A0 : BORDER;
        graphics.fill(-half - 1, -halfH - 1, half + 1, halfH + 1, border);
        ItemStack shown = ItemStack.EMPTY;
        if (!front) {
            graphics.fill(-half, -halfH, half, halfH, 0xFF3A2414);
            graphics.fill(-half + 3, -halfH + 3, half - 3, halfH - 3, 0xFF5A3A1E);
            graphics.renderItem(new ItemStack(Items.CHEST), -8, -8);
        } else {
            graphics.fill(-half, -halfH, half, halfH, chosen ? 0xFF2A2418 : 0xFF141218);
            Card card = revealed.get(Math.min(index, revealed.size() - 1));
            if (!card.stack().isEmpty()) {
                graphics.renderItem(card.stack(), -8, -12);
                graphics.renderItemDecorations(font, card.stack(), -8, -12);
                shown = card.stack();
            } else {
                graphics.renderItem(new ItemStack(Items.GOLD_NUGGET), -8, -12);
                String gold = card.gold() + "G";
                graphics.drawString(font, gold, -font.width(gold) / 2, 8, 0xFFFFD34A);
            }
            if (!chosen) {
                graphics.fill(-half, -halfH, half, halfH, 0x90000000);
            }
        }
        graphics.pose().popPose();
        return shown;
    }

    private void panel(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, BORDER);
        graphics.fill(x0, y0, x1, y1, PANEL);
        graphics.fill(x0, y0, x1, y0 + 12, 0x40000000);
    }

    private static int rarityColor(ItemStack stack) {
        return EquipmentStacks.read(stack).map(equipment -> 0xFF000000 | equipment.rarity().color())
                .orElse(0xFF5A5060);
    }

    private static String clock(double seconds) {
        int total = (int) Math.max(0, Math.round(seconds));
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }

    private static int jobColor(String job) {
        return switch (job) {
            case "knight" -> 0xFF7FA8FF;
            case "assassin" -> 0xFFE05050;
            case "mage" -> 0xFFD08CFF;
            case "ranger" -> 0xFF7CE07C;
            case "priest" -> 0xFFFFD966;
            case "spellblade" -> 0xFF66E6E6;
            default -> 0xFFFFFFFF;
        };
    }
}
