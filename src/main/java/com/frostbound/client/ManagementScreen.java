package com.frostbound.client;

import com.frostbound.core.city.BuildingCategory;
import com.frostbound.core.city.BuildingType;
import com.frostbound.core.city.BuildingTypes;
import com.frostbound.core.expedition.ExpeditionSystem;
import com.frostbound.core.expedition.Site;
import com.frostbound.core.expedition.SiteType;
import com.frostbound.core.population.Profession;
import com.frostbound.core.research.Tech;
import com.frostbound.core.research.Techs;
import com.frostbound.core.resources.Cost;
import com.frostbound.core.resources.ResourceType;
import com.frostbound.core.sim.SimClock;
import com.frostbound.core.society.Law;
import com.frostbound.core.society.Laws;
import com.frostbound.network.Net;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Management mode (M): top-down camera over the city with ten tabs (overview, build, resources, population,
 * work, laws, research, expeditions, weather, history), a building panel, the heat map and mouse placement.
 * Every number comes from the server snapshot; every action is sent to the server, which validates it.
 */
public final class ManagementScreen extends FScreen {
    public enum Tab {
        OVERVIEW("generator"),
        BUILD("build"),
        RESOURCE("coal"),
        POPULATION("population"),
        WORK("iron"),
        LAW("law"),
        RESEARCH("research"),
        EXPEDITION("expedition"),
        WEATHER("weather_snow"),
        HISTORY("warning");

        final String icon;

        Tab(String icon) {
            this.icon = icon;
        }

        Component label() {
            return Component.translatable("frostbound.ui.tab." + name().toLowerCase(Locale.ROOT));
        }
    }

    private static final int TOP = 20;
    private static final int TABW = 24;
    private static final int BOTTOM = 14;
    private static Tab lastTab = Tab.OVERVIEW;
    private static String buildCategory = "HOUSING";
    private static String researchCategory = "generator";
    private static int expeditionMembers = 4;

    private Tab tab;
    private boolean panelOpen = true;
    private int selected = -1;
    private final Map<Tab, Integer> scroll = new EnumMap<>(Tab.class);
    private int buildingScroll;
    private int contentHeight;
    private int citizenPage;
    private long lastHeatRequest;
    private long lastCitizenRequest;
    private int confirmDemolish = -1;
    private int px0;
    private int px1;

    public ManagementScreen(String tabName, int buildingId) {
        super(Component.translatable("frostbound.ui.management"));
        this.tab = lastTab;
        if (tabName != null) {
            for (Tab t : Tab.values()) {
                if (t.name().equalsIgnoreCase(tabName)) {
                    this.tab = t;
                }
            }
        }
        if ("building".equals(tabName) && buildingId >= 0) {
            select(buildingId);
        }
    }

    public boolean typing() {
        return false;
    }

    @Override
    protected void init() {
        CameraController.begin();
        CompoundTag t = new CompoundTag();
        t.putBoolean("on", true);
        Net.sendToServer("management", t);
        Sounds.play("ui_open", 0.5f, 1.0f);
    }

    @Override
    public void removed() {
        CameraController.end();
        CompoundTag t = new CompoundTag();
        t.putBoolean("on", false);
        Net.sendToServer("management", t);
        WorldOverlay.placing = null;
        WorldOverlay.hover = null;
        WorldOverlay.selected = -1;
        lastTab = tab;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ actions

    static void act(String op, Object... kv) {
        CompoundTag t = new CompoundTag();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = (String) kv[i];
            Object v = kv[i + 1];
            if (v instanceof Integer n) {
                t.putInt(k, n);
            } else if (v instanceof Boolean b) {
                t.putBoolean(k, b);
            } else {
                t.putString(k, String.valueOf(v));
            }
        }
        Net.sendToServer(op, t);
    }

    private void select(int id) {
        CompoundTag b = ClientCity.building(id);
        if (b != null && b.getString("type").equals("generator")) {
            selected = -1;
            tab = Tab.OVERVIEW;
            panelOpen = true;
        } else {
            selected = id;
            panelOpen = true;
            buildingScroll = 0;
        }
        confirmDemolish = -1;
        WorldOverlay.selected = id;
    }

    private void openTab(Tab t) {
        if (tab == t && panelOpen && selected < 0) {
            panelOpen = false;
        } else {
            tab = t;
            panelOpen = true;
        }
        selected = -1;
        WorldOverlay.selected = -1;
        if (t != Tab.BUILD) {
            WorldOverlay.placing = null;
        }
        if (t == Tab.POPULATION) {
            requestCitizens();
        }
    }

    private void requestCitizens() {
        lastCitizenRequest = System.currentTimeMillis();
        act("citizens", "page", citizenPage);
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        if (WorldOverlay.showHeat && now - lastHeatRequest > 2000) {
            lastHeatRequest = now;
            act("heat_grid");
        }
        if (panelOpen && tab == Tab.POPULATION && selected < 0 && now - lastCitizenRequest > 3000) {
            requestCitizens();
        }
        if (!ClientCity.hasCity()) {
            onClose();
        }
    }

    // ------------------------------------------------------------------ layout helpers

    private int panelWidth() {
        return Mth.clamp((int) (width * 0.36), 172, 232);
    }

    private boolean overWorld(double x, double y) {
        if (y < TOP || y >= height - BOTTOM) {
            return false;
        }
        if (x < TABW) {
            return false;
        }
        return !(panelOpen && x < TABW + panelWidth());
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private int section(GuiGraphics g, int x, int y, int w, Component title) {
        g.drawString(font, title, x, y + 2, Theme.EMBER, false);
        g.fill(x, y + 12, x + w, y + 13, Theme.ORANGE_DIM);
        return y + 16;
    }

    private int row(GuiGraphics g, int x, int y, int w, Component label, String value, int color) {
        g.drawString(font, label, x, y, Theme.TEXT_DIM, false);
        int vw = font.width(value);
        g.drawString(font, value, x + w - vw, y, color, false);
        return y + 11;
    }

    private int costLine(GuiGraphics g, int x, int y, Cost cost) {
        int cx = x;
        for (ResourceType r : ResourceType.values()) {
            int n = cost.get(r);
            if (n <= 0) {
                continue;
            }
            Theme.iconAt(g, r.id(), cx, y - 1, 9);
            String s = String.valueOf(n);
            boolean enough = ClientCity.amount(r.id()) + 1e-6 >= n;
            g.drawString(font, s, cx + 10, y, enough ? Theme.TEXT : Theme.BAD, false);
            cx += 12 + font.width(s) + 3;
        }
        return cx - x;
    }

    private static List<Component> costTooltip(Cost cost) {
        List<Component> out = new ArrayList<>();
        for (ResourceType r : ResourceType.values()) {
            int n = cost.get(r);
            if (n > 0) {
                out.add(Component.translatable(r.key()).append(": " + n));
            }
        }
        return out;
    }

    private static String clock(long step) {
        int day = (int) (step / SimClock.STEPS_PER_DAY) + 1;
        int sod = (int) (step % SimClock.STEPS_PER_DAY);
        int hour = (6 + sod / SimClock.STEPS_PER_HOUR) % 24;
        int min = (sod % SimClock.STEPS_PER_HOUR) * 60 / SimClock.STEPS_PER_HOUR;
        return String.format(Locale.ROOT, "D%d %02d:%02d", day, hour, min);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void draw(GuiGraphics g, float pt) {
        if (!ClientCity.hasCity()) {
            return;
        }
        updateHover();
        topBar(g);
        tabs(g);
        if (panelOpen) {
            px0 = TABW;
            px1 = TABW + panelWidth();
            Theme.panel(g, px0, TOP, px1, height - BOTTOM);
            if (selected >= 0 && ClientCity.building(selected) != null) {
                buildingPanel(g);
            } else {
                selected = -1;
                tabPanel(g);
            }
        }
        bottomBar(g);
        worldLabels(g);
        HudOverlay.toasts(g, width, height - BOTTOM - 4, true);
        HudOverlay.tutorial(g, panelOpen ? TABW + panelWidth() + 6 : TABW + 6, height - BOTTOM - 4, Math.min(260, width / 2));
    }

    private void updateHover() {
        if (overWorld(mx, my)) {
            WorldOverlay.hover = WorldOverlay.pick(mx, my, width, height);
        } else if (WorldOverlay.placing == null) {
            WorldOverlay.hover = null;
        }
        if (WorldOverlay.placing != null && WorldOverlay.hover != null) {
            BuildingType t = BuildingTypes.get(WorldOverlay.placing);
            WorldOverlay.problem = t == null ? "frostbound.reason.unknown_building" : WorldOverlay.predict(t, WorldOverlay.hover);
        }
    }

    private void topBar(GuiGraphics g) {
        CompoundTag s = ClientCity.snap();
        Theme.panel(g, 0, 0, width, TOP);
        int x = 5;
        String time = String.format(Locale.ROOT, "%02d:%02d", s.getInt("hour"), s.getInt("minute"));
        Component day = tr("frostbound.ui.day", s.getInt("day"));
        g.drawString(font, day, x, 6, Theme.EMBER, false);
        x += font.width(day) + 4;
        g.drawString(font, time, x, 6, Theme.TEXT, false);
        x += font.width(time) + 8;
        boolean compact = width < 560;
        for (ResourceType r : ResourceType.values()) {
            CompoundTag res = ClientCity.resource(r.id());
            double amount = res.getDouble("amount");
            double days = res.getDouble("days");
            Theme.iconAt(g, r.id(), x, 3, 12);
            String v = Theme.num(amount);
            int color = days < 1.5 && res.getDouble("cons") > 0 ? Theme.BAD : days < 3 && res.getDouble("cons") > 0 ? Theme.WARN : Theme.TEXT;
            g.drawString(font, v, x + 13, 6, color, false);
            int w = 13 + Math.max(font.width(v), compact ? 18 : 24) + 5;
            tip(x, 0, x + w, TOP, Component.translatable(r.key()).withStyle(st -> st.withColor(Theme.EMBER)),
                    tr("frostbound.ui.amount_cap", Theme.num(amount), res.getInt("cap")),
                    tr("frostbound.ui.per_day", Theme.signed(res.getDouble("prod")), Theme.signed(-res.getDouble("cons"))),
                    days >= 999 ? tr("frostbound.ui.days_left_inf") : tr("frostbound.ui.days_left", String.format(Locale.ROOT, "%.1f", days)));
            x += w;
        }
        double outside = s.getDouble("outside");
        Theme.iconAt(g, "temperature", x, 3, 12);
        String tt = Theme.temp(outside);
        g.drawString(font, tt, x + 13, 6, Theme.tempColor(outside), false);
        int tw = 13 + font.width(tt) + 4;
        String weather = s.getString("weather");
        Theme.iconAt(g, weatherIcon(weather), x + tw, 3, 12);
        tip(x, 0, x + tw + 14, TOP, Component.translatable("frostbound.weather." + weather.toLowerCase(Locale.ROOT)),
                tr("frostbound.ui.outside", Theme.temp(outside)));
        x += tw + 18;
        CompoundTag pop = ClientCity.pop();
        if (x + 90 < width - 80) {
            Theme.iconAt(g, "hope", x, 3, 12);
            Theme.bar(g, x + 13, 7, 30, 6, pop.getDouble("hope") / 100.0, 0xFF6FB0E0);
            Theme.iconAt(g, "discontent", x + 46, 3, 12);
            Theme.bar(g, x + 59, 7, 30, 6, pop.getDouble("disc") / 100.0, 0xFFD8604A);
            tip(x, 0, x + 92, TOP, tr("frostbound.ui.hope_value", String.format(Locale.ROOT, "%.0f", pop.getDouble("hope"))),
                    tr("frostbound.ui.disc_value", String.format(Locale.ROOT, "%.0f", pop.getDouble("disc"))));
        }
        int bx = width - 4 - 4 * 17;
        boolean paused = s.getBoolean("paused");
        int speed = s.getInt("speed");
        button(g, bx, 3, 16, 14, Component.literal("II"), true, paused, () -> act("pause", "on", !paused));
        tip(bx, 3, bx + 16, 17, tr("frostbound.ui.pause"));
        for (int i = 1; i <= 3; i++) {
            final int sp = i;
            int xx = bx + i * 17;
            button(g, xx, 3, 16, 14, Component.literal(i + "x"), true, !paused && speed == i, () -> {
                act("speed", "speed", sp);
                if (paused) {
                    act("pause", "on", false);
                }
            });
        }
        CompoundTag ev = ClientCity.event();
        if (ev != null) {
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            int ex = bx - 58;
            button(g, ex, 3, 54, 14, tr("frostbound.ui.event_button"), true, blink, () -> minecraft.setScreen(new EventScreen(this)));
        } else if (ClientCity.ended()) {
            int ex = bx - 58;
            button(g, ex, 3, 54, 14, tr("frostbound.ui.ending_button"), true, true, () -> minecraft.setScreen(new EndingScreen(this)));
        }
    }

    static String weatherIcon(String weather) {
        return switch (weather) {
            case "CLEAR" -> "weather_clear";
            case "WIND" -> "weather_wind";
            case "BLIZZARD" -> "weather_blizzard";
            case "EXTREME_COLD" -> "weather_extreme";
            case "GREAT_COLD_WAVE" -> "weather_great_wave";
            default -> "weather_snow";
        };
    }

    private void tabs(GuiGraphics g) {
        Theme.panel(g, 0, TOP, TABW, height - BOTTOM);
        int y = TOP + 4;
        int step = Math.min(20, (height - BOTTOM - TOP - 8) / Tab.values().length);
        for (Tab t : Tab.values()) {
            boolean sel = panelOpen && selected < 0 && tab == t;
            boolean hv = hover(2, y, TABW - 2, y + step - 1);
            Theme.button(g, 2, y, TABW - 4, step - 1, null, hv, true, sel);
            Theme.iconAt(g, t.icon, 4, y + (step - 1 - 16) / 2, 16);
            hot(2, y, TABW - 2, y + step - 1, () -> openTab(t));
            tip(2, y, TABW - 2, y + step - 1, t.label());
            y += step;
        }
    }

    private void bottomBar(GuiGraphics g) {
        Theme.panel(g, 0, height - BOTTOM, width, height);
        Component hint = WorldOverlay.placing != null ? tr("frostbound.ui.hint_place") : tr("frostbound.ui.hint");
        g.drawString(font, font.plainSubstrByWidth(hint.getString(), width - 120), 5, height - BOTTOM + 3, Theme.TEXT_DIM, false);
        String heat = WorldOverlay.showHeat ? "H: ON" : "H: OFF";
        int hx = width - 60;
        button(g, hx, height - BOTTOM + 1, 56, BOTTOM - 2, tr("frostbound.ui.heatmap"), true, WorldOverlay.showHeat,
                () -> toggleHeat());
        tip(hx, height - BOTTOM, width, height, Component.literal(heat));
    }

    private void toggleHeat() {
        WorldOverlay.showHeat = !WorldOverlay.showHeat;
        if (WorldOverlay.showHeat) {
            lastHeatRequest = System.currentTimeMillis();
            act("heat_grid");
        }
    }

    /** Text near the cursor in the world area: placement problems or the hovered building's name. */
    private void worldLabels(GuiGraphics g) {
        if (!overWorld(mx, my) || WorldOverlay.hover == null) {
            return;
        }
        if (WorldOverlay.placing != null) {
            BuildingType t = BuildingTypes.get(WorldOverlay.placing);
            if (t == null) {
                return;
            }
            Component c = WorldOverlay.problem == null ? Component.translatable(t.key())
                    : Component.translatable(WorldOverlay.problem);
            int w = font.width(c) + 8;
            g.fill(mx + 10, my + 8, mx + 10 + w, my + 21, 0xC0101214);
            g.drawString(font, c, mx + 14, my + 11, WorldOverlay.problem == null ? Theme.GOOD : Theme.BAD, false);
            return;
        }
        CompoundTag b = buildingAt(WorldOverlay.hover);
        if (b != null) {
            BuildingType t = BuildingTypes.get(b.getString("type"));
            if (t != null) {
                MutableComponent c = Component.translatable(t.key()).append(" Lv" + b.getInt("level"));
                int w = font.width(c) + 8;
                g.fill(mx + 10, my + 8, mx + 10 + w, my + 21, 0xC0101214);
                g.drawString(font, c, mx + 14, my + 11, Theme.EMBER, false);
            }
        }
    }

    private CompoundTag buildingAt(Vec3 p) {
        ListTag list = ClientCity.buildings();
        int x = (int) Math.floor(p.x);
        int z = (int) Math.floor(p.z);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            int bx = b.getInt("x");
            int bz = b.getInt("z");
            if (x >= bx && x < bx + b.getInt("sx") && z >= bz && z < bz + b.getInt("sz")) {
                return b;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ tab panel

    private void tabPanel(GuiGraphics g) {
        int x0 = px0 + 6;
        int w = px1 - px0 - 12;
        g.drawString(font, tab.label(), x0, TOP + 5, Theme.EMBER, false);
        g.fill(px0 + 1, TOP + 16, px1 - 1, TOP + 17, Theme.ORANGE_DIM);
        int top = TOP + 19;
        int bottom = height - BOTTOM - 3;
        int sc = scroll.getOrDefault(tab, 0);
        clip(g, px0 + 1, top, px1 - 1, bottom);
        int y = top + 2 - sc;
        int end = switch (tab) {
            case OVERVIEW -> overview(g, x0, y, w);
            case BUILD -> build(g, x0, y, w);
            case RESOURCE -> resources(g, x0, y, w);
            case POPULATION -> population(g, x0, y, w);
            case WORK -> work(g, x0, y, w);
            case LAW -> laws(g, x0, y, w);
            case RESEARCH -> research(g, x0, y, w);
            case EXPEDITION -> expeditions(g, x0, y, w);
            case WEATHER -> weather(g, x0, y, w);
            case HISTORY -> history(g, x0, y, w);
        };
        unclip(g);
        contentHeight = end - (top + 2 - sc);
        int view = bottom - top;
        int max = Math.max(0, contentHeight - view + 6);
        if (sc > max) {
            scroll.put(tab, max);
        }
        if (max > 0) {
            int barH = Math.max(10, view * view / Math.max(view, contentHeight));
            int barY = top + (int) ((view - barH) * (sc / (double) max));
            g.fill(px1 - 4, top, px1 - 2, bottom, 0xFF0B0C0E);
            g.fill(px1 - 4, barY, px1 - 2, barY + barH, Theme.ORANGE_DIM);
        }
    }

    private int overview(GuiGraphics g, int x, int y, int w) {
        CompoundTag gen = ClientCity.generator();
        CompoundTag pop = ClientCity.pop();
        y = section(g, x, y, w, tr("frostbound.ui.generator"));
        int level = gen.getInt("level");
        int max = gen.getInt("maxLevel");
        g.drawString(font, tr("frostbound.ui.gen_level", level, max), x, y + 3, Theme.TEXT, false);
        button(g, x + w - 34, y, 16, 14, Component.literal("-"), level > 0, () -> act("gen_level", "level", level - 1));
        button(g, x + w - 16, y, 16, 14, Component.literal("+"), level < max, () -> act("gen_level", "level", level + 1));
        y += 17;
        String state = gen.getBoolean("broken") ? (gen.getBoolean("repairing") ? "repairing" : "broken")
                : gen.getBoolean("fuelOut") ? "fuel_out" : level == 0 ? "off" : "running";
        int sc = state.equals("running") ? Theme.GOOD : state.equals("off") ? Theme.TEXT_DIM : Theme.BAD;
        y = row(g, x, y, w, tr("frostbound.ui.state"), Component.translatable("frostbound.ui.gen_state." + state,
                String.format(Locale.ROOT, "%.0f", gen.getDouble("repairHours"))).getString(), sc);
        boolean overload = gen.getBoolean("overload");
        g.drawString(font, tr("frostbound.ui.overload"), x, y + 3, Theme.TEXT_DIM, false);
        button(g, x + w - 50, y, 50, 14, tr(overload ? "frostbound.ui.on" : "frostbound.ui.off"), level > 0 && !gen.getBoolean("broken"),
                overload, () -> act("overload", "on", !overload));
        y += 16;
        double stress = gen.getDouble("stress");
        g.drawString(font, tr("frostbound.ui.stress"), x, y, Theme.TEXT_DIM, false);
        Theme.bar(g, x + 60, y + 1, w - 60, 7, stress / 100.0, stress > 70 ? Theme.BAD : stress > 40 ? Theme.WARN : Theme.ORANGE);
        y += 11;
        y = row(g, x, y, w, tr("frostbound.ui.burn"), String.format(Locale.ROOT, "%.0f / %s", gen.getDouble("burn"),
                Component.translatable("frostbound.ui.day_unit").getString()), Theme.TEXT);
        y = row(g, x, y, w, tr("frostbound.ui.heat_supply"), String.format(Locale.ROOT, "%.0f%%  (%.0f / %.0f)",
                gen.getDouble("supply") * 100, gen.getDouble("capacity"), gen.getDouble("demand")),
                gen.getDouble("supply") < 0.8 ? Theme.WARN : Theme.TEXT);
        y = row(g, x, y, w, tr("frostbound.ui.range"), String.format(Locale.ROOT, "%.0f", gen.getDouble("range")), Theme.TEXT);
        if (gen.getBoolean("broken") && !gen.getBoolean("repairing")) {
            button(g, x, y, w, 15, tr("frostbound.ui.repair"), true, () -> act("repair"));
            y += 18;
        }
        y += 4;
        y = section(g, x, y, w, tr("frostbound.ui.city"));
        y = row(g, x, y, w, tr("frostbound.ui.population"), tr("frostbound.ui.pop_value", pop.getInt("alive"), pop.getInt("sick"),
                pop.getInt("homeless")).getString(), Theme.TEXT);
        g.drawString(font, tr("frostbound.ui.hope"), x, y, Theme.TEXT_DIM, false);
        Theme.bar(g, x + 60, y + 1, w - 60, 7, pop.getDouble("hope") / 100.0, 0xFF6FB0E0);
        y += 11;
        g.drawString(font, tr("frostbound.ui.discontent"), x, y, Theme.TEXT_DIM, false);
        Theme.bar(g, x + 60, y + 1, w - 60, 7, pop.getDouble("disc") / 100.0, 0xFFD8604A);
        y += 11;
        y = row(g, x, y, w, tr("frostbound.ui.deaths"), pop.getInt("deathsToday") + " / " + pop.getInt("deaths"),
                pop.getInt("deathsToday") > 0 ? Theme.BAD : Theme.TEXT);
        CompoundTag s = ClientCity.snap();
        y = row(g, x, y, w, tr("frostbound.ui.tomorrow"), Theme.temp(s.getDouble("forecastBase")), Theme.tempColor(s.getDouble("forecastBase")));
        CompoundTag rs = s.getCompound("research");
        if (rs.contains("current")) {
            Tech t = Techs.get(rs.getString("current"));
            if (t != null) {
                y = row(g, x, y, w, tr("frostbound.ui.researching"), Component.translatable(t.key()).getString(), Theme.TEXT);
                Theme.bar(g, x, y, w, 6, rs.getDouble("progress") / Math.max(1, t.points), Theme.ORANGE);
                y += 9;
            }
        }
        y += 4;
        if (ClientCity.event() != null) {
            button(g, x, y, w, 16, tr("frostbound.ui.open_event"), true, true, () -> minecraft.setScreen(new EventScreen(this)));
            y += 19;
        }
        if (ClientCity.ended()) {
            button(g, x, y, w, 16, tr("frostbound.ui.ending_button"), true, true, () -> minecraft.setScreen(new EndingScreen(this)));
            y += 19;
        }
        CompoundTag tut = s.getCompound("tutorial");
        if (tut.getBoolean("enabled") && !tut.getString("step").equals("DONE")) {
            button(g, x, y, w, 14, tr("frostbound.ui.skip_tutorial"), true, () -> act("tutorial_off"));
            y += 17;
        }
        y = section(g, x, y, w, tr("frostbound.ui.day_length"));
        int len = s.getInt("dayLength");
        g.drawString(font, tr("frostbound.ui.minutes", len), x, y + 3, Theme.TEXT, false);
        int[] presets = {10, 20, 30, 60};
        int bw = (w - 60) / presets.length;
        for (int i = 0; i < presets.length; i++) {
            int m = presets[i];
            button(g, x + 60 + i * bw, y, bw - 2, 14, Component.literal(String.valueOf(m)), true, len == m,
                    () -> act("day_length", "minutes", m));
        }
        y += 18;
        return y;
    }

    private int build(GuiGraphics g, int x, int y, int w) {
        BuildingCategory[] cats = {BuildingCategory.HOUSING, BuildingCategory.RESOURCE, BuildingCategory.FOOD, BuildingCategory.MEDICAL,
                BuildingCategory.INDUSTRY, BuildingCategory.SOCIAL};
        int cw = w / 3;
        for (int i = 0; i < cats.length; i++) {
            BuildingCategory c = cats[i];
            int bx = x + (i % 3) * cw;
            int by = y + (i / 3) * 16;
            button(g, bx, by, cw - 2, 15, Component.translatable(c.key()), true, buildCategory.equals(c.name()),
                    () -> buildCategory = c.name());
        }
        y += 36;
        for (BuildingType t : BuildingTypes.buildable()) {
            if (!t.category.name().equals(buildCategory)) {
                continue;
            }
            boolean unlocked = ClientCity.unlocked(t.id);
            boolean sel = t.id.equals(WorldOverlay.placing);
            int h = 26;
            boolean hv = hover(x, y, x + w, y + h);
            Theme.button(g, x, y, w, h, null, hv, unlocked, sel);
            g.drawString(font, Component.translatable(t.key()), x + 4, y + 3, unlocked ? Theme.TEXT : Theme.TEXT_FAINT, false);
            String size = t.width + "x" + t.depth;
            g.drawString(font, size, x + w - 4 - font.width(size), y + 3, Theme.TEXT_DIM, false);
            if (unlocked) {
                costLine(g, x + 4, y + 15, t.cost);
                hot(x, y, x + w, y + h, () -> {
                    WorldOverlay.placing = t.id;
                    WorldOverlay.rotation = 0;
                });
            } else {
                String req = t.unlockTech != null ? Component.translatable("frostbound.ui.requires_tech",
                        Component.translatable("frostbound.tech." + t.unlockTech)).getString()
                        : t.unlockLaw != null ? Component.translatable("frostbound.ui.requires_law",
                                Component.translatable("frostbound.law." + t.unlockLaw)).getString() : "";
                g.drawString(font, font.plainSubstrByWidth(req, w - 8), x + 4, y + 15, Theme.TEXT_FAINT, false);
            }
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable(t.key()).withStyle(st -> st.withColor(Theme.EMBER)));
            tip.add(Component.translatable(t.key() + ".desc"));
            tip.add(tr("frostbound.ui.build_time", t.buildHours, t.builders));
            if (t.maxWorkers > 0) {
                tip.add(tr("frostbound.ui.workers_max", t.maxWorkers));
            }
            if (t.housing > 0) {
                tip.add(tr("frostbound.ui.housing", t.housing));
            }
            if (t.beds > 0) {
                tip.add(tr("frostbound.ui.beds", t.beds));
            }
            if (t.heatDemand > 0) {
                tip.add(tr("frostbound.ui.insulation", String.format(Locale.ROOT, "%.0f", t.insulation)));
            }
            tip.addAll(costTooltip(t.cost));
            tip(x, y, x + w, y + h, tip);
            y += h + 2;
        }
        y += 4;
        if (WorldOverlay.placing != null) {
            BuildingType t = BuildingTypes.get(WorldOverlay.placing);
            if (t != null) {
                y += Theme.wrapped(g, tr("frostbound.ui.placing", Component.translatable(t.key())), x, y, w, Theme.EMBER);
                button(g, x, y + 2, w, 14, tr("frostbound.ui.cancel"), true, () -> WorldOverlay.placing = null);
                y += 18;
            }
        }
        return y;
    }

    private int resources(GuiGraphics g, int x, int y, int w) {
        y = section(g, x, y, w, tr("frostbound.ui.stockpile"));
        for (ResourceType r : ResourceType.values()) {
            CompoundTag res = ClientCity.resource(r.id());
            Theme.iconAt(g, r.id(), x, y, 12);
            g.drawString(font, Component.translatable(r.key()), x + 15, y + 2, Theme.TEXT, false);
            String amt = Theme.num(res.getDouble("amount")) + " / " + res.getInt("cap");
            g.drawString(font, amt, x + w - font.width(amt), y + 2, Theme.TEXT, false);
            y += 12;
            String flow = Theme.signed(res.getDouble("prod")) + " / " + Theme.signed(-res.getDouble("cons"));
            g.drawString(font, flow, x + 15, y, Theme.TEXT_DIM, false);
            double days = res.getDouble("days");
            String d = days >= 999 ? "∞" : String.format(Locale.ROOT, "%.1f%s", days, Component.translatable("frostbound.ui.day_unit").getString());
            g.drawString(font, d, x + w - font.width(d), y, days < 2 ? Theme.BAD : days < 4 ? Theme.WARN : Theme.TEXT_DIM, false);
            y += 13;
        }
        y += 2;
        y = section(g, x, y, w, tr("frostbound.ui.producers"));
        ListTag list = ClientCity.buildings();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            BuildingType t = BuildingTypes.get(b.getString("type"));
            if (t == null || t.output == null || !b.getString("state").equals("ACTIVE")) {
                continue;
            }
            Theme.iconAt(g, t.output.id(), x, y - 1, 10);
            g.drawString(font, Component.translatable(t.key()), x + 13, y, Theme.TEXT, false);
            String v = String.format(Locale.ROOT, "+%.1f/h  %d%%", b.getDouble("out"), (int) Math.round(b.getDouble("eff") * 100));
            g.drawString(font, v, x + w - font.width(v), y, Theme.TEXT_DIM, false);
            int id = b.getInt("id");
            hot(x, y - 1, x + w, y + 10, () -> select(id));
            y += 11;
        }
        return y;
    }

    private int population(GuiGraphics g, int x, int y, int w) {
        CompoundTag pop = ClientCity.pop();
        y = section(g, x, y, w, tr("frostbound.ui.people"));
        y = row(g, x, y, w, tr("frostbound.ui.alive"), String.valueOf(pop.getInt("alive")), Theme.TEXT);
        y = row(g, x, y, w, tr("frostbound.ui.children"), String.valueOf(pop.getInt("children")), Theme.TEXT);
        y = row(g, x, y, w, tr("frostbound.ui.sick"), String.valueOf(pop.getInt("sick")), pop.getInt("sick") > 0 ? Theme.WARN : Theme.TEXT);
        y = row(g, x, y, w, tr("frostbound.ui.homeless"), String.valueOf(pop.getInt("homeless")),
                pop.getInt("homeless") > 0 ? Theme.BAD : Theme.TEXT);
        y = row(g, x, y, w, tr("frostbound.ui.on_expedition"), String.valueOf(pop.getInt("expedition")), Theme.TEXT);
        CompoundTag prof = pop.getCompound("prof");
        for (Profession p : Profession.values()) {
            y = row(g, x, y, w, Component.translatable(p.key()), String.valueOf(prof.getInt(p.id())), Theme.TEXT_DIM);
        }
        y += 3;
        y = section(g, x, y, w, tr("frostbound.ui.condition"));
        g.drawString(font, tr("frostbound.ui.health"), x, y, Theme.TEXT_DIM, false);
        Theme.bar(g, x + 60, y + 1, w - 60, 7, pop.getDouble("health") / 100.0, Theme.GOOD);
        y += 11;
        g.drawString(font, tr("frostbound.ui.satiety"), x, y, Theme.TEXT_DIM, false);
        Theme.bar(g, x + 60, y + 1, w - 60, 7, pop.getDouble("satiety") / 100.0, Theme.WARN);
        y += 11;
        g.drawString(font, tr("frostbound.ui.fatigue"), x, y, Theme.TEXT_DIM, false);
        Theme.bar(g, x + 60, y + 1, w - 60, 7, pop.getDouble("fatigue") / 100.0, 0xFF9A7AD8);
        y += 11;
        y = row(g, x, y, w, tr("frostbound.ui.body_temp"), String.format(Locale.ROOT, "%.1f°C", pop.getDouble("body")),
                pop.getDouble("body") < 35.5 ? Theme.COLD : Theme.TEXT);
        y = stacked(g, x, y, w, tr("frostbound.ui.hunger_bands"), pop.getList("hunger", Tag.TAG_INT),
                new int[] {0xFF86CF6E, 0xFFC9C46A, 0xFFE09A4A, 0xFFE5604E});
        y = stacked(g, x, y, w, tr("frostbound.ui.temp_bands"), pop.getList("temp", Tag.TAG_INT),
                new int[] {0xFF86CF6E, 0xFF9BCBEA, 0xFF71B6EC, 0xFF4A7DD8, 0xFF2B3F9E});
        y += 3;
        CompoundTag cit = ClientCity.citizens();
        int total = cit == null ? 0 : cit.getInt("total");
        int pages = Math.max(1, (total + 39) / 40);
        y = section(g, x, y, w, tr("frostbound.ui.citizens_page", citizenPage + 1, pages));
        button(g, x + w - 34, y - 16, 16, 12, Component.literal("<"), citizenPage > 0, () -> {
            citizenPage--;
            requestCitizens();
        });
        button(g, x + w - 16, y - 16, 16, 12, Component.literal(">"), citizenPage + 1 < pages, () -> {
            citizenPage++;
            requestCitizens();
        });
        if (cit != null) {
            ListTag list = cit.getList("citizens", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                String cond = c.getString("cond");
                int color = cond.equals("HEALTHY") ? Theme.TEXT : cond.equals("RECOVERING") ? Theme.TEXT_DIM : Theme.WARN;
                String name = font.plainSubstrByWidth(c.getString("name"), w / 2 - 4);
                g.drawString(font, name, x, y, color, false);
                String info = c.getInt("age") + " " + Component.translatable("frostbound.profession."
                        + c.getString("prof").toLowerCase(Locale.ROOT)).getString();
                g.drawString(font, font.plainSubstrByWidth(info, w / 2 - 22), x + w / 2, y, Theme.TEXT_DIM, false);
                Theme.bar(g, x + w - 18, y + 2, 18, 5, c.getDouble("health") / 100.0, c.getDouble("health") < 40 ? Theme.BAD : Theme.GOOD);
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(c.getString("name")).withStyle(st -> st.withColor(Theme.EMBER)));
                tip.add(Component.translatable("frostbound.condition." + cond.toLowerCase(Locale.ROOT)));
                tip.add(tr("frostbound.ui.citizen_stats", (int) c.getDouble("health"), (int) c.getDouble("satiety"),
                        String.format(Locale.ROOT, "%.1f", c.getDouble("body")), (int) c.getDouble("hope"), (int) c.getDouble("disc")));
                if (c.contains("workType")) {
                    tip.add(tr("frostbound.ui.works_at", Component.translatable("frostbound.building." + c.getString("workType"))));
                }
                tip(x, y - 1, x + w, y + 9, tip);
                y += 10;
            }
        }
        return y;
    }

    private int stacked(GuiGraphics g, int x, int y, int w, Component label, ListTag values, int[] colors) {
        g.drawString(font, label, x, y, Theme.TEXT_DIM, false);
        y += 10;
        int total = 0;
        for (int i = 0; i < values.size(); i++) {
            total += values.getInt(i);
        }
        g.fill(x, y, x + w, y + 7, 0xFF0B0C0E);
        if (total > 0) {
            int cx = x + 1;
            for (int i = 0; i < values.size() && i < colors.length; i++) {
                int seg = (int) Math.round((w - 2) * values.getInt(i) / (double) total);
                g.fill(cx, y + 1, Math.min(x + w - 1, cx + seg), y + 6, colors[i]);
                cx += seg;
            }
        }
        List<Component> tip = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            tip.add(Component.literal(values.getInt(i) + "  ").append(Component.translatable(label == null ? "" : bandKey(colors.length, i))));
        }
        tip(x, y - 10, x + w, y + 7, tip);
        return y + 10;
    }

    private static String bandKey(int bands, int i) {
        return (bands == 4 ? "frostbound.ui.hunger_band." : "frostbound.ui.temp_band.") + i;
    }

    private int work(GuiGraphics g, int x, int y, int w) {
        CompoundTag pop = ClientCity.pop();
        CompoundTag s = ClientCity.snap();
        y = row(g, x, y, w, tr("frostbound.ui.employed"), String.valueOf(pop.getInt("employed")), Theme.TEXT);
        y = row(g, x, y, w, tr("frostbound.ui.unemployed"), String.valueOf(pop.getInt("unemployed")),
                pop.getInt("unemployed") > 0 ? Theme.WARN : Theme.TEXT);
        y = row(g, x, y, w, tr("frostbound.ui.shift"), String.format(Locale.ROOT, "08:00 - %02d:00", s.getInt("shiftEnd")), Theme.TEXT);
        if (s.getCompound("laws").getBoolean("emergency")) {
            y = row(g, x, y, w, tr("frostbound.ui.emergency"), "!", Theme.BAD);
        }
        y += 3;
        y = section(g, x, y, w, tr("frostbound.ui.workplaces"));
        ListTag list = ClientCity.buildings();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            int max = b.getInt("max");
            if (max <= 0 || b.getString("type").equals("generator")) {
                continue;
            }
            BuildingType t = BuildingTypes.get(b.getString("type"));
            if (t == null) {
                continue;
            }
            int id = b.getInt("id");
            int desired = b.getInt("desired");
            boolean construction = b.getString("state").equals("CONSTRUCTION");
            Component name = construction ? Component.translatable(t.key()).append(Component.literal(" *"))
                    : Component.translatable(t.key());
            g.drawString(font, font.plainSubstrByWidth(name.getString(), w - 82), x, y + 3, b.getBoolean("paused") ? Theme.TEXT_FAINT : Theme.TEXT,
                    false);
            hot(x, y, x + w - 82, y + 13, () -> select(id));
            String v = b.getInt("workers") + "/" + desired + "/" + max;
            g.drawString(font, v, x + w - 36 - font.width(v), y + 3, b.getInt("workers") < desired ? Theme.WARN : Theme.TEXT_DIM, false);
            button(g, x + w - 34, y, 16, 13, Component.literal("-"), desired > 0, () -> act("workers", "id", id, "count", desired - 1));
            button(g, x + w - 16, y, 16, 13, Component.literal("+"), desired < max, () -> act("workers", "id", id, "count", desired + 1));
            tip(x, y, x + w - 36, y + 13, tr("frostbound.ui.workers_tip"));
            y += 15;
        }
        return y;
    }

    private int laws(GuiGraphics g, int x, int y, int w) {
        CompoundTag laws = ClientCity.snap().getCompound("laws");
        double ready = laws.getDouble("readyIn");
        y += Theme.wrapped(g, ready > 0 ? tr("frostbound.ui.law_ready_in", String.format(Locale.ROOT, "%.0f", ready))
                : tr("frostbound.ui.law_ready"), x, y, w, ready > 0 ? Theme.TEXT_DIM : Theme.GOOD);
        if (laws.getDouble("strikeH") > 0) {
            y += Theme.wrapped(g, tr("frostbound.ui.strike", String.format(Locale.ROOT, "%.0f", laws.getDouble("strikeH"))), x, y, w, Theme.BAD);
        }
        y += 4;
        List<String> enacted = ClientCity.strings(laws, "enacted");
        CompoundTag state = laws.getCompound("state");
        for (Law law : Laws.all()) {
            boolean on = enacted.contains(law.id);
            String reason = state.getString(law.id);
            boolean can = reason.isEmpty();
            int h = 26;
            boolean hv = hover(x, y, x + w, y + h);
            Theme.button(g, x, y, w, h, null, hv, true, on);
            g.drawString(font, Component.translatable(law.key()), x + 4, y + 3, on ? Theme.EMBER : Theme.TEXT, false);
            String effects = String.format(Locale.ROOT, "%s %+.0f  %s %+.0f", Component.translatable("frostbound.ui.hope_short").getString(),
                    law.hopeNow, Component.translatable("frostbound.ui.disc_short").getString(), law.discontentNow);
            g.drawString(font, effects, x + 4, y + 14, Theme.TEXT_DIM, false);
            if (on && !law.decree) {
                String st = Component.translatable("frostbound.ui.enacted").getString();
                g.drawString(font, st, x + w - 4 - font.width(st), y + 3, Theme.GOOD, false);
            } else if (can) {
                button(g, x + w - 52, y + 6, 48, 14, tr(law.decree ? "frostbound.ui.decree" : "frostbound.ui.enact"), true,
                        () -> act("law", "id", law.id));
            } else {
                String st = Component.translatable(reason).getString();
                st = font.plainSubstrByWidth(st, 70);
                g.drawString(font, st, x + w - 4 - font.width(st), y + 14, Theme.TEXT_FAINT, false);
            }
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable(law.key()).withStyle(st -> st.withColor(Theme.EMBER)));
            tip.add(Component.translatable(law.key() + ".desc"));
            if (!law.dailyCost.isEmpty()) {
                tip.add(tr("frostbound.ui.daily_cost"));
                tip.addAll(costTooltip(law.dailyCost));
            }
            for (String r : law.requires) {
                tip.add(tr("frostbound.ui.requires_law", Component.translatable("frostbound.law." + r)));
            }
            for (String c : law.conflicts) {
                tip.add(tr("frostbound.ui.conflicts", Component.translatable("frostbound.law." + c)));
            }
            tip(x, y, x + w - 54, y + h, tip);
            y += h + 2;
        }
        return y;
    }

    private int research(GuiGraphics g, int x, int y, int w) {
        CompoundTag rs = ClientCity.snap().getCompound("research");
        List<String> done = ClientCity.strings(rs, "completed");
        String current = rs.getString("current");
        Tech cur = Techs.get(current);
        if (cur != null) {
            y = row(g, x, y, w, tr("frostbound.ui.researching"), Component.translatable(cur.key()).getString(), Theme.EMBER);
            Theme.bar(g, x, y, w, 7, rs.getDouble("progress") / Math.max(1, cur.points), Theme.ORANGE);
            y += 10;
        } else {
            y += Theme.wrapped(g, tr("frostbound.ui.no_research"), x, y, w, Theme.WARN);
        }
        y = row(g, x, y, w, tr("frostbound.ui.points_per_hour"), String.format(Locale.ROOT, "%.1f", rs.getDouble("points")), Theme.TEXT_DIM);
        y += 2;
        int cw = w / 4;
        for (int i = 0; i < Techs.CATEGORIES.length; i++) {
            String c = Techs.CATEGORIES[i];
            int bx = x + (i % 4) * cw;
            int by = y + (i / 4) * 15;
            button(g, bx, by, cw - 2, 14, Component.translatable("frostbound.research.category." + c), true, researchCategory.equals(c),
                    () -> researchCategory = c);
            tip(bx, by, bx + cw - 2, by + 14, Component.translatable("frostbound.research.category." + c));
        }
        y += 34;
        for (Tech t : Techs.inCategory(researchCategory)) {
            boolean isDone = done.contains(t.id);
            boolean isCur = t.id.equals(current);
            String reason = null;
            for (String r : t.requires) {
                if (!done.contains(r)) {
                    reason = "frostbound.research.reason.requires";
                }
            }
            int h = 26;
            boolean hv = hover(x, y, x + w, y + h);
            Theme.button(g, x, y, w, h, null, hv, true, isCur);
            g.drawString(font, Component.translatable(t.key()), x + 4, y + 3, isDone ? Theme.GOOD : Theme.TEXT, false);
            String tier = "T" + t.tier;
            g.drawString(font, tier, x + w - 4 - font.width(tier), y + 3, Theme.TEXT_DIM, false);
            if (isDone) {
                g.drawString(font, tr("frostbound.ui.done"), x + 4, y + 14, Theme.GOOD, false);
            } else if (isCur) {
                Theme.bar(g, x + 4, y + 15, w - 8, 6, rs.getDouble("progress") / Math.max(1, t.points), Theme.ORANGE);
            } else {
                costLine(g, x + 4, y + 14, t.cost);
                if (reason == null) {
                    button(g, x + w - 44, y + 11, 40, 13, tr("frostbound.ui.start"), true, () -> act("research", "id", t.id));
                } else {
                    String st = font.plainSubstrByWidth(Component.translatable(reason).getString(), 60);
                    g.drawString(font, st, x + w - 4 - font.width(st), y + 14, Theme.TEXT_FAINT, false);
                }
            }
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable(t.key()).withStyle(st -> st.withColor(Theme.EMBER)));
            tip.add(Component.translatable(t.descKey()));
            tip.add(tr("frostbound.ui.research_points", t.points));
            for (String r : t.requires) {
                tip.add(tr("frostbound.ui.requires_tech", Component.translatable("frostbound.tech." + r)));
            }
            tip.addAll(costTooltip(t.cost));
            tip(x, y, x + w - 46, y + h, tip);
            y += h + 2;
        }
        return y;
    }

    private int expeditions(GuiGraphics g, int x, int y, int w) {
        CompoundTag ex = ClientCity.snap().getCompound("expeditions");
        ListTag active = ex.getList("active", Tag.TAG_COMPOUND);
        ListTag sites = ex.getList("sites", Tag.TAG_COMPOUND);
        y = section(g, x, y, w, tr("frostbound.ui.active_expeditions"));
        if (active.isEmpty()) {
            g.drawString(font, tr("frostbound.ui.none"), x, y, Theme.TEXT_FAINT, false);
            y += 11;
        }
        for (int i = 0; i < active.size(); i++) {
            CompoundTag e = active.getCompound(i);
            CompoundTag site = siteById(sites, e.getInt("site"));
            Component name = site == null ? Component.literal("?") : siteName(site);
            g.drawString(font, name, x, y, Theme.TEXT, false);
            String m = e.getInt("members") + " " + Component.translatable(e.getBoolean("returning") ? "frostbound.ui.returning"
                    : "frostbound.ui.outbound").getString();
            g.drawString(font, m, x + w - font.width(m), y, Theme.TEXT_DIM, false);
            y += 10;
            Theme.bar(g, x, y, w, 5, e.getDouble("progress"), e.getBoolean("returning") ? Theme.GOOD : Theme.COLD);
            y += 8;
        }
        y += 3;
        y = section(g, x, y, w, tr("frostbound.ui.team_size"));
        g.drawString(font, tr("frostbound.ui.members", expeditionMembers), x, y + 3, Theme.TEXT, false);
        button(g, x + w - 34, y, 16, 14, Component.literal("-"), expeditionMembers > ExpeditionSystem.MIN_TEAM,
                () -> expeditionMembers--);
        button(g, x + w - 16, y, 16, 14, Component.literal("+"), expeditionMembers < ExpeditionSystem.MAX_TEAM,
                () -> expeditionMembers++);
        y += 18;
        y = section(g, x, y, w, tr("frostbound.ui.sites"));
        for (int i = 0; i < sites.size(); i++) {
            CompoundTag sTag = sites.getCompound(i);
            boolean open = sTag.getBoolean("open");
            boolean depleted = sTag.getBoolean("depleted");
            int h = 26;
            boolean hv = hover(x, y, x + w, y + h);
            Theme.button(g, x, y, w, h, null, hv, open, false);
            g.drawString(font, siteName(sTag), x + 4, y + 3, open ? Theme.TEXT : Theme.TEXT_FAINT, false);
            String info = String.format(Locale.ROOT, "%.0fkm  %s %.0f%%", sTag.getDouble("dist"),
                    Component.translatable("frostbound.ui.danger").getString(), sTag.getDouble("danger") * 100);
            g.drawString(font, info, x + 4, y + 14, Theme.TEXT_DIM, false);
            int id = sTag.getInt("id");
            if (open) {
                button(g, x + w - 44, y + 6, 40, 14, tr("frostbound.ui.send"), true,
                        () -> act("expedition", "site", id, "members", expeditionMembers));
            } else {
                String st = Component.translatable(depleted ? "frostbound.ui.depleted" : "frostbound.ui.too_far_site").getString();
                g.drawString(font, st, x + w - 4 - font.width(st), y + 3, Theme.TEXT_FAINT, false);
            }
            Site site = toSite(sTag);
            List<Component> tip = new ArrayList<>();
            tip.add(siteName(sTag).withStyle(st -> st.withColor(Theme.EMBER)));
            tip.add(Component.translatable("frostbound.site." + sTag.getString("type").toLowerCase(Locale.ROOT) + ".desc"));
            tip.add(tr("frostbound.ui.visits", sTag.getInt("visits")));
            if (site != null) {
                tip.add(tr("frostbound.ui.supplies"));
                tip.addAll(costTooltip(ExpeditionSystem.supplies(site, expeditionMembers)));
            }
            tip(x, y, x + w - 46, y + h, tip);
            y += h + 2;
        }
        return y;
    }

    private static Site toSite(CompoundTag t) {
        try {
            return new Site(t.getInt("id"), SiteType.valueOf(t.getString("type")), t.getDouble("dist"), t.getDouble("danger"),
                    t.getBoolean("far"), t.getInt("mx"), t.getInt("mz"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static CompoundTag siteById(ListTag sites, int id) {
        for (int i = 0; i < sites.size(); i++) {
            if (sites.getCompound(i).getInt("id") == id) {
                return sites.getCompound(i);
            }
        }
        return null;
    }

    private static MutableComponent siteName(CompoundTag s) {
        return Component.translatable("frostbound.site." + s.getString("type").toLowerCase(Locale.ROOT)).append(" #" + s.getInt("id"));
    }

    private int weather(GuiGraphics g, int x, int y, int w) {
        CompoundTag s = ClientCity.snap();
        String cur = s.getString("weather");
        Theme.iconAt(g, weatherIcon(cur), x, y, 16);
        g.drawString(font, Component.translatable("frostbound.weather." + cur.toLowerCase(Locale.ROOT)), x + 20, y + 1, Theme.EMBER, false);
        g.drawString(font, Theme.temp(s.getDouble("outside")), x + 20, y + 10, Theme.tempColor(s.getDouble("outside")), false);
        y += 22;
        y = row(g, x, y, w, tr("frostbound.ui.tomorrow"), Theme.temp(s.getDouble("forecastBase")), Theme.tempColor(s.getDouble("forecastBase")));
        if (s.contains("waveDay")) {
            int wd = s.getInt("waveDay");
            int in = wd - s.getInt("day");
            Component wave = Component.translatable("frostbound.weather." + s.getString("waveState").toLowerCase(Locale.ROOT));
            y += Theme.wrapped(g, in <= 0 ? tr("frostbound.ui.wave_now", wave) : tr("frostbound.ui.wave_in", wave, in, wd), x, y, w,
                    in <= 2 ? Theme.BAD : Theme.WARN);
        }
        y += 4;
        y = section(g, x, y, w, tr("frostbound.ui.forecast"));
        List<String> fc = ClientCity.strings(s, "forecast");
        int hour = s.getInt("hour");
        int slotStart = (hour / 6) * 6;
        for (int i = 0; i < fc.size(); i++) {
            String ws = fc.get(i);
            int h = (slotStart + 6 * (i + 1)) % 24;
            int dayOff = (slotStart + 6 * (i + 1)) / 24;
            String label = String.format(Locale.ROOT, "+%dd %02d:00", dayOff, h);
            Theme.iconAt(g, weatherIcon(ws), x, y, 10);
            g.drawString(font, label, x + 13, y + 1, Theme.TEXT_DIM, false);
            Component n = Component.translatable("frostbound.weather." + ws.toLowerCase(Locale.ROOT));
            g.drawString(font, n, x + w - font.width(n), y + 1, ws.equals("CLEAR") || ws.equals("SNOW") ? Theme.TEXT : Theme.WARN, false);
            y += 11;
        }
        return y;
    }

    private int history(GuiGraphics g, int x, int y, int w) {
        ListTag hist = ClientCity.snap().getList("history", Tag.TAG_COMPOUND);
        for (int i = hist.size() - 1; i >= 0; i--) {
            CompoundTag e = hist.getCompound(i);
            String kind = e.getString("kind");
            int color = switch (kind) {
                case "death", "crisis" -> Theme.BAD;
                case "event", "law" -> Theme.EMBER;
                case "research", "build" -> Theme.GOOD;
                default -> Theme.TEXT;
            };
            g.drawString(font, clock(e.getLong("step")), x, y, Theme.TEXT_FAINT, false);
            y += 9;
            y += Theme.wrapped(g, ClientCity.msg(e), x + 4, y, w - 4, color);
            y += 3;
        }
        return y;
    }

    // ------------------------------------------------------------------ building panel

    private void buildingPanel(GuiGraphics g) {
        CompoundTag b = ClientCity.building(selected);
        BuildingType t = BuildingTypes.get(b.getString("type"));
        if (t == null) {
            selected = -1;
            return;
        }
        int x = px0 + 6;
        int w = px1 - px0 - 12;
        int level = b.getInt("level");
        g.drawString(font, Component.translatable(t.key()).append(" Lv" + level), x, TOP + 5, Theme.EMBER, false);
        button(g, px1 - 16, TOP + 3, 12, 11, Component.literal("x"), true, () -> {
            selected = -1;
            WorldOverlay.selected = -1;
        });
        g.fill(px0 + 1, TOP + 16, px1 - 1, TOP + 17, Theme.ORANGE_DIM);
        int top = TOP + 19;
        int bottom = height - BOTTOM - 3;
        clip(g, px0 + 1, top, px1 - 1, bottom);
        int y = top + 2 - buildingScroll;
        y += Theme.wrapped(g, Component.translatable(t.key() + ".desc"), x, y, w, Theme.TEXT_DIM) + 4;
        String state = b.getString("state");
        int id = b.getInt("id");
        if (state.equals("CONSTRUCTION")) {
            y = row(g, x, y, w, tr("frostbound.ui.state"), Component.translatable("frostbound.ui.bstate.construction").getString(), Theme.WARN);
            Theme.bar(g, x, y, w, 7, b.getDouble("progress"), Theme.ORANGE);
            y += 11;
        } else {
            String key = b.getBoolean("paused") ? "paused" : b.getDouble("upgradeH") > 0 ? "upgrading" : state.toLowerCase(Locale.ROOT);
            y = row(g, x, y, w, tr("frostbound.ui.state"), Component.translatable("frostbound.ui.bstate." + key,
                    String.format(Locale.ROOT, "%.0f", b.getDouble("upgradeH"))).getString(), b.getBoolean("paused") ? Theme.WARN : Theme.GOOD);
        }
        int max = b.getInt("max");
        if (max > 0) {
            int desired = b.getInt("desired");
            g.drawString(font, tr("frostbound.ui.workers_line", b.getInt("workers"), desired, max), x, y + 3, Theme.TEXT, false);
            button(g, x + w - 34, y, 16, 14, Component.literal("-"), desired > 0, () -> act("workers", "id", id, "count", desired - 1));
            button(g, x + w - 16, y, 16, 14, Component.literal("+"), desired < max, () -> act("workers", "id", id, "count", desired + 1));
            y += 17;
        }
        if (t.heatDemand > 0 || t.housing > 0) {
            double temp = b.getDouble("temp");
            y = row(g, x, y, w, tr("frostbound.ui.temperature"), Theme.temp(temp), Theme.tempColor(temp));
            y = row(g, x, y, w, tr("frostbound.ui.heat_reach"), String.format(Locale.ROOT, "%.0f%%", b.getDouble("eta") * 100), Theme.TEXT_DIM);
        }
        if (t.employs()) {
            y = row(g, x, y, w, tr("frostbound.ui.efficiency"), String.format(Locale.ROOT, "%.0f%%", b.getDouble("eff") * 100),
                    b.getDouble("eff") < 0.6 ? Theme.WARN : Theme.TEXT);
        }
        if (t.output != null) {
            y = row(g, x, y, w, Component.translatable(t.output.key()), String.format(Locale.ROOT, "+%.1f/h", b.getDouble("out")), Theme.GOOD);
        }
        if (t.housing > 0) {
            y = row(g, x, y, w, tr("frostbound.ui.residents"), b.getInt("residents") + " / " + b.getInt("housing"), Theme.TEXT);
        }
        if (t.beds > 0) {
            y = row(g, x, y, w, tr("frostbound.ui.beds_line"), String.valueOf(b.getInt("beds")), Theme.TEXT);
        }
        y += 6;
        if (state.equals("ACTIVE")) {
            boolean canUp = level < t.maxLevel && b.getDouble("upgradeH") <= 0;
            if (level < t.maxLevel) {
                Cost up = t.upgradeCost(level);
                button(g, x, y, w, 15, tr("frostbound.ui.upgrade", level + 1), canUp, () -> act("upgrade", "id", id));
                List<Component> tip = new ArrayList<>();
                tip.add(tr("frostbound.ui.upgrade_time", t.upgradeHours(level)));
                tip.addAll(costTooltip(up));
                tip(x, y, x + w, y + 15, tip);
                y += 17;
                costLine(g, x, y, up);
                y += 12;
            }
            boolean paused = b.getBoolean("paused");
            if (t.employs()) {
                button(g, x, y, w, 15, tr(paused ? "frostbound.ui.resume" : "frostbound.ui.pause_building"), true,
                        () -> act("pause_building", "id", id, "on", !paused));
                y += 17;
            }
        }
        boolean confirm = confirmDemolish == id;
        button(g, x, y, w, 15, tr(confirm ? "frostbound.ui.demolish_confirm" : "frostbound.ui.demolish"), true, confirm, () -> {
            if (confirmDemolish == id) {
                act("demolish", "id", id);
                selected = -1;
                WorldOverlay.selected = -1;
                confirmDemolish = -1;
            } else {
                confirmDemolish = id;
            }
        });
        y += 17;
        unclip(g);
        int content = y + buildingScroll - (top + 2);
        int max2 = Math.max(0, content - (bottom - top) + 6);
        buildingScroll = Math.min(buildingScroll, max2);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) {
            return true;
        }
        if (!overWorld(x, y)) {
            return false;
        }
        Vec3 p = WorldOverlay.pick(x, y, width, height);
        if (button == 1) {
            if (WorldOverlay.placing != null) {
                WorldOverlay.placing = null;
                return true;
            }
            return false;
        }
        if (button != 0 || p == null) {
            return false;
        }
        if (WorldOverlay.placing != null) {
            BuildingType t = BuildingTypes.get(WorldOverlay.placing);
            if (t != null) {
                int[] fp = WorldOverlay.footprint(t, p);
                act("place", "type", t.id, "x", fp[0], "z", fp[1], "rot", WorldOverlay.rotation);
                Sounds.play("build", 0.6f, 1.0f);
                if (!Screen.hasShiftDown()) {
                    WorldOverlay.placing = null;
                }
            }
            return true;
        }
        CompoundTag b = buildingAt(p);
        if (b != null) {
            Sounds.click();
            select(b.getInt("id"));
        } else {
            selected = -1;
            WorldOverlay.selected = -1;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 1) {
            CameraController.rotate(dx * 0.5, dy * 0.4);
            return true;
        }
        if (button == 2) {
            double k = CameraController.dist / 220.0;
            CameraController.pan(-dx * k * 2, dy * k * 2);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (panelOpen && x >= TABW && x < TABW + panelWidth() && y >= TOP && y < height - BOTTOM) {
            if (selected >= 0) {
                buildingScroll = Math.max(0, buildingScroll - (int) (delta * 12));
            } else {
                scroll.put(tab, Math.max(0, scroll.getOrDefault(tab, 0) - (int) (delta * 12)));
            }
            return true;
        }
        CameraController.zoom(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientSetup.MANAGEMENT_KEY.matches(key, scan)) {
            onClose();
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_R -> {
                WorldOverlay.rotation = (WorldOverlay.rotation + 1) % 4;
                return true;
            }
            case GLFW.GLFW_KEY_H -> {
                toggleHeat();
                return true;
            }
            case GLFW.GLFW_KEY_SPACE -> {
                act("pause", "on", !ClientCity.snap().getBoolean("paused"));
                return true;
            }
            case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3 -> {
                act("speed", "speed", key - GLFW.GLFW_KEY_0);
                return true;
            }
            default -> {
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && (WorldOverlay.placing != null || selected >= 0)) {
            WorldOverlay.placing = null;
            selected = -1;
            WorldOverlay.selected = -1;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Used by the CI screenshot tour. */
    public void showTab(Tab t) {
        tab = t;
        panelOpen = true;
        selected = -1;
        WorldOverlay.selected = -1;
        if (t == Tab.POPULATION) {
            requestCitizens();
        }
    }

    /** Used by the CI screenshot tour. */
    public void showBuilding(int id) {
        select(id);
    }
}
