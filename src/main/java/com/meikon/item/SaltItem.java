package com.meikon.item;

import com.meikon.game.GameManager;
import com.meikon.registry.ModBlocks;
import com.meikon.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 清め塩: throw it at her to drive her away, or heap it on the floor as a ward she will not cross. */
public class SaltItem extends LoreItem {
    public SaltItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP) {
            BlockPos pos = hit.getBlockPos().above();
            if (level.getBlockState(pos).canBeReplaced()) {
                if (!level.isClientSide) {
                    level.setBlock(pos, ModBlocks.SALT_PILE.get().defaultBlockState(), 3);
                    level.playSound(null, pos, ModSounds.GAME_SALT.get(), SoundSource.PLAYERS, 0.8f, 1.1f);
                    if (!player.isCreative()) stack.shrink(1);
                    player.getCooldowns().addCooldown(this, 20);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            Vec3 look = sp.getLookAngle();
            ServerLevel sl = sp.serverLevel();
            sl.playSound(null, sp.getX(), sp.getY() + 1, sp.getZ(), ModSounds.GAME_SALT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            for (int i = 1; i <= 5; i++) {
                Vec3 p = sp.getEyePosition().add(look.scale(i * 0.8));
                sl.sendParticles(ParticleTypes.WHITE_ASH, p.x, p.y, p.z, 14, 0.25, 0.25, 0.25, 0.02);
            }
            GameManager.saltThrown(sp);
            if (!player.isCreative()) stack.shrink(1);
            player.getCooldowns().addCooldown(this, 30);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
