package com.huntcraft.monster.small;

import com.huntcraft.core.Element;
import com.huntcraft.entity.monster.Hitzone;
import com.huntcraft.entity.monster.MonsterProfile;
import com.huntcraft.entity.monster.PartDef;
import com.huntcraft.entity.monster.RewardTable;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import com.huntcraft.entity.monster.attack.TimelineAttack;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ガンセキガニ - crab that wears a boulder as its shell. Pinches and slams with its claws; when hit too often it
 * withdraws under the boulder (immobile, very low hitzones). The boulder can be broken, exposing the soft body.
 */
public class GansekiganiEntity extends SmallHuntMonster {
    public static final int POSE_SHELL = 1;

    static final Hitzone HZ_FACE = Hitzone.of(80, 75, 70, 20, 10, 35, 20, 10);
    static final Hitzone HZ_BODY = Hitzone.of(55, 60, 50, 15, 10, 30, 15, 5);
    static final Hitzone HZ_CLAW = Hitzone.of(35, 45, 30, 10, 5, 25, 10, 5);
    /** The boulder: very hard (also used for every hit while withdrawn). */
    static final Hitzone HZ_ROCK = Hitzone.of(15, 35, 10, 5, 5, 15, 5, 5);

    private static PartDef rock() {
        PartDef p = new PartDef("shell", 0, 0.55F, -0.15F, 1.0F, 0.65F, HZ_ROCK).breakable(14, "boulder");
        p.brokenSoften = 45;
        return p;
    }

    public static final MonsterProfile PROFILE = MonsterProfile.builder("gansekigani", false)
            .health(34, 52).highRank(1.35F, 0.85F)
            .scale(0.9F, 1.12F)
            .weakness(Element.THUNDER, 3).weakness(Element.ICE, 1).weakness(Element.DRAGON, 1)
            .part(new PartDef("face", 0, 0.25F, 0.6F, 0.5F, 0.4F, HZ_FACE).ko())
            .part(PartDef.body(HZ_BODY))
            .part(rock())
            .part(new PartDef("claw_l", -0.55F, 0.15F, 0.75F, 0.4F, 0.4F, HZ_CLAW))
            .part(new PartDef("claw_r", 0.55F, 0.15F, 0.75F, 0.4F, 0.4F, HZ_CLAW))
            .rageThreshold(99F).flinchThreshold(0.18F)
            .carves(new RewardTable()
                    .low(HCItems.GANSEKI_SHELL, 50).low(HCItems.GANSEKI_CLAW, 50)
                    .high(HCItems.GANSEKI_SHELL, 50, 1, 2).high(HCItems.GANSEKI_CLAW, 50))
            .carveCount(1)
            .questReward(350, 8)
            .build();

    public static final String[] ANIMS = {"flinch", "pinch", "slam", "emerge", "wave", "bubble"};

    private int shellTicks;
    private int shellCooldown;
    private int hitsTaken;

    public GansekiganiEntity(EntityType<? extends GansekiganiEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public MonsterProfile profile() {
        return PROFILE;
    }

    @Override
    public String[] animationNames() {
        return ANIMS;
    }

    @Override
    protected List<MonsterAttack> createAttacks() {
        MonsterAttack pinch = TimelineAttack.builder("pinch", 20).range(0, 2.1F).angle(50).weight(14).cooldown(16)
                .track(0, 5, 14).move(5, 8, 0.15, 0)
                .hit(8, 0, 0.4, 0.95, 0.8F, 4, Element.NONE, 0.6F)
                .sound(8, HCSounds.BITE, 1.0F, 1.7F)
                .build();
        MonsterAttack slam = TimelineAttack.builder("slam", 36).range(0, 2.6F).angle(55).weight(9).cooldown(50)
                .track(0, 12, 8)
                .hit(17, 0, 0.2, 1.1, 1.2F, 6, Element.NONE, 1.1F)
                .particles(17, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), 0, 0.1, 1.1, 14, 0.4)
                .sound(17, HCSounds.STOMP, 1.0F, 1.5F)
                .build();
        return List.of(pinch, slam);
    }

    // ------------------------------------------------------------------ shell defence
    public boolean inShell() {
        return getSpecialPose() == POSE_SHELL;
    }

    @Override
    public boolean isLocked() {
        return super.isLocked() || inShell();
    }

    @Override
    @Nullable
    protected String specialStatePose() {
        return inShell() ? "shell" : null;
    }

    @Override
    public String getCurrentHitPart() {
        // withdrawn: every blow lands on the boulder
        if (inShell() && !isPartBroken("shell")) return "shell";
        return super.getCurrentHitPart();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (inShell() && !isPartBroken("shell")) amount *= 0.35F;
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide && !isDeadOrDying() && !inShell() && shellCooldown <= 0 && !isPartBroken("shell")
                && source.getEntity() != null && !super.isLocked()) {
            hitsTaken++;
            if (hitsTaken >= 3 || (getHealth() < getMaxHealth() * 0.5F && getRandom().nextFloat() < 0.4F)) enterShell();
        }
        return r;
    }

    private void enterShell() {
        stopAction();
        idleActionTicks = 0;
        setSpecialPose(POSE_SHELL);
        shellTicks = 70 + getRandom().nextInt(40);
        hitsTaken = 0;
        this.getNavigation().stop();
        playSound(SoundEvents.SHULKER_CLOSE, 1.0F, 0.7F);
    }

    private void leaveShell(boolean counter) {
        setSpecialPose(0);
        shellCooldown = 160;
        playSound(SoundEvents.SHULKER_OPEN, 1.0F, 0.7F);
        if (counter) playAction("emerge", 12);
    }

    @Override
    protected void smallTick() {
        if (shellCooldown > 0) shellCooldown--;
        if (inShell()) {
            if (--shellTicks <= 0 || isPartBroken("shell")) leaveShell(true);
            else if (this.tickCount % 20 == 0) burstParticles(ParticleTypes.BUBBLE_POP, position().add(0, 0.5, 0), 3, 0.3);
        }
    }

    @Override
    protected void onPartBroken(PartDef part, @Nullable Player player) {
        if (!"shell".equals(part.name)) return;
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()),
                    getX(), getY() + 0.8, getZ(), 40, 0.5, 0.4, 0.5, 0.1);
            Vec3 p = position().add(0, 1.0, 0);
            sl.addFreshEntity(new ItemEntity(sl, p.x, p.y, p.z, new ItemStack(HCItems.GANSEKI_SHELL.get())));
        }
        playSound(SoundEvents.STONE_BREAK, 1.5F, 0.7F);
        if (inShell()) leaveShell(false);
        playAction("flinch", 20);
    }

    @Override
    public int getAttackGap() {
        return isTired() ? 36 : 24;
    }

    @Override
    public float getTurnSpeed() {
        return 14.0F;
    }

    @Override
    protected double runThreshold() {
        return 0.12;
    }

    @Override
    protected IdleAction[] idleActions() {
        return new IdleAction[]{new IdleAction("wave", 30, 3), new IdleAction("bubble", 40, 2)};
    }

    @Override
    protected void onIdleAction(String anim) {
        if ("bubble".equals(anim)) burstParticles(ParticleTypes.BUBBLE_POP, localPos(0, 0.35, 0.55), 6, 0.1);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SHULKER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return inShell() ? SoundEvents.SHULKER_HURT_CLOSED : SoundEvents.SHULKER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SHULKER_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HCShell", shellTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        shellTicks = tag.getInt("HCShell");
    }
}
