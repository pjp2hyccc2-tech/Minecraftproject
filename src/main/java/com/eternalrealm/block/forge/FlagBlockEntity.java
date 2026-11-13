package com.eternalrealm.block.forge;

import com.eternalrealm.world.town.FlagCloth;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Locale;

/**
 * A flag's cloth: {@code Width} blocks long (out from the pole towards the block's {@code facing}), {@code Height}
 * blocks tall at the pole, one of the {@link Design}s, and a {@code Wind} factor (1 = the town's wind). Saved as
 * {@code {Width:3.0f,Height:2.0f,Design:"kingdom",Wind:1.0f}}; the capital build writes exactly this.
 */
public class FlagBlockEntity extends SyncedBlockEntity {
    /** The flag designs (textures {@code textures/entity/flag/<id>.png}). */
    public enum Design {
        KINGDOM(FlagCloth.Shape.RECTANGLE),
        GUILD(FlagCloth.Shape.RECTANGLE),
        PENNANT(FlagCloth.Shape.PENNANT),
        CATHEDRAL(FlagCloth.Shape.RECTANGLE),
        MARKET(FlagCloth.Shape.RECTANGLE),
        HARBOUR(FlagCloth.Shape.RECTANGLE);

        private final FlagCloth.Shape shape;

        Design(FlagCloth.Shape shape) {
            this.shape = shape;
        }

        public FlagCloth.Shape shape() {
            return shape;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Design of(String id) {
            for (Design design : values()) {
                if (design.id().equals(id)) {
                    return design;
                }
            }
            return KINGDOM;
        }
    }

    private float width = 3.0F;
    private float height = 2.0F;
    private float wind = 1.0F;
    private Design design = Design.KINGDOM;

    /** Client-side animation state (wave phase accumulated per frame). */
    public double clientPhase;
    public double clientLastSeconds = Double.NaN;
    /** The client's still cloth (waving switched off): the shape of {@link #clientStillCols} x {@link #clientStillRows}. */
    public double[] clientStillGrid;
    public float[] clientStillNormals;
    public float[] clientStillShade;
    public int clientStillCols = -1;
    public int clientStillRows = -1;

    public FlagBlockEntity(BlockPos pos, BlockState state) {
        super(ERBlocks.FLAG_ENTITY.get(), pos, state);
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float wind() {
        return wind;
    }

    public Design design() {
        return design;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        width = clampedFloat(tag, "Width", width, 0.5F, 12.0F);
        height = clampedFloat(tag, "Height", height, 0.25F, 8.0F);
        wind = clampedFloat(tag, "Wind", wind, 0.0F, 3.0F);
        if (tag.contains("Design")) {
            design = Design.of(tag.getString("Design"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("Width", width);
        tag.putFloat("Height", height);
        tag.putFloat("Wind", wind);
        tag.putString("Design", design.id());
    }

    @Override
    public AABB getRenderBoundingBox() {
        double reach = width + 1.5;
        return new AABB(worldPosition).inflate(reach, height + 1.5, reach);
    }
}
