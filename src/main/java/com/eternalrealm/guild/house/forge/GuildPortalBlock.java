package com.eternalrealm.guild.house.forge;

import com.eternalrealm.guild.house.GuildHouseCatalog;
import com.eternalrealm.guild.house.GuildHouseDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * The shimmering surface in a guild gate (ギルド門の光): a thin plane across the gate's opening ({@code axis} = the
 * direction it spans), coloured by its {@link GuildTheme} (the design's two portal colours; textures and model from
 * {@code tools/art/guild_portal.py}). It has no collision: walking into it hands the player to {@link GuildGates}
 * (into the guild's house, out of it, or a polite refusal). Unbreakable, never dropped.
 */
public class GuildPortalBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<GuildTheme> THEME = EnumProperty.create("theme", GuildTheme.class);
    private static final VoxelShape X_PLANE = Block.box(0.0, 0.0, 6.5, 16.0, 16.0, 9.5);
    private static final VoxelShape Z_PLANE = Block.box(6.5, 0.0, 0.0, 9.5, 16.0, 16.0);

    public GuildPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(THEME,
                GuildTheme.GREAT_TREE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, THEME);
    }

    /** Per theme (by ordinal): its two colours, read from the catalogue once. */
    private static volatile int[][] colorTable;

    /** The two colours (0xRRGGBB: base, accent) of a theme's portal. */
    public static int[] colors(GuildTheme theme) {
        int[][] table = colorTable;
        if (table == null) {
            GuildTheme[] themes = GuildTheme.values();
            table = new int[themes.length][];
            for (GuildTheme each : themes) {
                table[each.ordinal()] = GuildHouseCatalog.standard().design(each.id())
                        .map(design -> new int[]{design.baseColor(), design.accentColor()})
                        .orElse(new int[]{0xA070FF, 0xFFFFFF});
            }
            colorTable = table;
        }
        return table[theme.ordinal()];
    }

    public static GuildHouseDesign design(BlockState state) {
        return GuildHouseCatalog.standard().design(state.getValue(THEME).id()).orElseThrow();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? X_PLANE : Z_PLANE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && !player.isSpectator()) {
            GuildGates.onPortal(player, pos, state);
        }
    }

    /** Motes of the portal's colours rising and drifting off its surface, and now and then a soft chime. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int[] colors = colors(state.getValue(THEME));
        boolean alongX = state.getValue(AXIS) == Direction.Axis.X;
        for (int i = 0; i < 2; i++) {
            double across = random.nextDouble();
            double off = (random.nextDouble() - 0.5) * 0.9;
            double x = pos.getX() + (alongX ? across : 0.5 + off);
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + (alongX ? 0.5 + off : across);
            double drift = (random.nextDouble() - 0.5) * 0.02;
            if (random.nextFloat() < 0.65F) {
                level.addParticle(new DustParticleOptions(rgb(colors[0]), 0.7F + random.nextFloat() * 0.5F), x, y, z,
                        alongX ? 0.0 : drift, 0.02, alongX ? drift : 0.0);
            } else {
                level.addParticle(new DustColorTransitionOptions(rgb(colors[1]), rgb(colors[0]),
                        0.5F + random.nextFloat() * 0.4F), x, y, z, 0.0, 0.03, 0.0);
            }
        }
        if (random.nextInt(160) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.35F, 0.7F + random.nextFloat() * 0.6F, false);
        }
    }

    private static Vector3f rgb(int color) {
        return new Vector3f(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
    }
}
