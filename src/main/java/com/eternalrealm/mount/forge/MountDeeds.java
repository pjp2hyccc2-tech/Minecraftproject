package com.eternalrealm.mount.forge;

import com.eternalrealm.mount.MountIndividual;
import com.eternalrealm.mount.MountKind;
import com.eternalrealm.mount.MountLook;
import com.eternalrealm.mount.MountRules;
import com.eternalrealm.mount.MountTraits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * A kept horse or griffin as an item (騎獣の証書): the compound {@value MountRules#DEED_TAG} holds the mount's id,
 * kind ({@code species}), look, values and grade, so it can be traded, sold at the market (which prices deeds by
 * species and grade) and taken into a stable again.
 */
public final class MountDeeds {
    private MountDeeds() {
    }

    public static ItemStack create(MountIndividual mount) {
        ItemStack stack = new ItemStack(MountItems.DEED.get());
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", mount.id());
        tag.putString("species", mount.kind().id());
        tag.putString("coat", mount.look().coat());
        tag.putString("shape", mount.look().shape());
        tag.putInt("speed", mount.traits().speed());
        tag.putInt("accel", mount.traits().acceleration());
        tag.putInt("lift", mount.traits().lift());
        tag.putString("grade", mount.grade().id());
        stack.getOrCreateTag().put(MountRules.DEED_TAG, tag);
        return stack;
    }

    public static boolean isDeed(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof MountDeedItem;
    }

    /** The mount a deed holds; empty for anything else or a damaged deed. */
    public static Optional<MountIndividual> read(ItemStack stack) {
        if (!isDeed(stack) || stack.getTag() == null || !stack.getTag().contains(MountRules.DEED_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag().getCompound(MountRules.DEED_TAG);
        Optional<MountKind> kind = MountKind.byId(tag.getString("species"));
        if (kind.isEmpty() || !tag.hasUUID("id") || !MountRules.tradeable(kind.get())) {
            return Optional.empty();
        }
        UUID id = tag.getUUID("id");
        MountTraits traits = new MountTraits(MountTraits.clamp(tag.getInt("speed")),
                MountTraits.clamp(tag.getInt("accel")), MountTraits.clamp(tag.getInt("lift")));
        return Optional.of(new MountIndividual(id, kind.get(), new MountLook(tag.getString("coat"),
                tag.getString("shape")), traits));
    }
}
