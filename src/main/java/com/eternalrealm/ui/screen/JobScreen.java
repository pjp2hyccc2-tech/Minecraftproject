package com.eternalrealm.ui.screen;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.client.ClientCharacterState;
import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.job.JobDefinition;
import com.eternalrealm.job.JobRules;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Job page: the six jobs with their role and description; choosing sends {@code job.choose}. */
public final class JobScreen extends JournalScreen {
    private static final int CARD_WIDTH = 156;
    private static final int CARD_HEIGHT = 66;
    private final List<Button> chooseButtons = new ArrayList<>();
    private long lastRequestMillis;

    public JobScreen() {
        super(Tab.JOB);
    }

    @Override
    protected void initPage() {
        chooseButtons.clear();
        List<JobDefinition> jobs = Jobs.selectable();
        for (int i = 0; i < jobs.size(); i++) {
            JobType type = jobs.get(i).type();
            int x = left + (i % 2) * (CARD_WIDTH + 8);
            int y = top + 34 + (i / 2) * (CARD_HEIGHT + 4);
            Button button = Button.builder(Component.translatable("screen.eternalrealm.job.choose"), b -> choose(type))
                    .bounds(x + CARD_WIDTH - 46, y + 2, 44, 14).build();
            chooseButtons.add(addRenderableWidget(button));
        }
        updateButtons();
    }

    private void choose(JobType type) {
        long now = System.currentTimeMillis();
        if (now - lastRequestMillis < 500L) {
            return;
        }
        lastRequestMillis = now;
        JsonObject args = new JsonObject();
        args.addProperty("job", type.id());
        ERNetwork.sendToServer(new UiActionPacket("job.choose", args));
    }

    @Override
    public void tick() {
        super.tick();
        updateButtons();
    }

    private void updateButtons() {
        JobType current = ClientCharacterState.snapshot().map(s -> JobType.fromStored(s.jobId())).orElse(JobType.NONE);
        List<JobDefinition> jobs = Jobs.selectable();
        for (int i = 0; i < chooseButtons.size(); i++) {
            chooseButtons.get(i).active = ClientCharacterState.snapshot().isPresent() && jobs.get(i).type() != current;
        }
    }

    @Override
    protected void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Optional<CharacterSnapshot> state = ClientCharacterState.snapshot();
        JobType current = state.map(s -> JobType.fromStored(s.jobId())).orElse(JobType.NONE);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.job.current",
                Component.translatable(current.translationKey())), left, top + 12, TEXT);
        graphics.drawString(font, costLine(state, current), left, top + 22, MUTED);
        List<JobDefinition> jobs = Jobs.selectable();
        for (int i = 0; i < jobs.size(); i++) {
            JobDefinition job = jobs.get(i);
            int x = left + (i % 2) * (CARD_WIDTH + 8);
            int y = top + 34 + (i / 2) * (CARD_HEIGHT + 4);
            boolean selected = job.type() == current;
            graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, selected ? 0x60E8C66A : 0x40FFFFFF);
            graphics.drawString(font, Component.translatable(job.type().translationKey()), x + 3, y + 4, TITLE_COLOR);
            graphics.drawString(font, Component.translatable("screen.eternalrealm.job.role_weapon",
                    Component.translatable(job.role().translationKey()),
                    Component.translatable(job.starterWeapon().translationKey())), x + 3, y + 16, 0xB0D0FF);
            drawWrapped(graphics, Component.translatable(job.type().descriptionKey()), x + 3, y + 28,
                    CARD_WIDTH - 6, 0xD0D0D0, 4);
        }
    }

    private Component costLine(Optional<CharacterSnapshot> state, JobType current) {
        JsonObject rules = ClientUiState.get("job_rules").orElse(null);
        int freeBelow = rules == null ? JobRules.defaults().freeChangeBelowLevel()
                : ClientUiState.intOr(rules, "freeBelow", JobRules.defaults().freeChangeBelowLevel());
        long perLevel = rules == null ? JobRules.defaults().goldPerLevel()
                : ClientUiState.longOr(rules, "goldPerLevel", JobRules.defaults().goldPerLevel());
        if (!current.isChosen()) {
            return Component.translatable("screen.eternalrealm.job.first_free");
        }
        int level = state.map(CharacterSnapshot::level).orElse(1);
        long cost = new JobRules(freeBelow, perLevel).changeCost(current, level);
        return cost == 0 ? Component.translatable("screen.eternalrealm.job.change_free", freeBelow)
                : Component.translatable("screen.eternalrealm.job.change_cost", cost);
    }
}
