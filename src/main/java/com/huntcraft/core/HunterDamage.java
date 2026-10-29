package com.huntcraft.core;

import com.huntcraft.HuntCraft;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.Hitzone;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterPart;
import com.huntcraft.entity.monster.PartDef;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.registry.HCEffects;
import com.huntcraft.registry.HCSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The MH damage formula applied to every hit made with an {@link IHunterWeapon}:
 * <pre>
 * raw  = attack(+skills) * motionValue * sharpnessRaw * crit * hitzoneRaw/100
 * elem = elementValue/100 * sharpnessElem * hitzoneElem/100
 * total = (raw + elem) * monsterDefenseRate
 * </pre>
 * Left clicks use the weapon's left-click motion value; special actions use {@link #hit} / {@link #hitArea}
 * with an explicit motion value.
 */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HunterDamage {

    /** Per-hit options for special attacks. */
    public static final class Context {
        public float motionValue = -1;
        @Nullable
        public DamageKind kind;
        /** Weapon snapshot (projectiles carry the bow stack). Defaults to main hand. */
        @Nullable
        public ItemStack weapon;
        public boolean useSharpness = true;
        public int sharpnessCost = 1;
        public boolean canBounce = true;
        public float elementMult = 1.0F;
        public float statusMult = 1.0F;
        /** Extra KO buildup added on blunt hits (hammer charge finishers...). */
        public float extraKO = 0;
        /** Force a part (otherwise chosen by the hitbox that was struck). */
        @Nullable
        public String forcedPart;
    }

    private static final ThreadLocal<Context> CTX = new ThreadLocal<>();

    private HunterDamage() {
    }

    // ================================================================== public helpers for weapons
    /** Hits one target with a special attack. Returns true if damage was applied. */
    public static boolean hit(Player player, Entity target, float motionValue, @Nullable Consumer<Context> config) {
        Context ctx = new Context();
        ctx.motionValue = motionValue;
        if (config != null) config.accept(ctx);
        CTX.set(ctx);
        try {
            if (target instanceof LivingEntity le) le.invulnerableTime = 0;
            if (target instanceof MonsterPart mp) mp.getParent().invulnerableTime = 0;
            return target.hurt(player.damageSources().playerAttack(player), 1.0F);
        } finally {
            CTX.remove();
        }
    }

    /**
     * Hits every valid entity within a sphere. For monsters the part whose hitbox is closest to the center is
     * struck. Returns the number of entities hit.
     */
    public static int hitArea(Player player, Vec3 center, double radius, float motionValue, @Nullable Consumer<Context> config) {
        AABB box = new AABB(center, center).inflate(radius);
        List<Entity> done = new ArrayList<>();
        int hits = 0;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !(e instanceof Player))) {
            if (done.contains(e)) continue;
            done.add(e);
            Entity struck = e;
            if (e instanceof HuntMonster m) {
                double best = HuntMonster.boxDistSqr(m.getBoundingBox(), center);
                for (MonsterPart part : m.getMonsterParts()) {
                    double d = HuntMonster.boxDistSqr(part.getBoundingBox(), center);
                    if (d < best) {
                        best = d;
                        struck = part;
                    }
                }
                if (best > radius * radius) continue;
            } else if (HuntMonster.boxDistSqr(e.getBoundingBox(), center) > radius * radius) {
                continue;
            }
            if (hit(player, struck, motionValue, config)) hits++;
        }
        return hits;
    }

    /** Hits entities in front of the player within range and a horizontal cone (full angle, degrees). */
    public static int hitCone(Player player, double range, float coneDeg, float motionValue, @Nullable Consumer<Context> config) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        double cos = Math.cos(Math.toRadians(coneDeg / 2.0));
        int hits = 0;
        AABB box = player.getBoundingBox().inflate(range + 2);
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !(e instanceof Player))) {
            Entity struck = e;
            Vec3 aimPoint = e.getBoundingBox().getCenter();
            double bestDist = HuntMonster.boxDistSqr(e.getBoundingBox(), eye);
            if (e instanceof HuntMonster m) {
                for (MonsterPart part : m.getMonsterParts()) {
                    Vec3 c = part.getBoundingBox().getCenter();
                    Vec3 to = c.subtract(eye);
                    Vec3 tf = new Vec3(to.x, 0, to.z);
                    double d = HuntMonster.boxDistSqr(part.getBoundingBox(), eye);
                    if (d < bestDist && tf.lengthSqr() > 0 && tf.normalize().dot(flat) >= cos) {
                        bestDist = d;
                        struck = part;
                        aimPoint = c;
                    }
                }
            }
            if (bestDist > range * range) continue;
            Vec3 to = aimPoint.subtract(eye);
            Vec3 tf = new Vec3(to.x, 0, to.z);
            if (bestDist > 2.25 && (tf.lengthSqr() < 1e-4 || tf.normalize().dot(flat) < cos)) continue;
            if (hit(player, struck, motionValue, config)) hits++;
        }
        return hits;
    }

    // ================================================================== events
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(AttackEntityEvent event) {
        Player p = event.getEntity();
        HunterData.get(p).lastStrengthScale = p.getAttackStrengthScale(0.5F);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        DamageSource src = event.getSource();
        Context ctx = CTX.get();
        if (src.getEntity() instanceof Player player) {
            ItemStack stack = ctx != null && ctx.weapon != null ? ctx.weapon : player.getMainHandItem();
            boolean direct = src.getDirectEntity() == player;
            if (stack.getItem() instanceof IHunterWeapon weapon && (direct || ctx != null)) {
                event.setAmount(compute(player, stack, weapon, target, ctx));
                return;
            }
        }
        if (target instanceof HuntMonster m) {
            boolean fixed = src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) || src.is(net.minecraft.world.damagesource.DamageTypes.MAGIC);
            event.setAmount(event.getAmount() * (fixed ? 1.0F : 0.6F) * m.defenseMultiplier());
        }
    }

    // ================================================================== formula
    public static float compute(Player p, ItemStack stack, IHunterWeapon w, LivingEntity target, @Nullable Context ctx) {
        HunterData data = HunterData.get(p);
        float raw = w.getAttack(stack);
        int atkLv = HunterSkills.level(p, SkillType.ATTACK_BOOST);
        raw = raw * (1.0F + 0.03F * atkLv) + 0.3F * atkLv;
        if (p.hasEffect(HCEffects.DEMONDRUG.get())) raw += 1.0F;
        int heroics = HunterSkills.level(p, SkillType.HEROICS);
        if (heroics > 0 && p.getHealth() <= p.getMaxHealth() * 0.35F) raw *= 1.0F + 0.1F * heroics;
        int affinity = w.getAffinity(stack) + 5 * HunterSkills.level(p, SkillType.CRITICAL_EYE) + (atkLv >= 4 ? 5 : 0);
        HuntMonster monster = target instanceof HuntMonster hm ? hm : null;
        int agitator = HunterSkills.level(p, SkillType.AGITATOR);
        if (monster != null && monster.isEnraged() && agitator > 0) {
            raw *= 1.0F + 0.05F * agitator;
            affinity += 5 * agitator;
        }
        raw *= HuntHooks.attackMultiplier(p);

        float mv;
        if (ctx != null && ctx.motionValue >= 0) {
            mv = ctx.motionValue;
        } else {
            float s = data.lastStrengthScale;
            mv = w.getLeftClickMotionValue(p, stack, target) * (0.2F + s * s * 0.8F);
        }
        mv *= w.getStateMultiplier(p, stack);

        Sharpness sharp = w.usesSharpness() && (ctx == null || ctx.useSharpness) ? w.getSharpness(stack, p) : null;
        float sharpRaw = sharp != null ? sharp.rawMult : 1.0F;
        float sharpElem = sharp != null ? sharp.elemMult : 1.0F;

        DamageKind kind = ctx != null && ctx.kind != null ? ctx.kind : w.getDamageKind(stack);
        String partName = "body";
        Hitzone hz = null;
        if (monster != null) {
            partName = ctx != null && ctx.forcedPart != null ? ctx.forcedPart : monster.getCurrentHitPart();
            PartDef pd = monster.profile().part(partName);
            partName = pd.name;
            hz = pd.hitzone;
            if (monster.isPartBroken(pd.name)) hz = hz.softened(pd.brokenSoften);
        }
        float hzRaw = hz != null ? hz.raw(kind) / 100.0F : 1.0F;
        boolean weak = hz != null && hz.raw(kind) >= 45;
        if (weak) {
            int we = HunterSkills.level(p, SkillType.WEAKNESS_EXPLOIT);
            affinity += we == 1 ? 15 : we == 2 ? 30 : we >= 3 ? 50 : 0;
        }
        float critMult = 1.0F;
        boolean crit = false;
        int roll = p.getRandom().nextInt(100);
        if (affinity > 0 && roll < affinity) {
            crit = true;
            critMult = 1.25F + 0.05F * HunterSkills.level(p, SkillType.CRITICAL_BOOST);
        } else if (affinity < 0 && roll < -affinity) {
            critMult = 0.75F;
        }
        float rawDmg = raw * mv * sharpRaw * critMult * hzRaw;

        Element el = w.getElement(stack);
        float ev = w.getElementValue(stack);
        if (p.hasEffect(HCEffects.DRAGONBLIGHT.get())) ev = 0;
        float elemDmg = 0, statusAmt = 0;
        if (el.isElemental() && ev > 0) {
            float ehz = hz != null ? hz.element(el) / 100.0F : 0.2F;
            elemDmg = ev / 100.0F * (1.0F + 0.1F * HunterSkills.level(p, SkillType.ELEMENT_ATTACK)) * sharpElem * ehz * (ctx != null ? ctx.elementMult : 1.0F);
        } else if (el.isStatus() && ev > 0) {
            statusAmt = ev / 10.0F * (ctx != null ? ctx.statusMult : 1.0F);
            if (monster == null && p.getRandom().nextFloat() < 0.15F) {
                switch (el) {
                    case POISON -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
                    case PARALYSIS -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4));
                    case SLEEP -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                    default -> { }
                }
            }
        }

        boolean bounce = false;
        if (sharp != null && hz != null && kind != DamageKind.SHOT && (ctx == null || ctx.canBounce)) {
            bounce = hz.raw(kind) * sharp.rawMult < 25.0F;
        }
        float total = rawDmg + elemDmg;
        if (monster != null) total *= monster.defenseMultiplier();
        if (bounce) total *= 0.7F;

        if (sharp != null) {
            int cost = (ctx != null ? ctx.sharpnessCost : 1) + (bounce ? 1 : 0);
            int razor = HunterSkills.level(p, SkillType.RAZOR_SHARP);
            float skip = razor == 1 ? 0.1F : razor == 2 ? 0.25F : razor >= 3 ? 0.5F : 0;
            if (p.getRandom().nextFloat() >= skip) w.consumeSharpness(stack, p, cost);
        }

        if (monster != null) {
            monster.onHunterDamage(partName, kind, total, el, statusAmt, p);
            if (ctx != null && ctx.extraKO > 0 && kind == DamageKind.BLUNT) monster.addStatus(4, ctx.extraKO, p);
        }
        w.onHunterHit(p, stack, target, total, weak);

        // feedback
        if (p instanceof ServerPlayer sp) {
            Vec3 pos = target.position().add(0, target.getBbHeight() * 0.7, 0);
            if (monster != null) {
                PartDef pd = monster.profile().part(partName);
                if (pd.hasHitbox) pos = monster.localPos(pd.side, pd.up + pd.height * 0.6, pd.forward);
            }
            int color = bounce ? 0x9A9A9A : weak ? 0xFFA126 : 0xFFFFFF;
            sendDamageNumber(sp, pos, total, color, crit, el);
            ServerLevel sl = sp.serverLevel();
            if (bounce) {
                sl.playSound(null, target.getX(), target.getY(), target.getZ(), HCSounds.BOUNCE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                sl.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 6, 0.2, 0.2, 0.2, 0.3);
            } else if (total >= 15) {
                sl.playSound(null, target.getX(), target.getY(), target.getZ(), HCSounds.HIT_HEAVY.get(), SoundSource.PLAYERS, 1.0F, 0.9F + p.getRandom().nextFloat() * 0.2F);
                sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pos.x, pos.y, pos.z, 4, 0.2, 0.2, 0.2, 0.1);
            }
            if (crit) sl.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y, pos.z, 8, 0.3, 0.3, 0.3, 0.2);
        }
        return Math.max(0.1F, total);
    }

    public static void sendDamageNumber(ServerPlayer player, Vec3 pos, float amount, int color, boolean crit, Element element) {
        CompoundTag t = new CompoundTag();
        t.putDouble("x", pos.x);
        t.putDouble("y", pos.y);
        t.putDouble("z", pos.z);
        t.putFloat("a", amount);
        t.putInt("c", color);
        t.putBoolean("k", crit);
        t.putInt("e", element.ordinal());
        HCNetwork.sendToPlayer(player, "core/dmg", t);
    }
}
