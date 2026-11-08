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

import java.util.Optional;

/** Guild page: create (tag + name) or join by invite; members with ranks, invite, kick/promote/demote, leave. */
public final class GuildScreen extends JournalScreen {
    private EditBox first;
    private EditBox second;
    private final String[] typed = {"", ""};
    private String seenGuild;
    private long lastRequest;

    public GuildScreen() {
        super(Tab.GUILD);
    }

    @Override
    protected void initPage() {
        if (seenGuild == null) {
            send("guild.view", new JsonObject());
        }
        seenGuild = guildJson();
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
        first = box(left, top + 30, 120, "screen.eternalrealm.party.name", 16, 0);
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.party.invite"), b -> {
            JsonObject args = new JsonObject();
            args.addProperty("name", first.getValue().trim());
            send("guild.invite", args);
            first.setValue("");
            typed[0] = "";
        }).bounds(left + 124, top + 29, 60, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.guild.leave"),
                b -> send("guild.leave", new JsonObject())).bounds(left, top + PANEL_HEIGHT - 20, 80, 18).build());
        String myRank = ClientUiState.stringOr(guild.get(), "myRank", "member");
        if (!"member".equals(myRank)) {
            int y = top + 56;
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

    private void idAction(String action, String id) {
        JsonObject args = new JsonObject();
        args.addProperty("id", id);
        send(action, args);
    }

    private void send(String action, JsonObject args) {
        long now = System.currentTimeMillis();
        if (now - lastRequest < 300L && !action.equals("guild.view")) {
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
        if (!guildJson().equals(seenGuild)) {
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
        graphics.drawString(font, Component.translatable("screen.eternalrealm.guild.header",
                ClientUiState.stringOr(g, "name", "?"), ClientUiState.stringOr(g, "tag", "?"),
                ClientUiState.intOr(g, "level", 1), g.getAsJsonArray("members").size(), ClientUiState.intOr(g, "cap", 30),
                ClientUiState.longOr(g, "contribution", 0)), left, top + 14, TITLE_COLOR);
        int y = top + 56;
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
}
