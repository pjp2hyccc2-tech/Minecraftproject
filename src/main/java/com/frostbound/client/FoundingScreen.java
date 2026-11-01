package com.frostbound.client;

import com.frostbound.FrostboundMod;
import com.frostbound.core.Difficulty;
import com.frostbound.server.CityService;
import com.frostbound.world.Dims;
import java.util.Locale;
import net.minecraft.FileUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;

/**
 * New city settings: difficulty, real minutes per day, city name and tutorial. From the title screen it
 * creates a new FROSTBOUND world; inside an unfounded FROSTBOUND world it founds the city.
 */
public final class FoundingScreen extends FScreen {
    private final boolean preWorld;
    private final Screen parent;
    private Difficulty difficulty = Difficulty.NORMAL;
    private int dayLength = 20;
    private boolean tutorial = true;
    private EditBox name;
    private boolean started;

    public FoundingScreen(boolean preWorld) {
        this(preWorld, null);
    }

    public FoundingScreen(boolean preWorld, Screen parent) {
        super(Component.translatable("frostbound.ui.founding"));
        this.preWorld = preWorld;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(300, width - 30);
        int x0 = (width - w) / 2;
        int y0 = Math.max(10, height / 2 - 100);
        String prev = name == null ? Component.translatable("frostbound.ui.default_city").getString() : name.getValue();
        name = new EditBox(font, x0 + 98, y0 + 0, w - 110, 12, Component.translatable("frostbound.ui.city_name"));
        name.setMaxLength(32);
        name.setBordered(false);
        name.setTextColor(Theme.TEXT);
        name.setValue(prev);
        addRenderableWidget(name);
    }

    @Override
    public boolean isPauseScreen() {
        return preWorld;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    protected void draw(GuiGraphics g, float pt) {
        if (preWorld) {
            renderBackground(g);
        }
        g.fill(0, 0, width, height, 0x70000000);
        int w = Math.min(300, width - 30);
        int x0 = (width - w) / 2;
        int y0 = Math.max(10, height / 2 - 100);
        int h = 210;
        Theme.titled(g, x0, y0, x0 + w, y0 + h, Component.translatable("frostbound.ui.founding"));
        int y = y0 + 18;
        y += Theme.wrapped(g, Component.translatable("frostbound.ui.founding_intro"), x0 + 10, y, w - 20, Theme.TEXT_DIM) + 4;
        g.drawString(font, Component.translatable("frostbound.ui.difficulty"), x0 + 10, y + 4, Theme.TEXT, false);
        int bw = (w - 108) / 3;
        int i = 0;
        for (Difficulty d : Difficulty.values()) {
            int bx = x0 + 98 + i * (bw + 2);
            button(g, bx, y, bw, 15, Component.translatable(d.key()), true, difficulty == d, () -> difficulty = d);
            tip(bx, y, bx + bw, y + 15, Component.translatable(d.key() + ".desc"));
            i++;
        }
        y += 18;
        y += Theme.wrapped(g, Component.translatable(difficulty.key() + ".desc"), x0 + 98, y, w - 108, Theme.TEXT_FAINT);
        y = Math.max(y + 4, y0 + 78);
        g.drawString(font, Component.translatable("frostbound.ui.day_length"), x0 + 10, y + 4, Theme.TEXT, false);
        g.drawString(font, Component.translatable("frostbound.ui.minutes", dayLength), x0 + 98, y + 4, Theme.EMBER, false);
        int[] presets = {10, 20, 30, 60};
        int px = x0 + w - 10 - presets.length * 26;
        for (int k = 0; k < presets.length; k++) {
            int m = presets[k];
            button(g, px + k * 26, y, 24, 15, Component.literal(String.valueOf(m)), true, dayLength == m, () -> dayLength = m);
        }
        y += 18;
        g.drawString(font, Component.translatable("frostbound.ui.total_time", 100, Math.round(100 * dayLength / 60.0)), x0 + 98, y,
                Theme.TEXT_FAINT, false);
        y += 12;
        g.drawString(font, Component.translatable("frostbound.ui.city_name"), x0 + 10, y + 7, Theme.TEXT, false);
        Theme.inset(g, x0 + 94, y + 3, x0 + w - 10, y + 17);
        if (name != null) {
            name.setY(y + 6);
        }
        y += 22;
        g.drawString(font, Component.translatable("frostbound.ui.tutorial"), x0 + 10, y + 4, Theme.TEXT, false);
        button(g, x0 + 98, y, 60, 15, Component.translatable(tutorial ? "frostbound.ui.on" : "frostbound.ui.off"), true, tutorial,
                () -> tutorial = !tutorial);
        y += 24;
        int sw = 110;
        button(g, x0 + w / 2 - sw - 4, y0 + h - 24, sw, 18, Component.translatable("frostbound.ui.found_city"), !started, true,
                this::start);
        if (preWorld) {
            button(g, x0 + w / 2 + 4, y0 + h - 24, sw, 18, Component.translatable("frostbound.ui.back"), true, this::onClose);
        }
    }

    private String cityName() {
        String n = name == null ? "" : name.getValue().trim();
        return n.isEmpty() ? "FROSTBOUND" : n;
    }

    private void start() {
        if (started) {
            return;
        }
        started = true;
        if (!preWorld) {
            CompoundTag t = new CompoundTag();
            t.putString("difficulty", difficulty.name());
            t.putInt("dayLength", dayLength);
            t.putString("name", cityName());
            t.putBoolean("tutorial", tutorial);
            com.frostbound.network.Net.sendToServer("found", t);
            return;
        }
        createWorld(minecraft, difficulty, dayLength, cityName(), tutorial, GameType.SURVIVAL, System.nanoTime());
    }

    /** Creates and opens a new FROSTBOUND world (integrated server) whose city is founded on start. */
    public static void createWorld(net.minecraft.client.Minecraft mc, Difficulty d, int dayLength, String city, boolean tutorial,
            GameType mode, long seed) {
        CityService.pendingFounding = new CityService.FoundingSettings(d, dayLength, city, tutorial);
        try {
            String base = "FROSTBOUND " + city.replaceAll("[^A-Za-z0-9 _-]", "").trim();
            String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), base.trim(), "");
            net.minecraft.world.Difficulty vanilla = d == Difficulty.EASY ? net.minecraft.world.Difficulty.EASY
                    : d == Difficulty.HARD ? net.minecraft.world.Difficulty.HARD : net.minecraft.world.Difficulty.NORMAL;
            LevelSettings settings = new LevelSettings(city, mode, false, vanilla, mode == GameType.CREATIVE, new GameRules(),
                    WorldDataConfiguration.DEFAULT);
            WorldOptions options = new WorldOptions(seed, false, false);
            ClientCity.reset();
            CameraController.forget();
            mc.createWorldOpenFlows().createFreshLevel(folder, settings, options,
                    ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(Dims.PRESET).value().createWorldDimensions());
        } catch (Exception e) {
            FrostboundMod.LOGGER.error("[FROSTBOUND] could not create world", e);
            CityService.pendingFounding = null;
        }
    }

    static String lower(Difficulty d) {
        return d.name().toLowerCase(Locale.ROOT);
    }
}
