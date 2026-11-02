package com.sounan.client;

import com.sounan.client.gui.PhoneScreen;
import com.sounan.client.gui.TrailMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** Client-only entry points called from common code through DistExecutor. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openPhone(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new PhoneScreen());
    }

    public static void openMap() {
        Minecraft.getInstance().setScreen(new TrailMapScreen());
    }
}
