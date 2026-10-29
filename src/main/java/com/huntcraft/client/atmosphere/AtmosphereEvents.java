package com.huntcraft.client.atmosphere;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.ClientState;
import com.huntcraft.registry.HCEffects;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge-bus client atmosphere: per-dimension fog colour and distance, ambient particles (ash & embers,
 * snow flurries, pollen & fireflies, cherry petals), and screen effects for roars and lightning.
 * Everything here is cheap and capped; it is active with or without a shader loader.
 */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AtmosphereEvents {
    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");
    private static final RandomSource RNG = RandomSource.create();
    /** Hard cap of ambient particles spawned per client tick. */
    private static final int MAX_PARTICLES_PER_TICK = 6;

    private static float lightningFlash;
    private static float lightningFlashPrev;
    private static float roar;
    private static float roarPrev;
    private static boolean roarActive;
    private static int tick;

    private AtmosphereEvents() {
    }

    // ------------------------------------------------------------------ fog
    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        AtmosphereModule.Kind kind = AtmosphereModule.kindOf(mc.level);
        float r = event.getRed(), g = event.getGreen(), b = event.getBlue();
        if (kind != null && event.getCamera().getFluidInCamera() == FogType.NONE) {
            float[] tint;
            float mix;
            switch (kind) {
                case VILLAGE -> {
                    tint = new float[]{1.00F, 0.92F, 0.88F};
                    mix = 0.5F;
                }
                case HILLS -> {
                    tint = new float[]{0.88F, 0.96F, 1.00F};
                    mix = 0.4F;
                }
                case FROST -> {
                    float gust = 0.5F + 0.5F * Mth.sin((tick + (float) event.getPartialTick()) * 0.012F);
                    tint = new float[]{0.84F + 0.06F * gust, 0.90F + 0.04F * gust, 1.00F};
                    mix = 0.65F;
                }
                default -> {
                    tint = new float[]{1.00F, 0.52F, 0.26F};
                    mix = 0.6F;
                }
            }
            r = Mth.lerp(mix, r, r * tint[0]);
            g = Mth.lerp(mix, g, g * tint[1]);
            b = Mth.lerp(mix, b, b * tint[2]);
        }
        float flash = Mth.lerp((float) event.getPartialTick(), lightningFlashPrev, lightningFlash);
        if (flash > 0.001F) {
            r = Mth.clamp(r + flash * 0.55F, 0.0F, 1.0F);
            g = Mth.clamp(g + flash * 0.58F, 0.0F, 1.0F);
            b = Mth.clamp(b + flash * 0.65F, 0.0F, 1.0F);
        }
        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }

    @SubscribeEvent
    public static void renderFog(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || event.getType() != FogType.NONE) return;
        AtmosphereModule.Kind kind = AtmosphereModule.kindOf(Minecraft.getInstance().level);
        if (kind == null) return;
        float far = event.getFarPlaneDistance();
        float near;
        switch (kind) {
            case VILLAGE -> near = far * 0.45F;
            case HILLS -> {
                near = far * 0.30F;
                far *= 0.97F;
            }
            case FROST -> {
                float gust = 0.48F + 0.12F * Mth.sin((tick + (float) event.getPartialTick()) * 0.015F);
                near = far * 0.06F;
                far *= gust;
            }
            default -> {
                near = far * 0.04F;
                far *= 0.62F;
            }
        }
        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }

    // ------------------------------------------------------------------ one-time install message
    @SubscribeEvent
    public static void loggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        String key = ShaderPackInstaller.takePendingMessage();
        if (key != null) event.getPlayer().displayClientMessage(Component.translatable(key), false);
    }

    // ------------------------------------------------------------------ triggers
    @SubscribeEvent
    public static void entityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        Entity e = event.getEntity();
        if (!(e instanceof LightningBolt)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d = mc.player.distanceTo(e);
        float strength = (float) Mth.clamp(1.0 - d / 96.0, 0.0, 1.0);
        lightningFlash = Math.max(lightningFlash, 0.25F + 0.75F * strength);
    }

    @SubscribeEvent
    public static void sound(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s == null) return;
        ResourceLocation loc = s.getLocation();
        if (!loc.getNamespace().equals(HuntCraft.MODID)) return;
        String path = loc.getPath();
        if (!path.startsWith("monster.roar")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d = mc.player.position().distanceTo(new Vec3(s.getX(), s.getY(), s.getZ()));
        float strength = (float) Mth.clamp(1.0 - d / 40.0, 0.0, 1.0);
        if (strength <= 0.0F) return;
        roar = Math.max(roar, 0.4F + 0.6F * strength);
        ClientState.addShake(0.35F * strength, 10);
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        lightningFlashPrev = lightningFlash;
        roarPrev = roar;
        lightningFlash *= 0.72F;
        if (lightningFlash < 0.005F) lightningFlash = 0.0F;
        if (mc.level == null || mc.player == null) {
            roar = 0.0F;
            return;
        }
        if (mc.isPaused()) return;
        tick++;
        LocalPlayer p = mc.player;
        boolean roared = p.hasEffect(HCEffects.ROAR.get());
        if (roared) {
            roar = Math.max(roar, 1.0F);
            if (!roarActive) ClientState.addShake(0.6F, 12);
        } else {
            roar *= 0.88F;
            if (roar < 0.01F) roar = 0.0F;
        }
        roarActive = roared;

        AtmosphereModule.Kind kind = AtmosphereModule.kindOf(mc.level);
        if (kind != null) spawnAmbient(kind, mc.level, p);
    }

    private static void spawnAmbient(AtmosphereModule.Kind kind, ClientLevel level, LocalPlayer p) {
        int budget = MAX_PARTICLES_PER_TICK;
        Vec3 c = p.position();
        long dayTime = level.getDayTime() % 24000L;
        boolean night = dayTime > 12800L && dayTime < 23200L;
        switch (kind) {
            case VILLAGE -> {
                if (tick % 2 == 0) spawnDrifting(level, c, 14, 10, ParticleTypes.CHERRY_LEAVES, 0.0, -0.01, 0.0, true, 1);
            }
            case HILLS -> {
                if (night) {
                    if (tick % 2 == 0) spawnFireflies(level, c, 12, 1);
                } else if (tick % 3 == 0) {
                    spawnDrifting(level, c, 12, 6, ParticleTypes.SPORE_BLOSSOM_AIR, 0.0, 0.0, 0.0, true, 1);
                }
            }
            case FROST -> {
                float gust = 0.5F + 0.5F * Mth.sin(tick * 0.02F);
                int n = Math.min(budget, 3 + Math.round(gust * 2.0F));
                spawnDrifting(level, c, 12, 8, ParticleTypes.SNOWFLAKE, -0.06 - 0.08 * gust, -0.02, 0.03, true, n);
            }
            case EMBER -> {
                spawnDrifting(level, c, 14, 8, ParticleTypes.ASH, 0.0, -0.005, 0.0, false, 2);
                if (tick % 2 == 0) spawnDrifting(level, c, 10, 4, ParticleTypes.SMALL_FLAME, 0.0, 0.025, 0.0, false, 1);
                if (tick % 9 == 0) spawnDrifting(level, c, 10, 4, ParticleTypes.LAVA, 0.0, 0.1, 0.0, false, 1);
                if (tick % 15 == 0) spawnDrifting(level, c, 16, 6, ParticleTypes.CAMPFIRE_COSY_SMOKE, 0.0, 0.02, 0.0, false, 1);
            }
        }
    }

    /** Spawns up to count particles around the player; sky-only particles are skipped under a roof. */
    private static void spawnDrifting(ClientLevel level, Vec3 c, double radius, double height, ParticleOptions type,
                                      double vx, double vy, double vz, boolean needsSky, int count) {
        for (int i = 0; i < count; i++) {
            double x = c.x + (RNG.nextDouble() - 0.5) * 2.0 * radius;
            double y = c.y + (RNG.nextDouble() - 0.35) * height;
            double z = c.z + (RNG.nextDouble() - 0.5) * 2.0 * radius;
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.isEmptyBlock(pos)) continue;
            if (needsSky && !level.canSeeSky(pos)) continue;
            level.addParticle(type, x, y, z, vx + (RNG.nextDouble() - 0.5) * 0.01, vy, vz + (RNG.nextDouble() - 0.5) * 0.01);
        }
    }

    /** Fireflies: glowing motes hovering just above the ground in the dark. */
    private static void spawnFireflies(ClientLevel level, Vec3 c, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double x = c.x + (RNG.nextDouble() - 0.5) * 2.0 * radius;
            double z = c.z + (RNG.nextDouble() - 0.5) * 2.0 * radius;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            double y = ground + 0.6 + RNG.nextDouble() * 2.2;
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.isEmptyBlock(pos) || level.getMaxLocalRawBrightness(pos) > 9) continue;
            level.addParticle(ParticleTypes.GLOW, x, y, z, (RNG.nextDouble() - 0.5) * 0.02, RNG.nextDouble() * 0.01, (RNG.nextDouble() - 0.5) * 0.02);
        }
    }

    // ------------------------------------------------------------------ overlay
    /** Lightning flash and roar vignette drawn above everything (registered by AtmosphereModule). */
    public static void renderOverlay(ForgeGui gui, GuiGraphics gfx, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui && mc.screen != null) return;
        float flash = Mth.lerp(partialTick, lightningFlashPrev, lightningFlash);
        if (flash > 0.01F) {
            int a = (int) (Mth.clamp(flash, 0.0F, 1.0F) * 110.0F);
            gfx.fill(0, 0, width, height, (a << 24) | 0xE8F0FF);
        }
        float r = Mth.lerp(partialTick, roarPrev, roar);
        if (r > 0.01F) drawVignette(gfx, width, height, Mth.clamp(r, 0.0F, 1.0F));
    }

    private static void drawVignette(GuiGraphics gfx, int width, int height, float strength) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        // darkens the edges; red is kept so the frame turns blood-red
        RenderSystem.setShaderColor(0.25F * strength, 0.85F * strength, 0.85F * strength, 1.0F);
        gfx.blit(VIGNETTE, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
    }
}
