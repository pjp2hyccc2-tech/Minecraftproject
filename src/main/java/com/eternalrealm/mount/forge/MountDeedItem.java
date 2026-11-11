package com.eternalrealm.mount.forge;

import com.eternalrealm.mount.MountIndividual;
import com.eternalrealm.mount.MountLooks;
import com.eternalrealm.mount.MountRules;
import com.eternalrealm.mount.MountTraits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * The deed of a kept horse or griffin ({@link MountDeeds}): named after the mount's kind, its grade on the seal and
 * in the name's colour, its look and values in the tooltip; an SS deed shimmers. Taken into a stable at the
 * stablemaster.
 */
public final class MountDeedItem extends Item {
    public MountDeedItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Optional<MountIndividual> mount = MountDeeds.read(stack);
        if (mount.isEmpty()) {
            return super.getName(stack);
        }
        MountTraits.Grade grade = mount.get().grade();
        return Component.translatable(MountRules.DEED_NAME, Component.translatable(mount.get().kind().translationKey()),
                Component.translatable(grade.translationKey())).withStyle(style -> style.withColor(
                TextColor.fromRgb(grade.color())));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return MountDeeds.read(stack).map(mount -> mount.grade() == MountTraits.Grade.SS).orElse(false);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        Optional<MountIndividual> read = MountDeeds.read(stack);
        if (read.isEmpty()) {
            lines.add(Component.translatable(MountRules.DEED_BROKEN).withStyle(ChatFormatting.RED));
            return;
        }
        MountIndividual mount = read.get();
        lines.add(Component.translatable(MountRules.DEED_LOOK,
                Component.translatable(MountLooks.coatKey(mount.kind(), mount.look().coat())),
                Component.translatable(MountLooks.shapeKey(mount.kind(), mount.look().shape())))
                .withStyle(MountLooks.rare(mount.kind(), mount.look()) ? ChatFormatting.LIGHT_PURPLE
                        : ChatFormatting.GRAY));
        lines.add(Component.translatable(MountRules.DEED_VALUES, mount.traits().speed(),
                mount.traits().acceleration(), Component.translatable(mount.kind().flies() ? MountRules.SCREEN_FLIGHT
                        : MountRules.SCREEN_JUMP), mount.traits().lift()).withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable(MountRules.DEED_GRADE, Component.translatable(mount.grade().translationKey()),
                mount.traits().total()).withStyle(style -> style.withColor(TextColor.fromRgb(mount.grade().color()))));
        lines.add(Component.translatable(MountRules.DEED_LEVEL, mount.kind().requiredLevel())
                .withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable(MountRules.DEED_USE).withStyle(ChatFormatting.DARK_GRAY));
    }
}
