package jp.archiquest.client;

import com.mojang.blaze3d.platform.InputConstants;
import jp.archiquest.ArchiQuest;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** クライアントのMODバスイベント（キー登録） */
@Mod.EventBusSubscriber(modid = ArchiQuest.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    /** 教科書を開くキー（初期設定: K） */
    public static final KeyMapping OPEN_TEXTBOOK = new KeyMapping("key.archiquest.textbook",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.archiquest");

    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TEXTBOOK);
    }
}
