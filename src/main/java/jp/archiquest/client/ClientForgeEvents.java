package jp.archiquest.client;

import jp.archiquest.ArchiQuest;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** クライアントのゲームイベント（キー入力） */
@Mod.EventBusSubscriber(modid = ArchiQuest.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (ClientModEvents.OPEN_TEXTBOOK.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new TextbookScreen());
            }
        }
    }
}
