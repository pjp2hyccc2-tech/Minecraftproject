package com.huntcraft.quest;

import com.huntcraft.HuntCraft;
import com.huntcraft.data.HCCapabilities;
import com.huntcraft.data.HunterData;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Workaround for a core issue: the core HunterData capability provider permanently invalidates its LazyOptional
 * when Forge invalidates the player's capabilities (dimension change / death), so after a quest-map teleport
 * {@link HunterData#get} would return a fresh, unsaved object. This second provider (attached after the core one)
 * serves the SAME HunterData instance whenever the core optional is dead; it is never invalidated and not serialized.
 * Harmless once the core provider is fixed (it is then simply never asked).
 */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HunterDataGuard {
    private static final ResourceLocation ID = HuntCraft.id("hunter_data_guard");
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private HunterDataGuard() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player)) return;
        ICapabilityProvider core = event.getCapabilities().get(HCCapabilities.HUNTER_ID);
        if (core == null) return;
        HunterData data = core.getCapability(HCCapabilities.HUNTER, null).orElse(null);
        if (data == null) return;
        event.addCapability(ID, new Guard(data));
    }

    private static final class Guard implements ICapabilityProvider {
        private final LazyOptional<HunterData> optional;

        Guard(HunterData data) {
            this.optional = LazyOptional.of(() -> data);
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap != HCCapabilities.HUNTER) return LazyOptional.empty();
            if (REPORTED.compareAndSet(false, true)) {
                HuntCraft.LOGGER.warn("HunterData capability of a player was invalidated (dimension change / death); "
                        + "served by the quest module's guard provider. Core fix: do not invalidate the HunterData LazyOptional permanently.");
            }
            return optional.cast();
        }
    }
}
