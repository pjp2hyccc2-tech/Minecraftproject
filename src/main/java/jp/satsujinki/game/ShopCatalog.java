package jp.satsujinki.game;

import jp.satsujinki.registry.ModItems;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/** What the host terminal sells for joy points. Shared by client (display) and server (purchase). */
public final class ShopCatalog {
    public record Entry(String id, Supplier<ItemStack> stack, int price) {
    }

    public static final List<Entry> ENTRIES = List.of(
            new Entry("contestant_ticket", () -> new ItemStack(ModItems.CONTESTANT_TICKET.get(), 5), 150),
            new Entry("spike_bed", () -> new ItemStack(ModItems.SPIKE_BED.get(), 9), 500),
            new Entry("saw", () -> new ItemStack(ModItems.SAW.get()), 1200),
            new Entry("guillotine", () -> new ItemStack(ModItems.GUILLOTINE.get()), 1800),
            new Entry("mixer_kit", () -> new ItemStack(ModItems.MIXER_KIT.get()), 2500),
            new Entry("press_kit", () -> new ItemStack(ModItems.PRESS_KIT.get()), 2500),
            new Entry("arena_kit", () -> new ItemStack(ModItems.ARENA_KIT.get()), 800),
            new Entry("host_hand", () -> new ItemStack(ModItems.HOST_HAND.get()), 300),
            new Entry("host_remote", () -> new ItemStack(ModItems.HOST_REMOTE.get()), 300),
            new Entry("mop", () -> new ItemStack(ModItems.MOP.get()), 80),
            new Entry("execution_tiles", () -> new ItemStack(ModItems.EXECUTION_TILES.get(), 32), 100),
            new Entry("bloody_tiles", () -> new ItemStack(ModItems.BLOODY_TILES.get(), 32), 120));

    private ShopCatalog() {
    }
}
