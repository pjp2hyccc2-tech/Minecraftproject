package com.eternalrealm.ui.guildhouse;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.guild.house.forge.GuildHouseUi;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.eternalrealm.ui.guildhouse.GuildHouseScreen.bool;
import static com.eternalrealm.ui.guildhouse.GuildHouseScreen.inside;
import static com.eternalrealm.ui.guildhouse.GuildHouseScreen.num;
import static com.eternalrealm.ui.guildhouse.GuildHouseScreen.objects;
import static com.eternalrealm.ui.guildhouse.GuildHouseScreen.str;

/**
 * The guild board (docs/modules/guildhouse.md), opened at the board in the guild house: the message of the day (the
 * master and the officers write it), the members' notes (each member pins up to two; their authors, the master and the
 * officers take them down), the storage log (who put what into the house's chests or took it out) and the members.
 */
public final class GuildBoardScreen extends Screen {
    private static final int WIDTH = 360;
    private static final int HEIGHT = 236;
    private static final int PANEL = 0xF0161210;
    private static final int PAPER = 0xFFEFE3C2;
    private static final int PAPER_DARK = 0xFFD9C79A;
    private static final int INK = 0xFF2E2418;
    private static final int RIM = 0xFFC9A55A;
    private static final int RIM_DARK = 0xFF3A2C18;
    private static final int TEXT = 0xFFE8E0C8;
    private static final int MUTED = 0xFF9A90A8;
    private static final int GOOD = 0xFF8CE07A;
    private static final int BAD = 0xFFFF8A70;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private enum Tab { BOARD, LOG, MEMBERS }

    private JsonObject data;
    private long seenRevision = -1;
    private Tab tab = Tab.BOARD;
    private int scroll;
    private String typedMotd = "";
    private String typedNote = "";
    private EditBox motdBox;
    private EditBox noteBox;
    private Button motdButton;
    private Button noteButton;

    private GuildBoardScreen() {
        super(Component.translatable("screen.eternalrealm.guildboard.title"));
    }

    /** A new board document: opens the screen when asked (the board renderer reads the same document). */
    public static void accept(JsonObject json) {
        Minecraft minecraft = Minecraft.getInstance();
        if (bool(json, "open") && !(minecraft.screen instanceof GuildBoardScreen)) {
            minecraft.setScreen(new GuildBoardScreen());
        }
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        motdBox = new EditBox(font, left + 10, top + 74, WIDTH - 20 - 64, 16,
                Component.translatable("screen.eternalrealm.guildboard.motd_hint"));
        motdBox.setMaxLength(120);
        motdBox.setHint(Component.translatable("screen.eternalrealm.guildboard.motd_hint"));
        motdBox.setValue(typedMotd);
        motdBox.setResponder(value -> typedMotd = value);
        addRenderableWidget(motdBox);
        motdButton = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.guildboard.set"),
                b -> {
                    JsonObject args = new JsonObject();
                    args.addProperty("text", typedMotd);
                    send("guildhouse.motd", args);
                    typedMotd = "";
                    motdBox.setValue("");
                }).bounds(left + WIDTH - 10 - 60, top + 73, 60, 18).build());
        noteBox = new EditBox(font, left + 10, top + HEIGHT - 26, WIDTH - 20 - 64, 16,
                Component.translatable("screen.eternalrealm.guildboard.note_hint"));
        noteBox.setMaxLength(80);
        noteBox.setHint(Component.translatable("screen.eternalrealm.guildboard.note_hint"));
        noteBox.setValue(typedNote);
        noteBox.setResponder(value -> typedNote = value);
        addRenderableWidget(noteBox);
        noteButton = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.guildboard.pin"),
                b -> {
                    if (!typedNote.isBlank()) {
                        JsonObject args = new JsonObject();
                        args.addProperty("text", typedNote);
                        send("guildhouse.note", args);
                        typedNote = "";
                        noteBox.setValue("");
                    }
                }).bounds(left + WIDTH - 10 - 60, top + HEIGHT - 27, 60, 18).build());
        send("guildhouse.board", new JsonObject());
        refresh();
        updateWidgets();
    }

    @Override
    public void tick() {
        motdBox.tick();
        noteBox.tick();
        refresh();
        updateWidgets();
    }

    private void refresh() {
        if (ClientUiState.revision() == seenRevision) {
            return;
        }
        seenRevision = ClientUiState.revision();
        data = ClientUiState.get(GuildHouseUi.BOARD).orElse(data);
    }

    private void updateWidgets() {
        boolean board = tab == Tab.BOARD && data != null && data.has("guild");
        boolean moderator = board && bool(data, "moderator");
        motdBox.visible = moderator;
        motdButton.visible = moderator;
        noteBox.visible = board;
        noteButton.visible = board;
        noteButton.active = !typedNote.isBlank();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void send(String action, JsonObject args) {
        ERNetwork.sendToServer(new UiActionPacket(action, args));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        int x = left + 8;
        for (Tab each : Tab.values()) {
            int w = tabWidth(each);
            if (inside(mouseX, mouseY, x, top + 20, w, 13)) {
                tab = each;
                scroll = 0;
                return true;
            }
            x += w + 2;
        }
        if (tab == Tab.BOARD && data != null) {
            List<JsonObject> notes = objects(data, "notes");
            int y = notesTop(top);
            for (JsonObject note : notes) {
                if (y > top + HEIGHT - 44) {
                    break;
                }
                boolean removable = bool(note, "mine") || bool(data, "moderator");
                if (removable && inside(mouseX, mouseY, left + WIDTH - 22, y, 12, 10)) {
                    JsonObject args = new JsonObject();
                    args.addProperty("id", num(note, "id"));
                    send("guildhouse.unpin", args);
                    return true;
                }
                y += 12;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab != Tab.BOARD) {
            int lines = tab == Tab.LOG ? objects(data, "log").size() : objects(data, "members").size();
            scroll = Math.max(0, Math.min(Math.max(0, lines - 16), scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, RIM_DARK);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, RIM);
        Component heading = data != null && data.has("guild")
                ? Component.translatable("screen.eternalrealm.guildboard.heading", str(data, "guild"), str(data, "tag"))
                : title;
        graphics.drawString(font, heading, left + 8, top + 7, RIM, true);
        int x = left + 8;
        for (Tab each : Tab.values()) {
            int w = tabWidth(each);
            boolean on = each == tab;
            graphics.fill(x, top + 20, x + w, top + 33, on ? 0xFF4A3A20 : 0xFF1E1A26);
            graphics.renderOutline(x, top + 20, w, 13, on ? RIM : RIM_DARK);
            graphics.drawString(font, tabName(each), x + (w - font.width(tabName(each))) / 2, top + 23,
                    on ? 0xFFFFE8A8 : MUTED, false);
            x += w + 2;
        }
        if (data == null || !data.has("guild")) {
            graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.guildboard.no_guild"),
                    left + WIDTH / 2, top + HEIGHT / 2, MUTED);
        } else {
            switch (tab) {
                case BOARD -> drawBoard(graphics, left, top, mouseX, mouseY);
                case LOG -> drawLog(graphics, left, top);
                case MEMBERS -> drawMembers(graphics, left, top);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int notesTop(int top) {
        return top + (bool(data, "moderator") ? 112 : 92);
    }

    private void drawBoard(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        graphics.fill(left + 8, top + 38, left + WIDTH - 8, top + 70, PAPER);
        graphics.renderOutline(left + 8, top + 38, WIDTH - 16, 32, PAPER_DARK);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.guildboard.motd"), left + 12, top + 41,
                0xFF8A5A20, false);
        String motd = str(data, "motd");
        List<FormattedCharSequence> lines = font.split(motd.isEmpty()
                ? Component.translatable("screen.eternalrealm.guildboard.motd_empty") : Component.literal(motd),
                WIDTH - 28);
        for (int i = 0; i < lines.size() && i < 2; i++) {
            graphics.drawString(font, lines.get(i), left + 12, top + 51 + i * 9, INK, false);
        }
        int y = notesTop(top);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.guildboard.notes",
                objects(data, "notes").size()), left + 10, y - 12, RIM, false);
        for (JsonObject note : objects(data, "notes")) {
            if (y > top + HEIGHT - 44) {
                break;
            }
            graphics.fill(left + 8, y - 1, left + WIDTH - 8, y + 10, 0xFF2A2234);
            String when = TIME.format(Instant.ofEpochMilli(num(note, "at")));
            Component line = Component.literal(str(note, "name")).withStyle(s -> s.withColor(0xFFC860))
                    .append(Component.literal(" " + str(note, "text")).withStyle(s -> s.withColor(0xE8E0C8)));
            graphics.drawString(font, ellipsize(line, WIDTH - 90), left + 12, y + 1, TEXT, false);
            graphics.drawString(font, when, left + WIDTH - 26 - font.width(when), y + 1, MUTED, false);
            if (bool(note, "mine") || bool(data, "moderator")) {
                boolean hover = inside(mouseX, mouseY, left + WIDTH - 22, y, 12, 10);
                graphics.drawString(font, "×", left + WIDTH - 19, y + 1, hover ? BAD : MUTED, false);
            }
            y += 12;
        }
    }

    private void drawLog(GuiGraphics graphics, int left, int top) {
        List<JsonObject> log = objects(data, "log");
        if (log.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.guildboard.log_empty"),
                    left + WIDTH / 2, top + 80, MUTED);
            return;
        }
        int y = top + 40;
        for (int i = scroll; i < log.size() && y < top + HEIGHT - 12; i++) {
            JsonObject entry = log.get(i);
            String when = TIME.format(Instant.ofEpochMilli(num(entry, "at")));
            Component item = itemName(str(entry, "item"));
            Component line = Component.translatable(bool(entry, "deposit")
                            ? "screen.eternalrealm.guildboard.deposit" : "screen.eternalrealm.guildboard.withdraw",
                    str(entry, "name"), item, num(entry, "count"));
            graphics.drawString(font, when, left + 10, y, MUTED, false);
            graphics.drawString(font, ellipsize(line, WIDTH - 110), left + 76, y,
                    bool(entry, "deposit") ? GOOD : 0xFFFFC090, false);
            if (bool(entry, "vault")) {
                graphics.drawString(font, Component.translatable("screen.eternalrealm.guildboard.vault"),
                        left + WIDTH - 40, y, RIM, false);
            }
            y += 11;
        }
    }

    private void drawMembers(GuiGraphics graphics, int left, int top) {
        int y = top + 40;
        List<JsonObject> members = objects(data, "members");
        for (int i = scroll; i < members.size() && y < top + HEIGHT - 12; i++) {
            JsonObject member = members.get(i);
            boolean online = bool(member, "online");
            graphics.fill(left + 12, y + 2, left + 17, y + 7, online ? 0xFF6CE06C : 0xFF55505E);
            graphics.drawString(font, str(member, "name"), left + 22, y, online ? TEXT : MUTED, false);
            Component rank = Component.translatable("guild_rank.eternalrealm." + str(member, "rank"));
            graphics.drawString(font, rank, left + WIDTH - 12 - font.width(rank), y, RIM, false);
            y += 11;
        }
    }

    private int tabWidth(Tab each) {
        return Math.max(70, font.width(tabName(each)) + 12);
    }

    private Component tabName(Tab each) {
        return Component.translatable("screen.eternalrealm.guildboard.tab." + each.name().toLowerCase(Locale.ROOT));
    }

    private Component itemName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
            return BuiltInRegistries.ITEM.get(key).getDescription();
        }
        return Component.literal(id);
    }

    private Component ellipsize(Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String plain = text.getString();
        while (!plain.isEmpty() && font.width(plain + "…") > width) {
            plain = plain.substring(0, plain.length() - 1);
        }
        return Component.literal(plain + "…");
    }
}
