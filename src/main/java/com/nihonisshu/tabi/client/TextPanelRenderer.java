package com.nihonisshu.tabi.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nihonisshu.tabi.block.FacingBlock;
import com.nihonisshu.tabi.block.TextPanelBlock;
import com.nihonisshu.tabi.blockentity.TextPanelBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.List;

/** Draws Japanese text on sign boards (station name boards, shop signs, Chinese gate plaques...). */
public class TextPanelRenderer implements BlockEntityRenderer<TextPanelBlockEntity> {
    private final Font font;

    public TextPanelRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(TextPanelBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        List<String> lines = be.getLines();
        if (lines.isEmpty()) return;
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof TextPanelBlock panel)) return;
        Direction facing = state.getValue(FacingBlock.FACING);
        drawSide(be, panel, facing, ps, buffers, light, false);
        if (be.isBack()) drawSide(be, panel, facing, ps, buffers, light, true);
    }

    private void drawSide(TextPanelBlockEntity be, TextPanelBlock panel, Direction facing, PoseStack ps, MultiBufferSource buffers, int light, boolean back) {
        List<String> lines = be.getLines();
        int span = be.getSpan();
        String style = be.getStyle();
        int main, sub;
        switch (style) {
            case "station" -> { main = 0xFF1A1A1A; sub = 0xFF3A3A3A; }
            case "shop" -> { main = 0xFFF4E9CF; sub = 0xFFE0D2B0; }
            case "dark", "info" -> { main = 0xFFFFFFFF; sub = 0xFFDDE6F0; }
            case "gate" -> { main = 0xFFFFD84A; sub = 0xFFFFE68A; }
            case "neon" -> { main = 0xFFFF6FB0; sub = 0xFF9FE8FF; }
            case "stop" -> { main = 0xFFFFFFFF; sub = 0xFFFFFFFF; }
            case "led" -> { main = 0xFFFF9A2A; sub = 0xFF8CFFA8; }
            case "lcd" -> { main = 0xFFFF9A2A; sub = 0xFF7CFC9A; }
            default -> { main = 0xFF202020; sub = 0xFF404040; }
        }
        if (be.getColor() != -1) main = 0xFF000000 | be.getColor();
        int useLight = be.isGlow() || style.equals("neon") || style.equals("lcd") || style.equals("led") ? LightTexture.FULL_BRIGHT : light;

        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        float rot = 180f - facing.toYRot() + (back ? 180f : 0f);
        ps.mulPose(Axis.YP.rotationDegrees(rot));
        // (180 - yRot) frame: the front face sits at local z = -0.5 + frontDepth/16; the back face (frame turned
        // another 180 degrees) sits at local z = -0.5 + (16 - backDepth)/16. Text floats 0.004 in front of the face.
        double z = back ? -0.5 + (16.0 - panel.backDepth()) / 16.0 - 0.004 : -0.5 + panel.frontDepth() / 16.0 - 0.004;
        ps.translate(back ? (span - 1) / 2.0 : -(span - 1) / 2.0, panel.textCenterY() - 0.5, z);

        float width = span * 0.86f;
        float height = panel.textHeight();
        String first = lines.get(0);
        int fw = Math.max(1, font.width(first));
        float k0 = Math.min((height * (lines.size() > 1 ? 0.62f : 0.9f)) / 9f, width / fw);
        float k1 = Math.min(k0 * 0.42f, height * 0.18f / 9f);
        float total = 9 * k0 + (lines.size() - 1) * 10 * k1;
        float y = total / 2f;
        drawLine(first, k0, y, main, ps, buffers, useLight);
        y -= 9 * k0 + 1 * k1;
        for (int i = 1; i < lines.size(); i++) {
            String s = lines.get(i);
            float kk = Math.min(k1, width / Math.max(1, font.width(s)));
            drawLine(s, kk, y, sub, ps, buffers, useLight);
            y -= 10 * k1;
        }
        ps.popPose();
    }

    private void drawLine(String s, float k, float topY, int color, PoseStack ps, MultiBufferSource buffers, int light) {
        ps.pushPose();
        ps.translate(0, topY, 0);
        ps.scale(-k, -k, k);
        Matrix4f m = ps.last().pose();
        font.drawInBatch(s, -font.width(s) / 2f, 0, color, false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(TextPanelBlockEntity be) {
        return be.getSpan() > 1;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
