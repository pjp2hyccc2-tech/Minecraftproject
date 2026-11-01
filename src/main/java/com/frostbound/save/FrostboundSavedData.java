package com.frostbound.save;

import com.frostbound.FrostboundMod;
import com.frostbound.core.city.City;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * All FROSTBOUND state of a world in one file ({@code data/frostbound_city.dat}). Writes go to a temporary
 * file that replaces the real one only when complete, and the previous file is kept as {@code .bak}.
 */
public final class FrostboundSavedData extends SavedData {
    public static final String NAME = "frostbound_city";

    public enum Phase {
        NOT_STARTED,
        SETUP,
        RUNNING
    }

    public Phase phase = Phase.NOT_STARTED;
    public boolean frostboundWorld;
    /** The live city (null until founded). */
    public City city;
    /** City data as loaded from disk, decoded by the city service. */
    public CompoundTag cityTag;
    /** Set when the save came from a newer mod version: never overwrite it. */
    public boolean readOnly;

    public static FrostboundSavedData create() {
        return new FrostboundSavedData();
    }

    public static FrostboundSavedData load(CompoundTag tag) {
        FrostboundSavedData d = new FrostboundSavedData();
        try {
            d.phase = Phase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException e) {
            d.phase = Phase.NOT_STARTED;
        }
        d.frostboundWorld = tag.getBoolean("frostboundWorld");
        if (tag.contains("city", Tag10.COMPOUND)) {
            d.cityTag = tag.getCompound("city");
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString("phase", phase.name());
        tag.putBoolean("frostboundWorld", frostboundWorld);
        if (city != null) {
            tag.put("city", NbtTree.toNbt(city.save()));
        } else if (cityTag != null) {
            tag.put("city", cityTag);
        }
        return tag;
    }

    @Override
    public void save(File file) {
        if (!isDirty()) {
            return;
        }
        if (readOnly) {
            setDirty(false);
            return;
        }
        CompoundTag root = new CompoundTag();
        root.put("data", save(new CompoundTag()));
        NbtUtils.addCurrentDataVersion(root);
        Path target = file.toPath();
        Path tmp = target.resolveSibling(file.getName() + ".tmp");
        Path bak = target.resolveSibling(file.getName() + ".bak");
        try {
            NbtIo.writeCompressed(root, tmp.toFile());
            if (Files.exists(target)) {
                Files.copy(target, bak, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            FrostboundMod.LOGGER.error("[FROSTBOUND] could not save city data", e);
            return;
        }
        setDirty(false);
    }

    /** NBT type id of compound tags (avoids importing Tag for one constant). */
    private static final class Tag10 {
        static final int COMPOUND = 10;
    }
}
