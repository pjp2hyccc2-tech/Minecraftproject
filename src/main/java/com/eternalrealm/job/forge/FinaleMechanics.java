package com.eternalrealm.job.forge;

import com.eternalrealm.combat.forge.Afflictions;
import com.eternalrealm.combat.forge.CombatHits;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.SkillFxPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the finales (秘奥義) do beyond hit frames, on the server: the shinobi's engraved marks and their reckoning, the
 * inquisitor's sentences. Server thread only.
 */
public final class FinaleMechanics {
    /** Marks older than this are forgotten (a cast never takes longer). */
    private static final int MARK_TICKS = 20 * 10;
    private static final Map<UUID, Marks> MARKS = new HashMap<>();

    private FinaleMechanics() {
    }

    /** The enemies one cast of a skill has engraved so far. */
    private static final class Marks {
        final String skillId;
        final long since;
        final Set<Integer> entities = new LinkedHashSet<>();

        Marks(String skillId, long since) {
            this.skillId = skillId;
            this.since = since;
        }
    }

    /**
     * Engraves every enemy within the effect's radius for the caster's cast of {@code skillId} and pins it in place
     * (a stun for the hold; bosses are only slowed). No damage yet: a cut shows where each one was marked.
     */
    public static void engrave(ServerPlayer caster, String skillId, SkillEffect.Engrave engrave) {
        long now = caster.server.getTickCount();
        Marks marks = MARKS.get(caster.getUUID());
        if (marks == null || !marks.skillId.equals(skillId) || now - marks.since > MARK_TICKS) {
            marks = new Marks(skillId, now);
            MARKS.put(caster.getUUID(), marks);
        }
        ServerLevel level = caster.serverLevel();
        double radius = engrave.radius();
        AABB area = caster.getBoundingBox().inflate(radius, 3.0, radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area,
                candidate -> CombatHits.isValidTarget(caster, candidate))) {
            if (entity.distanceToSqr(caster) > radius * radius) {
                continue;
            }
            boolean fresh = marks.entities.add(entity.getId());
            Afflictions.apply(caster, entity, 0.0F, SkillEffect.AfflictionKind.STUN, engrave.holdTicks(), 0.0, 1.0,
                    caster.position());
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, entity.getX(), entity.getY() + entity.getBbHeight() * 0.55,
                    entity.getZ(), fresh ? 2 : 1, 0.25, 0.25, 0.25, 0.0);
            level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + entity.getBbHeight() * 0.55,
                    entity.getZ(), 6, 0.3, 0.3, 0.3, 0.4);
        }
    }

    /**
     * The enemies the caster's cast of {@code skillId} engraved that are still alive in its level and within
     * {@code range} blocks; the marks are used up.
     */
    public static List<LivingEntity> reckoning(ServerPlayer caster, String skillId, double range) {
        Marks marks = MARKS.remove(caster.getUUID());
        List<LivingEntity> found = new ArrayList<>();
        if (marks == null || !marks.skillId.equals(skillId)
                || caster.server.getTickCount() - marks.since > MARK_TICKS) {
            return found;
        }
        for (int id : marks.entities) {
            Entity entity = caster.serverLevel().getEntity(id);
            if (entity instanceof LivingEntity living && living.isAlive()
                    && living.distanceToSqr(caster) <= range * range) {
                found.add(living);
            }
        }
        return found;
    }

    /**
     * Hands every enemy the frame damaged its sentence (nearest first: chains, brand, withering, hellfire, blindness,
     * frost, then round again) with the skill's effect scale, and marks it over its head for everyone close enough.
     */
    public static void verdicts(ServerPlayer caster, List<CombatHits.HitTarget> targets, int frame, double scale) {
        List<CombatHits.HitTarget> order = new ArrayList<>(targets);
        order.sort(Comparator.comparingDouble(hit -> hit.entity().distanceToSqr(caster)));
        ServerLevel level = caster.serverLevel();
        for (int i = 0; i < order.size(); i++) {
            CombatHits.HitTarget hit = order.get(i);
            LivingEntity target = hit.entity();
            SkillEffect.Sentence sentence = SkillEffect.Sentence.of(i);
            for (SkillEffect.Afflict affliction : sentence.afflictions(frame)) {
                Afflictions.apply(caster, target, hit.amount(), affliction.kind(), affliction.durationTicks(),
                        affliction.power(), scale, caster.position());
            }
            double headY = target.getY() + target.getBbHeight() + 0.6;
            level.sendParticles(ParticleTypes.END_ROD, target.getX(), headY, target.getZ(), 10, 0.2, 0.3, 0.2, 0.02);
            level.sendParticles(ParticleTypes.ENCHANT, target.getX(), headY, target.getZ(), 16, 0.3, 0.3, 0.3, 0.6);
            ERNetwork.sendToNear(level, target.getX(), headY, target.getZ(), 64.0,
                    SkillFxPacket.verdict(sentence, target.getX(), headY, target.getZ(), caster.getYRot(),
                            target.getBbHeight(), caster.getRandom().nextInt()));
        }
    }

    /** Forgets a player's marks (logging out). */
    public static void forget(UUID player) {
        MARKS.remove(player);
    }
}
