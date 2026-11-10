package com.eternalrealm.ui.hud;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.hud.HudLayout;
import com.eternalrealm.hud.Rect;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Optional;

/**
 * Party frames under the player frame: name, level, job colour and a health bar per member. Where the layout ({@link
 * HudFrame}) has less room the rows get compact and then split into two columns.
 */
public final class PartyHudOverlay implements IGuiOverlay {
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
        HudLayout.Layout layout = HudFrame.layout();
        Rect area = layout.party();
        if (area.isEmpty() || layout.partyPerColumn() <= 0) {
            return;
        }
        JsonArray members = party.get().getAsJsonArray("members");
        Font font = minecraft.font;
        int rowHeight = layout.partyRow();
        boolean compact = rowHeight < HudLayout.PARTY_ROW;
        int width = HudLayout.PARTY_WIDTH;
        int shown = 0;
        for (JsonElement element : members) {
            if (shown >= layout.partyShown()) {
                break;
            }
            int column = shown / layout.partyPerColumn();
            int x = area.x() + column * (width + HudLayout.GAP);
            int y = area.y() + (shown % layout.partyPerColumn()) * rowHeight;
            JsonObject member = element.getAsJsonObject();
            boolean online = member.has("online") && member.get("online").getAsBoolean();
            String name = ClientUiState.stringOr(member, "name", "?");
            boolean leader = member.has("leader") && member.get("leader").getAsBoolean();
            graphics.fill(x, y, x + width, y + rowHeight - (compact ? 1 : 2), 0x80000000);
            int nameColor = online ? jobColor(ClientUiState.stringOr(member, "job", "none")) : 0x707070;
            String label = (leader ? "★" : "") + name;
            if (online && member.has("level")) {
                label += " Lv" + member.get("level").getAsInt();
            }
            boolean down = online && member.has("down") && member.get("down").getAsBoolean();
            net.minecraft.network.chat.Component downLabel = net.minecraft.network.chat.Component.translatable(
                    "revival.eternalrealm.party_down");
            int nameWidth = down ? width - 6 - font.width(downLabel) : width - 4;
            graphics.drawString(font, font.plainSubstrByWidth(label, nameWidth), x + 2, y + 1,
                    down ? 0x9A8A8A : nameColor);
            int barTop = compact ? y + 9 : y + 11;
            int barBottom = compact ? y + 11 : y + 14;
            int barWidth = width - 4;
            if (down) {
                // fallen (戦闘不能): a pulsing dark red bar, waiting for a petal or a resurrection
                graphics.drawString(font, downLabel, x + width - 2 - font.width(downLabel), y + 1, 0xFFFF6A5A);
                long time = System.currentTimeMillis() % 1200L;
                int alpha = 0x90 + (int) (0x50 * Math.abs(Math.sin(time / 1200.0 * Math.PI)));
                graphics.fill(x + 2, barTop, x + 2 + barWidth, barBottom, (alpha << 24) | 0x7A1010);
            } else if (online && member.has("hp") && member.has("maxHp")) {
                double hp = member.get("hp").getAsDouble();
                double max = Math.max(1.0, member.get("maxHp").getAsDouble());
                double ratio = Math.max(0.0, Math.min(1.0, hp / max));
                graphics.fill(x + 2, barTop, x + 2 + barWidth, barBottom, 0xFF3A1010);
                int color = ratio > 0.5 ? 0xFF4CD964 : ratio > 0.25 ? 0xFFE8C14A : 0xFFE04A3A;
                graphics.fill(x + 2, barTop, x + 2 + (int) Math.round(barWidth * ratio), barBottom, color);
            }
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
