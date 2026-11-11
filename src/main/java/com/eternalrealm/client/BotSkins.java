package com.eternalrealm.client;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.testkit.bots.BotIds;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * The look of the test bots (docs/modules/testkit.md §4): each is drawn in its own single colour (a little lighter on
 * top of the head, darker on the legs, two dots for eyes), in the world and in the player list, so a crowd of them is
 * easy to tell from real players and from each other. The skins are painted here at runtime; bots have no skins of
 * their own (no account), and the entry of each bot in the player list is given its painted skin.
 */
public final class BotSkins {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int CHECK_TICKS = 10;
    private static final Map<UUID, PlayerInfo> PAINTED = new HashMap<>();
    private static final Set<Integer> REGISTERED = new HashSet<>();
    private static Field textures;
    private static boolean unavailable;
    private static int clock;

    private BotSkins() {
    }

    /** Client tick: a bot that appeared in the player list gets its skin (once per entry). */
    public static void tick(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            PAINTED.clear();
            return;
        }
        if (unavailable || ++clock % CHECK_TICKS != 0) {
            return;
        }
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            UUID id = info.getProfile().getId();
            OptionalInt number = BotIds.numberOf(id);
            if (number.isEmpty() || PAINTED.get(id) == info) {
                continue;
            }
            Map<MinecraftProfileTexture.Type, ResourceLocation> map = texturesOf(info);
            if (map == null) {
                return;
            }
            map.put(MinecraftProfileTexture.Type.SKIN, skin(minecraft, number.getAsInt()));
            PAINTED.put(id, info);
        }
    }

    /** The entry's texture map (found by its type: the only map it holds). */
    @SuppressWarnings("unchecked")
    private static Map<MinecraftProfileTexture.Type, ResourceLocation> texturesOf(PlayerInfo info) {
        try {
            if (textures == null) {
                for (Field field : PlayerInfo.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(field.getType()) && !Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        textures = field;
                        break;
                    }
                }
                if (textures == null) {
                    throw new NoSuchFieldException("no texture map in the player list entry");
                }
            }
            return (Map<MinecraftProfileTexture.Type, ResourceLocation>) textures.get(info);
        } catch (ReflectiveOperationException | RuntimeException e) {
            unavailable = true;
            LOG.warn("Test bots keep the default skins: {}", e.toString());
            return null;
        }
    }

    private static ResourceLocation skin(Minecraft minecraft, int number) {
        ResourceLocation id = new ResourceLocation(ModInfo.MOD_ID, "test_bot_skin/" + number);
        if (REGISTERED.add(number)) {
            minecraft.getTextureManager().register(id, new DynamicTexture(paint(BotIds.colorOf(number))));
        }
        return id;
    }

    /** A 64x64 skin in one colour (0xRRGGBB): shaded by part, with eyes; the outer layer left clear. */
    static NativeImage paint(int rgb) {
        NativeImage image = new NativeImage(64, 64, true); // zeroed: everything clear to begin with
        fill(image, 0, 0, 32, 16, rgb, 1.0);      // head
        fill(image, 8, 0, 8, 8, rgb, 1.15);       // top of the head
        fill(image, 16, 16, 24, 16, rgb, 0.9);    // body
        fill(image, 40, 16, 16, 16, rgb, 0.95);   // right arm
        fill(image, 32, 48, 16, 16, rgb, 0.95);   // left arm
        fill(image, 0, 16, 16, 16, rgb, 0.75);    // right leg
        fill(image, 16, 48, 16, 16, rgb, 0.75);   // left leg
        int white = abgr(0xF2F2F2);
        int dark = abgr(0x1C1C24);
        image.setPixelRGBA(9, 12, white);
        image.setPixelRGBA(10, 12, dark);
        image.setPixelRGBA(13, 12, dark);
        image.setPixelRGBA(14, 12, white);
        return image;
    }

    private static void fill(NativeImage image, int x, int y, int width, int height, int rgb, double shade) {
        image.fillRect(x, y, width, height, abgr(shaded(rgb, shade)));
    }

    private static int shaded(int rgb, double factor) {
        int r = Math.min(255, (int) Math.round(((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) Math.round(((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) Math.round((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    /** 0xRRGGBB to the image's opaque ABGR. */
    private static int abgr(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }
}
