package com.mcbesiege.client;

import net.minecraft.nbt.CompoundTag;

/** Client-side state received from the server. */
public final class ClientState {
    private ClientState() {}

    /** Machine entity whose control screen should open as soon as the entity arrives. */
    public static int pendingControl = -1;
    public static int pendingControlTicks;
    /** Latest mission status (empty = no mission). */
    public static CompoundTag mission = new CompoundTag();
    /** Last key set sent while riding. */
    public static int[] ridingKeys = new int[0];

    public static void reset() {
        pendingControl = -1;
        mission = new CompoundTag();
        ridingKeys = new int[0];
    }
}
