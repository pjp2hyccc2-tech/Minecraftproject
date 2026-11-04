package jp.satsujinki.client.render;

import jp.satsujinki.Satsujinki;
import jp.satsujinki.game.ContestantType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Texture locations used by the renderers. */
public final class Skins {
    private static final ResourceLocation[] SKINS = new ResourceLocation[ContestantType.skinCount()];
    public static final ResourceLocation[] BLOOD = {
            Satsujinki.id("textures/entity/blood_overlay_1.png"),
            Satsujinki.id("textures/entity/blood_overlay_2.png"),
            Satsujinki.id("textures/entity/blood_overlay_3.png")};
    public static final ResourceLocation GIB_MEAT = Satsujinki.id("textures/entity/gib_meat.png");
    public static final ResourceLocation GIB_ORGAN = Satsujinki.id("textures/entity/gib_organ.png");
    public static final ResourceLocation GIB_BONE = Satsujinki.id("textures/entity/gib_bone.png");
    public static final ResourceLocation MIXER_BLADES = Satsujinki.id("textures/entity/mixer_blades.png");
    public static final ResourceLocation MIXER_BLADES_BLOODY = Satsujinki.id("textures/entity/mixer_blades_bloody.png");
    public static final ResourceLocation MIXER_SOUP = Satsujinki.id("textures/entity/mixer_soup.png");
    public static final ResourceLocation PRESS_PLATE = Satsujinki.id("textures/entity/press_plate.png");
    public static final ResourceLocation PRESS_PLATE_BLOODY = Satsujinki.id("textures/entity/press_plate_bloody.png");
    public static final ResourceLocation PRESS_ROD = Satsujinki.id("textures/entity/press_rod.png");
    public static final ResourceLocation SAW_BLADE = Satsujinki.id("textures/entity/saw_blade.png");
    public static final ResourceLocation SAW_BLADE_BLOODY = Satsujinki.id("textures/entity/saw_blade_bloody.png");
    public static final ResourceLocation GUILLOTINE = Satsujinki.id("textures/entity/guillotine.png");
    public static final ResourceLocation GUILLOTINE_BLOODY = Satsujinki.id("textures/entity/guillotine_bloody.png");

    static {
        for (ContestantType t : ContestantType.values()) {
            for (int v = 0; v < ContestantType.SKINS_PER_TYPE; v++) {
                SKINS[t.ordinal() * ContestantType.SKINS_PER_TYPE + v] = Satsujinki.id("textures/entity/contestant/" + t.id + "_" + v + ".png");
            }
        }
    }

    private Skins() {
    }

    public static ResourceLocation skin(int i) {
        return SKINS[Mth.clamp(i, 0, SKINS.length - 1)];
    }
}
