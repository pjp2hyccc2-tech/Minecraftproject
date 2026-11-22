package jp.archiquest.client;

import net.minecraft.client.Minecraft;

/** クライアント専用の処理（サーバー側から直接参照しないこと） */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openTextbook() {
        Minecraft.getInstance().setScreen(new TextbookScreen());
    }
}
