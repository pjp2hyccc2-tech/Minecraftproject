package com.nihonisshu.tabi.network;

import com.nihonisshu.tabi.entity.VehicleEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Common-side entry points that forward to client-only code without loading it on a dedicated server. */
public final class ClientHooksProxy {
    private ClientHooksProxy() {}

    public static void vehicleTick(VehicleEntity v) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.nihonisshu.tabi.client.ClientHooks.vehicleTick(v));
    }

    public static void vehicleStateChanged(VehicleEntity v, byte from, byte to) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.nihonisshu.tabi.client.ClientHooks.vehicleStateChanged(v, from, to));
    }

    public static void openJournal() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.nihonisshu.tabi.client.ClientHooks.openJournal());
    }
}
