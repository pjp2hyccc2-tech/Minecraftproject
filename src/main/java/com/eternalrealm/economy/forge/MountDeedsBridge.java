package com.eternalrealm.economy.forge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Optional;

/**
 * How the market recognises a mount deed (騎獣の証: one owned mount made tradeable) without depending on the mount
 * module: the deed's stack carries a compound {@value #TAG} with at least {@code species} and {@code grade}. Deeds are
 * shown under the board's "mount" kind and priced per species and grade.
 */
public final class MountDeedsBridge {
    public static final String TAG = "er_mount";

    private MountDeedsBridge() {
    }

    public static boolean isDeed(ItemStack stack) {
        CompoundTag tag = stack.isEmpty() ? null : stack.getTag();
        return tag != null && tag.contains(TAG, Tag.TAG_COMPOUND);
    }

    /** {@code mount:<species>:<grade>} for a deed. */
    public static Optional<String> group(ItemStack stack) {
        if (!isDeed(stack)) {
            return Optional.empty();
        }
        CompoundTag mount = stack.getTag().getCompound(TAG);
        String species = clean(mount.getString("species"));
        String grade = clean(mount.getString("grade"));
        return Optional.of("mount:" + (species.isEmpty() ? "unknown" : species) + ":"
                + (grade.isEmpty() ? "unknown" : grade));
    }

    private static String clean(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (char c : lower.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                out.append(c);
            }
        }
        return out.length() > 40 ? out.substring(0, 40) : out.toString();
    }
}
