package com.birdiecraft.client;

import com.birdiecraft.client.screen.GolfMenuScreen;
import com.birdiecraft.network.C2SMenuActionPacket;
import com.birdiecraft.network.GolfNetwork;
import net.minecraft.client.Minecraft;

/** Entry points called from common code through DistExecutor. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openGolfMenu() {
        Minecraft mc = Minecraft.getInstance();
        GolfNetwork.sendToServer(new C2SMenuActionPacket(C2SMenuActionPacket.REQUEST_MENU, ""));
        mc.setScreen(new GolfMenuScreen());
    }
}
