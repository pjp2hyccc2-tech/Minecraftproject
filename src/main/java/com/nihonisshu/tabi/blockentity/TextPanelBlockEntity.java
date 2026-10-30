package com.nihonisshu.tabi.blockentity;

import com.nihonisshu.tabi.registry.TabiBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the text of a sign. style: station / shop / dark / gate / info / white / neon
 * span: how many blocks wide the board is (text is centred over the span, starting at this block going right when viewed from the front)
 */
public class TextPanelBlockEntity extends BlockEntity {
    private final List<String> lines = new ArrayList<>();
    private String style = "white";
    private int span = 1;
    private int color = -1;
    private boolean glow = false;
    private boolean back = false;

    public TextPanelBlockEntity(BlockPos pos, BlockState state) {
        super(TabiBlockEntities.TEXT_PANEL.get(), pos, state);
    }

    public List<String> getLines() { return lines; }
    public String getStyle() { return style; }
    public int getSpan() { return span; }
    public int getColor() { return color; }
    public boolean isGlow() { return glow; }
    public boolean isBack() { return back; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeText(tag);
    }

    private void writeText(CompoundTag tag) {
        ListTag list = new ListTag();
        for (String s : lines) list.add(StringTag.valueOf(s));
        tag.put("lines", list);
        tag.putString("style", style);
        tag.putInt("span", span);
        tag.putInt("color", color);
        tag.putBoolean("glow", glow);
        tag.putBoolean("back", back);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        lines.clear();
        ListTag list = tag.getList("lines", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) lines.add(list.getString(i));
        style = tag.contains("style") ? tag.getString("style") : "white";
        span = Math.max(1, tag.getInt("span"));
        color = tag.contains("color") ? tag.getInt("color") : -1;
        glow = tag.getBoolean("glow");
        back = tag.getBoolean("back");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeText(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        return new AABB(p).inflate(span + 1);
    }
}
