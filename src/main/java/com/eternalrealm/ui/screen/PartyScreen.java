package com.eternalrealm.ui.screen;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/** Party page: members with health, invite by name, leave, raid conversion, kick/promote for the leader. */
public final class PartyScreen extends JournalScreen {
    private EditBox nameBox;
    private String typedName = "";
    private String seenStructure = "";
    private long lastRequest;

    public PartyScreen() {
        super(Tab.PARTY);
    }

    @Override
    protected void initPage() {
        nameBox = new EditBox(font, left, top + 16, 120, 16, Component.translatable("screen.eternalrealm.party.name"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.translatable("screen.eternalrealm.party.name"));
        nameBox.setValue(typedName);
        nameBox.setResponder(value -> typedName = value);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.party.invite"), b -> {
            String name = nameBox.getValue().trim();
            if (!name.isEmpty()) {
                send("party.invite", "name", name);
                nameBox.setValue("");
            }
        }).bounds(left + 124, top + 15, 60, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.party.accept"),
                b -> send("party.accept", null, null)).bounds(left + 188, top + 15, 64, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.party.leave"),
                b -> send("party.leave", null, null)).bounds(left, top + PANEL_HEIGHT - 20, 80, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.party.raid"),
                b -> send("party.raid", null, null)).bounds(left + 84, top + PANEL_HEIGHT - 20, 100, 18).build());
        Optional<JsonObject> party = ClientUiState.get("party");
        if (party.isPresent() && party.get().has("members")) {
            boolean iAmLeader = false;
            String me = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getUUID().toString();
            for (JsonElement element : party.get().getAsJsonArray("members")) {
                JsonObject member = element.getAsJsonObject();
                if (ClientUiState.stringOr(member, "id", "").equals(me) && member.has("leader")
                        && member.get("leader").getAsBoolean()) {
                    iAmLeader = true;
                }
            }
            int y = top + 44;
            for (JsonElement element : party.get().getAsJsonArray("members")) {
                JsonObject member = element.getAsJsonObject();
                String id = ClientUiState.stringOr(member, "id", "");
                if (member.has("companion")) {
                    String who = ClientUiState.stringOr(member, "companion", "");
                    addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.party.part"),
                            b -> send("traveller.dismiss", "id", who)).bounds(left + CONTENT_WIDTH - 52, y, 50, 14)
                            .build());
                } else if (iAmLeader && !id.equals(me)) {
                    addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.party.promote"),
                            b -> send("party.promote", "id", id)).bounds(left + CONTENT_WIDTH - 104, y, 50, 14).build());
                    addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.party.kick"),
                            b -> send("party.kick", "id", id)).bounds(left + CONTENT_WIDTH - 52, y, 50, 14).build());
                }
                y += 18;
                if (y > top + PANEL_HEIGHT - 40) {
                    break;
                }
            }
        }
        seenStructure = structure();
    }

    /** Membership, leadership and raid state: the page rebuilds its buttons only when these change. */
    private static String structure() {
        Optional<JsonObject> party = ClientUiState.get("party");
        if (party.isEmpty() || !party.get().has("members")) {
            return "";
        }
        StringBuilder key = new StringBuilder(party.get().has("raid") && party.get().get("raid").getAsBoolean() ? "R" : "P");
        for (JsonElement element : party.get().getAsJsonArray("members")) {
            JsonObject member = element.getAsJsonObject();
            key.append(ClientUiState.stringOr(member, "id", "")).append(member.has("leader")
                    && member.get("leader").getAsBoolean() ? '*' : ',');
        }
        return key.toString();
    }

    private void send(String action, String key, String value) {
        long now = System.currentTimeMillis();
        if (now - lastRequest < 300L) {
            return;
        }
        lastRequest = now;
        JsonObject args = new JsonObject();
        if (key != null) {
            args.addProperty(key, value);
        }
        ERNetwork.sendToServer(new UiActionPacket(action, args));
    }

    @Override
    public void tick() {
        super.tick();
        nameBox.tick();
        if (!structure().equals(seenStructure)) {
            boolean focused = nameBox.isFocused();
            rebuildWidgets();
            if (focused) {
                setFocused(nameBox);
            }
        }
    }

    @Override
    protected void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Optional<JsonObject> party = ClientUiState.get("party");
        if (party.isEmpty() || !party.get().has("members")) {
            drawWrapped(graphics, Component.translatable("screen.eternalrealm.party.none"), left, top + 44,
                    CONTENT_WIDTH, MUTED, 4);
            return;
        }
        JsonArray members = party.get().getAsJsonArray("members");
        boolean raid = party.get().has("raid") && party.get().get("raid").getAsBoolean();
        graphics.drawString(font, Component.translatable(raid ? "screen.eternalrealm.party.raid_title"
                : "screen.eternalrealm.party.title", members.size(), ClientUiState.intOr(party.get(), "capacity", 5)),
                left, top + 34, TITLE_COLOR);
        int y = top + 44;
        for (JsonElement element : members) {
            JsonObject member = element.getAsJsonObject();
            boolean online = member.has("online") && member.get("online").getAsBoolean();
            boolean companion = member.has("companion");
            String name = member.has("nameKey") ? Component.translatable(ClientUiState.stringOr(member, "nameKey", ""))
                    .getString() : ClientUiState.stringOr(member, "name", "?");
            String label = (member.has("leader") && member.get("leader").getAsBoolean() ? "★ " : companion ? "✦ " : "")
                    + name;
            graphics.drawString(font, label, left + 2, y + 3, companion ? 0xFFE8B0 : online ? 0xFFFFFF : 0x707070);
            if (online && member.has("level")) {
                Component role = companion ? Component.translatable(ClientUiState.stringOr(member, "titleKey", ""))
                        : Component.translatable("job.eternalrealm." + ClientUiState.stringOr(member, "job", "none"));
                graphics.drawString(font, role.copy().append(" Lv" + member.get("level").getAsInt()), left + 100,
                        y + 3, companion ? 0xFFD27A : 0xB0D0FF);
            }
            if (online && member.has("hp")) {
                graphics.drawString(font, String.format(java.util.Locale.ROOT, "%.0f/%.0f",
                        member.get("hp").getAsDouble(), member.get("maxHp").getAsDouble()), left + 190, y + 3, 0x7CFC7C);
            }
            y += 18;
            if (y > top + PANEL_HEIGHT - 40) {
                break;
            }
        }
    }
}
