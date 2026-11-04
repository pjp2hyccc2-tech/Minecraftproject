package jp.satsujinki.client;

import jp.satsujinki.block.entity.TrapBlockEntity;
import jp.satsujinki.client.gui.HostTerminalScreen;
import jp.satsujinki.client.sound.TrapLoopSound;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;

/** Entry points called from common code through DistExecutor. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openTerminal(BlockPos pos) {
        Minecraft.getInstance().setScreen(new HostTerminalScreen(pos));
    }

    public static void playTrapLoop(TrapBlockEntity be, SoundEvent sound) {
        Minecraft.getInstance().getSoundManager().play(new TrapLoopSound(be, sound));
    }
}
