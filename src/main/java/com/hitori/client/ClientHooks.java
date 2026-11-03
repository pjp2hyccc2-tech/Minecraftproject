package com.hitori.client;

import com.hitori.client.gui.PhoneScreen;
import com.hitori.client.gui.PrintoutScreen;
import com.hitori.network.C2S;
import com.hitori.network.ModNetwork;
import net.minecraft.client.Minecraft;

/** Client-only entry points called from common code through DistExecutor. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openCameraApp(int device) {
        if (ClientState.hiding() && Peek.facingFusuma(Minecraft.getInstance())) return;
        CameraApp.open(device);
    }

    /** Right-click with the phone: its screen; with sneak, the flashlight. */
    public static void openPhone(boolean sneaking) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientState.inGame()) return;
        if (ClientState.hiding() && Peek.facingFusuma(mc)) return;
        if (PhoneUi.callState == 1) {
            ModNetwork.sendToServer(new C2S.Action(C2S.PHONE, 2));
            return;
        }
        if (sneaking) {
            ModNetwork.sendToServer(new C2S.Action(C2S.PHONE, 1));
            return;
        }
        if (mc.screen == null) mc.setScreen(new PhoneScreen());
    }

    public static void openPrintout() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) mc.setScreen(new PrintoutScreen());
    }
}
