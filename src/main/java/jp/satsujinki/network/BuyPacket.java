package jp.satsujinki.network;

import jp.satsujinki.game.ScoreManager;
import jp.satsujinki.game.ShopCatalog;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: buy a shop entry with joy points. */
public final class BuyPacket {
    public final int index;

    public BuyPacket(int index) {
        this.index = index;
    }

    public static void encode(BuyPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.index);
    }

    public static BuyPacket decode(FriendlyByteBuf buf) {
        return new BuyPacket(buf.readVarInt());
    }

    public static void handle(BuyPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (sp == null || p.index < 0 || p.index >= ShopCatalog.ENTRIES.size()) return;
        ShopCatalog.Entry e = ShopCatalog.ENTRIES.get(p.index);
        if (!ScoreManager.spend(sp, e.price())) {
            sp.displayClientMessage(Component.translatable("satsujinki.shop.poor").withStyle(ChatFormatting.RED), true);
            sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.MASTER, 0.8F, 1.0F);
            return;
        }
        ItemStack st = e.stack().get();
        Component name = st.getHoverName();
        if (!sp.getInventory().add(st)) sp.drop(st, false);
        sp.displayClientMessage(Component.translatable("satsujinki.shop.bought", name, e.price()).withStyle(ChatFormatting.GOLD), true);
        sp.playNotifySound(ModSounds.UI_BUY.get(), SoundSource.MASTER, 1.0F, 1.0F);
    }
}
