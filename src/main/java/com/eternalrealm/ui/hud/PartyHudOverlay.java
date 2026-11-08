package com.eternalrealm.ui.hud;

import com.eternalrealm.client.ClientUiState;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Optional;

/** Party frames on the left edge: name, level, job emblem colour and a health bar per member. */
public final class PartyHudOverlay implements IGuiOverlay {
    private static final int WIDTH = 92;
    private static final int ROW = 17;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        Optional<JsonObject> party = ClientUiState.get("party");
        if (party.isEmpty() || !party.get().has("members")) {
            return;
        }
        JsonArray members = party.get().getAsJsonArray("members");
        Font font = minecraft.font;
        int x = 4;
        int y = screenHeight / 2 - members.size() * ROW / 2;
        boolean raid = party.get().has("raid") && party.get().get("raid").getAsBoolean();
        int shown = 0;
        for (JsonElement element : members) {
            if (raid && shown >= 10) {
                break;
            }
            JsonObject member = element.getAsJsonObject();
            boolean online = member.has("online") && member.get("online").getAsBoolean();
            String name = ClientUiState.stringOr(member, "name", "?");
            boolean leader = member.has("leader") && member.get("leader").getAsBoolean();
            graphics.fill(x, y, x + WIDTH, y + ROW - 2, 0x80000000);
            int nameColor = online ? jobColor(ClientUiState.stringOr(member, "job", "none")) : 0x707070;
            String label = (leader ? "★" : "") + name;
            if (online && member.has("level")) {
                label += " Lv" + member.get("level").getAsInt();
            }
            graphics.drawString(font, font.plainSubstrByWidth(label, WIDTH - 4), x + 2, y + 1, nameColor);
            if (online && member.has("hp") && member.has("maxHp")) {
                double hp = member.get("hp").getAsDouble();
                double max = Math.max(1.0, member.get("maxHp").getAsDouble());
                double ratio = Math.max(0.0, Math.min(1.0, hp / max));
                int barWidth = WIDTH - 4;
                graphics.fill(x + 2, y + 11, x + 2 + barWidth, y + 14, 0xFF3A1010);
                int color = ratio > 0.5 ? 0xFF4CD964 : ratio > 0.25 ? 0xFFE8C14A : 0xFFE04A3A;
                graphics.fill(x + 2, y + 11, x + 2 + (int) Math.round(barWidth * ratio), y + 14, color);
            }
            y += ROW;
            shown++;
        }
    }

    static int jobColor(String job) {
        return switch (job) {
            case "knight" -> 0x7FA8FF;
            case "assassin" -> 0xE05050;
            case "mage" -> 0xD08CFF;
            case "ranger" -> 0x7CE07C;
            case "priest" -> 0xFFD966;
            case "spellblade" -> 0x66E6E6;
            default -> 0xFFFFFF;
        };
    }
}
