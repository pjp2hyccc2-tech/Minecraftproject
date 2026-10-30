package com.mcbesiege.client;

import com.mcbesiege.block.MachinePartBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Entry points called from common code through DistExecutor. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openPartConfig(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockState st = mc.level.getBlockState(pos);
        if (st.getBlock() instanceof MachinePartBlock mb) mc.setScreen(new PartConfigScreen(pos, mb.type));
    }

    public static void openBuilder() {
        Minecraft.getInstance().setScreen(new BuilderScreen());
    }
}
