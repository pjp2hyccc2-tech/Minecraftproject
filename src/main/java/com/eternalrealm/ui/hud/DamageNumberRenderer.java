package com.eternalrealm.ui.hud;

import com.eternalrealm.client.ClientCombatState;
import com.eternalrealm.config.ERClientConfig;
import com.eternalrealm.network.forge.DamageNumberPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * Floating numbers for the damage the local player dealt (data from {@link DamageNumberPacket}, attacker only).
 * Drawn like name tags: billboarded text in world space that rises and fades out.
 */
public final class DamageNumberRenderer {
    private static final Logger LOG = LogUtils.getLogger();
    private static final float BASE_SCALE = 0.025F;
    private static final double RISE_PER_TICK = 0.035;
    private static final float FADE_FROM = 0.6F;
    private static final int NORMAL = 0xFFFFFF;
    private static final int CRITICAL = 0xFFD54F;
    private static final int HEAVY = 0xFFA040;
    private static final int SKILL = 0x7FD4FF;

    /** A rendering bug must never crash the game every frame: the feature turns itself off instead. */
    private static boolean disabled;

    private DamageNumberRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (disabled || !ERClientConfig.DAMAGE_NUMBERS.get() || ClientCombatState.damageNumbers().isEmpty()) {
            return;
        }
        try {
            draw(event);
        } catch (RuntimeException e) {
            disabled = true;
            LOG.error("Damage numbers disabled after a rendering error", e);
        }
    }

    private static void draw(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;
        long now = ClientCombatState.tick();
        float partialTick = event.getPartialTick();

        for (ClientCombatState.DamageNumber number : ClientCombatState.damageNumbers()) {
            float age = (now - number.spawnTick()) + partialTick;
            float life = age / ClientCombatState.DAMAGE_NUMBER_TICKS;
            if (life < 0.0F || life > 1.0F) {
                continue;
            }
            int alpha = life <= FADE_FROM ? 255 : Math.round(255.0F * (1.0F - (life - FADE_FROM) / (1.0F - FADE_FROM)));
            alpha = Math.max(8, Math.min(255, alpha));
            String text = format(number.amount()) + (number.critical() ? "!" : "");
            float scale = BASE_SCALE * (number.critical() ? 1.4F : 1.0F) * (life < 0.15F ? 1.0F + (0.15F - life) * 3.0F : 1.0F);

            pose.pushPose();
            pose.translate(number.x() - cameraPosition.x, number.y() + age * RISE_PER_TICK - cameraPosition.y,
                    number.z() - cameraPosition.z);
            pose.mulPose(camera.rotation());
            pose.scale(-scale, -scale, scale);
            Matrix4f matrix = pose.last().pose();
            font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, (alpha << 24) | color(number.flags()), true, matrix,
                    buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static int color(int flags) {
        if ((flags & DamageNumberPacket.CRITICAL) != 0) {
            return CRITICAL;
        }
        if ((flags & DamageNumberPacket.SKILL) != 0) {
            return SKILL;
        }
        if ((flags & DamageNumberPacket.HEAVY) != 0) {
            return HEAVY;
        }
        return NORMAL;
    }

    private static String format(float amount) {
        return amount < 10.0F ? String.format(Locale.ROOT, "%.1f", amount) : Integer.toString(Math.round(amount));
    }
}
