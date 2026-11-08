package com.eternalrealm.ui.screen;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.client.ClientCharacterState;
import com.eternalrealm.client.ClientKeyMappings;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.JobDefinition;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.SkillTree;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Skill page: the job's four skills with level, cooldown, key and description; "+" invests a skill point. */
public final class SkillScreen extends JournalScreen {
    private static final int ROW_HEIGHT = 50;
    private final List<Button> investButtons = new ArrayList<>();
    private long lastRequestMillis;

    public SkillScreen() {
        super(Tab.SKILLS);
    }

    @Override
    protected void initPage() {
        investButtons.clear();
        for (int slot = 1; slot <= 4; slot++) {
            int row = slot;
            Button button = Button.builder(Component.literal("+"), b -> invest(row))
                    .bounds(left + CONTENT_WIDTH - 22, top + 30 + (slot - 1) * ROW_HEIGHT, 20, 16).build();
            investButtons.add(addRenderableWidget(button));
        }
        updateButtons();
    }

    private Optional<JobDefinition> job() {
        return ClientCharacterState.snapshot().map(s -> JobType.fromStored(s.jobId()))
                .filter(JobType::isChosen).map(Jobs::get);
    }

    private void invest(int slot) {
        long now = System.currentTimeMillis();
        if (now - lastRequestMillis < 300L) {
            return;
        }
        Optional<JobDefinition> job = job();
        if (job.isEmpty()) {
            return;
        }
        lastRequestMillis = now;
        JsonObject args = new JsonObject();
        args.addProperty("skill", job.get().skill(slot).id());
        ERNetwork.sendToServer(new UiActionPacket("skill.invest", args));
    }

    @Override
    public void tick() {
        super.tick();
        updateButtons();
    }

    private void updateButtons() {
        Optional<CharacterSnapshot> state = ClientCharacterState.snapshot();
        Optional<JobDefinition> job = job();
        for (int i = 0; i < investButtons.size(); i++) {
            Button button = investButtons.get(i);
            button.visible = job.isPresent();
            button.active = job.isPresent() && state.isPresent() && SkillTree.canInvest(job.get().type(),
                    state.get().level(), state.get().skillInvestments(), state.get().unspentSkillPoints(),
                    job.get().skill(i + 1).id()) == SkillTree.InvestResult.OK;
        }
    }

    @Override
    protected void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Optional<CharacterSnapshot> state = ClientCharacterState.snapshot();
        Optional<JobDefinition> job = job();
        if (state.isEmpty() || job.isEmpty()) {
            drawWrapped(graphics, Component.translatable("screen.eternalrealm.skill.no_job"), left, top + 20,
                    CONTENT_WIDTH, MUTED, 3);
            return;
        }
        CharacterSnapshot snapshot = state.get();
        graphics.drawString(font, Component.translatable("screen.eternalrealm.skill.points",
                snapshot.unspentSkillPoints()), left, top + 14, snapshot.unspentSkillPoints() > 0 ? 0xFFFF55 : MUTED);
        for (int slot = 1; slot <= 4; slot++) {
            SkillDefinition skill = job.get().skill(slot);
            int y = top + 28 + (slot - 1) * ROW_HEIGHT;
            int level = SkillTree.levelOf(skill, snapshot.level(), snapshot.skillInvestments());
            graphics.fill(left, y, left + CONTENT_WIDTH, y + ROW_HEIGHT - 3, level > 0 ? 0x40FFFFFF : 0x20FFFFFF);
            MutableComponent name = Component.literal("[")
                    .append(ClientKeyMappings.jobSkill(slot).getTranslatedKeyMessage()).append("] ")
                    .append(Component.translatable(skill.translationKey()));
            graphics.setColor(level > 0 ? 1.0F : 0.45F, level > 0 ? 1.0F : 0.45F, level > 0 ? 1.0F : 0.45F, 1.0F);
            graphics.blit(com.eternalrealm.ui.hud.CombatHudOverlay.skillIcon(skill.id()), left + 3, y + 3, 0, 0, 16, 16,
                    16, 16);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.drawString(font, name, left + 23, y + 3, level > 0 ? TITLE_COLOR : MUTED);
            Component levelText = level > 0
                    ? Component.translatable("screen.eternalrealm.skill.level", level, skill.maxLevel())
                    : Component.translatable("screen.eternalrealm.skill.locked", skill.unlockLevel());
            graphics.drawString(font, levelText, left + 170, y + 3, level > 0 ? 0x7CFC7C : 0xFF8080);
            int shownLevel = Math.max(1, level);
            String cooldown = String.format(Locale.ROOT, "%.1f", skill.cooldownTicks(shownLevel) / 20.0);
            Component info = Component.translatable("screen.eternalrealm.skill.info",
                    Component.translatable(skill.damageType().translationKey()), cooldown,
                    String.format(Locale.ROOT, "%.0f", skill.damageScale(shownLevel) * 100.0), weapons(skill));
            graphics.drawString(font, info, left + 23, y + 14, 0xB0D0FF);
            drawWrapped(graphics, Component.translatable(skill.descriptionKey()), left + 23, y + 25,
                    CONTENT_WIDTH - 50, 0xD0D0D0, 2);
        }
    }

    private static Component weapons(SkillDefinition skill) {
        if (skill.weapons().isEmpty()) {
            return Component.translatable("screen.eternalrealm.skill.any_weapon");
        }
        MutableComponent text = Component.empty();
        boolean first = true;
        for (WeaponType type : WeaponType.values()) {
            if (skill.weapons().contains(type)) {
                if (!first) {
                    text.append("/");
                }
                text.append(Component.translatable(type.translationKey()));
                first = false;
            }
        }
        return text;
    }
}
