package com.eternalrealm.item.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.item.EquipmentSlot;
import com.eternalrealm.job.JobType;
import com.eternalrealm.server.ServerContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Job outfits: a worn armor piece takes the look of its wearer's job (tools/art draws one outfit per job) unless the
 * player chose the tier look. The style is stored on the stack ({@value #TAG}, plus {@code CustomModelData} 101..106
 * for the inventory icon), so every client renders it without knowing anyone's job. Stats never depend on the style.
 */
public final class OutfitStyles {
    public static final String TAG = "ERStyle";
    /** Player extension holding the preference: {@code "job"} (default) or {@code "tier"}. */
    public static final String EXTENSION = "outfit";
    private static final int MODEL_DATA_BASE = 101;

    private OutfitStyles() {
    }

    public static Optional<JobType> styleOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        return JobType.byId(tag.getString(TAG)).filter(JobType::isChosen);
    }

    /** Server: brings the style of every worn piece in line with the player's job and preference. */
    public static void refresh(ServerPlayer player) {
        Optional<JobType> wanted = wantedStyle(player);
        for (EquipmentSlot slot : EquipmentSlot.armor()) {
            apply(player.getItemBySlot(GearArmorItem.vanillaSlot(slot)), wanted);
        }
    }

    /** Server: styles one piece that was just put on. */
    public static void onEquipped(ServerPlayer player, ItemStack stack) {
        if (stack.getItem() instanceof GearArmorItem) {
            apply(stack, wantedStyle(player));
        }
    }

    public static boolean prefersTierLook(ServerPlayer player) {
        return ServerContext.current().flatMap(context -> context.playerData().extension(player.getUUID(), EXTENSION))
                .map(value -> value.contains("tier")).orElse(false);
    }

    public static void setPrefersTierLook(ServerPlayer player, boolean tierLook) {
        ServerContext.current().ifPresent(context -> context.playerData().setExtension(player.getUUID(), EXTENSION,
                tierLook ? "\"tier\"" : "\"job\""));
        refresh(player);
    }

    private static Optional<JobType> wantedStyle(ServerPlayer player) {
        if (prefersTierLook(player)) {
            return Optional.empty();
        }
        return ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(CharacterSnapshot::jobId).map(JobType::fromStored).filter(JobType::isChosen);
    }

    private static void apply(ItemStack stack, Optional<JobType> style) {
        if (!(stack.getItem() instanceof GearArmorItem) || styleOf(stack).equals(style)
                || EquipmentStacks.designOf(stack).isPresent()) {
            return; // named armor keeps the look of its set
        }
        if (style.isPresent()) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putString(TAG, style.get().id());
            tag.putInt("CustomModelData", MODEL_DATA_BASE + style.get().ordinal() - 1);
        } else {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(TAG);
                tag.remove("CustomModelData");
            }
        }
    }
}
