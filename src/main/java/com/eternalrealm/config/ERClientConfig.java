package com.eternalrealm.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-side preferences ({@code config/eternalrealm-client.toml}). Contains no client-only classes, so it is safe
 * to register from common code.
 */
public final class ERClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue HUD_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MINIMAP_ENABLED;
    public static final ForgeConfigSpec.IntValue MINIMAP_SIZE;
    public static final ForgeConfigSpec.IntValue MINIMAP_ZOOM;
    public static final ForgeConfigSpec.BooleanValue MINIMAP_ROTATE;
    public static final ForgeConfigSpec.BooleanValue MINIMAP_COORDINATES;
    public static final ForgeConfigSpec.BooleanValue DAMAGE_NUMBERS;
    public static final ForgeConfigSpec.BooleanValue COMBAT_HUD;
    public static final ForgeConfigSpec.BooleanValue CAMERA_SHAKE;
    public static final ForgeConfigSpec.BooleanValue WEAPON_ANIMATIONS;
    public static final ForgeConfigSpec.BooleanValue SKILL_EFFECTS;
    public static final ForgeConfigSpec.BooleanValue DIALOGUE_AUTO_ADVANCE;
    public static final ForgeConfigSpec.IntValue DIALOGUE_TEXT_SPEED;
    public static final ForgeConfigSpec.BooleanValue FLAG_ANIMATION;
    public static final ForgeConfigSpec.BooleanValue QUEST_PATH;
    public static final ForgeConfigSpec.BooleanValue FANTASY_CHAT;
    public static final ForgeConfigSpec.IntValue CHAT_LINES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("The game's HUD: player frame, skill bar with quick-use items, bag and equipment icons. It replaces",
                "the vanilla hotbar, hearts, armor and experience bar (false: the vanilla HUD comes back).").push("hud");
        HUD_ENABLED = builder.define("enabled", true);
        builder.pop();
        builder.comment("Minimap in the top right corner (keys: Y shows/hides it, = and - zoom, M opens the world map)")
                .push("minimap");
        MINIMAP_ENABLED = builder.comment("Show the minimap").define("enabled", true);
        MINIMAP_SIZE = builder.comment("Diameter in GUI pixels").defineInRange("size", 96, 64, 192);
        MINIMAP_ZOOM = builder.comment("Zoom step: 0 = closest (32 blocks around you) .. 4 = farthest (128 blocks)")
                .defineInRange("zoom", 2, 0, 4);
        MINIMAP_ROTATE = builder.comment("Turn the map with you (false: north is always up)").define("rotate", false);
        MINIMAP_COORDINATES = builder.comment("Show your coordinates under the minimap").define("coordinates", true);
        builder.pop();
        builder.comment("Combat feedback").push("combat");
        DAMAGE_NUMBERS = builder.comment("Show the damage you deal as floating numbers").define("damageNumbers", true);
        COMBAT_HUD = builder.comment("Show stamina, charge, skill cooldown and defense popups").define("combatHud", true);
        CAMERA_SHAKE = builder.comment("Shake the camera on heavy hits, skills and boss slams").define("cameraShake", true);
        WEAPON_ANIMATIONS = builder.comment("Animate weapons and arms for combo steps, skills and charges (first and third person)")
                .define("weaponAnimations", true);
        SKILL_EFFECTS = builder.comment("Draw the light of job skills and ultimates: cuts, magic circles, blasts, glowing",
                "ground areas and the trails of skill spells and arrows (false: only the particles; always off with a",
                "shader pack)").define("skillEffects", true);
        builder.pop();
        builder.comment("Dialogue box").push("dialogue");
        DIALOGUE_AUTO_ADVANCE = builder.comment("Lines in the box above the hotbar (areas, bosses, news) advance by themselves "
                        + "after a reading pause. Conversations with NPCs always wait for you.")
                .define("autoAdvanceAmbient", false);
        DIALOGUE_TEXT_SPEED = builder.comment("Characters per second while a line is being written out (0 = show at once)")
                .defineInRange("textSpeed", 40, 0, 400);
        builder.pop();
        builder.comment("Scenery").push("scenery");
        FLAG_ANIMATION = builder.comment("Flags, banners and bunting wave in the wind (false: they hang still in a light breeze,",
                "which is lighter in the capital with its hundreds of flags)").define("flagAnimation", true);
        builder.pop();
        builder.comment("Quest guide").push("guide");
        QUEST_PATH = builder.comment("Yellow dots on the ground lead to where the guided quest wants you next (pick the quest",
                "with the 案内 button in the journal)").define("questPath", true);
        builder.pop();
        builder.comment("Chat").push("chat");
        FANTASY_CHAT = builder.comment("A small gold-framed chat bottom left that fades when quiet; while typing, the vanilla chat",
                "in the same frame (false: the vanilla chat)").define("fantasyChat", true);
        CHAT_LINES = builder.comment("Lines the small chat shows while you are not typing").defineInRange("lines", 5, 2, 10);
        builder.pop();
        SPEC = builder.build();
    }

    private ERClientConfig() {
    }
}
