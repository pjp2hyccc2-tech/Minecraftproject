package com.eternalrealm.client.guildhouse;

import com.eternalrealm.guild.house.forge.GuildPortalBlock;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The names over the gates of the Guild Gate Terrace (owner, 2026-11-15: 「どれが自分のギルドのハウスか分からない…
 * 名前つけるとか」), drawn in the world for this player only ({@link GuildGateClient}):
 * <ul>
 *     <li>every gate, seen from near, has its house's name hanging in front of it, over the opening;</li>
 *     <li>the gate of the player's guild's house has, instead, the guild's name in the colour of its standing over
 *     「▼ あなたのギルドの門 ▼」, seen through walls from anywhere in the capital and growing with the distance so it
 *     stays readable, and a slow golden beam of light rising from the floor in front of it.</li>
 * </ul>
 * The labels float a few blocks in front of the portal (wall gates are set two blocks deep into their wall), just above
 * the opening, whose height is read from the portal blocks once they are loaded.
 */
public final class GateLabelRenderer {
    private static final Logger LOG = LogUtils.getLogger();
    /** Other gates' names show within this distance (blocks), fading over the last {@link #NAME_FADE}. */
    private static final double NAME_RANGE = 24.0;
    private static final double NAME_FADE = 6.0;
    /** The guild's gate shows within this distance. */
    private static final double OWN_RANGE = 200.0;
    /** Labels float this far in front of the portal plane. */
    private static final double LABEL_FRONT = 3.2;
    /** The beam stands this far in front of the portal plane. */
    private static final double BEAM_FRONT = 2.5;
    private static final float BEAM_HEIGHT = 44.0F;
    private static final float NAME_SCALE = 0.024F;
    private static final float OWN_SCALE = 0.03F;
    /** Opening heights of the gates (blocks), by the feet block at the portal's centre. */
    private static final Map<Long, Integer> OPENINGS = new HashMap<>();
    private static Level openingsLevel;
    private static boolean disabled;

    private GateLabelRenderer() {
    }

    /** Translucent position-colour triangles, depth-tested, not written to depth. */
    private static final class Types extends RenderType {
        static final RenderType BEAM = RenderType.create("eternalrealm_guild_gate_beam",
                DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 1 << 12, false, true,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                      boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (disabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui
                || !GuildGateClient.here(minecraft.level) || GuildGateClient.gates().isEmpty()) {
            return;
        }
        try {
            draw(event, minecraft);
        } catch (RuntimeException e) {
            disabled = true;
            LOG.error("Guild gate labels disabled after a rendering error", e);
        }
    }

    private static void draw(RenderLevelStageEvent event, Minecraft minecraft) {
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;
        float time = minecraft.level.getGameTime() + event.getPartialTick();
        Optional<GuildGateClient.Own> own = GuildGateClient.own();
        // the beam first (translucent geometry), then the words over everything
        own.ifPresent(mine -> {
            double[] foot = mine.gate().inFront(BEAM_FRONT);
            double distance = Math.hypot(foot[0] - eye.x, foot[1] - eye.z);
            if (distance < OWN_RANGE) {
                VertexConsumer out = buffers.getBuffer(Types.BEAM);
                beam(out, pose.last().pose(), (float) (foot[0] - eye.x), (float) (mine.gate().y() - eye.y),
                        (float) (foot[1] - eye.z), time, (float) Mth.clamp((OWN_RANGE - distance) / 24.0, 0.0, 1.0));
                buffers.endBatch(Types.BEAM);
            }
        });
        int background = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        for (GuildGateClient.Gate gate : GuildGateClient.gates()) {
            boolean mine = own.isPresent() && own.get().gate().equals(gate);
            int opening = opening(minecraft.level, gate);
            double[] at = gate.inFront(LABEL_FRONT);
            double y = gate.y() + opening + 0.9;
            double distance = Math.sqrt(sq(at[0] - eye.x) + sq(y - eye.y) + sq(at[1] - eye.z));
            if (mine) {
                if (distance < OWN_RANGE) {
                    ownLabel(pose, camera, font, buffers, own.get(), at[0] - eye.x, y - eye.y, at[1] - eye.z, distance,
                            background, time);
                }
                continue;
            }
            if (distance < NAME_RANGE) {
                float fade = (float) Mth.clamp((NAME_RANGE - distance) / NAME_FADE, 0.0, 1.0);
                nameLabel(pose, camera, font, buffers, Component.translatable(gate.nameKey()), at[0] - eye.x,
                        y - 0.35 - eye.y, at[1] - eye.z, fade, background);
            }
        }
        buffers.endBatch();
    }

    /** A gate's name, as on a name tag: hidden behind blocks, fading out with the distance. */
    private static void nameLabel(PoseStack pose, Camera camera, Font font, MultiBufferSource buffers, Component name,
                                  double x, double y, double z, float fade, int background) {
        int alpha = Math.max(8, Math.round(255 * fade));
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(camera.rotation());
        pose.scale(-NAME_SCALE, -NAME_SCALE, NAME_SCALE);
        Matrix4f matrix = pose.last().pose();
        float left = -font.width(name) / 2.0F;
        int shade = (int) (((background >>> 24) & 0xFF) * fade) << 24;
        font.drawInBatch(name, left, 0.0F, (alpha << 24) | 0xF4E8C8, false, matrix, buffers, Font.DisplayMode.NORMAL,
                shade, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /**
     * The guild's gate: the guild's name and 「▼ あなたのギルドの門 ▼」, first faintly through everything (so it can be
     * found from afar), then fully where nothing is in front; larger with the distance so it stays readable.
     */
    private static void ownLabel(PoseStack pose, Camera camera, Font font, MultiBufferSource buffers,
                                 GuildGateClient.Own own, double x, double y, double z, double distance,
                                 int background, float time) {
        float grow = (float) Mth.clamp(distance / 12.0, 1.0, 6.0);
        float scale = OWN_SCALE * grow;
        float bob = 0.08F * Mth.sin(time * 0.08F);
        Component title = own.title();
        Component mark = Component.translatable("label.eternalrealm.guildgate.own").withStyle(ChatFormatting.GOLD);
        pose.pushPose();
        pose.translate(x, y + bob + (grow - 1.0F) * 0.25F, z);
        pose.mulPose(camera.rotation());
        pose.scale(-scale, -scale, scale);
        Matrix4f matrix = pose.last().pose();
        float titleLeft = -font.width(title) / 2.0F;
        float markLeft = -font.width(mark) / 2.0F;
        font.drawInBatch(title, titleLeft, -11.0F, 0x60FFFFFF, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH,
                background, LightTexture.FULL_BRIGHT);
        font.drawInBatch(mark, markLeft, 0.0F, 0x60FFFFFF, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH,
                background, LightTexture.FULL_BRIGHT);
        font.drawInBatch(title, titleLeft, -11.0F, 0xFFFFFFFF, false, matrix, buffers, Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        font.drawInBatch(mark, markLeft, 0.0F, 0xFFFFFFFF, false, matrix, buffers, Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** A slow golden beam: two crossed planes fading upwards and a ring of light on the floor. */
    private static void beam(VertexConsumer out, Matrix4f matrix, float x, float y, float z, float time, float fade) {
        float pulse = 0.5F + 0.5F * Mth.sin(time * 0.1F);
        float width = 0.42F;
        int low = (int) ((120 + 50 * pulse) * fade);
        for (int k = 0; k < 2; k++) {
            float dx = k == 0 ? width : 0.0F;
            float dz = k == 0 ? 0.0F : width;
            quad(out, matrix, x - dx, y, z - dz, x + dx, y, z + dz, x + dx, y + BEAM_HEIGHT, z + dz, x - dx,
                    y + BEAM_HEIGHT, z - dz, 255, 208, 80, low, 0);
            // a brighter core
            quad(out, matrix, x - dx * 0.35F, y, z - dz * 0.35F, x + dx * 0.35F, y, z + dz * 0.35F, x + dx * 0.35F,
                    y + BEAM_HEIGHT * 0.6F, z + dz * 0.35F, x - dx * 0.35F, y + BEAM_HEIGHT * 0.6F, z - dz * 0.35F,
                    255, 240, 170, (int) (low * 0.9F), 0);
        }
        float ring = 1.1F + 0.3F * pulse;
        int ringAlpha = (int) (170 * (1.0F - 0.4F * pulse) * fade);
        for (int s = 0; s < 20; s++) {
            double a0 = Math.PI * 2.0 * s / 20;
            double a1 = Math.PI * 2.0 * (s + 1) / 20;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float inner = ring - 0.14F;
            quad(out, matrix, x + c0 * inner, y + 0.04F, z + s0 * inner, x + c0 * ring, y + 0.04F, z + s0 * ring,
                    x + c1 * ring, y + 0.04F, z + s1 * ring, x + c1 * inner, y + 0.04F, z + s1 * inner, 255, 222, 110,
                    ringAlpha, ringAlpha);
        }
    }

    /** Two triangles; the first two corners take {@code alphaLow}, the last two {@code alphaHigh}. */
    private static void quad(VertexConsumer out, Matrix4f matrix, float x0, float y0, float z0, float x1, float y1,
                             float z1, float x2, float y2, float z2, float x3, float y3, float z3, int r, int g, int b,
                             int alphaLow, int alphaHigh) {
        out.vertex(matrix, x0, y0, z0).color(r, g, b, alphaLow).endVertex();
        out.vertex(matrix, x1, y1, z1).color(r, g, b, alphaLow).endVertex();
        out.vertex(matrix, x2, y2, z2).color(r, g, b, alphaHigh).endVertex();
        out.vertex(matrix, x0, y0, z0).color(r, g, b, alphaLow).endVertex();
        out.vertex(matrix, x2, y2, z2).color(r, g, b, alphaHigh).endVertex();
        out.vertex(matrix, x3, y3, z3).color(r, g, b, alphaHigh).endVertex();
    }

    /**
     * The height of the gate's opening (blocks of light above the feet block), read once its blocks are loaded; five
     * (the smallest gate's) until then.
     */
    private static int opening(Level level, GuildGateClient.Gate gate) {
        if (level != openingsLevel) {
            OPENINGS.clear();
            openingsLevel = level;
        }
        BlockPos base = new BlockPos(gate.x(), gate.y(), gate.z());
        Integer known = OPENINGS.get(base.asLong());
        if (known != null) {
            return known;
        }
        if (!level.isLoaded(base)) {
            return 5;
        }
        int height = 0;
        BlockPos.MutableBlockPos at = base.mutable();
        for (int dy = 0; dy < 24; dy++) {
            at.setY(base.getY() + dy);
            if (level.getBlockState(at).getBlock() instanceof GuildPortalBlock) {
                height = dy + 1;
            } else if (height > 0 || dy >= 2) {
                break;
            }
        }
        if (height == 0) {
            return 5;
        }
        OPENINGS.put(base.asLong(), height);
        return height;
    }

    private static double sq(double value) {
        return value * value;
    }
}
