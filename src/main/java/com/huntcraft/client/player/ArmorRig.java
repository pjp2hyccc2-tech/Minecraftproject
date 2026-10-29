package com.huntcraft.client.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.huntcraft.HuntCraft;
import com.huntcraft.gear.armor.HCArmorItem;
import com.huntcraft.gear.armor.client.ArmorGeometry;
import com.huntcraft.gear.armor.client.ArmorModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeHooksClient;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Armor on the hunter rig. Humanoid armor geometry (ArmorGeometry JSON for HuntCraft sets, the vanilla humanoid
 * armor boxes for everything else) is re-parented onto the rig bones: body cubes are split at the waist, arm and leg
 * cubes at the elbow / knee (box UV split: the lower half uses the texture rows below the upper half), so armor bends
 * with the limbs. Each rig bone gets one baked ModelPart whose cubes are relative to the bone pivot.
 */
@OnlyIn(Dist.CLIENT)
public final class ArmorRig {
    private static final float DEG = (float) (Math.PI / 180.0);
    private static final Map<String, Optional<ArmorRig>> CACHE = new HashMap<>();
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    /** Vanilla humanoid armor boxes (64x32 layout) in the ArmorGeometry JSON format. */
    private static String vanillaJson(float inflate) {
        float hat = inflate + 0.5F;
        return String.format(Locale.ROOT, "{\"texture_width\":64,\"texture_height\":32,\"parts\":["
                        + "{\"name\":\"head\",\"pivot\":[0,0,0],\"cubes\":[{\"uv\":[0,0],\"origin\":[-4,-8,-4],\"size\":[8,8,8],\"inflate\":%1$s}]},"
                        + "{\"name\":\"hat\",\"pivot\":[0,0,0],\"cubes\":[{\"uv\":[32,0],\"origin\":[-4,-8,-4],\"size\":[8,8,8],\"inflate\":%2$s}]},"
                        + "{\"name\":\"body\",\"pivot\":[0,0,0],\"cubes\":[{\"uv\":[16,16],\"origin\":[-4,0,-2],\"size\":[8,12,4],\"inflate\":%1$s}]},"
                        + "{\"name\":\"right_arm\",\"pivot\":[-5,2,0],\"cubes\":[{\"uv\":[40,16],\"origin\":[-3,-2,-2],\"size\":[4,12,4],\"inflate\":%1$s}]},"
                        + "{\"name\":\"left_arm\",\"pivot\":[5,2,0],\"cubes\":[{\"uv\":[40,16],\"origin\":[-1,-2,-2],\"size\":[4,12,4],\"inflate\":%1$s,\"mirror\":true}]},"
                        + "{\"name\":\"right_leg\",\"pivot\":[-1.9,12,0],\"cubes\":[{\"uv\":[0,16],\"origin\":[-2,0,-2],\"size\":[4,12,4],\"inflate\":%1$s}]},"
                        + "{\"name\":\"left_leg\",\"pivot\":[1.9,12,0],\"cubes\":[{\"uv\":[0,16],\"origin\":[-2,0,-2],\"size\":[4,12,4],\"inflate\":%1$s,\"mirror\":true}]}]}",
                inflate, hat);
    }

    /** Vanilla pivot of each humanoid part and the rig bones (upper, lower, split y relative to the vanilla pivot). */
    private record Mapping(String upper, @Nullable String lower, float splitY, float[] vanillaPivot, float[] upperPivot, float[] lowerPivot) {
    }

    private static final Map<String, Mapping> MAP = new HashMap<>();

    static {
        MAP.put("head", new Mapping("head", null, 0, new float[]{0, 0, 0}, new float[]{0, 0, 0}, null));
        MAP.put("hat", new Mapping("head", null, 0, new float[]{0, 0, 0}, new float[]{0, 0, 0}, null));
        MAP.put("body", new Mapping("chest", "hips", 7, new float[]{0, 0, 0}, new float[]{0, 7, 0}, new float[]{0, 12, 0}));
        MAP.put("right_arm", new Mapping("arm_r", "arm_r_lower", 4, new float[]{-5, 2, 0}, new float[]{-5, 2, 0}, new float[]{-5, 6, 0}));
        MAP.put("left_arm", new Mapping("arm_l", "arm_l_lower", 4, new float[]{5, 2, 0}, new float[]{5, 2, 0}, new float[]{5, 6, 0}));
        MAP.put("right_leg", new Mapping("leg_r", "leg_r_lower", 6, new float[]{-1.9F, 12, 0}, new float[]{-1.9F, 12, 0}, new float[]{-1.9F, 18, 0}));
        MAP.put("left_leg", new Mapping("leg_l", "leg_l_lower", 6, new float[]{1.9F, 12, 0}, new float[]{1.9F, 12, 0}, new float[]{1.9F, 18, 0}));
    }

    /** Which humanoid parts a slot shows (vanilla HumanoidArmorLayer.setPartVisibility). */
    private static boolean slotShows(EquipmentSlot slot, String humanoidPart) {
        return switch (slot) {
            case HEAD -> humanoidPart.equals("head") || humanoidPart.equals("hat");
            case CHEST -> humanoidPart.equals("body") || humanoidPart.equals("right_arm") || humanoidPart.equals("left_arm");
            case LEGS -> humanoidPart.equals("body") || humanoidPart.equals("right_leg") || humanoidPart.equals("left_leg");
            case FEET -> humanoidPart.equals("right_leg") || humanoidPart.equals("left_leg");
            default -> false;
        };
    }

    /** rig bone -> {humanoid part -> baked part} */
    private final Map<String, Map<String, ModelPart>> parts = new HashMap<>();

    private ArmorRig() {
    }

    // ================================================================== building
    @Nullable
    public static ArmorRig get(String key) {
        return CACHE.computeIfAbsent(key, k -> {
            String json;
            if (k.startsWith("vanilla_")) json = vanillaJson(k.endsWith("inner") ? 0.5F : 1.0F);
            else json = ArmorGeometry.get(k);
            if (json == null) return Optional.empty();
            try {
                return Optional.of(build(JsonParser.parseString(json).getAsJsonObject()));
            } catch (Exception e) {
                HuntCraft.LOGGER.error("Failed to rig armor {}", k, e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    public static void clearCache() {
        CACHE.clear();
        TEXTURES.clear();
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    private static ArmorRig build(JsonObject o) {
        int tw = o.get("texture_width").getAsInt();
        int th = o.get("texture_height").getAsInt();
        ArmorRig rig = new ArmorRig();
        // one mesh per (bone, humanoid part)
        Map<String, Map<String, MeshDefinition>> meshes = new HashMap<>();
        for (JsonElement pe : o.getAsJsonArray("parts")) {
            JsonObject p = pe.getAsJsonObject();
            String hp = p.get("name").getAsString();
            Mapping m = MAP.get(hp);
            if (m == null) continue;
            float[] pivot = p.has("pivot") ? floats(p.getAsJsonArray("pivot")) : m.vanillaPivot;
            CubeListBuilder upper = CubeListBuilder.create();
            CubeListBuilder lower = CubeListBuilder.create();
            boolean anyUpper = false, anyLower = false;
            if (p.has("cubes")) {
                for (JsonElement ce : p.getAsJsonArray("cubes")) {
                    JsonObject c = ce.getAsJsonObject();
                    float[] uv = floats(c.getAsJsonArray("uv"));
                    float[] origin = floats(c.getAsJsonArray("origin"));
                    float[] size = floats(c.getAsJsonArray("size"));
                    float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0F;
                    boolean mirror = c.has("mirror") && c.get("mirror").getAsBoolean();
                    // absolute (model) coordinates
                    float ay0 = pivot[1] + origin[1];
                    float ay1 = ay0 + size[1];
                    float split = m.lower == null ? Float.MAX_VALUE : m.vanillaPivot[1] + m.splitY;
                    if (m.lower == null || ay1 <= split + 0.01F) {
                        addCube(upper, c, uv, origin, size, inflate, mirror, pivot, m.upperPivot, 0, size[1]);
                        anyUpper = true;
                    } else if (ay0 >= split - 0.01F) {
                        addCube(lower, c, uv, origin, size, inflate, mirror, pivot, m.lowerPivot, 0, size[1]);
                        anyLower = true;
                    } else {
                        float h1 = Math.round(split - ay0);
                        if (h1 > 0 && h1 < size[1]) {
                            addCube(upper, c, uv, origin, size, inflate, mirror, pivot, m.upperPivot, 0, h1);
                            addCube(lower, c, uv, origin, size, inflate, mirror, pivot, m.lowerPivot, h1, size[1] - h1);
                            anyUpper = anyLower = true;
                        } else {
                            addCube(upper, c, uv, origin, size, inflate, mirror, pivot, m.upperPivot, 0, size[1]);
                            anyUpper = true;
                        }
                    }
                }
            }
            MeshDefinition um = meshes.computeIfAbsent(m.upper, k -> new HashMap<>()).computeIfAbsent(hp, k -> new MeshDefinition());
            PartDefinition up = um.getRoot().addOrReplaceChild("main", upper, PartPose.ZERO);
            PartDefinition lp = null;
            if (m.lower != null) {
                MeshDefinition lm = meshes.computeIfAbsent(m.lower, k -> new HashMap<>()).computeIfAbsent(hp, k -> new MeshDefinition());
                lp = lm.getRoot().addOrReplaceChild("main", lower, PartPose.ZERO);
            }
            if (p.has("children")) {
                for (JsonElement ch : p.getAsJsonArray("children")) {
                    JsonObject co = ch.getAsJsonObject();
                    float[] cp = co.has("pivot") ? floats(co.getAsJsonArray("pivot")) : new float[3];
                    boolean toLower = lp != null && pivot[1] + cp[1] >= m.vanillaPivot[1] + m.splitY - 0.01F;
                    float[] bp = toLower ? m.lowerPivot : m.upperPivot;
                    addChild(toLower ? lp : up, co, new float[]{pivot[0] - bp[0], pivot[1] - bp[1], pivot[2] - bp[2]});
                }
            }
        }
        for (Map.Entry<String, Map<String, MeshDefinition>> be : meshes.entrySet()) {
            Map<String, ModelPart> byPart = new HashMap<>();
            for (Map.Entry<String, MeshDefinition> me : be.getValue().entrySet()) {
                byPart.put(me.getKey(), LayerDefinition.create(me.getValue(), tw, th).bakeRoot());
            }
            rig.parts.put(be.getKey(), byPart);
        }
        return rig;
    }

    /** Adds the vertical slice [yFrom, yFrom + h) of a cube, re-based on the rig bone pivot. */
    private static void addCube(CubeListBuilder cb, JsonObject c, float[] uv, float[] origin, float[] size, float inflate, boolean mirror,
                                float[] partPivot, float[] bonePivot, float yFrom, float h) {
        float ox = origin[0] + partPivot[0] - bonePivot[0];
        float oy = origin[1] + yFrom + partPivot[1] - bonePivot[1];
        float oz = origin[2] + partPivot[2] - bonePivot[2];
        cb.texOffs((int) uv[0], (int) (uv[1] + yFrom)).mirror(mirror).addBox(ox, oy, oz, size[0], h, size[2], new CubeDeformation(inflate));
    }

    private static void addChild(PartDefinition parent, JsonObject o, float[] shift) {
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
                cb.texOffs((int) uv[0], (int) uv[1]).mirror(mirror).addBox(origin[0], origin[1], origin[2], size[0], size[1], size[2], new CubeDeformation(inflate));
            }
        }
        PartDefinition pd = parent.addOrReplaceChild(name, cb,
                PartPose.offsetAndRotation(pivot[0] + shift[0], pivot[1] + shift[1], pivot[2] + shift[2], rot[0] * DEG, rot[1] * DEG, rot[2] * DEG));
        if (o.has("children")) {
            for (JsonElement ch : o.getAsJsonArray("children")) addChild(pd, ch.getAsJsonObject(), new float[3]);
        }
    }

    // ================================================================== rendering
    /** Renders every equipped armor piece on the posed rig (call with the pose stack at the model root). */
    public static void render(LivingEntity entity, HunterModel model, PoseStack ps, MultiBufferSource buffers, int light, float ageInTicks) {
        for (EquipmentSlot slot : SLOTS) {
            ItemStack st = entity.getItemBySlot(slot);
            if (!(st.getItem() instanceof ArmorItem ai) || ai.getEquipmentSlot() != slot) continue;
            boolean inner = slot == EquipmentSlot.LEGS;
            ArmorRig rig;
            ResourceLocation glow = null;
            if (ai instanceof HCArmorItem hc) {
                rig = get(hc.set.id + (inner ? "_inner" : "_outer"));
                glow = ArmorModels.glowTexture(hc.set.id, inner);
            } else {
                rig = get(inner ? "vanilla_inner" : "vanilla_outer");
            }
            if (rig == null) continue;
            ResourceLocation tex = texture(entity, st, slot, inner, null);
            float r = 1, g = 1, b = 1;
            if (ai instanceof DyeableLeatherItem dye) {
                int c = dye.getColor(st);
                r = ((c >> 16) & 255) / 255F;
                g = ((c >> 8) & 255) / 255F;
                b = (c & 255) / 255F;
            }
            VertexConsumer vc = buffers.getBuffer(RenderType.armorCutoutNoCull(tex));
            rig.renderSlot(model, slot, ps, vc, light, OverlayTexture.NO_OVERLAY, r, g, b);
            if (ai instanceof DyeableLeatherItem) {
                VertexConsumer ov = buffers.getBuffer(RenderType.armorCutoutNoCull(texture(entity, st, slot, inner, "overlay")));
                rig.renderSlot(model, slot, ps, ov, light, OverlayTexture.NO_OVERLAY, 1, 1, 1);
            }
            if (glow != null) {
                float pulse = 0.82F + 0.18F * (float) Math.sin(ageInTicks * 0.12F);
                rig.renderSlot(model, slot, ps, buffers.getBuffer(RenderType.eyes(glow)), 0xF000F0, OverlayTexture.NO_OVERLAY, pulse, pulse, pulse);
            }
        }
    }

    /** First person: only the arm pieces of the chest armor, inside the chest bone frame. */
    public static void renderArms(LivingEntity entity, HunterModel model, PoseStack ps, MultiBufferSource buffers, int light, float ageInTicks) {
        ItemStack st = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (!(st.getItem() instanceof ArmorItem ai) || ai.getEquipmentSlot() != EquipmentSlot.CHEST) return;
        ArmorRig rig = ai instanceof HCArmorItem hc ? get(hc.set.id + "_outer") : get("vanilla_outer");
        if (rig == null) return;
        float r = 1, g = 1, b = 1;
        if (ai instanceof DyeableLeatherItem dye) {
            int c = dye.getColor(st);
            r = ((c >> 16) & 255) / 255F;
            g = ((c >> 8) & 255) / 255F;
            b = (c & 255) / 255F;
        }
        ps.pushPose();
        model.translateTo("chest", ps);
        VertexConsumer vc = buffers.getBuffer(RenderType.armorCutoutNoCull(texture(entity, st, EquipmentSlot.CHEST, false, null)));
        rig.renderBone(model, "arm_r", EquipmentSlot.CHEST, ps, vc, light, OverlayTexture.NO_OVERLAY, r, g, b);
        rig.renderBone(model, "arm_l", EquipmentSlot.CHEST, ps, vc, light, OverlayTexture.NO_OVERLAY, r, g, b);
        ResourceLocation glow = ai instanceof HCArmorItem hc ? ArmorModels.glowTexture(hc.set.id, false) : null;
        if (glow != null) {
            float pulse = 0.82F + 0.18F * (float) Math.sin(ageInTicks * 0.12F);
            VertexConsumer gv = buffers.getBuffer(RenderType.eyes(glow));
            rig.renderBone(model, "arm_r", EquipmentSlot.CHEST, ps, gv, 0xF000F0, OverlayTexture.NO_OVERLAY, pulse, pulse, pulse);
            rig.renderBone(model, "arm_l", EquipmentSlot.CHEST, ps, gv, 0xF000F0, OverlayTexture.NO_OVERLAY, pulse, pulse, pulse);
        }
        ps.popPose();
    }

    private static ResourceLocation texture(LivingEntity e, ItemStack st, EquipmentSlot slot, boolean inner, @Nullable String type) {
        ArmorItem item = (ArmorItem) st.getItem();
        String name = item.getMaterial().getName();
        String domain = "minecraft";
        int idx = name.indexOf(':');
        if (idx != -1) {
            domain = name.substring(0, idx);
            name = name.substring(idx + 1);
        }
        String s = String.format(Locale.ROOT, "%s:textures/models/armor/%s_layer_%d%s.png", domain, name, inner ? 2 : 1, type == null ? "" : "_" + type);
        s = ForgeHooksClient.getArmorTexture(e, st, s, slot, type);
        return TEXTURES.computeIfAbsent(s, ResourceLocation::new);
    }

    private void renderSlot(HunterModel model, EquipmentSlot slot, PoseStack ps, VertexConsumer vc, int light, int overlay, float r, float g, float b) {
        renderBone(model, "root", slot, ps, vc, light, overlay, r, g, b);
    }

    private void renderBone(HunterModel model, String bone, EquipmentSlot slot, PoseStack ps, VertexConsumer vc, int light, int overlay, float r, float g, float b) {
        ModelPart rigPart = model.bone(bone);
        if (rigPart == null) return;
        ps.pushPose();
        rigPart.translateAndRotate(ps);
        Map<String, ModelPart> byPart = parts.get(bone);
        if (byPart != null) {
            for (Map.Entry<String, ModelPart> e : byPart.entrySet()) {
                if (slotShows(slot, e.getKey())) e.getValue().render(ps, vc, light, overlay, r, g, b, 1.0F);
            }
        }
        for (String child : HunterModel.CHILDREN.get(bone)) renderBone(model, child, slot, ps, vc, light, overlay, r, g, b);
        ps.popPose();
    }
}
