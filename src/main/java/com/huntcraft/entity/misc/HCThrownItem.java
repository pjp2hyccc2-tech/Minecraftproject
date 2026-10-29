package com.huntcraft.entity.misc;

import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterPart;
import com.huntcraft.item.ThrowableToolItem;
import com.huntcraft.registry.HCEntities;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Thrown hunting tool: paintball, flash bomb, dung bomb, tranq bomb. */
public class HCThrownItem extends ThrowableItemProjectile {

    public HCThrownItem(EntityType<? extends HCThrownItem> type, Level level) {
        super(type, level);
    }

    public HCThrownItem(Level level, LivingEntity shooter) {
        super(HCEntities.THROWN_ITEM.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return HCItems.PAINTBALL.get();
    }

    private ThrowableToolItem.Kind kind() {
        return getItem().getItem() instanceof ThrowableToolItem t ? t.kind : ThrowableToolItem.Kind.PAINT;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity e = result.getEntity();
        HuntMonster m = e instanceof HuntMonster hm ? hm : e instanceof MonsterPart mp ? mp.getParent() : null;
        if (m == null || level().isClientSide) return;
        Player player = getOwner() instanceof Player p ? p : null;
        switch (kind()) {
            case PAINT -> {
                m.paint(20 * 60 * 8);
                if (player != null) player.displayClientMessage(Component.translatable("message.huntcraft.painted", m.getDisplayName()), true);
            }
            case DUNG -> {
                m.repel();
                if (player != null) player.displayClientMessage(Component.translatable("message.huntcraft.repelled"), true);
            }
            case TRANQ -> {
                if (m.onTranqHit(player) && player != null) {
                    player.displayClientMessage(Component.translatable("message.huntcraft.captured", m.getDisplayName()), false);
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide) return;
        ServerLevel sl = (ServerLevel) level();
        Vec3 pos = result.getLocation();
        switch (kind()) {
            case FLASH -> {
                sl.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 0.5, pos.z, 1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.5, pos.z, 40, 0.5, 0.5, 0.5, 0.25);
                sl.playSound(null, pos.x, pos.y, pos.z, HCSounds.FLASH.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
                for (HuntMonster m : sl.getEntitiesOfClass(HuntMonster.class, new AABB(pos, pos).inflate(14))) {
                    m.blind(m.profile().large ? 140 : 100);
                }
            }
            case PAINT -> {
                sl.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.3F, 0.75F), 1.6F), pos.x, pos.y, pos.z, 25, 0.3, 0.3, 0.3, 0.05);
                sl.playSound(null, pos.x, pos.y, pos.z, HCSounds.PAINT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            case DUNG -> sl.sendParticles(new DustParticleOptions(new Vector3f(0.45F, 0.35F, 0.15F), 2.0F), pos.x, pos.y, pos.z, 40, 0.6, 0.6, 0.6, 0.05);
            case TRANQ -> sl.sendParticles(new DustParticleOptions(new Vector3f(0.9F, 0.85F, 0.95F), 2.0F), pos.x, pos.y, pos.z, 40, 0.6, 0.6, 0.6, 0.05);
        }
        discard();
    }
}
