package com.frostbound.client;

import com.frostbound.FrostboundMod;
import com.frostbound.world.Dims;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client game events: the management key, camera, ambience (fog, snow, sound), title screen entry point. */
@Mod.EventBusSubscriber(modid = FrostboundMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final RandomSource RANDOM = RandomSource.create();
    private static float fogFactor = 1f;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (ClientSetup.MANAGEMENT_KEY.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                if (ClientCity.hasCity() && ClientCity.event() != null) {
                    mc.setScreen(new EventScreen(new ManagementScreen(null, -1)));
                } else if (ClientCity.hasCity()) {
                    mc.setScreen(new ManagementScreen(null, -1));
                } else if (ClientCity.frostbound()) {
                    mc.setScreen(new FoundingScreen(false));
                }
            }
        }
        Sounds.tick();
        snow(mc);
        AutoShots.tick(mc);
    }

    /** Extra wind-driven snow around the camera in storms. */
    private static void snow(Minecraft mc) {
        if (mc.level == null || mc.isPaused() || !mc.level.dimension().equals(Dims.FROZEN_WASTES) || !ClientCity.hasCity()) {
            return;
        }
        String w = ClientCity.snap().getString("weather");
        int n = switch (w) {
            case "BLIZZARD" -> 22;
            case "GREAT_COLD_WAVE" -> 34;
            case "EXTREME_COLD" -> 9;
            case "WIND" -> 4;
            default -> 0;
        };
        if (n == 0 || mc.getCameraEntity() == null) {
            return;
        }
        if (mc.options.particles().get().getId() > 0) {
            n /= 3;
        }
        Vec3 c = mc.getCameraEntity().position();
        double radius = CameraController.active() ? 40 : 14;
        double minDist = CameraController.active() ? 12 : 4.5;
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = minDist + RANDOM.nextDouble() * (radius - minDist);
            double x = c.x + Math.cos(a) * r;
            double y = c.y + (RANDOM.nextDouble() - 0.3) * 10;
            double z = c.z + Math.sin(a) * r;
            mc.level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0.35 + RANDOM.nextDouble() * 0.3, -0.12, 0.12);
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            CameraController.update();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CameraController.end();
        CameraController.forget();
        ClientCity.reset();
        WorldOverlay.reset();
        Sounds.stopAll();
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (CameraController.active()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof ManagementScreen) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(Dims.FROZEN_WASTES)) {
            return;
        }
        String w = ClientCity.snap().getString("weather");
        float target = switch (w) {
            case "SNOW" -> 0.8f;
            case "WIND" -> 0.65f;
            case "BLIZZARD" -> 0.28f;
            case "EXTREME_COLD" -> 0.45f;
            case "GREAT_COLD_WAVE" -> 0.2f;
            default -> 1.0f;
        };
        if (CameraController.active()) {
            target = Math.min(1f, target * 2.5f);
        }
        fogFactor += (target - fogFactor) * 0.02f;
        if (fogFactor > 0.97f) {
            return;
        }
        float far = event.getFarPlaneDistance();
        float newFar = Math.max(24f, far * fogFactor);
        float near = CameraController.active() ? newFar * 0.6f : newFar * (fogFactor < 0.5f ? 0.02f : 0.3f);
        event.setFarPlaneDistance(newFar);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), near));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(Dims.FROZEN_WASTES)) {
            return;
        }
        float k = (1f - fogFactor) * 0.75f;
        if (k <= 0.01f) {
            return;
        }
        float r = event.getRed();
        float g = event.getGreen();
        float b = event.getBlue();
        float lum = (r + g + b) / 3f;
        event.setRed(r + (lum * 0.86f + 0.04f - r) * k);
        event.setGreen(g + (lum * 0.93f + 0.05f - g) * k);
        event.setBlue(b + (lum * 1.02f + 0.07f - b) * k);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen title) {
            int w = 200;
            int x = title.width / 2 - w / 2;
            int y = title.height / 4 + 24;
            event.addListener(new FButton(x, y, w, 20, Component.translatable("frostbound.ui.title_button"),
                    () -> Minecraft.getInstance().setScreen(new FoundingScreen(true, title))));
            AutoShots.onTitle(title);
        }
    }
}
