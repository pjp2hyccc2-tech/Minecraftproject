package com.eternalrealm.ui.screen;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.client.ClientCharacterState;
import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.job.JobClass;
import com.eternalrealm.job.JobDefinition;
import com.eternalrealm.job.JobPath;
import com.eternalrealm.job.JobRules;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.SkillKind;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Job page. "職業": the six jobs with their role and description; choosing sends {@code job.choose}. "二次職": the two
 * classes the character's job grows into at level {@value JobClass#LEVEL}, each with its branches, description and
 * skills (viewable before advancing); advancing (free the first time) or changing to the other class (for gold)
 * sends {@code job.advance}.
 */
public final class JobScreen extends JournalScreen {
    private static final int CARD_WIDTH = 156;
    private static final int CARD_HEIGHT = 66;
    private static final int CLASS_CARD_HEIGHT = 172;
    /** The mode the page was last left in (null: decided by the character, see {@link #defaultClassMode()}). */
    private static Boolean rememberedClassMode;

    private final List<Button> chooseButtons = new ArrayList<>();
    private final List<Button> advanceButtons = new ArrayList<>();
    private final List<JobClass> shownClasses = new ArrayList<>();
    private boolean classMode;
    private long lastRequestMillis;

    public JobScreen() {
        super(Tab.JOB);
    }

    /** Opens the page on the second classes (the skill screen's class tab before the character advanced). */
    public static void openClasses() {
        rememberedClassMode = true;
        Minecraft.getInstance().setScreen(new JobScreen());
    }

    private static boolean defaultClassMode() {
        return ClientCharacterState.snapshot().map(s -> {
            JobPath path = JobPath.of(s.jobId());
            return path.job().isChosen() && (path.isAdvanced() || s.level() >= JobClass.LEVEL);
        }).orElse(false);
    }

    @Override
    protected void initPage() {
        classMode = rememberedClassMode != null ? rememberedClassMode : defaultClassMode();
        chooseButtons.clear();
        advanceButtons.clear();
        shownClasses.clear();
        int toggleY = top + 10;
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.job.mode.jobs"),
                b -> setClassMode(false)).bounds(left + contentWidth() - 112, toggleY, 54, 14).build()).active = classMode;
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.job.mode.classes"),
                b -> setClassMode(true)).bounds(left + contentWidth() - 56, toggleY, 54, 14).build()).active = !classMode;
        if (classMode) {
            initClasses();
        } else {
            initJobs();
        }
        updateButtons();
    }

    private void setClassMode(boolean value) {
        rememberedClassMode = value;
        rebuildWidgets();
    }

    private void initJobs() {
        List<JobDefinition> jobs = Jobs.selectable();
        for (int i = 0; i < jobs.size(); i++) {
            JobType type = jobs.get(i).type();
            int x = left + (i % 2) * (CARD_WIDTH + 8);
            int y = top + 34 + (i / 2) * (CARD_HEIGHT + 4);
            Button button = Button.builder(Component.translatable("screen.eternalrealm.job.choose"), b -> choose(type))
                    .bounds(x + CARD_WIDTH - 46, y + 2, 44, 14).build();
            chooseButtons.add(addRenderableWidget(button));
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.outfit.job"), b -> outfit(false))
                .bounds(left, top + PANEL_HEIGHT - 18, 120, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.outfit.tier"), b -> outfit(true))
                .bounds(left + 124, top + PANEL_HEIGHT - 18, 120, 16).build());
    }

    private void initClasses() {
        JobType job = currentPath().job();
        shownClasses.addAll(JobClass.of(job));
        for (int i = 0; i < shownClasses.size(); i++) {
            JobClass jobClass = shownClasses.get(i);
            int x = left + i * (CARD_WIDTH + 8);
            int y = classCardTop();
            addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.job.view_skills"),
                    b -> SkillScreen.openClassTree(jobClass)).bounds(x + 4, y + CLASS_CARD_HEIGHT - 20, 72, 16).build());
            advanceButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.job.advance"),
                    b -> advance(jobClass)).bounds(x + CARD_WIDTH - 78, y + CLASS_CARD_HEIGHT - 20, 74, 16).build()));
        }
    }

    private int classCardTop() {
        return top + 36;
    }

    private void outfit(boolean tierLook) {
        if (throttled()) {
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("tier", tierLook);
        ERNetwork.sendToServer(new UiActionPacket("outfit.set", args));
    }

    /** The job's emblem (tools/art, 16x16). */
    static ResourceLocation emblem(JobType type) {
        return new ResourceLocation("eternalrealm", "textures/gui/job/" + type.id() + ".png");
    }

    /** A second class's emblem (tools/art/class_skill_icons.py, 16x16). */
    static ResourceLocation emblem(JobClass jobClass) {
        return new ResourceLocation("eternalrealm", "textures/gui/job/" + jobClass.id() + ".png");
    }

    private boolean throttled() {
        long now = System.currentTimeMillis();
        if (now - lastRequestMillis < 500L) {
            return true;
        }
        lastRequestMillis = now;
        return false;
    }

    private void choose(JobType type) {
        if (throttled()) {
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("job", type.id());
        ERNetwork.sendToServer(new UiActionPacket("job.choose", args));
    }

    private void advance(JobClass jobClass) {
        if (throttled()) {
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("class", jobClass.id());
        ERNetwork.sendToServer(new UiActionPacket("job.advance", args));
        com.eternalrealm.client.sound.UiSounds.click();
    }

    private static JobPath currentPath() {
        return ClientCharacterState.snapshot().map(s -> JobPath.of(s.jobId())).orElse(JobPath.NONE);
    }

    @Override
    public void tick() {
        super.tick();
        if (classMode && !shownClasses.equals(JobClass.of(currentPath().job()))) {
            rebuildWidgets(); // the job changed while the page was open
            return;
        }
        updateButtons();
    }

    private void updateButtons() {
        Optional<CharacterSnapshot> state = ClientCharacterState.snapshot();
        JobPath path = currentPath();
        List<JobDefinition> jobs = Jobs.selectable();
        for (int i = 0; i < chooseButtons.size(); i++) {
            chooseButtons.get(i).active = state.isPresent() && jobs.get(i).type() != path.job();
        }
        int level = state.map(CharacterSnapshot::level).orElse(1);
        for (int i = 0; i < advanceButtons.size(); i++) {
            JobClass jobClass = shownClasses.get(i);
            Button button = advanceButtons.get(i);
            boolean current = path.advanced() == jobClass;
            button.active = state.isPresent() && !current && level >= JobClass.LEVEL;
            button.setMessage(Component.translatable(current ? "screen.eternalrealm.job.current_class"
                    : path.isAdvanced() ? "screen.eternalrealm.job.change_class" : "screen.eternalrealm.job.advance"));
        }
    }

    @Override
    protected void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (classMode) {
            renderClasses(graphics);
            return;
        }
        Optional<CharacterSnapshot> state = ClientCharacterState.snapshot();
        JobPath path = currentPath();
        JobType current = path.job();
        graphics.drawString(font, Component.translatable("screen.eternalrealm.job.current",
                Component.translatable(path.translationKey())), left, top + 12, TEXT);
        graphics.drawString(font, costLine(state, current), left, top + 22, MUTED);
        List<JobDefinition> jobs = Jobs.selectable();
        for (int i = 0; i < jobs.size(); i++) {
            JobDefinition job = jobs.get(i);
            int x = left + (i % 2) * (CARD_WIDTH + 8);
            int y = top + 34 + (i / 2) * (CARD_HEIGHT + 4);
            boolean selected = job.type() == current;
            graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, selected ? 0x60E8C66A : 0x40FFFFFF);
            graphics.blit(emblem(job.type()), x + 3, y + 2, 0, 0, 16, 16, 16, 16);
            graphics.drawString(font, Component.translatable(job.type().translationKey()), x + 22, y + 6, TITLE_COLOR);
            graphics.drawString(font, Component.translatable("screen.eternalrealm.job.role_weapon",
                    Component.translatable(job.role().translationKey()), weapons(job)), x + 3, y + 20, 0xB0D0FF);
            drawWrapped(graphics, Component.translatable(job.type().descriptionKey()), x + 3, y + 31,
                    CARD_WIDTH - 6, 0xD0D0D0, 3);
        }
    }

    private void renderClasses(GuiGraphics graphics) {
        Optional<CharacterSnapshot> state = ClientCharacterState.snapshot();
        JobPath path = currentPath();
        if (!path.job().isChosen()) {
            drawWrapped(graphics, Component.translatable("screen.eternalrealm.job.class_pick_job"), left, top + 30,
                    contentWidth(), MUTED, 3);
            return;
        }
        Component job = Component.translatable(path.job().translationKey());
        graphics.drawString(font, path.isAdvanced()
                ? Component.translatable("screen.eternalrealm.job.class_of",
                Component.translatable(path.advanced().translationKey()), job)
                : Component.translatable("screen.eternalrealm.job.class_none", job), left, top + 13, TEXT);
        int level = state.map(CharacterSnapshot::level).orElse(1);
        long changeCost = rules().classChangeCost(Math.max(level, JobClass.LEVEL));
        Component info = level < JobClass.LEVEL
                ? Component.translatable("screen.eternalrealm.job.class_locked", JobClass.LEVEL, level)
                : path.isAdvanced() ? Component.translatable("screen.eternalrealm.job.class_change_cost", changeCost)
                : Component.translatable("screen.eternalrealm.job.class_first_free", changeCost);
        drawWrapped(graphics, info, left, top + 24, contentWidth() - 4, level < JobClass.LEVEL ? 0xFFCC8866 : MUTED, 1);
        for (int i = 0; i < shownClasses.size(); i++) {
            drawClassCard(graphics, shownClasses.get(i), left + i * (CARD_WIDTH + 8), classCardTop(),
                    path.advanced() == shownClasses.get(i));
        }
        drawWrapped(graphics, Component.translatable("screen.eternalrealm.job.class_rule", job), left,
                classCardTop() + CLASS_CARD_HEIGHT + 6, contentWidth(), 0xFFB0A090, 3);
    }

    private void drawClassCard(GuiGraphics graphics, JobClass jobClass, int x, int y, boolean current) {
        int h = CLASS_CARD_HEIGHT;
        graphics.fill(x - 1, y - 1, x + CARD_WIDTH + 1, y + h + 1, current ? 0xFFE8C66A : 0xFF4A3E26);
        graphics.fill(x, y, x + CARD_WIDTH, y + h, current ? 0xF0262014 : 0xF0141218);
        graphics.fill(x, y, x + CARD_WIDTH, y + 38, current ? 0x40E8C66A : 0x20FFFFFF);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 4, y + 3, 0);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.blit(emblem(jobClass), 0, 0, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();
        graphics.drawString(font, Component.translatable(jobClass.translationKey()), x + 42, y + 8, TITLE_COLOR);
        graphics.drawString(font, Component.translatable(jobClass.role().translationKey()), x + 42, y + 21, 0xB0D0FF);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.job.class_branches",
                Component.translatable(jobClass.branchKey(0)), Component.translatable(jobClass.branchKey(1))),
                x + 4, y + 43, 0xFFD8C8A0);
        int below = drawWrapped(graphics, Component.translatable(jobClass.descriptionKey()), x + 4, y + 56,
                CARD_WIDTH - 8, 0xD8D8D8, 6);
        List<SkillDefinition> skills = Jobs.classSkills(jobClass);
        long ultimates = skills.stream().filter(skill -> skill.kind() == SkillKind.ULTIMATE).count();
        graphics.drawString(font, Component.translatable("screen.eternalrealm.job.class_skills", skills.size(),
                ultimates), x + 4, Math.max(below + 4, y + h - 52), 0xFF9AD0A0);
        // the class's two ultimates and its first actives, so the card shows what the class is like
        List<SkillDefinition> featured = new ArrayList<>();
        skills.stream().filter(skill -> skill.kind() == SkillKind.ULTIMATE).forEach(featured::add);
        skills.stream().filter(skill -> skill.kind() == SkillKind.ACTIVE && skill.tier() == 1).forEach(featured::add);
        int iconY = y + h - 40;
        for (int i = 0; i < Math.min(8, featured.size()); i++) {
            SkillDefinition skill = featured.get(i);
            int ix = x + 4 + i * 19;
            graphics.fill(ix - 1, iconY - 1, ix + 17, iconY + 17,
                    skill.kind() == SkillKind.ULTIMATE ? 0xFFB08A2A : 0xFF3A3A40);
            graphics.blit(com.eternalrealm.ui.hud.CombatHudOverlay.skillIcon(skill.id()), ix, iconY, 0, 0, 16, 16, 16,
                    16);
        }
    }

    /** "片手剣・両手剣・槍": the weapons the job can use. */
    private static Component weapons(JobDefinition job) {
        net.minecraft.network.chat.MutableComponent text = Component.empty();
        boolean first = true;
        for (com.eternalrealm.combat.WeaponType type : com.eternalrealm.combat.WeaponType.values()) {
            if (job.weapons().contains(type)) {
                if (!first) {
                    text.append(Component.translatable("tooltip.eternalrealm.equipment.job_separator"));
                }
                text.append(Component.translatable(type.translationKey()));
                first = false;
            }
        }
        return text;
    }

    private static JobRules rules() {
        JsonObject json = ClientUiState.get("job_rules").orElse(null);
        JobRules defaults = JobRules.defaults();
        if (json == null) {
            return defaults;
        }
        return new JobRules(ClientUiState.intOr(json, "freeBelow", defaults.freeChangeBelowLevel()),
                ClientUiState.longOr(json, "goldPerLevel", defaults.goldPerLevel()),
                ClientUiState.longOr(json, "resetGoldPerLevel", defaults.skillResetGoldPerLevel()));
    }

    private Component costLine(Optional<CharacterSnapshot> state, JobType current) {
        JobRules rules = rules();
        if (!current.isChosen()) {
            return Component.translatable("screen.eternalrealm.job.first_free");
        }
        int level = state.map(CharacterSnapshot::level).orElse(1);
        long cost = rules.changeCost(current, level);
        return cost == 0 ? Component.translatable("screen.eternalrealm.job.change_free", rules.freeChangeBelowLevel())
                : Component.translatable("screen.eternalrealm.job.change_cost", cost);
    }
}
