package com.eternalrealm.ui.screen;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.client.ClientCharacterState;
import com.eternalrealm.client.ClientKeyMappings;
import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.job.JobDefinition;
import com.eternalrealm.job.JobRules;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.PassiveTotals;
import com.eternalrealm.job.Prerequisite;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.SkillKind;
import com.eternalrealm.job.SkillTree;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The job's skill tree: three branches side by side, six tiers deep. Nodes are connected to their prerequisites; their
 * frame shows the state (locked, learnable, learned x/max, maxed) and the kind (actives square, passives with cut
 * corners, ultimates in gold). Hovering shows the numbers, what the next level adds and what the node opens; the
 * button below learns the selected node. The loadout bar puts learned actives into the six skill slots (drag a node
 * onto a slot, or select it and click the slot; right click empties a slot). Everything is checked again by the
 * server, which answers with a new character snapshot.
 */
public final class SkillScreen extends JournalScreen {
    private static final int NODE = 22;
    private static final int ROW = 32;
    private static final int GUTTER = 26;
    private static final int BRANCH_GAP = 8;
    private static final int HEADER = 40;
    private static final int FOOTER = 46;
    private static final int SLOT = 22;
    private static final int GOLD = 0xFFE8C66A;
    private static final int LINE_MET = 0xFFE8C66A;
    private static final int LINE_UNMET = 0xFF4A4A52;
    private static final int[] SLOT_ORDER = {1, 2, 3, 5, 6, 4};

    private Button investButton;
    private Button resetButton;
    private String selected;
    private String dragging;
    private boolean dragMoved;
    private double scroll;
    private long lastRequestMillis;

    public SkillScreen() {
        super(Tab.SKILLS);
    }

    @Override
    protected int contentWidth() {
        return Math.max(CONTENT_WIDTH, Math.min(420, width - TAB_WIDTH - 30));
    }

    @Override
    protected int panelHeight() {
        return Math.max(PANEL_HEIGHT, Math.min(300, height - 16));
    }

    @Override
    protected void initPage() {
        investButton = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.skill.learn"),
                b -> invest()).bounds(left + contentWidth() - 66, bottom() - 20, 66, 18).build());
        resetButton = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.skill.reset"),
                b -> reset()).bounds(left + contentWidth() - 52, top + 11, 52, 14).build());
        updateButtons();
    }

    // ------------------------------------------------------------------ data

    private Optional<CharacterSnapshot> state() {
        return ClientCharacterState.snapshot();
    }

    private Optional<JobDefinition> job() {
        return state().map(s -> JobType.fromStored(s.jobId())).filter(JobType::isChosen).map(Jobs::get);
    }

    private int level(SkillDefinition skill) {
        return state().map(s -> SkillTree.levelOf(skill, s.level(), s.skillInvestments())).orElse(0);
    }

    private SkillTree.InvestResult investResult(SkillDefinition skill) {
        CharacterSnapshot s = state().orElse(null);
        if (s == null) {
            return SkillTree.InvestResult.NO_JOB;
        }
        return SkillTree.canInvest(JobType.fromStored(s.jobId()), s.level(), s.skillInvestments(),
                s.unspentSkillPoints(), skill.id());
    }

    private List<String> loadout() {
        CharacterSnapshot s = state().orElse(null);
        if (s == null) {
            return List.of();
        }
        return SkillTree.effectiveLoadout(JobType.fromStored(s.jobId()), s.level(), s.skillInvestments(),
                s.skillLoadout());
    }

    private PassiveTotals passives() {
        return state().map(s -> SkillTree.passives(JobType.fromStored(s.jobId()), s.level(), s.skillInvestments()))
                .orElse(PassiveTotals.NONE);
    }

    // ------------------------------------------------------------------ geometry

    private int bottom() {
        return top + panelHeight();
    }

    private int pitch() {
        return (contentWidth() - GUTTER - 2 * BRANCH_GAP) / 9;
    }

    private int treeTop() {
        return top + HEADER;
    }

    private int treeBottom() {
        return bottom() - FOOTER;
    }

    private int nodeX(SkillDefinition skill) {
        return left + GUTTER + skill.gridColumn() * pitch() + skill.branch() * BRANCH_GAP + pitch() / 2;
    }

    private int rowY(int tier) {
        return treeTop() + 4 + (tier - 1) * ROW + NODE / 2 - (int) Math.round(scroll);
    }

    private int nodeY(SkillDefinition skill) {
        return rowY(skill.tier());
    }

    private double maxScroll() {
        return Math.max(0, SkillDefinition.TIERS * ROW + 4 - (treeBottom() - treeTop()));
    }

    private boolean insideTree(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + contentWidth() && mouseY >= treeTop() && mouseY < treeBottom();
    }

    private Optional<SkillDefinition> nodeAt(double mouseX, double mouseY) {
        if (!insideTree(mouseX, mouseY)) {
            return Optional.empty();
        }
        for (SkillDefinition skill : job().map(JobDefinition::skills).orElse(List.of())) {
            int x = nodeX(skill);
            int y = nodeY(skill);
            if (Math.abs(mouseX - x) <= NODE / 2.0 && Math.abs(mouseY - y) <= NODE / 2.0) {
                return Optional.of(skill);
            }
        }
        return Optional.empty();
    }

    /** Left edge of the loadout box of {@code slot}. */
    private int slotX(int slot) {
        int x = left + 2;
        for (int each : SLOT_ORDER) {
            if (each == SkillTree.ULTIMATE_SLOT) {
                x += 6;
            }
            if (each == slot) {
                return x;
            }
            x += SLOT + 3;
        }
        return x;
    }

    private int slotY() {
        return bottom() - SLOT - 2;
    }

    private int slotAt(double mouseX, double mouseY) {
        for (int slot : SLOT_ORDER) {
            int x = slotX(slot);
            if (mouseX >= x && mouseX < x + SLOT && mouseY >= slotY() && mouseY < slotY() + SLOT) {
                return slot;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ actions

    private boolean throttled() {
        long now = System.currentTimeMillis();
        if (now - lastRequestMillis < 250L) {
            return true;
        }
        lastRequestMillis = now;
        return false;
    }

    private void invest() {
        if (selected == null || throttled()) {
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("skill", selected);
        ERNetwork.sendToServer(new UiActionPacket("skill.invest", args));
    }

    private void reset() {
        if (throttled()) {
            return;
        }
        ERNetwork.sendToServer(new UiActionPacket("skill.reset", new JsonObject()));
    }

    private void assign(int slot, String skillId) {
        if (throttled()) {
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("slot", slot);
        args.addProperty("skill", skillId);
        ERNetwork.sendToServer(new UiActionPacket("skill.slot", args));
        com.eternalrealm.client.sound.UiSounds.click();
    }

    @Override
    public void tick() {
        super.tick();
        updateButtons();
    }

    private void updateButtons() {
        if (investButton == null) {
            return;
        }
        boolean hasJob = job().isPresent();
        SkillDefinition skill = selected == null ? null : Jobs.skill(selected).orElse(null);
        investButton.visible = hasJob;
        resetButton.visible = hasJob;
        investButton.active = skill != null && investResult(skill) == SkillTree.InvestResult.OK;
        investButton.setMessage(Component.translatable(skill != null && level(skill) > 0
                ? "screen.eternalrealm.skill.upgrade" : "screen.eternalrealm.skill.learn"));
        CharacterSnapshot s = state().orElse(null);
        resetButton.active = s != null && !s.skillInvestments().isEmpty();
        if (s != null) {
            resetButton.setTooltip(Tooltip.create(Component.translatable("screen.eternalrealm.skill.reset_tooltip",
                    rules().skillResetCost(s.level()))));
        }
    }

    private JobRules rules() {
        JsonObject json = ClientUiState.get("job_rules").orElse(null);
        JobRules defaults = JobRules.defaults();
        if (json == null) {
            return defaults;
        }
        return new JobRules(ClientUiState.intOr(json, "freeBelow", defaults.freeChangeBelowLevel()),
                ClientUiState.longOr(json, "goldPerLevel", defaults.goldPerLevel()),
                ClientUiState.longOr(json, "resetGoldPerLevel", defaults.skillResetGoldPerLevel()));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int slot = slotAt(mouseX, mouseY);
        if (slot > 0 && job().isPresent()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                assign(slot, SkillTree.EMPTY);
            } else if (selected != null) {
                assign(slot, selected);
            }
            return true;
        }
        Optional<SkillDefinition> node = nodeAt(mouseX, mouseY);
        if (node.isPresent() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            selected = node.get().id();
            dragging = node.get().isUsable() && (level(node.get()) > 0 || node.get().isInnate()) ? selected : null;
            dragMoved = false;
            updateButtons();
            com.eternalrealm.client.sound.UiSounds.click();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragMoved = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null && dragMoved) {
            int slot = slotAt(mouseX, mouseY);
            if (slot > 0) {
                assign(slot, dragging);
            }
        }
        dragging = null;
        dragMoved = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (insideTree(mouseX, mouseY) && maxScroll() > 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - delta * 16.0));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Optional<CharacterSnapshot> state = state();
        Optional<JobDefinition> job = job();
        if (state.isEmpty() || job.isEmpty()) {
            drawWrapped(graphics, Component.translatable("screen.eternalrealm.skill.no_job"), left, top + 20,
                    contentWidth(), MUTED, 3);
            return;
        }
        CharacterSnapshot snapshot = state.get();
        JobDefinition definition = job.get();
        scroll = Math.min(scroll, maxScroll());
        drawHeader(graphics, snapshot, definition);
        graphics.enableScissor(left, treeTop(), left + contentWidth(), treeBottom());
        drawRows(graphics, snapshot);
        for (SkillDefinition skill : definition.skills()) {
            drawLines(graphics, skill, snapshot);
        }
        List<String> loadout = loadout();
        for (SkillDefinition skill : definition.skills()) {
            drawNode(graphics, skill, snapshot, loadout, mouseX, mouseY);
        }
        graphics.disableScissor();
        if (maxScroll() > 0) {
            int trackTop = treeTop();
            int trackHeight = treeBottom() - treeTop();
            int barHeight = Math.max(12, (int) (trackHeight * trackHeight / (trackHeight + maxScroll())));
            int barTop = trackTop + (int) ((trackHeight - barHeight) * scroll / maxScroll());
            int x = left + contentWidth() - 2;
            graphics.fill(x, trackTop, x + 2, trackTop + trackHeight, 0x30FFFFFF);
            graphics.fill(x, barTop, x + 2, barTop + barHeight, 0xA0E8C66A);
        }
        drawFooter(graphics, loadout, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphics graphics, CharacterSnapshot snapshot, JobDefinition definition) {
        int y = top + 13;
        graphics.blit(JobScreen.emblem(definition.type()), left, y - 4, 0, 0, 16, 16, 16, 16);
        graphics.drawString(font, Component.translatable(definition.type().translationKey()), left + 19, y, TITLE_COLOR);
        int learned = 0;
        for (SkillDefinition skill : definition.skills()) {
            if (level(skill) > 0) {
                learned++;
            }
        }
        Component points = Component.translatable("screen.eternalrealm.skill.points_header",
                snapshot.unspentSkillPoints(), learned, definition.skills().size());
        graphics.drawString(font, points, left + 19 + font.width(Component.translatable(
                definition.type().translationKey())) + 8, y, snapshot.unspentSkillPoints() > 0 ? 0xFFFF55 : MUTED);
        int titleY = treeTop() - 10;
        for (int branch = 0; branch < SkillDefinition.BRANCHES; branch++) {
            int x0 = left + GUTTER + branch * (3 * pitch() + BRANCH_GAP);
            int x1 = x0 + 3 * pitch();
            graphics.fill(x0 + 2, titleY + 9, x1 - 2, titleY + 10, 0x60E8C66A);
            graphics.drawCenteredString(font, Component.translatable(definition.branchKey(branch)), (x0 + x1) / 2,
                    titleY, 0xFFD8C8A0);
        }
    }

    private void drawRows(GuiGraphics graphics, CharacterSnapshot snapshot) {
        for (int branch = 0; branch < SkillDefinition.BRANCHES; branch++) {
            int x0 = left + GUTTER + branch * (3 * pitch() + BRANCH_GAP);
            graphics.fill(x0, treeTop(), x0 + 3 * pitch(), treeBottom(), branch % 2 == 0 ? 0x14FFFFFF : 0x0AFFFFFF);
        }
        for (int tier = 1; tier <= SkillDefinition.TIERS; tier++) {
            int y = rowY(tier);
            int required = tierLevel(tier);
            boolean reached = snapshot.level() >= required;
            graphics.drawString(font, "Lv" + required, left + 1, y - 4, reached ? 0xFFB0A070 : 0xFF884444);
            if (!reached) {
                graphics.fill(left + GUTTER - 2, y - NODE / 2 - 3, left + contentWidth() - 4, y + NODE / 2 + 5,
                        0x50000000);
            }
        }
    }

    /** The lowest required level of the tier's nodes (the tier's gate). */
    private int tierLevel(int tier) {
        int lowest = 100;
        for (SkillDefinition skill : job().map(JobDefinition::skills).orElse(List.of())) {
            if (skill.tier() == tier) {
                lowest = Math.min(lowest, skill.requiredLevel());
            }
        }
        return lowest;
    }

    /** Orthogonal lines from each prerequisite's bottom to the node's top, routed around other nodes. */
    private void drawLines(GuiGraphics graphics, SkillDefinition skill, CharacterSnapshot snapshot) {
        int cx = nodeX(skill);
        int cy = nodeY(skill) - NODE / 2;
        int gapAbove = cy - (ROW - NODE) / 2;
        for (Prerequisite prerequisite : skill.prerequisites()) {
            SkillDefinition parent = Jobs.skill(prerequisite.skillId()).orElse(null);
            if (parent == null) {
                continue;
            }
            boolean met = level(parent) >= prerequisite.level();
            int color = met ? LINE_MET : LINE_UNMET;
            int px = nodeX(parent);
            int py = nodeY(parent) + NODE / 2;
            int gapBelow = py + (ROW - NODE) / 2;
            if (skill.tier() == parent.tier() + 1) {
                vertical(graphics, px, py, gapBelow, color);
                horizontal(graphics, px, cx, gapBelow, color);
                vertical(graphics, cx, gapBelow, cy, color);
            } else {
                int gutter = px + (skill.gridColumn() >= parent.gridColumn() ? pitch() / 2 : -pitch() / 2);
                vertical(graphics, px, py, gapBelow, color);
                horizontal(graphics, px, gutter, gapBelow, color);
                vertical(graphics, gutter, gapBelow, gapAbove, color);
                horizontal(graphics, gutter, cx, gapAbove, color);
                vertical(graphics, cx, gapAbove, cy, color);
            }
            if (prerequisite.level() > 1) {
                graphics.pose().pushPose();
                graphics.pose().translate(cx + 3, cy - 7, 0);
                graphics.pose().scale(0.6F, 0.6F, 1.0F);
                graphics.drawString(font, "Lv" + prerequisite.level(), 0, 0, met ? 0xFFE8C66A : 0xFF9A9AA4);
                graphics.pose().popPose();
            }
        }
    }

    private static void vertical(GuiGraphics graphics, int x, int y0, int y1, int color) {
        graphics.fill(x, Math.min(y0, y1), x + 1, Math.max(y0, y1) + 1, color);
    }

    private static void horizontal(GuiGraphics graphics, int x0, int x1, int y, int color) {
        graphics.fill(Math.min(x0, x1), y, Math.max(x0, x1) + 1, y + 1, color);
    }

    private void drawNode(GuiGraphics graphics, SkillDefinition skill, CharacterSnapshot snapshot, List<String> loadout,
                          int mouseX, int mouseY) {
        int x = nodeX(skill) - NODE / 2;
        int y = nodeY(skill) - NODE / 2;
        int level = level(skill);
        SkillTree.InvestResult result = investResult(skill);
        boolean learnable = result == SkillTree.InvestResult.OK || result == SkillTree.InvestResult.NO_POINTS;
        boolean maxed = level >= skill.maxLevel();
        boolean hovered = Math.abs(mouseX - nodeX(skill)) <= NODE / 2 && Math.abs(mouseY - nodeY(skill)) <= NODE / 2
                && insideTree(mouseX, mouseY);
        int border;
        if (maxed) {
            border = 0xFFFFD34A;
        } else if (level > 0) {
            border = 0xFF7CFC7C;
        } else if (learnable) {
            border = pulse(0xFFE8C66A);
        } else {
            border = 0xFF3A3A40;
        }
        if (skill.kind() == SkillKind.ULTIMATE) {
            graphics.fill(x - 2, y - 2, x + NODE + 2, y + NODE + 2, level > 0 ? 0xFFB08A2A : 0xFF4A3A1A);
        }
        graphics.fill(x - 1, y - 1, x + NODE + 1, y + NODE + 1, border);
        graphics.fill(x, y, x + NODE, y + NODE, level > 0 ? 0xFF1A1A22 : 0xFF101014);
        float tint = level > 0 ? 1.0F : learnable ? 0.75F : 0.35F;
        graphics.setColor(tint, tint, tint, 1.0F);
        graphics.blit(com.eternalrealm.ui.hud.CombatHudOverlay.skillIcon(skill.id()), x + 3, y + 3, 0, 0, 16, 16, 16,
                16);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (skill.isPassive()) {
            // cut corners: passives read as round medallions next to the square actives
            for (int i = 0; i < 3; i++) {
                graphics.fill(x - 1, y - 1 + i, x + 2 - i, y + i, 0xFF0C0C10);
                graphics.fill(x + NODE - 2 + i, y - 1 + i, x + NODE + 1, y + i, 0xFF0C0C10);
                graphics.fill(x - 1, y + NODE - i, x + 2 - i, y + NODE + 1 - i, 0xFF0C0C10);
                graphics.fill(x + NODE - 2 + i, y + NODE - i, x + NODE + 1, y + NODE + 1 - i, 0xFF0C0C10);
            }
        }
        // level pips under the node
        int pipsWidth = skill.maxLevel() * 3 - 1;
        int pipX = x + (NODE - pipsWidth) / 2;
        for (int i = 0; i < skill.maxLevel(); i++) {
            graphics.fill(pipX + i * 3, y + NODE + 2, pipX + i * 3 + 2, y + NODE + 4, i < level ? GOLD : 0xFF3A3A40);
        }
        if (level > 0 && result == SkillTree.InvestResult.OK) {
            graphics.fill(x + NODE - 6, y + 1, x + NODE - 1, y + 2, 0xFF7CFC7C);
            graphics.fill(x + NODE - 4, y - 1, x + NODE - 3, y + 4, 0xFF7CFC7C);
        }
        int slot = loadout.indexOf(skill.id()) + 1;
        if (slot > 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(x + 1, y + NODE - 7, 100);
            graphics.pose().scale(0.6F, 0.6F, 1.0F);
            graphics.drawString(font, ClientKeyMappings.jobSkill(slot).getTranslatedKeyMessage(), 0, 0, 0xFFE8C66A);
            graphics.pose().popPose();
        }
        if (skill.id().equals(selected)) {
            graphics.renderOutline(x - 3, y - 3, NODE + 6, NODE + 6, 0xFFFFFFFF);
        } else if (hovered) {
            graphics.renderOutline(x - 2, y - 2, NODE + 4, NODE + 4, 0xA0FFFFFF);
        }
    }

    private static int pulse(int color) {
        double t = (System.currentTimeMillis() % 1200L) / 1200.0;
        int alpha = 150 + (int) Math.round(105 * (0.5 + 0.5 * Math.sin(t * Math.PI * 2.0)));
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    private void drawFooter(GuiGraphics graphics, List<String> loadout, int mouseX, int mouseY) {
        graphics.fill(left, treeBottom() + 1, left + contentWidth(), treeBottom() + 2, 0x60E8C66A);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.skill.loadout"), left + 2,
                treeBottom() + 5, 0xFFD8C8A0);
        for (int slot : SLOT_ORDER) {
            int x = slotX(slot);
            int y = slotY();
            boolean ultimate = slot == SkillTree.ULTIMATE_SLOT;
            graphics.fill(x - 1, y - 1, x + SLOT + 1, y + SLOT + 1, ultimate ? 0xFFB08A2A : 0xFF5A4A2A);
            graphics.fill(x, y, x + SLOT, y + SLOT, 0xFF101014);
            String id = slot - 1 < loadout.size() ? loadout.get(slot - 1) : "";
            if (!id.isEmpty()) {
                SkillDefinition skill = Jobs.skill(id).orElse(null);
                boolean ready = skill != null && level(skill) > 0;
                float tint = ready ? 1.0F : 0.4F;
                graphics.setColor(tint, tint, tint, 1.0F);
                graphics.blit(com.eternalrealm.ui.hud.CombatHudOverlay.skillIcon(id), x + 3, y + 3, 0, 0, 16, 16, 16,
                        16);
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
            boolean target = dragging != null && dragMoved && mouseX >= x && mouseX < x + SLOT && mouseY >= y
                    && mouseY < y + SLOT;
            if (target) {
                graphics.renderOutline(x - 2, y - 2, SLOT + 4, SLOT + 4, 0xFFFFFFFF);
            }
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 150);
            graphics.drawString(font, ClientKeyMappings.jobSkill(slot).getTranslatedKeyMessage(), x + 1, y + 1,
                    0xFFE8C66A);
            graphics.pose().popPose();
        }
        SkillDefinition skill = selected == null ? null : Jobs.skill(selected).orElse(null);
        if (skill != null) {
            int x = slotX(SkillTree.ULTIMATE_SLOT) + SLOT + 8;
            int maxWidth = left + contentWidth() - 70 - x;
            Component name = Component.translatable(skill.translationKey()).withStyle(ChatFormatting.GOLD);
            graphics.drawString(font, font.plainSubstrByWidth(name.getString(), maxWidth), x, slotY() + 2, 0xFFE8C66A);
            int level = level(skill);
            Component status = level > 0
                    ? Component.translatable("screen.eternalrealm.skill.level", level, skill.maxLevel())
                    : Component.translatable(investResult(skill).translationKey());
            graphics.drawString(font, font.plainSubstrByWidth(status.getString(), maxWidth), x, slotY() + 12,
                    level > 0 ? 0xFF7CFC7C : 0xFFB0A090);
        }
    }

    @Override
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (job().isEmpty()) {
            return;
        }
        if (dragging != null && dragMoved) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.blit(com.eternalrealm.ui.hud.CombatHudOverlay.skillIcon(dragging), mouseX - 8, mouseY - 8, 0, 0,
                    16, 16, 16, 16);
            graphics.pose().popPose();
            return;
        }
        Optional<SkillDefinition> hovered = nodeAt(mouseX, mouseY);
        if (hovered.isPresent()) {
            graphics.renderComponentTooltip(font, tooltip(hovered.get()), mouseX, mouseY);
            return;
        }
        int slot = slotAt(mouseX, mouseY);
        if (slot > 0) {
            List<String> loadout = loadout();
            String id = slot - 1 < loadout.size() ? loadout.get(slot - 1) : "";
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(slot == SkillTree.ULTIMATE_SLOT ? "screen.eternalrealm.skill.slot_ultimate"
                    : "screen.eternalrealm.skill.slot", ClientKeyMappings.jobSkill(slot).getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.GOLD));
            if (!id.isEmpty()) {
                lines.add(Component.translatable(Jobs.skill(id).map(SkillDefinition::translationKey).orElse(id)));
            }
            lines.add(Component.translatable("screen.eternalrealm.skill.slot_hint").withStyle(ChatFormatting.DARK_GRAY));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** The hover details of a node. */
    private List<Component> tooltip(SkillDefinition skill) {
        CharacterSnapshot snapshot = state().orElseThrow();
        List<Component> lines = new ArrayList<>();
        int level = level(skill);
        MutableComponent title = Component.translatable(skill.translationKey())
                .withStyle(skill.kind() == SkillKind.ULTIMATE ? ChatFormatting.GOLD : ChatFormatting.YELLOW);
        title.append(Component.literal("  ").append(Component.translatable(skill.kind().translationKey()))
                .withStyle(ChatFormatting.DARK_GRAY));
        lines.add(title);
        lines.add(level > 0
                ? Component.translatable("skill_info.eternalrealm.level", level, skill.maxLevel())
                .withStyle(level >= skill.maxLevel() ? ChatFormatting.GOLD : ChatFormatting.GREEN)
                : Component.translatable(skill.isInnate() ? "skill_info.eternalrealm.innate"
                : "skill_info.eternalrealm.unlearned", skill.isInnate() ? skill.requiredLevel() : skill.maxLevel())
                .withStyle(ChatFormatting.GRAY));
        lines.addAll(wrap(Component.translatable(skill.descriptionKey()).withStyle(ChatFormatting.WHITE)));
        PassiveTotals passive = passives();
        if (skill.isPassive()) {
            lines.addAll(SkillTexts.passive(skill, Math.max(1, level)));
        } else {
            lines.addAll(SkillTexts.combat(skill, level, passive));
            lines.addAll(SkillTexts.effects(skill, level));
        }
        if (level > 0 && level < skill.maxLevel()) {
            lines.add(SkillTexts.next(skill, level));
            if (skill.isPassive()) {
                lines.addAll(SkillTexts.passive(skill, level + 1));
            }
        } else if (level >= skill.maxLevel()) {
            lines.add(Component.translatable("skill_info.eternalrealm.maxed").withStyle(ChatFormatting.GOLD));
        }
        if (snapshot.level() < skill.requiredLevel()) {
            lines.add(Component.translatable("skill_info.eternalrealm.requires_level", skill.requiredLevel())
                    .withStyle(ChatFormatting.RED));
        }
        for (Prerequisite prerequisite : skill.prerequisites()) {
            SkillDefinition parent = Jobs.skill(prerequisite.skillId()).orElseThrow();
            boolean met = level(parent) >= prerequisite.level();
            lines.add(Component.translatable("skill_info.eternalrealm.requires",
                            Component.translatable(parent.translationKey()), prerequisite.level())
                    .append(met ? " ✔" : " ✘").withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        List<SkillDefinition> opens = Jobs.get(skill.job()).unlockedBy(skill.id());
        if (!opens.isEmpty()) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < opens.size(); i++) {
                if (i > 0) {
                    names.append(Component.translatable("tooltip.eternalrealm.equipment.job_separator"));
                }
                names.append(Component.translatable(opens.get(i).translationKey()));
            }
            lines.add(Component.translatable("skill_info.eternalrealm.unlocks", names)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        SkillTree.InvestResult result = investResult(skill);
        if (result == SkillTree.InvestResult.OK) {
            lines.add(Component.translatable("skill_info.eternalrealm.hint_learn", snapshot.unspentSkillPoints())
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
        if (skill.isUsable()) {
            lines.add(Component.translatable(skill.kind() == SkillKind.ULTIMATE ? "skill_info.eternalrealm.hint_ultimate"
                            : "skill_info.eternalrealm.hint_slot",
                    ClientKeyMappings.jobSkill(SkillTree.ULTIMATE_SLOT).getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    /** Splits a long line into tooltip-width pieces. */
    private List<Component> wrap(Component text) {
        List<Component> lines = new ArrayList<>();
        for (net.minecraft.network.chat.FormattedText piece : font.getSplitter().splitLines(text, 220,
                text.getStyle())) {
            lines.add(Component.literal(piece.getString()).withStyle(text.getStyle()));
        }
        return lines;
    }
}
