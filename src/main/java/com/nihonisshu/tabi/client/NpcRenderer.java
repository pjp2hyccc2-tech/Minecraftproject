package com.nihonisshu.tabi.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nihonisshu.tabi.TabiMod;
import com.nihonisshu.tabi.entity.TabiNpc;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Player-shaped NPCs with their own skins, plus the little speech bubbles above their heads. */
public class NpcRenderer extends HumanoidMobRenderer<TabiNpc, PlayerModel<TabiNpc>> {
    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    public NpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new NpcModel(ctx), 0.5f);
    }

    static final class NpcModel extends PlayerModel<TabiNpc> {
        NpcModel(EntityRendererProvider.Context ctx) {
            super(ctx.bakeLayer(ModelLayers.PLAYER), false);
        }

        @Override
        public void setupAnim(TabiNpc e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            this.riding = e.getMode() == TabiNpc.SIT;
            super.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(TabiNpc e) {
        return SKINS.computeIfAbsent(e.getSkin(), s -> new ResourceLocation(TabiMod.MODID, "textures/entity/npc/" + s + ".png"));
    }

    @Override
    public void render(TabiNpc e, float yaw, float partialTicks, PoseStack ps, MultiBufferSource buffers, int light) {
        boolean sit = e.getMode() == TabiNpc.SIT;
        ps.pushPose();
        if (sit) ps.translate(0, -0.55, 0);
        super.render(e, yaw, partialTicks, ps, buffers, light);
        ps.popPose();
        List<String> bubble = Bubbles.textFor(e);
        if (bubble != null && !bubble.isEmpty()) {
            renderBubble(e, bubble, ps, buffers, light, sit ? -0.45 : 0.0);
        }
    }

    private void renderBubble(TabiNpc e, List<String> lines, PoseStack ps, MultiBufferSource buffers, int light, double dy) {
        Font font = getFont();
        float alpha = Bubbles.alphaFor(e);
        ps.pushPose();
        ps.translate(0, e.getBbHeight() + 0.55 + dy + 0.12 * (lines.size() - 1), 0);
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        ps.scale(-0.022f, -0.022f, 0.022f);
        Matrix4f m = ps.last().pose();
        int bg = ((int) (alpha * 0xE0) << 24) | 0xFFFDF6;
        int fg = ((int) (Math.max(0.1f, alpha) * 0xFF) << 24) | 0x2B2B2B;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            float x = -font.width(s) / 2f;
            font.drawInBatch(s, x, i * 10f, fg, false, m, buffers, Font.DisplayMode.NORMAL, bg, light);
        }
        ps.popPose();
    }
}
