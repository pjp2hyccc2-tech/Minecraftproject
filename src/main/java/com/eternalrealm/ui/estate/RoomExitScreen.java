package com.eternalrealm.ui.estate;

import com.eternalrealm.estate.forge.InnService;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The room's door from inside: back to where one came from (the field, a town, the inn's gallery...), to the capital's
 * lobby, or stay. The server sends where "back" is; when there is no way back the capital is the only way out.
 */
public final class RoomExitScreen extends Screen {
    private static final int WIDTH = 236;
    private static final int HEIGHT = 112;
    private final String back;
    private int left;
    private int top;

    private RoomExitScreen(String back) {
        super(Component.translatable("screen.eternalrealm.exit.title"));
        this.back = back;
    }

    public static void accept(JsonObject json) {
        if (EstateUi.bool(json, "open")) {
            Minecraft.getInstance().setScreen(new RoomExitScreen(EstateUi.str(json, "back")));
        }
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        Button backButton = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.exit.back",
                place(back)), button -> go("back")).bounds(left + 12, top + 28, WIDTH - 24, 20).build());
        backButton.active = !back.isEmpty();
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.exit.capital"),
                button -> go("capital")).bounds(left + 12, top + 52, WIDTH - 24, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.exit.stay"),
                button -> onClose()).bounds(left + 12, top + 82, WIDTH - 24, 18).build());
    }

    /** "the field", "the capital", "a dungeon's..." for the dimension the way back leads to. */
    private static Component place(String dimension) {
        if (dimension.isEmpty()) {
            return Component.translatable("screen.eternalrealm.exit.nowhere");
        }
        String path = dimension.substring(dimension.indexOf(':') + 1);
        return switch (path) {
            case "astoria" -> Component.translatable("screen.eternalrealm.exit.astoria");
            case "overworld" -> Component.translatable("screen.eternalrealm.exit.field");
            default -> Component.translatable("screen.eternalrealm.exit.elsewhere");
        };
    }

    private void go(String to) {
        JsonObject args = new JsonObject();
        args.addProperty("to", to);
        EstateUi.send(InnService.EXIT, args);
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        EstateUi.panel(graphics, left, top, WIDTH, HEIGHT);
        EstateUi.title(graphics, font, title, left + WIDTH / 2, top + 7, WIDTH - 40);
        graphics.fill(left + 12, top + 77, left + WIDTH - 12, top + 78, EstateUi.BRASS_DARK);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
