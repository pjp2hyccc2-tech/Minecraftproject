package com.eternalrealm.block.forge;

import com.eternalrealm.world.town.BuntingLayout;
import com.eternalrealm.world.town.BuntingLine;
import com.eternalrealm.world.town.BuntingStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A string of bunting from the anchor's centre to the centre of the block {@code (Tx, Ty, Tz)} away (each at most 32),
 * sagging {@code Sag} blocks at mid-span, carrying {@code Style} pieces coloured from {@code Colors} (dye ids 0-15) in
 * turn and swaying with {@code Wind} times the town wind:
 * {@code {Tx:12,Ty:0,Tz:0,Sag:1.2f,Style:"pennant",Colors:[I;14,4,11],Wind:1.0f}} (the capital build writes exactly
 * this). No target ({@code 0,0,0}) means a bare hook, e.g. the far end of another anchor's string.
 */
public class BuntingBlockEntity extends SyncedBlockEntity {
    /** Light samples kept along the string by the client. */
    public static final int LIGHT_SAMPLES = 9;
    private static final int MAX_COLORS = 32;

    private int tx;
    private int ty;
    private int tz;
    private float sag = 1.2F;
    private float wind = 1.0F;
    private BuntingStyle style = BuntingStyle.PENNANT;
    private int[] colors = new int[0];

    @Nullable
    private BuntingLine line;
    @Nullable
    private BuntingLayout layout;
    private AABB renderBox;

    /** Client animation state: accumulated flutter phase and the time it was last advanced. */
    public double clientPhase;
    public double clientLastSeconds = Double.NaN;
    /** Client: packed light along the string, refreshed now and then. */
    public final int[] clientLight = new int[LIGHT_SAMPLES];
    public long clientLightAt = Long.MIN_VALUE;
    /** Client: what the two hooks are fixed to (a direction's 3d value, or -1 for nothing), refreshed now and then. */
    public int clientAnchorMount = -1;
    public int clientFarMount = -1;
    public long clientMountAt = Long.MIN_VALUE;

    public BuntingBlockEntity(BlockPos pos, BlockState state) {
        super(TownGimmickBlocks.BUNTING_ENTITY.get(), pos, state);
        this.renderBox = new AABB(pos).inflate(0.25);
    }

    // ------------------------------------------------------------------------------------------ data

    public int targetX() {
        return tx;
    }

    public int targetY() {
        return ty;
    }

    public int targetZ() {
        return tz;
    }

    public float sag() {
        return sag;
    }

    public float wind() {
        return wind;
    }

    public BuntingStyle style() {
        return style;
    }

    /** The dye ids the pieces take in turn (the style's defaults if the data names none). */
    public int[] colors() {
        return colors.length == 0 ? style.defaultColors() : colors.clone();
    }

    /** Dye id of the piece with colour slot {@code slot} (the colours go round in turn). */
    public int color(int slot) {
        return colors.length == 0 ? style.defaultColor(slot) : colors[Math.floorMod(slot, colors.length)];
    }

    /** The cord (null for a bare hook). */
    @Nullable
    public BuntingLine line() {
        return line;
    }

    /** The pieces along the cord (null for a bare hook). */
    @Nullable
    public BuntingLayout layout() {
        return layout;
    }

    /** Whether this anchor's string ends at {@code pos}. */
    public boolean reaches(BlockPos pos) {
        return line != null && worldPosition.offset(tx, ty, tz).equals(pos);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tx = clampedInt(tag, "Tx", tx);
        ty = clampedInt(tag, "Ty", ty);
        tz = clampedInt(tag, "Tz", tz);
        sag = clampedFloat(tag, "Sag", sag, 0.0F, (float) BuntingLine.MAX_SAG);
        wind = clampedFloat(tag, "Wind", wind, 0.0F, 3.0F);
        if (tag.contains("Style", Tag.TAG_STRING)) {
            style = BuntingStyle.of(tag.getString("Style"));
        }
        if (tag.contains("Colors", Tag.TAG_INT_ARRAY)) {
            colors = BuntingStyle.cleanColors(tag.getIntArray("Colors"), MAX_COLORS);
        } else if (tag.contains("Colors", Tag.TAG_BYTE_ARRAY)) {
            byte[] bytes = tag.getByteArray("Colors");
            int[] values = new int[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                values[i] = bytes[i];
            }
            colors = BuntingStyle.cleanColors(values, MAX_COLORS);
        } else if (tag.contains("Colors", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Colors", Tag.TAG_INT);
            int[] values = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                values[i] = list.getInt(i);
            }
            colors = BuntingStyle.cleanColors(values, MAX_COLORS);
        }
        rebuild();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Tx", tx);
        tag.putInt("Ty", ty);
        tag.putInt("Tz", tz);
        tag.putFloat("Sag", sag);
        tag.putString("Style", style.id());
        tag.putIntArray("Colors", colors);
        tag.putFloat("Wind", wind);
    }

    private static int clampedInt(CompoundTag tag, String key, int fallback) {
        if (!tag.contains(key, Tag.TAG_ANY_NUMERIC)) {
            return fallback;
        }
        return Math.max(-BuntingLine.MAX_REACH, Math.min(BuntingLine.MAX_REACH, tag.getInt(key)));
    }

    private void rebuild() {
        clientLightAt = Long.MIN_VALUE;
        clientMountAt = Long.MIN_VALUE;
        if (tx == 0 && ty == 0 && tz == 0) {
            line = null;
            layout = null;
            renderBox = new AABB(worldPosition).inflate(0.25);
            return;
        }
        line = BuntingLine.of(tx, ty, tz, sag);
        layout = BuntingLayout.of(style, line.length(), worldPosition.asLong());
        double x0 = worldPosition.getX() + 0.5;
        double y0 = worldPosition.getY() + 0.5;
        double z0 = worldPosition.getZ() + 0.5;
        double x1 = x0 + tx;
        double y1 = y0 + ty;
        double z1 = z0 + tz;
        double side = line.sag() * 0.8 + style.hang() + 0.6;
        double drop = line.sag() + style.hang() + 0.6;
        renderBox = new AABB(Math.min(x0, x1) - side, Math.min(y0, y1) - drop, Math.min(z0, z1) - side,
                Math.max(x0, x1) + side, Math.max(y0, y1) + 0.6, Math.max(z0, z1) + side);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return renderBox;
    }

    // ------------------------------------------------------------------------------------------ changes (server)

    /** Sets the whole string and sends it to the players nearby. */
    public void configure(int x, int y, int z, float newSag, BuntingStyle newStyle, int[] newColors, float newWind) {
        tx = Math.max(-BuntingLine.MAX_REACH, Math.min(BuntingLine.MAX_REACH, x));
        ty = Math.max(-BuntingLine.MAX_REACH, Math.min(BuntingLine.MAX_REACH, y));
        tz = Math.max(-BuntingLine.MAX_REACH, Math.min(BuntingLine.MAX_REACH, z));
        sag = Math.max(0.0F, Math.min((float) BuntingLine.MAX_SAG, newSag));
        style = newStyle;
        colors = BuntingStyle.cleanColors(newColors, MAX_COLORS);
        wind = Math.max(0.0F, Math.min(3.0F, newWind));
        rebuild();
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** What it carries next (an operator using it in creative mode). */
    public void nextStyle() {
        BuntingStyle[] all = BuntingStyle.values();
        BuntingStyle next = all[(style.ordinal() + 1) % all.length];
        configure(tx, ty, tz, sag, next, new int[0], wind);
    }

    /**
     * Strings itself along {@code facing} (placed by hand): to the next anchor in that direction, or to the air in front
     * of the next wall, up to 32 blocks. If that anchor's own string already ends here it stays a bare hook.
     */
    void stringAcross() {
        if (level == null) {
            return;
        }
        Direction facing = getBlockState().getValue(BuntingBlock.FACING);
        for (int k = 1; k <= BuntingLine.MAX_REACH; k++) {
            BlockPos at = worldPosition.relative(facing, k);
            if (!level.isLoaded(at)) {
                return;
            }
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof BuntingBlock) {
                if (!(level.getBlockEntity(at) instanceof BuntingBlockEntity other && other.reaches(worldPosition))) {
                    string(facing, k);
                }
                return;
            }
            if (!state.getCollisionShape(level, at).isEmpty()) {
                if (k - 1 >= 2) {
                    string(facing, k - 1);
                }
                return;
            }
        }
    }

    private void string(Direction facing, int distance) {
        configure(facing.getStepX() * distance, 0, facing.getStepZ() * distance, 0.25F + 0.06F * distance, style,
                colors, wind);
    }
}
