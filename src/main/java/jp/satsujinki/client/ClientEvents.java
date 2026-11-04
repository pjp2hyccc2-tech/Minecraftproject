package jp.satsujinki.client;

import jp.satsujinki.Satsujinki;
import jp.satsujinki.SatsujinkiConfig;
import jp.satsujinki.client.gui.R18WarningScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Satsujinki.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    /** R18 age gate in front of the title screen (once per launch). */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof TitleScreen title && !ClientState.r18Accepted && SatsujinkiConfig.r18Warning()) {
            event.setNewScreen(new R18WarningScreen(title));
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().level == null) return;
        ClientState.tick();
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (ClientState.shakeTicks <= 0) return;
        float t = ClientState.clientTicks + Minecraft.getInstance().getFrameTime();
        float amp = ClientState.shake * (ClientState.shakeTicks / (float) ClientState.shakeMax);
        event.setYaw(event.getYaw() + Mth.sin(t * 2.9F) * amp * 1.6F);
        event.setPitch(event.getPitch() + Mth.cos(t * 3.7F) * amp * 1.6F);
        event.setRoll(event.getRoll() + Mth.sin(t * 4.3F) * amp * 1.2F);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
    }
}
