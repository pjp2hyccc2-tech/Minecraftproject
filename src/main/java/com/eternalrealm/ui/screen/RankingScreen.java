package com.eternalrealm.ui.screen;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.eternalrealm.ranking.RankingCategory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Optional;

/** Ranking page: category buttons and the top 20 of the selected category, with the player's own rank. */
public final class RankingScreen extends JournalScreen {
    private static RankingCategory selected = RankingCategory.OVERALL;

    public RankingScreen() {
        super(Tab.RANKING);
    }

    @Override
    protected void initPage() {
        int x = left;
        int y = top + 12;
        for (RankingCategory category : RankingCategory.values()) {
            if (category == RankingCategory.BOSS_TIME_ATTACK) {
                continue;
            }
            Button button = Button.builder(Component.translatable(category.translationKey()), b -> select(category))
                    .bounds(x, y, 78, 14).build();
            button.active = category != selected;
            addRenderableWidget(button);
            x += 80;
            if (x + 78 > left + CONTENT_WIDTH) {
                x = left;
                y += 16;
            }
        }
        select(selected);
    }

    private void select(RankingCategory category) {
        boolean changed = category != selected;
        selected = category;
        JsonObject args = new JsonObject();
        args.addProperty("category", category.id());
        ERNetwork.sendToServer(new UiActionPacket("ranking.view", args));
        if (changed) {
            rebuildWidgets();
        }
    }

    @Override
    protected void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Optional<JsonObject> data = ClientUiState.get("ranking");
        int y = top + 64;
        if (data.isEmpty() || !selected.id().equals(ClientUiState.stringOr(data.get(), "category", ""))) {
            graphics.drawString(font, Component.translatable("hud.eternalrealm.loading"), left, y, MUTED);
            return;
        }
        JsonObject ranking = data.get();
        graphics.drawString(font, Component.translatable("screen.eternalrealm.ranking.season",
                ClientUiState.stringOr(ranking, "season", "?"), ClientUiState.intOr(ranking, "day", 1),
                ClientUiState.intOr(ranking, "days", 14)), left, top + 52, 0xB0D0FF);
        for (JsonElement element : ranking.getAsJsonArray("entries")) {
            JsonObject entry = element.getAsJsonObject();
            int rank = ClientUiState.intOr(entry, "rank", 0);
            boolean self = entry.has("self") && entry.get("self").getAsBoolean();
            int color = rank == 1 ? 0xFFD700 : rank == 2 ? 0xE0E0E0 : rank == 3 ? 0xE0A060 : self ? 0x80E0FF : 0xC0C0C0;
            graphics.drawString(font, String.format(Locale.ROOT, "%2d. %s", rank, ClientUiState.stringOr(entry, "name", "?")),
                    left + 2, y, color);
            String score = String.format(Locale.ROOT, "%,d", ClientUiState.longOr(entry, "score", 0));
            graphics.drawString(font, score, left + CONTENT_WIDTH - font.width(score) - 4, y, color);
            y += 10;
            if (y > top + PANEL_HEIGHT - 22) {
                break;
            }
        }
        if (ranking.has("own")) {
            JsonObject own = ranking.getAsJsonObject("own");
            graphics.drawString(font, Component.translatable("screen.eternalrealm.ranking.own",
                    ClientUiState.intOr(own, "rank", 0), String.format(Locale.ROOT, "%,d", ClientUiState.longOr(own, "score", 0))),
                    left, top + PANEL_HEIGHT - 10, 0x80E0FF);
        }
    }
}
