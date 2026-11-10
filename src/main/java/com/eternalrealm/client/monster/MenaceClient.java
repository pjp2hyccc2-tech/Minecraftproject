package com.eternalrealm.client.monster;

import com.eternalrealm.client.map.ClientMapState;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.monster.MonsterMenace;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.field.FieldLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Client-side life of the field: field monsters show their level ({@link MonsterMenace}) with particles of their
 * field's element — a few motes at Lv15, a coloured aura at Lv25, more at Lv35 (when the body also wears the energy
 * shroud, {@link MenaceSwirlLayer}) and ground pulses at Lv45 — and the border between two fields shimmers in the
 * colour of the field beyond it when the player walks up to it, so nobody crosses into a harder field unawares.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MenaceClient {
    private static final String NAMEPLATE_PREFIX = "hud." + ModInfo.MOD_ID + ".monster.nameplate.";
    private static final double MONSTER_RANGE = 40.0;
    private static final int BORDER_RADIUS = 12;
    private static final int BORDER_STEP = 2;

    private MenaceClient() {
    }

    /** The level on a field monster's name plate, or -1 for anything else. */
    public static int level(Entity entity) {
        Component name = entity.getCustomName();
        if (name == null || !(name.getContents() instanceof TranslatableContents contents)
                || !contents.getKey().startsWith(NAMEPLATE_PREFIX)) {
            return -1;
        }
        Object[] args = contents.getArgs();
        if (args.length == 0) {
            return -1;
        }
        Object first = args[0];
        return MonsterMenace.parseLevel(first instanceof Component component ? component.getString() : String.valueOf(first));
    }

    /** The menace tier of an entity (0 for anything that is not a field monster). */
    public static int tier(Entity entity) {
        int level = level(entity);
        return level < 0 ? 0 : MonsterMenace.tier(level);
    }

    /** The element of the field an entity stands in (plain outside アストリア). */
    public static MonsterMenace.Element element(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !ClientMapState.inAstoria(minecraft.level)) {
            return MonsterMenace.Element.PLAIN;
        }
        return MonsterMenace.elementOf(FieldLayout.areaAt(entity.getX(), entity.getZ()));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || minecraft.isPaused()) {
            return;
        }
        RandomSource random = level.random;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(MONSTER_RANGE),
                m -> !m.isInvisible() && m.isAlive())) {
            int tier = tier(mob);
            if (tier <= 0) {
                continue;
            }
            MonsterMenace.Element element = element(mob);
            if (random.nextDouble() < MonsterMenace.particleChance(tier)) {
                aura(level, mob, element, tier, random);
            }
            if (tier >= 4 && (mob.tickCount + mob.getId() * 7) % 50 == 0) {
                pulse(level, mob, element);
            }
        }
        if (level.getGameTime() % 4 == 0 && ClientMapState.inAstoria(level)) {
            borderVeil(level, player, random);
        }
    }

    private static Vector3f rgb(int color) {
        return new Vector3f(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
    }

    private static ParticleOptions mote(MonsterMenace.Element element, int tier, RandomSource random) {
        return switch (element) {
            case PLAIN -> tier >= 4 ? ParticleTypes.END_ROD : ParticleTypes.ENCHANTED_HIT;
            case NATURE -> random.nextBoolean() ? ParticleTypes.COMPOSTER : ParticleTypes.SPORE_BLOSSOM_AIR;
            case EMBER -> random.nextInt(3) == 0 ? ParticleTypes.SMOKE : ParticleTypes.SMALL_FLAME;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case FLAME -> tier >= 4 && random.nextInt(6) == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME;
            case STORM -> ParticleTypes.ELECTRIC_SPARK;
            case VOID -> tier >= 4 && random.nextInt(3) == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.REVERSE_PORTAL;
            case TIDE -> random.nextBoolean() ? ParticleTypes.SPLASH : ParticleTypes.BUBBLE_POP;
            case GALE -> ParticleTypes.WHITE_ASH;
        };
    }

    /** One particle of the monster's aura: the element's mote, or (Lv25+) a dust grain of its colour. */
    private static void aura(ClientLevel level, Mob mob, MonsterMenace.Element element, int tier, RandomSource random) {
        double width = mob.getBbWidth() * 0.6 + 0.2;
        double x = mob.getX() + (random.nextDouble() - 0.5) * 2.0 * width;
        double y = mob.getY() + random.nextDouble() * mob.getBbHeight();
        double z = mob.getZ() + (random.nextDouble() - 0.5) * 2.0 * width;
        if (tier >= 2 && random.nextInt(3) != 0) {
            level.addParticle(new DustParticleOptions(rgb(element.color()), tier >= 3 ? 1.3F : 0.9F), x, y, z,
                    0.0, 0.0, 0.0);
        } else {
            level.addParticle(mote(element, tier, random), x, y, z, (random.nextDouble() - 0.5) * 0.02,
                    0.02 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.02);
        }
    }

    /** Lv45+: a ring of the element's colour runs out from the monster's feet. */
    private static void pulse(ClientLevel level, Mob mob, MonsterMenace.Element element) {
        DustParticleOptions dust = new DustParticleOptions(rgb(element.color()), 1.6F);
        double radius = mob.getBbWidth() * 0.5 + 1.0;
        for (int i = 0; i < 20; i++) {
            double a = i / 20.0 * Math.PI * 2.0;
            level.addParticle(dust, mob.getX() + Math.cos(a) * radius, mob.getY() + 0.1,
                    mob.getZ() + Math.sin(a) * radius, Math.cos(a) * 0.05, 0.0, Math.sin(a) * 0.05);
        }
    }

    /**
     * Near a field border: where two neighbouring sample points around the player lie in different fields, a few
     * grains of the farther field's colour rise from the ground — a faint shimmering line along the border.
     */
    private static void borderVeil(ClientLevel level, LocalPlayer player, RandomSource random) {
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        int here = FieldLayout.areaAt(px, pz);
        if (FieldLayout.inCapital(px, pz) || FieldLayout.borderDistance(px, pz) > BORDER_RADIUS * 2.5) {
            return;
        }
        int n = BORDER_RADIUS * 2 / BORDER_STEP + 1;
        int[] fields = new int[n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                fields[i * n + j] = FieldLayout.areaAt(px - BORDER_RADIUS + i * BORDER_STEP, pz - BORDER_RADIUS + j * BORDER_STEP);
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int a = fields[i * n + j];
                int b = i + 1 < n ? fields[(i + 1) * n + j] : a;
                int c = j + 1 < n ? fields[i * n + j + 1] : a;
                int other = b != a ? b : c != a ? c : -1;
                if (other < 0 || random.nextInt(3) != 0) {
                    continue;
                }
                int beyond = a == here ? other : a;
                double x = px - BORDER_RADIUS + i * BORDER_STEP + (b != a ? BORDER_STEP * 0.5 : 0.0) + random.nextDouble();
                double z = pz - BORDER_RADIUS + j * BORDER_STEP + (b == a ? BORDER_STEP * 0.5 : 0.0) + random.nextDouble();
                double ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        (int) Math.floor(x), (int) Math.floor(z));
                double y = ground + 0.2 + random.nextDouble() * 2.2;
                level.addParticle(new DustParticleOptions(rgb(Areas.require(beyond).color()), 1.1F), x, y, z, 0.0,
                        0.01, 0.0);
            }
        }
    }
}
