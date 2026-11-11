package com.eternalrealm.client.guildhouse;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.guild.house.forge.GuildBoardBlock;
import com.eternalrealm.guild.house.forge.GuildBoardBlockEntity;
import com.eternalrealm.guild.house.forge.GuildHouseUi;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Writes the guild's board on the board in the house (the block model is a framed cork board three blocks wide and two
 * high, the block at its bottom centre): the guild's name, the message of the day on a parchment sheet and the newest
 * notes on paper slips. The text is the viewer's own guild's (only its members are ever inside); drawn within 12
 * blocks.
 */
public class GuildBoardRenderer implements BlockEntityRenderer<GuildBoardBlockEntity> {
    /** Font pixels per block. */
    private static final float SCALE = 1.0F / 96.0F;
    private static final int INK = 0xFF2E2418;
    private static final int PAPER = 0xE8EFE3C2;
    private static final int SLIP = 0xE0F6EED8;
    private static final int TITLE = 0xFF6A3A10;
    private final Font font;

    public GuildBoardRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(GuildBoardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (camera.distanceToSqr(Vec3.atCenterOf(board.getBlockPos())) > 12.0 * 12.0) {
            return;
        }
        JsonObject data = ClientUiState.get(GuildHouseUi.BOARD).orElse(null);
        if (data == null || !data.has("guild")) {
            return;
        }
        Direction facing = board.getBlockState().getValue(GuildBoardBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // the board's front: 2/16 off the wall on the block's back side; it spans x -1.5..1.5, y 0..2 (block units)
        pose.translate(0.0, 2.0, -0.5 + 2.0 / 16.0 + 0.004);
        pose.scale(SCALE, -SCALE, SCALE);
        Matrix4f matrix = pose.last().pose();
        int width = 3 * 96 - 2 * 14;
        int x0 = -width / 2;
        int y = 12;
        Component title = Component.literal(string(data, "guild") + " [" + string(data, "tag") + "]");
        draw(matrix, buffers, title, -font.width(title) / 2.0F, y, TITLE, 0, packedLight);
        y += 13;
        String motd = string(data, "motd");
        if (!motd.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.literal(motd), width - 8);
            for (int i = 0; i < lines.size() && i < 4; i++) {
                draw(matrix, buffers, lines.get(i), x0 + 4, y, INK, PAPER, packedLight);
                y += 10;
            }
            y += 4;
        }
        JsonArray notes = data.has("notes") && data.get("notes").isJsonArray() ? data.getAsJsonArray("notes")
                : new JsonArray();
        int shown = 0;
        for (int i = notes.size() - 1; i >= 0 && y < 2 * 96 - 22 && shown < 8; i--, shown++) {
            JsonElement element = notes.get(i);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject note = element.getAsJsonObject();
            Component line = Component.literal(string(note, "name") + ": " + string(note, "text"));
            List<FormattedCharSequence> wrapped = font.split(line, width / 2 - 8);
            int column = shown % 2;
            int cx = x0 + 4 + column * (width / 2);
            draw(matrix, buffers, wrapped.get(0), cx, y, INK, SLIP, packedLight);
            if (column == 1) {
                y += 12;
            }
        }
        pose.popPose();
    }

    private void draw(Matrix4f matrix, MultiBufferSource buffers, Component text, float x, float y, int color,
                      int background, int light) {
        font.drawInBatch(text, x, y, color, false, matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, background, light);
    }

    private void draw(Matrix4f matrix, MultiBufferSource buffers, FormattedCharSequence text, float x, float y,
                      int color, int background, int light) {
        font.drawInBatch(text, x, y, color, false, matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, background, light);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    @Override
    public boolean shouldRenderOffScreen(GuildBoardBlockEntity board) {
        return true;
    }
}
