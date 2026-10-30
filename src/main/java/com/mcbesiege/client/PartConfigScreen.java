package com.mcbesiege.client;

import com.mcbesiege.block.MachinePartBlockEntity;
import com.mcbesiege.net.Net;
import com.mcbesiege.part.Keys;
import com.mcbesiege.part.PartConfig;
import com.mcbesiege.part.PartType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Besiege "key mapper": key bindings, sliders and toggles of one part, plus its connectors. */
public class PartConfigScreen extends Screen {
    private static final ResourceLocation ICON_SETTINGS = new ResourceLocation("mcbesiege", "textures/gui/icon_settings.png");

    private final BlockPos pos;
    private final PartType type;
    private PartConfig cfg;
    private ListTag connectors = new ListTag();
    private final List<Integer> changedConnectors = new ArrayList<>();
    private String listening;     // part key action being rebound
    private int listeningCon = -1;
    private String listeningConKey;
    private final List<Runnable> relabel = new ArrayList<>();
    private int left, top, panelW, panelH;

    public PartConfigScreen(BlockPos pos, PartType type) {
        super(Component.translatable("screen.mcbesiege.part_config"));
        this.pos = pos;
        this.type = type;
    }

    @Override
    protected void init() {
        relabel.clear();
        if (cfg == null) {
            CompoundTag tag = new CompoundTag();
            if (minecraft != null && minecraft.level != null) {
                BlockEntity be = minecraft.level.getBlockEntity(pos);
                if (be instanceof MachinePartBlockEntity pbe) {
                    tag = pbe.getConfig();
                    connectors = pbe.getConnectors().copy();
                }
            }
            cfg = PartConfig.of(type, tag);
        }
        int rows = type.keys.size() + type.sliders.size() + type.toggles.size() + Math.min(connectors.size(), 4) * 2;
        panelW = 300;
        panelH = Math.min(height - 16, 58 + Math.max(1, rows) * 22 + 30);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        int y = top + 30;
        int lx = left + 12, bx = left + 150, bw = 136;
        for (PartType.KeySpec ks : type.keys) {
            final String id = ks.id();
            Button b = Button.builder(Component.empty(), btn -> {
                listening = id;
                listeningCon = -1;
                relabelAll();
            }).bounds(bx, y, bw, 18).build();
            relabel.add(() -> b.setMessage(Component.literal(id.equals(listening) ? "> ? <" : Keys.names(cfg.keys(id)))));
            addRenderableWidget(b);
            y += 22;
        }
        for (PartType.SliderSpec ss : type.sliders) {
            addRenderableWidget(new Slider(left + 12, y, panelW - 24, 18, ss));
            y += 22;
        }
        for (PartType.ToggleSpec ts : type.toggles) {
            final String id = ts.id();
            Button b = Button.builder(Component.empty(), btn -> {
                cfg.setToggle(id, !cfg.toggle(id));
                relabelAll();
            }).bounds(left + 12, y, panelW - 24, 18).build();
            relabel.add(() -> b.setMessage(Component.translatable("toggle.mcbesiege." + id).append(": ")
                    .append(Component.translatable(cfg.toggle(id) ? "options.on" : "options.off"))));
            addRenderableWidget(b);
            y += 22;
        }
        for (int i = 0; i < Math.min(connectors.size(), 4); i++) {
            final int idx = i;
            CompoundTag c = connectors.getCompound(i);
            String kind = c.getString("kind");
            int cx = left + 12;
            if (kind.equals("spring")) {
                Button kb = Button.builder(Component.empty(), btn -> {
                    listeningCon = idx;
                    listeningConKey = "keys";
                    listening = null;
                    relabelAll();
                }).bounds(cx + 110, y, 80, 18).build();
                relabel.add(() -> kb.setMessage(Component.literal(listeningCon == idx && "keys".equals(listeningConKey) ? "> ? <"
                        : Keys.names(cfgOf(idx).getIntArray("keys")))));
                addRenderableWidget(kb);
            } else if (kind.equals("rope")) {
                Button wb = Button.builder(Component.empty(), btn -> {
                    listeningCon = idx;
                    listeningConKey = "wind";
                    listening = null;
                    relabelAll();
                }).bounds(cx + 110, y, 60, 18).build();
                relabel.add(() -> wb.setMessage(Component.literal(listeningCon == idx && "wind".equals(listeningConKey) ? "> ? <"
                        : "↑" + Keys.names(cfgOf(idx).getIntArray("wind")))));
                addRenderableWidget(wb);
                Button ub = Button.builder(Component.empty(), btn -> {
                    listeningCon = idx;
                    listeningConKey = "unwind";
                    listening = null;
                    relabelAll();
                }).bounds(cx + 172, y, 60, 18).build();
                relabel.add(() -> ub.setMessage(Component.literal(listeningCon == idx && "unwind".equals(listeningConKey) ? "> ? <"
                        : "↓" + Keys.names(cfgOf(idx).getIntArray("unwind")))));
                addRenderableWidget(ub);
            }
            addRenderableWidget(Button.builder(Component.translatable("button.mcbesiege.remove"), btn -> {
                CompoundTag d = new CompoundTag();
                d.putLong("pos", pos.asLong());
                d.putInt("index", idx);
                d.putBoolean("remove", true);
                Net.toServer("con_cfg", d);
                connectors.remove(idx);
                changedConnectors.clear();
                rebuildWidgets();
            }).bounds(left + panelW - 60, y, 48, 18).build());
            y += 44;
        }
        int by = top + panelH - 26;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> save(false)).bounds(left + 12, by, 80, 18).build());
        if (type.hasConfig()) {
            addRenderableWidget(Button.builder(Component.translatable("button.mcbesiege.apply_all"), b -> save(true)).bounds(left + 96, by, 116, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + panelW - 84, by, 72, 18).build());
        relabelAll();
    }

    private CompoundTag cfgOf(int idx) {
        return connectors.getCompound(idx).getCompound("cfg");
    }

    private void relabelAll() {
        for (Runnable r : relabel) r.run();
    }

    private void save(boolean all) {
        CompoundTag d = new CompoundTag();
        d.putLong("pos", pos.asLong());
        d.put("cfg", cfg.save());
        d.putBoolean("all", all);
        Net.toServer("cfg", d);
        for (int idx : changedConnectors) {
            if (idx >= connectors.size()) continue;
            CompoundTag c = new CompoundTag();
            c.putLong("pos", pos.asLong());
            c.putInt("index", idx);
            c.put("cfg", cfgOf(idx));
            Net.toServer("con_cfg", c);
        }
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (listening != null || listeningCon >= 0) {
            int[] v;
            if (key == Keys.ESCAPE) v = null;
            else if (key == Keys.BACKSPACE || key == 261) v = new int[0];
            else v = new int[]{key};
            if (v != null) {
                if (listening != null) cfg.setKeys(listening, v);
                else {
                    CompoundTag c = connectors.getCompound(listeningCon);
                    CompoundTag cc = c.getCompound("cfg");
                    cc.putIntArray(listeningConKey, v);
                    c.put("cfg", cc);
                    if (!changedConnectors.contains(listeningCon)) changedConnectors.add(listeningCon);
                }
            }
            listening = null;
            listeningCon = -1;
            relabelAll();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g);
        g.fill(left - 2, top - 2, left + panelW + 2, top + panelH + 2, 0xFF6B4A2E);
        g.fill(left, top, left + panelW, top + panelH, 0xF0201C18);
        g.fill(left, top, left + panelW, top + 24, 0xFF3A2C1E);
        g.blit(ICON_SETTINGS, left + 6, top + 4, 0, 0, 16, 16, 16, 16);
        g.renderItem(new ItemStack(com.mcbesiege.registry.ModItems.part(type)), left + panelW - 24, top + 4);
        g.drawString(font, Component.translatable("block.mcbesiege." + type.id()), left + 26, top + 8, 0xFFFFD27A, true);
        int y = top + 30;
        for (PartType.KeySpec ks : type.keys) {
            g.drawString(font, Component.translatable("action.mcbesiege." + ks.id()), left + 12, y + 5, 0xFFE0E0E0, false);
            y += 22;
        }
        y += 22 * (type.sliders.size() + type.toggles.size());
        for (int i = 0; i < Math.min(connectors.size(), 4); i++) {
            CompoundTag c = connectors.getCompound(i);
            String kind = c.getString("kind");
            g.drawString(font, Component.translatable("item.mcbesiege." + kind + "_tool")
                    .append(String.format(Locale.ROOT, " (%d,%d,%d)", c.getInt("dx"), c.getInt("dy"), c.getInt("dz"))), left + 12, y + 5, 0xFFB8D8FF, false);
            y += 44;
        }
        if (!type.hasConfig() && connectors.isEmpty()) {
            g.drawString(font, Component.translatable("screen.mcbesiege.no_settings"), left + 12, top + 34, 0xFFB0B0B0, false);
        }
        if (listening != null || listeningCon >= 0) {
            g.drawCenteredString(font, Component.translatable("screen.mcbesiege.press_key"), width / 2, top + panelH - 40, 0xFFFFFF55);
        }
        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class Slider extends AbstractSliderButton {
        private final PartType.SliderSpec spec;

        Slider(int x, int y, int w, int h, PartType.SliderSpec spec) {
            super(x, y, w, h, Component.empty(), (cfg.slider(spec.id()) - spec.min()) / (spec.max() - spec.min()));
            this.spec = spec;
            updateMessage();
        }

        float real() {
            float v = spec.min() + (float) value * (spec.max() - spec.min());
            if (integral()) v = Math.round(v);
            return v;
        }

        boolean integral() {
            return spec.id().equals("ammo") || spec.id().equals("mode") || spec.id().equals("angle") || spec.id().equals("limit") || spec.id().equals("height");
        }

        @Override
        protected void updateMessage() {
            if (spec == null) return;
            float v = real();
            String text;
            if (spec.id().equals("mode")) {
                String[] names = {"AND", "OR", "XOR", "NOT", "NAND", "NOR"};
                text = names[Math.max(0, Math.min(5, (int) v))];
            } else if (integral()) text = String.valueOf((int) v);
            else text = String.format(Locale.ROOT, "%.2f", v);
            setMessage(Component.translatable("slider.mcbesiege." + spec.id()).append(": " + text));
        }

        @Override
        protected void applyValue() {
            cfg.setSlider(spec.id(), real());
        }
    }
}
