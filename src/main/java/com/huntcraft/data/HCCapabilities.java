package com.huntcraft.data;

import com.huntcraft.HuntCraft;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HCCapabilities {
    public static final Capability<HunterData> HUNTER = CapabilityManager.get(new CapabilityToken<>() {
    });
    public static final ResourceLocation HUNTER_ID = HuntCraft.id("hunter");

    private HCCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(HunterData.class);
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final HunterData data = new HunterData();
        private final LazyOptional<HunterData> optional = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == HUNTER ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.save(true);
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            data.load(nbt);
        }

        public void invalidate() {
            optional.invalidate();
        }
    }
}
