package com.huntcraft.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.huntcraft.HuntCraft;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads HuntCraft model JSON ("hcmodel") from assets/huntcraft/hcmodels/NAME.json.
 * The JSON is produced by tools/hcmodel (Python) and contains geometry (vanilla box-UV cubes),
 * keyframe animations (vanilla AnimationDefinition semantics) and render config.
 *
 * <p>Animation values: rotation in degrees (added to the bone rotation), position in model pixels
 * (model space, +Y = down, added to the bone offset), scale as a multiplier (1 = unchanged).
 */
@OnlyIn(Dist.CLIENT)
public final class HcModelLoader {
    private static final float DEG = (float) (Math.PI / 180.0);
    private static final Map<String, HcModelData> CACHE = new ConcurrentHashMap<>();

    private HcModelLoader() {
    }

    public record HeadBone(String name, float weight) {
    }

    /** Parsed model. */
    public static final class HcModelData {
        public final String name;
        public final LayerDefinition layer;
        public final Map<String, AnimationDefinition> animations;
        public final List<HeadBone> headBones;
        public final float walkSpeed;
        public final float runSpeed;
        public final float walkScale;
        public final float renderScale;
        public final float actionSpeed;

        HcModelData(String name, LayerDefinition layer, Map<String, AnimationDefinition> animations, List<HeadBone> headBones,
                    float walkSpeed, float runSpeed, float walkScale, float renderScale, float actionSpeed) {
            this.name = name;
            this.layer = layer;
            this.animations = animations;
            this.headBones = headBones;
            this.walkSpeed = walkSpeed;
            this.runSpeed = runSpeed;
            this.walkScale = walkScale;
            this.renderScale = renderScale;
            this.actionSpeed = actionSpeed;
        }
    }

    public static HcModelData get(String name) {
        return CACHE.computeIfAbsent(name, HcModelLoader::load);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static HcModelData load(String name) {
        String path = "/assets/" + HuntCraft.MODID + "/hcmodels/" + name + ".json";
        try (InputStream in = HcModelLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                HuntCraft.LOGGER.error("Missing hcmodel {}", path);
                return fallback(name);
            }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                return parse(name, json);
            }
        } catch (Exception e) {
            HuntCraft.LOGGER.error("Failed to load hcmodel {}", name, e);
            return fallback(name);
        }
    }

    private static HcModelData fallback(String name) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-8, -16, -8, 16, 16, 16), PartPose.offset(0, 24, 0));
        return new HcModelData(name, LayerDefinition.create(mesh, 64, 32), Collections.emptyMap(), Collections.emptyList(), 1, 1, 1, 1, 1);
    }

    private static HcModelData parse(String name, JsonObject json) {
        int tw = json.get("texture_width").getAsInt();
        int th = json.get("texture_height").getAsInt();
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (JsonElement p : json.getAsJsonArray("parts")) addPart(root, p.getAsJsonObject());
        LayerDefinition layer = LayerDefinition.create(mesh, tw, th);

        Map<String, AnimationDefinition> anims = new HashMap<>();
        if (json.has("animations")) {
            JsonObject ao = json.getAsJsonObject("animations");
            for (Map.Entry<String, JsonElement> e : ao.entrySet()) {
                try {
                    anims.put(e.getKey(), parseAnimation(e.getValue().getAsJsonObject()));
                } catch (Exception ex) {
                    HuntCraft.LOGGER.error("Bad animation {} in {}", e.getKey(), name, ex);
                }
            }
        }
        List<HeadBone> heads = new ArrayList<>();
        float walkSpeed = 1, runSpeed = 1.6F, walkScale = 2.0F, renderScale = 1, actionSpeed = 1;
        if (json.has("config")) {
            JsonObject c = json.getAsJsonObject("config");
            if (c.has("head_bones")) {
                for (JsonElement h : c.getAsJsonArray("head_bones")) {
                    JsonArray a = h.getAsJsonArray();
                    heads.add(new HeadBone(a.get(0).getAsString(), a.get(1).getAsFloat()));
                }
            }
            if (c.has("walk_speed")) walkSpeed = c.get("walk_speed").getAsFloat();
            if (c.has("run_speed")) runSpeed = c.get("run_speed").getAsFloat();
            if (c.has("walk_scale")) walkScale = c.get("walk_scale").getAsFloat();
            if (c.has("render_scale")) renderScale = c.get("render_scale").getAsFloat();
            if (c.has("action_speed")) actionSpeed = c.get("action_speed").getAsFloat();
        }
        return new HcModelData(name, layer, anims, heads, walkSpeed, runSpeed, walkScale, renderScale, actionSpeed);
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    private static void addPart(PartDefinition parent, JsonObject o) {
        String name = o.get("name").getAsString();
        float[] pivot = o.has("pivot") ? floats(o.getAsJsonArray("pivot")) : new float[3];
        float[] rot = o.has("rotation") ? floats(o.getAsJsonArray("rotation")) : new float[3];
        CubeListBuilder cb = CubeListBuilder.create();
        if (o.has("cubes")) {
            for (JsonElement ce : o.getAsJsonArray("cubes")) {
                JsonObject c = ce.getAsJsonObject();
                float[] uv = floats(c.getAsJsonArray("uv"));
                float[] origin = floats(c.getAsJsonArray("origin"));
                float[] size = floats(c.getAsJsonArray("size"));
                float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0F;
                boolean mirror = c.has("mirror") && c.get("mirror").getAsBoolean();
                cb.texOffs((int) uv[0], (int) uv[1]).mirror(mirror)
                        .addBox(origin[0], origin[1], origin[2], size[0], size[1], size[2], new CubeDeformation(inflate));
            }
        }
        PartDefinition pd = parent.addOrReplaceChild(name, cb,
                PartPose.offsetAndRotation(pivot[0], pivot[1], pivot[2], rot[0] * DEG, rot[1] * DEG, rot[2] * DEG));
        if (o.has("children")) {
            for (JsonElement ch : o.getAsJsonArray("children")) addPart(pd, ch.getAsJsonObject());
        }
    }

    private static AnimationDefinition parseAnimation(JsonObject o) {
        float length = o.get("length").getAsFloat();
        AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(length);
        if (o.has("loop") && o.get("loop").getAsBoolean()) b.looping();
        JsonObject bones = o.getAsJsonObject("bones");
        for (Map.Entry<String, JsonElement> be : bones.entrySet()) {
            JsonObject ch = be.getValue().getAsJsonObject();
            for (String chan : new String[]{"rotation", "position", "scale"}) {
                if (!ch.has(chan)) continue;
                JsonArray keys = ch.getAsJsonArray(chan);
                Keyframe[] kfs = new Keyframe[keys.size()];
                for (int i = 0; i < kfs.length; i++) {
                    JsonArray k = keys.get(i).getAsJsonArray();
                    float t = k.get(0).getAsFloat();
                    float[] v = floats(k.get(1).getAsJsonArray());
                    boolean linear = k.size() > 2 && "l".equals(k.get(2).getAsString());
                    Vector3f vec = switch (chan) {
                        case "rotation" -> new Vector3f(v[0] * DEG, v[1] * DEG, v[2] * DEG);
                        case "position" -> new Vector3f(v[0], v[1], v[2]);
                        default -> new Vector3f(v[0] - 1.0F, v[1] - 1.0F, v[2] - 1.0F);
                    };
                    kfs[i] = new Keyframe(t, vec, linear ? AnimationChannel.Interpolations.LINEAR : AnimationChannel.Interpolations.CATMULLROM);
                }
                AnimationChannel.Target target = switch (chan) {
                    case "rotation" -> AnimationChannel.Targets.ROTATION;
                    case "position" -> AnimationChannel.Targets.POSITION;
                    default -> AnimationChannel.Targets.SCALE;
                };
                b.addAnimation(be.getKey(), new AnimationChannel(target, kfs));
            }
        }
        return b.build();
    }
}
