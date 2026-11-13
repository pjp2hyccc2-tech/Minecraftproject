package com.eternalrealm.ui.screen;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Optional;

/**
 * Quest page: the active quests with their objectives (turn in when ready, guide there with the yellow dots, abandon) on
 * the left, the quests that can be accepted now on the right. Main quests are gold, side quests aqua, dailies green.
 */
public final class QuestScreen extends JournalScreen {
    private static final int COLUMN = CONTENT_WIDTH / 2 - 4;
    private String seen;
    private long lastRequest;

    public QuestScreen() {
        super(Tab.QUESTS);
    }

    @Override
    protected void initPage() {
        if (seen == null) {
            ERNetwork.sendToServer(new UiActionPacket("quest.view", new JsonObject()));
        }
        seen = json();
        Optional<JsonObject> data = ClientUiState.get("quests");
        if (data.isEmpty() || !data.get().has("active")) {
            return;
        }
        int y = top + 26;
        String guided = ClientUiState.stringOr(data.get(), "guided", "");
        for (JsonElement element : data.get().getAsJsonArray("active")) {
            JsonObject quest = element.getAsJsonObject();
            String id = ClientUiState.stringOr(quest, "id", "");
            int lines = quest.getAsJsonArray("objectives").size();
            int buttonY = y + 11 + lines * 10;
            if (quest.has("ready") && quest.get("ready").getAsBoolean()) {
                addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.quest.turn_in"),
                        b -> send("quest.turn_in", id)).bounds(left, buttonY, 50, 14).build());
            }
            // 案内: the yellow dots lead to this quest's next place; pressed again, no dots
            boolean guiding = id.equals(guided);
            addRenderableWidget(Button.builder(guiding
                            ? Component.translatable("screen.eternalrealm.quest.guiding").withStyle(
                            net.minecraft.ChatFormatting.GOLD)
                            : Component.translatable("screen.eternalrealm.quest.guide"),
                    b -> send("quest.guide", guiding ? "-" : id))
                    .bounds(left + COLUMN - 104, buttonY, 50, 14)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(guiding
                            ? "screen.eternalrealm.quest.guiding.tooltip" : "screen.eternalrealm.quest.guide.tooltip")))
                    .build());
            addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.quest.abandon"),
                    b -> send("quest.abandon", id)).bounds(left + COLUMN - 50, buttonY, 50, 14).build());
            y = buttonY + 18;
            if (y > top + PANEL_HEIGHT - 30) {
                break;
            }
        }
        y = top + 26;
        for (JsonElement element : data.get().getAsJsonArray("available")) {
            JsonObject quest = element.getAsJsonObject();
            String id = ClientUiState.stringOr(quest, "id", "");
            addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.quest.accept"),
                    b -> send("quest.accept", id)).bounds(left + CONTENT_WIDTH - 44, y + 1, 44, 14).build());
            y += 30;
            if (y > top + PANEL_HEIGHT - 30) {
                break;
            }
        }
    }

    private static String json() {
        return ClientUiState.get("quests").map(JsonObject::toString).orElse("");
    }

    private void send(String action, String id) {
        long now = System.currentTimeMillis();
        if (now - lastRequest < 300L) {
            return;
        }
        lastRequest = now;
        JsonObject args = new JsonObject();
        args.addProperty("id", id);
        ERNetwork.sendToServer(new UiActionPacket(action, args));
    }

    @Override
    public void tick() {
        super.tick();
        if (!json().equals(seen)) {
            rebuildWidgets();
        }
    }

    @Override
    protected void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Optional<JsonObject> data = ClientUiState.get("quests");
        graphics.drawString(font, Component.translatable("screen.eternalrealm.quest.active",
                data.map(d -> d.has("active") ? d.getAsJsonArray("active").size() : 0).orElse(0),
                data.map(d -> ClientUiState.intOr(d, "max", 5)).orElse(5)), left, top + 14, TITLE_COLOR);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.quest.available"),
                left + COLUMN + 8, top + 14, TITLE_COLOR);
        if (data.isEmpty() || !data.get().has("active")) {
            graphics.drawString(font, Component.translatable("hud.eternalrealm.loading"), left, top + 30, MUTED);
            return;
        }
        int y = top + 26;
        JsonArray active = data.get().getAsJsonArray("active");
        if (active.isEmpty()) {
            drawWrapped(graphics, Component.translatable("screen.eternalrealm.quest.none_active"), left, y, COLUMN,
                    MUTED, 3);
        }
        for (JsonElement element : active) {
            JsonObject quest = element.getAsJsonObject();
            graphics.drawString(font, title(quest), left, y, color(quest));
            int line = y + 11;
            for (JsonElement objectiveElement : quest.getAsJsonArray("objectives")) {
                JsonObject objective = objectiveElement.getAsJsonObject();
                boolean done = objective.has("done") && objective.get("done").getAsBoolean();
                MutableComponent text = Component.literal(done ? "✔ " : "- ").append(objectiveText(objective));
                graphics.drawString(font, font.substrByWidth(text, COLUMN - 4).getString(), left + 4, line,
                        done ? 0x7CFC7C : 0xD0D0D0);
                line += 10;
            }
            y = line + 18;
            if (y > top + PANEL_HEIGHT - 30) {
                break;
            }
        }
        y = top + 26;
        int x = left + COLUMN + 8;
        for (JsonElement element : data.get().getAsJsonArray("available")) {
            JsonObject quest = element.getAsJsonObject();
            graphics.drawString(font, font.substrByWidth(title(quest), COLUMN - 52).getString(), x, y + 4, color(quest));
            graphics.drawString(font, Component.translatable("screen.eternalrealm.quest.reward_line",
                    ClientUiState.intOr(quest, "level", 1), ClientUiState.longOr(quest, "exp", 0),
                    ClientUiState.longOr(quest, "gold", 0)), x, y + 15, MUTED);
            y += 30;
            if (y > top + PANEL_HEIGHT - 30) {
                break;
            }
        }
    }

    private static Component title(JsonObject quest) {
        return Component.translatable("quest.eternalrealm." + ClientUiState.stringOr(quest, "id", "?"));
    }

    private static int color(JsonObject quest) {
        return switch (ClientUiState.stringOr(quest, "kind", "main")) {
            case "side" -> 0x6AD6FF;
            case "daily" -> 0x7CFC7C;
            default -> 0xFFD700;
        };
    }

    /** An objective line from the server's key and {"k"|"t"} arguments. */
    static Component objectiveText(JsonObject objective) {
        JsonArray args = objective.getAsJsonArray("args");
        Object[] values = new Object[args.size()];
        for (int i = 0; i < values.length; i++) {
            JsonObject arg = args.get(i).getAsJsonObject();
            values[i] = arg.has("k") ? Component.translatable(arg.get("k").getAsString())
                    : ClientUiState.stringOr(arg, "t", "");
        }
        return Component.translatable(ClientUiState.stringOr(objective, "key", ""), values);
    }
}
