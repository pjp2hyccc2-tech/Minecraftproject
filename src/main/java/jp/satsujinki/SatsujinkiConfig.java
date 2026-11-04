package jp.satsujinki;

import net.minecraftforge.common.ForgeConfigSpec;

/** Common (server) and client options. All getters fall back to defaults if the config is not loaded yet. */
public final class SatsujinkiConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    private static final ForgeConfigSpec.BooleanValue BLOOD_DECALS;
    private static final ForgeConfigSpec.IntValue CORPSE_SECONDS;
    private static final ForgeConfigSpec.BooleanValue STARTER_KIT;
    private static final ForgeConfigSpec.IntValue ROUND_SECONDS;
    private static final ForgeConfigSpec.IntValue STARTING_POINTS;
    private static final ForgeConfigSpec.IntValue MAX_GIBS;

    private static final ForgeConfigSpec.DoubleValue GORE_PARTICLES;
    private static final ForgeConfigSpec.BooleanValue SCREEN_BLOOD;
    private static final ForgeConfigSpec.BooleanValue CAMERA_SHAKE;
    private static final ForgeConfigSpec.BooleanValue SPEECH_BUBBLES;
    private static final ForgeConfigSpec.BooleanValue R18_WARNING;
    private static final ForgeConfigSpec.BooleanValue ROUND_MUSIC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("殺人喜 -SATSUJINKI- gameplay settings").push("gameplay");
        BLOOD_DECALS = b.comment("Leave blood splatter blocks on the ground after kills").define("bloodDecals", true);
        CORPSE_SECONDS = b.comment("How long corpses stay (seconds)").defineInRange("corpseSeconds", 90, 5, 3600);
        STARTER_KIT = b.comment("Give every new host a starter kit on first join").define("starterKit", true);
        ROUND_SECONDS = b.comment("Time limit of one death game round (seconds)").defineInRange("roundSeconds", 300, 30, 3600);
        STARTING_POINTS = b.comment("Joy points a new host starts with").defineInRange("startingPoints", 1500, 0, 10000000);
        MAX_GIBS = b.comment("Maximum flying gore chunks per kill").defineInRange("maxGibsPerKill", 18, 0, 64);
        b.pop();
        COMMON_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.comment("殺人喜 -SATSUJINKI- client settings").push("gore");
        GORE_PARTICLES = c.comment("Blood particle multiplier (0 = none)").defineInRange("goreParticleMultiplier", 1.0D, 0.0D, 3.0D);
        SCREEN_BLOOD = c.comment("Blood splashes on the screen when a kill happens close to you").define("screenBlood", true);
        CAMERA_SHAKE = c.comment("Camera shake on heavy impacts").define("cameraShake", true);
        SPEECH_BUBBLES = c.comment("Show contestant speech bubbles").define("speechBubbles", true);
        R18_WARNING = c.comment("Show the R18 warning screen at startup").define("showR18Warning", true);
        ROUND_MUSIC = c.comment("Play the death game music during rounds").define("roundMusic", true);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private SatsujinkiConfig() {
    }

    private static <T> T get(ForgeConfigSpec.ConfigValue<T> v, T def) {
        try {
            return v.get();
        } catch (Exception e) {
            return def;
        }
    }

    public static boolean bloodDecals() {
        return get(BLOOD_DECALS, true);
    }

    public static int corpseTicks() {
        return get(CORPSE_SECONDS, 90) * 20;
    }

    public static boolean starterKit() {
        return get(STARTER_KIT, true);
    }

    public static int roundTicks() {
        return get(ROUND_SECONDS, 300) * 20;
    }

    public static int startingPoints() {
        return get(STARTING_POINTS, 1500);
    }

    public static int maxGibs() {
        return get(MAX_GIBS, 18);
    }

    public static double goreParticles() {
        return get(GORE_PARTICLES, 1.0D);
    }

    public static boolean screenBlood() {
        return get(SCREEN_BLOOD, true);
    }

    public static boolean cameraShake() {
        return get(CAMERA_SHAKE, true);
    }

    public static boolean speechBubbles() {
        return get(SPEECH_BUBBLES, true);
    }

    public static boolean r18Warning() {
        return get(R18_WARNING, true);
    }

    public static boolean roundMusic() {
        return get(ROUND_MUSIC, true);
    }
}
