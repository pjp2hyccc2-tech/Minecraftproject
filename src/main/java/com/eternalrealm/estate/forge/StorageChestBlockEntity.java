package com.eternalrealm.estate.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The 27 slots of a {@link StorageChestBlock}. Its screen is a plain chest screen whose title names the label
 * ("収納箱 — 薬"); opening and closing sound like a chest.
 */
public class StorageChestBlockEntity extends RandomizableContainerBlockEntity {
    public static final int SIZE = 27;
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            sound(level, pos, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            sound(level, pos, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int before, int after) {
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            if (player.containerMenu instanceof ChestMenu menu) {
                Container container = menu.getContainer();
                return container == StorageChestBlockEntity.this;
            }
            return false;
        }
    };

    public StorageChestBlockEntity(BlockPos pos, BlockState state) {
        super(EstateBlocks.STORAGE_CHEST_ENTITY.get(), pos, state);
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    /** "収納箱", or "収納箱 — 薬" with a label. */
    @Override
    protected Component getDefaultName() {
        ChestLabel label = getBlockState().hasProperty(StorageChestBlock.LABEL)
                ? getBlockState().getValue(StorageChestBlock.LABEL) : ChestLabel.NONE;
        String base = "container." + ModInfo.MOD_ID + ".storage_chest";
        return label == ChestLabel.NONE ? Component.translatable(base)
                : Component.translatable(base + ".labelled", Component.translatable(label.label().translationKey()));
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, items);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items);
        }
    }

    @Override
    public void startOpen(Player player) {
        if (!remove && !player.isSpectator() && level != null) {
            openers.incrementOpeners(player, level, getBlockPos(), getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!remove && !player.isSpectator() && level != null) {
            openers.decrementOpeners(player, level, getBlockPos(), getBlockState());
        }
    }

    /** Scheduled tick of the block: forgets players who left without closing. */
    void recheckOpen() {
        if (!remove && level != null) {
            openers.recheckOpeners(level, getBlockPos(), getBlockState());
        }
    }

    private static void sound(Level level, BlockPos pos, boolean open) {
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.45F,
                level.random.nextFloat() * 0.1F + 0.95F);
    }
}
