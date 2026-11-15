package com.eternalrealm.ui.screen;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Guild page: create (tag + name) or join by invite; for members the guild's level (merit to the next level, the
 * perks: docs/modules/guild_levels.md) above two views: the members (ranks, invite, kick, promote, demote, leave) and
 * the guild battles (record, the battle under way, challenges, guilds online to challenge:
 * docs/modules/guild_battles.md). The page only shows server documents ({@code guild}, {@code guild.battle}) and
 * sends requests.
 */
public final class GuildScreen extends JournalScreen {
    private static final String BATTLE = "guild.battle";
    private static final int GOLD = 0xFFE8C66A;
    private static final int GOOD = 0xFF8CE07A;
    private static final int RED = 0xFFFF6A5A;
    private static final int BLUE = 0xFF6AA8FF;
    private static boolean battleView;
    private EditBox first;
    private EditBox second;
    private final String[] typed = {"", ""};
    private String seenGuild;
    private String seenBattle;
    private long lastRequest;
    private long lastBattleRequest;

    public GuildScreen() {
        super(Tab.GUILD);
    }

    @Override
    protected void initPage() {
        if (seenGuild == null) {
            send("guild.view", new JsonObject());
            send("guild.battle.view", new JsonObject());
        }
        seenGuild = guildJson();
        seenBattle = battleJson();
        first = null;
        second = null;
        Optional<JsonObject> guild = ClientUiState.get("guild");
        boolean member = guild.isPresent() && guild.get().has("name");
        if (!member) {
            first = box(left, top + 40, 60, "screen.eternalrealm.guild.tag", 5, 0);
            second = box(left + 64, top + 40, 150, "screen.eternalrealm.guild.name", 16, 1);
            addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.guild.create"), b -> {
                JsonObject args = new JsonObject();
                args.addProperty("tag", first.getValue().trim());
                args.addProperty("name", second.getValue().trim());
                send("guild.create", args);
                typed[0] = "";
                typed[1] = "";
            }).bounds(left + 218, top + 39, 70, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.guild.accept"),
                    b -> send("guild.accept", new JsonObject())).bounds(left, top + 64, 90, 18).build());
            return;
        }
        addRenderableWidget(Button.builder(Component.translatable(battleView ? "screen.eternalrealm.guild.view_members"
                : "screen.eternalrealm.guild.view_battle"), b -> {
            battleView = !battleView;
            if (battleView) {
                send("guild.battle.view", new JsonObject());
            }
            rebuildWidgets();
        }).bounds(left + CONTENT_WIDTH - 78, top + 50, 78, 16).build());
        if (battleView) {
            initBattle();
            return;
        }
        first = box(left, top + 50, 120, "screen.eternalrealm.party.name", 16, 0);
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.party.invite"), b -> {
            JsonObject args = new JsonObject();
            args.addProperty("name", first.getValue().trim());
            send("guild.invite", args);
            first.setValue("");
            typed[0] = "";
        }).bounds(left + 124, top + 49, 60, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.guild.leave"),
                b -> send("guild.leave", new JsonObject())).bounds(left, top + PANEL_HEIGHT - 20, 80, 18).build());
        String myRank = ClientUiState.stringOr(guild.get(), "myRank", "member");
        if (!"member".equals(myRank)) {
            int y = top + 74;
            for (JsonElement element : guild.get().getAsJsonArray("members")) {
                JsonObject m = element.getAsJsonObject();
                String id = ClientUiState.stringOr(m, "id", "");
                if ("master".equals(myRank)) {
                    addRenderableWidget(Button.builder(Component.literal("▲"), b -> idAction("guild.promote", id))
                            .bounds(left + CONTENT_WIDTH - 74, y, 16, 12).build());
                    addRenderableWidget(Button.builder(Component.literal("▼"), b -> idAction("guild.demote", id))
                            .bounds(left + CONTENT_WIDTH - 56, y, 16, 12).build());
                }
                addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.party.kick"),
                        b -> idAction("guild.kick", id)).bounds(left + CONTENT_WIDTH - 38, y, 36, 12).build());
                y += 14;
                if (y > top + PANEL_HEIGHT - 36) {
                    break;
                }
            }
        }
    }

    /** The battle view's buttons: answer challenges, join or leave the battle, challenge a guild. */
    private void initBattle() {
        Optional<JsonObject> doc = ClientUiState.get(BATTLE);
        if (doc.isEmpty() || !doc.get().has("record")) {
            return;
        }
        JsonObject d = doc.get();
        boolean leads = d.has("leads") && d.get("leads").getAsBoolean();
        int y = top + 82;
        if (d.has("battle")) {
            JsonObject b = d.getAsJsonObject("battle");
            String phase = ClientUiState.stringOr(b, "phase", "");
            boolean joined = !ClientUiState.stringOr(b, "mySide", "").isEmpty();
            if ("muster".equals(phase) && !joined) {
                addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.guild_battle.join"),
                        btn -> send("guild.battle.join", new JsonObject())).bounds(left + CONTENT_WIDTH - 90, y, 90, 16)
                        .build());
            } else if (joined && !"ended".equals(phase)) {
                addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.guild_battle.leave"),
                        btn -> send("guild.battle.leave", new JsonObject())).bounds(left + CONTENT_WIDTH - 90, y, 90,
                        16).build());
            }
            return;
        }
        if (d.has("outgoing") && leads) {
            addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.guild_battle.withdraw"),
                    btn -> send("guild.battle.withdraw", new JsonObject())).bounds(left + CONTENT_WIDTH - 90, y, 90, 16)
                    .build());
            y += 20;
        }
        JsonArray incoming = d.has("incoming") ? d.getAsJsonArray("incoming") : new JsonArray();
        for (JsonElement element : incoming) {
            JsonObject c = element.getAsJsonObject();
            String tag = ClientUiState.stringOr(c, "tag", "");
            if (leads) {
                addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.guild_battle.accept"),
                        btn -> tagAction("guild.battle.accept", tag)).bounds(left + CONTENT_WIDTH - 104, y, 50, 14)
                        .build());
                addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.guild_battle.decline"),
                        btn -> tagAction("guild.battle.decline", tag)).bounds(left + CONTENT_WIDTH - 52, y, 50, 14)
                        .build());
            }
            y += 16;
            if (y > top + 140) {
                break;
            }
        }
        if (!leads || d.has("outgoing") || ClientUiState.longOr(d, "resting", 0) > 0) {
            return;
        }
        int gy = Math.max(y + 14, top + 112);
        JsonArray guilds = d.has("guilds") ? d.getAsJsonArray("guilds") : new JsonArray();
        for (JsonElement element : guilds) {
            JsonObject g = element.getAsJsonObject();
            if (gy > top + PANEL_HEIGHT - 16) {
                break;
            }
            if (!(g.has("busy") && g.get("busy").getAsBoolean())) {
                String tag = ClientUiState.stringOr(g, "tag", "");
                addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.guild_battle.challenge"),
                        btn -> tagAction("guild.battle.challenge", tag)).bounds(left + CONTENT_WIDTH - 60, gy - 2, 60,
                        14).build());
            }
            gy += 15;
        }
    }

    private EditBox box(int x, int y, int width, String hint, int max, int slot) {
        EditBox box = new EditBox(font, x, y, width, 16, Component.translatable(hint));
        box.setMaxLength(max);
        box.setHint(Component.translatable(hint));
        box.setValue(typed[slot]);
        box.setResponder(value -> typed[slot] = value);
        return addRenderableWidget(box);
    }

    private static String guildJson() {
        return ClientUiState.get("guild").map(JsonObject::toString).orElse("");
    }

    private static String battleJson() {
        return ClientUiState.get(BATTLE).map(json -> {
            JsonObject copy = json.deepCopy();
            copy.remove("nonce");
            if (copy.has("battle")) {
                copy.getAsJsonObject("battle").remove("left");
            }
            if (copy.has("incoming")) {
                for (JsonElement c : copy.getAsJsonArray("incoming")) {
                    c.getAsJsonObject().remove("left");
                }
            }
            if (copy.has("outgoing")) {
                copy.getAsJsonObject("outgoing").remove("left");
            }
            copy.remove("resting");
            return copy.toString();
        }).orElse("");
    }

    private void idAction(String action, String id) {
        JsonObject args = new JsonObject();
        args.addProperty("id", id);
        send(action, args);
    }

    private void tagAction(String action, String tag) {
        JsonObject args = new JsonObject();
        args.addProperty("tag", tag);
        send(action, args);
    }

    private void send(String action, JsonObject args) {
        long now = System.currentTimeMillis();
        if (now - lastRequest < 300L && !action.endsWith(".view")) {
            return;
        }
        lastRequest = now;
        ERNetwork.sendToServer(new UiActionPacket(action, args));
    }

    @Override
    public void tick() {
        super.tick();
        if (first != null) {
            first.tick();
        }
        if (second != null) {
            second.tick();
        }
        long now = System.currentTimeMillis();
        if (battleView && now - lastBattleRequest > 2_000L) {
            // the battle page's clocks and counts move on: ask again every two seconds while it is open
            lastBattleRequest = now;
            ERNetwork.sendToServer(new UiActionPacket("guild.battle.view", new JsonObject()));
        }
        if (!guildJson().equals(seenGuild) || !battleJson().equals(seenBattle)) {
            rebuildWidgets();
        }
    }

    @Override
    protected void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Optional<JsonObject> guild = ClientUiState.get("guild");
        if (guild.isEmpty()) {
            return;
        }
        JsonObject g = guild.get();
        if (!g.has("name")) {
            drawWrapped(graphics, Component.translatable("screen.eternalrealm.guild.none"), left, top + 14,
                    CONTENT_WIDTH, MUTED, 2);
            JsonArray invites = g.has("invites") ? g.getAsJsonArray("invites") : new JsonArray();
            int y = top + 88;
            for (JsonElement invite : invites) {
                graphics.drawString(font, Component.translatable("screen.eternalrealm.guild.invited", invite.getAsString()),
                        left, y, 0x7CFC7C);
                y += 11;
            }
            return;
        }
        renderLevel(graphics, g);
        if (battleView) {
            renderBattle(graphics);
            return;
        }
        int y = top + 74;
        for (JsonElement element : g.getAsJsonArray("members")) {
            JsonObject m = element.getAsJsonObject();
            boolean online = m.has("online") && m.get("online").getAsBoolean();
            graphics.drawString(font, Component.translatable("guild_rank.eternalrealm." + ClientUiState.stringOr(m, "rank", "member"))
                    .append("  " + ClientUiState.stringOr(m, "name", "?")), left + 2, y + 2, online ? 0xFFFFFF : 0x707070);
            y += 14;
            if (y > top + PANEL_HEIGHT - 36) {
                break;
            }
        }
    }

    /** The header: name, tag, level and standing, members; the merit bar to the next level; the perks. */
    private void renderLevel(GuiGraphics graphics, JsonObject g) {
        int level = ClientUiState.intOr(g, "level", 1);
        int standingColor = 0xFF000000 | ClientUiState.intOr(g, "standingColor", 0xAAAAAA);
        Component standing = Component.translatable("guild_standing.eternalrealm." + ClientUiState.stringOr(g,
                "standing", "fledgling"));
        Component head = Component.translatable("screen.eternalrealm.guild.header_level",
                ClientUiState.stringOr(g, "name", "?"), ClientUiState.stringOr(g, "tag", "?"), level);
        graphics.drawString(font, head, left, top + 14, TITLE_COLOR);
        graphics.drawString(font, standing, left + font.width(head) + 6, top + 14, standingColor);
        Component members = Component.translatable("screen.eternalrealm.guild.members_count",
                g.getAsJsonArray("members").size(), ClientUiState.intOr(g, "cap", 30));
        graphics.drawString(font, members, left + CONTENT_WIDTH - font.width(members), top + 14, MUTED);
        // the merit bar
        long merit = ClientUiState.longOr(g, "contribution", 0);
        long floor = ClientUiState.longOr(g, "levelFloor", 0);
        long next = ClientUiState.longOr(g, "levelNext", -1);
        int barX = left;
        int barY = top + 27;
        int barW = 200;
        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + 6, 0xFF3A3020);
        graphics.fill(barX, barY, barX + barW, barY + 5, 0xFF15120C);
        double fraction = next < 0 ? 1.0 : Math.max(0.0, Math.min(1.0, (double) (merit - floor) / Math.max(1, next - floor)));
        graphics.fill(barX, barY, barX + (int) Math.round(barW * fraction), barY + 5, 0xFFE0B040);
        String progress = next < 0 ? Component.translatable("screen.eternalrealm.guild.merit_max", merit).getString()
                : Component.translatable("screen.eternalrealm.guild.merit", String.format(Locale.ROOT, "%,d", merit),
                String.format(Locale.ROOT, "%,d", next)).getString();
        graphics.drawString(font, progress, barX + barW + 6, barY - 1, 0xFFD8C8A0);
        // the perks now and at the next level
        Component perks = Component.translatable("screen.eternalrealm.guild.perks", ClientUiState.stringOr(g, "exp",
                "0%"), ClientUiState.stringOr(g, "discount", "0%"));
        graphics.drawString(font, perks, left, top + 37, GOOD);
        if (g.has("nextExp")) {
            Component nextPerks = Component.translatable("screen.eternalrealm.guild.next_perks", level + 1,
                    ClientUiState.stringOr(g, "nextExp", ""), ClientUiState.intOr(g, "nextCap", 0));
            graphics.drawString(font, nextPerks, left + CONTENT_WIDTH - font.width(nextPerks), top + 37, MUTED);
        }
        graphics.fill(left, top + 47, left + CONTENT_WIDTH, top + 48, 0x40FFFFFF);
    }

    /** The battle view: record, the battle under way or the challenges, the guilds online. */
    private void renderBattle(GuiGraphics graphics) {
        Optional<JsonObject> doc = ClientUiState.get(BATTLE);
        if (doc.isEmpty() || !doc.get().has("record")) {
            drawWrapped(graphics, Component.translatable("screen.eternalrealm.guild_battle.loading"), left, top + 72,
                    CONTENT_WIDTH, MUTED, 2);
            return;
        }
        JsonObject d = doc.get();
        JsonObject record = d.getAsJsonObject("record");
        graphics.drawString(font, Component.translatable("screen.eternalrealm.guild_battle.record",
                ClientUiState.intOr(record, "w", 0), ClientUiState.intOr(record, "d", 0),
                ClientUiState.intOr(record, "l", 0), ClientUiState.intOr(record, "k", 0)), left, top + 54, GOLD);
        int y = top + 68;
        if (d.has("battle")) {
            JsonObject b = d.getAsJsonObject("battle");
            String phase = ClientUiState.stringOr(b, "phase", "muster");
            long seconds = ClientUiState.longOr(b, "left", 0);
            graphics.drawString(font, Component.translatable("screen.eternalrealm.guild_battle.phase." + phase,
                    seconds / 60, String.format(Locale.ROOT, "%02d", seconds % 60)), left, y, 0xFFFFFFFF);
            y += 14;
            JsonObject red = b.getAsJsonObject("red");
            JsonObject blue = b.getAsJsonObject("blue");
            String score = ClientUiState.intOr(red, "score", 0) + "  -  " + ClientUiState.intOr(blue, "score", 0);
            graphics.drawString(font, "【" + ClientUiState.stringOr(red, "tag", "") + "】 "
                    + ClientUiState.stringOr(red, "name", ""), left, y + 18, RED);
            graphics.drawString(font, "【" + ClientUiState.stringOr(blue, "tag", "") + "】 "
                    + ClientUiState.stringOr(blue, "name", ""), left + CONTENT_WIDTH / 2 + 8, y + 18, BLUE);
            graphics.drawCenteredString(font, score, left + CONTENT_WIDTH / 2, y + 18, 0xFFFFFFFF);
            int max = ClientUiState.intOr(b, "max", 10);
            fighters(graphics, red, left, y + 32, RED, max);
            fighters(graphics, blue, left + CONTENT_WIDTH / 2 + 8, y + 32, BLUE, max);
            if (b.has("winner")) {
                String winner = ClientUiState.stringOr(b, "winner", "draw");
                Component result = "draw".equals(winner) ? Component.translatable("screen.eternalrealm.guild_battle.draw")
                        : Component.translatable("screen.eternalrealm.guild_battle.winner",
                        ClientUiState.stringOr("red".equals(winner) ? red : blue, "name", ""));
                graphics.drawCenteredString(font, result, left + CONTENT_WIDTH / 2, top + PANEL_HEIGHT - 14, GOLD);
            }
            return;
        }
        long resting = ClientUiState.longOr(d, "resting", 0);
        if (resting > 0) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.guild_battle.resting",
                    resting / 60, String.format(Locale.ROOT, "%02d", resting % 60)), left, y, MUTED);
            y += 12;
        }
        y = Math.max(y, top + 84) - 2;
        if (d.has("outgoing")) {
            JsonObject c = d.getAsJsonObject("outgoing");
            graphics.drawString(font, Component.translatable("screen.eternalrealm.guild_battle.outgoing",
                    ClientUiState.stringOr(c, "name", ""), ClientUiState.longOr(c, "left", 0)), left, y + 4, GOLD);
            y += 20;
        }
        JsonArray incoming = d.has("incoming") ? d.getAsJsonArray("incoming") : new JsonArray();
        for (JsonElement element : incoming) {
            JsonObject c = element.getAsJsonObject();
            graphics.drawString(font, Component.translatable("screen.eternalrealm.guild_battle.incoming",
                    ClientUiState.stringOr(c, "name", ""), ClientUiState.longOr(c, "left", 0)), left, y + 3, GOLD);
            y += 16;
            if (y > top + 140) {
                break;
            }
        }
        int gy = Math.max(y + 14, top + 112);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.guild_battle.online_guilds"), left,
                gy - 12, MUTED);
        JsonArray guilds = d.has("guilds") ? d.getAsJsonArray("guilds") : new JsonArray();
        if (guilds.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.guild_battle.no_guilds"), left, gy,
                    MUTED);
        }
        for (JsonElement element : guilds) {
            if (gy > top + PANEL_HEIGHT - 16) {
                break;
            }
            JsonObject o = element.getAsJsonObject();
            boolean busy = o.has("busy") && o.get("busy").getAsBoolean();
            Component line = Component.translatable("screen.eternalrealm.guild_battle.guild_line",
                    ClientUiState.stringOr(o, "name", ""), ClientUiState.stringOr(o, "tag", ""),
                    ClientUiState.intOr(o, "level", 1), ClientUiState.intOr(o, "online", 0),
                    ClientUiState.intOr(o, "w", 0), ClientUiState.intOr(o, "l", 0));
            graphics.drawString(font, line, left + 2, gy + 1, busy ? 0xFF707070 : 0xFFFFFFFF);
            gy += 15;
        }
    }

    private void fighters(GuiGraphics graphics, JsonObject side, int x, int y, int color, int max) {
        JsonArray list = side.has("fighters") ? side.getAsJsonArray("fighters") : new JsonArray();
        graphics.drawString(font, Component.translatable("screen.eternalrealm.guild_battle.fighters", list.size(), max),
                x, y, 0xFFB0A890);
        int fy = y + 11;
        for (JsonElement element : list) {
            JsonObject f = element.getAsJsonObject();
            boolean present = !f.has("present") || f.get("present").getAsBoolean();
            String text = ClientUiState.stringOr(f, "name", "?") + "  KO " + ClientUiState.intOr(f, "ko", 0);
            graphics.drawString(font, text, x + 4, fy, present ? color : 0xFF606060);
            fy += 10;
            if (fy > top + PANEL_HEIGHT - 18) {
                break;
            }
        }
    }
}
