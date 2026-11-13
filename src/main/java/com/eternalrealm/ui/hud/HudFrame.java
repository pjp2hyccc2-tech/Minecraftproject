package com.eternalrealm.ui.hud;

import com.eternalrealm.client.ClientCharacterState;
import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.client.boss.BossHudOverlay;
import com.eternalrealm.client.map.Minimap;
import com.eternalrealm.client.map.ThreatClient;
import com.eternalrealm.config.ERClientConfig;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.hud.HudLayout;
import com.eternalrealm.ui.dialogue.ClientDialogue;
import com.eternalrealm.ui.map.MinimapOverlay;
import com.eternalrealm.ui.map.ThreatOverlay;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * The HUD layout of the frame being drawn: computed once before the overlays render (from the screen size and what is
 * showing), then every overlay of the game draws inside its rectangle ({@link HudLayout}).
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HudFrame {
    private static HudLayout.Layout layout;

    private HudFrame() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null) {
            layout = HudLayout.compute(input(minecraft, player, event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight()));
        }
    }

    /** This frame's layout (computed on the spot if the overlays draw before the first frame event). */
    public static HudLayout.Layout layout() {
        HudLayout.Layout current = layout;
        if (current == null) {
            Minecraft minecraft = Minecraft.getInstance();
            current = HudLayout.compute(HudLayout.Input.of(minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight()));
        }
        return current;
    }

    /**
     * Whether the game's HUD replaces the vanilla hotbar and bars: always, except in creative and spectator mode (for
     * building and testing) or when the HUD is switched off in the client config.
     */
    public static boolean mmo(Player player) {
        return player != null && !player.isCreative() && !player.isSpectator() && hudEnabled();
    }

    private static boolean hudEnabled() {
        try {
            return ERClientConfig.HUD_ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private static HudLayout.Input input(Minecraft minecraft, LocalPlayer player, int width, int height) {
        ChatComponent chat = minecraft.gui.getChat();
        boolean chatShown = minecraft.options.chatVisibility().get() != ChatVisiblity.HIDDEN;
        double scale = chat.getScale();
        int chatWidth = chatShown ? Mth.ceil(chat.getWidth() * scale) + 8 : 0;
        int chatHeight = chatShown
                ? Mth.ceil(ChatComponent.getHeight(minecraft.options.chatHeightUnfocused().get()) * scale) : 0;
        int[] small = chatShown ? com.eternalrealm.ui.chat.FantasyChat.reserved(minecraft, width) : null;
        if (small != null) { // the small framed chat instead of the vanilla one
            chatWidth = small[0];
            chatHeight = small[1];
        }

        int minimap = Minimap.visible() ? minimapSize() : 0;
        int field = ThreatClient.currentField();
        boolean gauge = field != 0 && ThreatClient.field(field).isPresent();

        int members = 0;
        boolean raid = false;
        Optional<JsonObject> party = ClientUiState.get("party");
        if (party.isPresent() && party.get().has("members") && party.get().get("members").isJsonArray()) {
            members = party.get().getAsJsonArray("members").size();
            raid = party.get().has("raid") && party.get().get("raid").getAsBoolean();
        }
        boolean points = ClientCharacterState.snapshot()
                .map(s -> s.unspentStatPoints() > 0 || s.unspentSkillPoints() > 0).orElse(false);
        boolean air = player.isEyeInFluidType(net.minecraftforge.common.ForgeMod.WATER_TYPE.get())
                || player.getAirSupply() < player.getMaxAirSupply();
        int effects = 0;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.showIcon()) {
                effects++;
            }
        }
        return new HudLayout.Input(width, height, chatWidth, chatHeight, minimap,
                minimap == 0 ? 0 : MinimapOverlay.labelLines(minecraft, player), gauge, ThreatOverlay.resultShowing(),
                BossHudOverlay.active(), BossHudOverlay.rankRows(), members, raid, points, air, effects,
                ClientDialogue.ambientShowing(), minecraft.options.showSubtitles().get());
    }

    private static int minimapSize() {
        try {
            return ERClientConfig.MINIMAP_SIZE.get();
        } catch (IllegalStateException e) {
            return 96;
        }
    }
}
