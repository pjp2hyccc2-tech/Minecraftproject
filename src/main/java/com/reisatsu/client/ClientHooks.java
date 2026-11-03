package com.reisatsu.client;

import com.reisatsu.client.gui.PhotoScreen;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.game.GameData;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

/** Client-only entry points called from common code through DistExecutor. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openPhoto(String id, @Nullable SpiritKind spirit, int stars) {
        Minecraft.getInstance().setScreen(new PhotoScreen(id, spirit == null ? GameData.RESULT_EMPTY : GameData.RESULT_CAPTURED, spirit, stars, null));
    }
}
