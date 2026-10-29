package com.huntcraft.village.client.render;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.model.HcModelLoader;
import com.huntcraft.client.model.HcModels;
import com.huntcraft.village.client.ClientVillage;
import com.huntcraft.village.client.model.HcEntityModel;
import com.huntcraft.village.companion.MofuData;
import com.huntcraft.village.companion.MofuEntity;
import com.huntcraft.village.companion.MofuGear;
import com.huntcraft.village.companion.MofuType;
import com.huntcraft.village.npc.NpcRole;
import com.huntcraft.village.npc.TrainingDummy;
import com.huntcraft.village.npc.VillageNpc;
import com.huntcraft.village.story.ElderStory;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Entity renderers of the village module. */
@OnlyIn(Dist.CLIENT)
public final class VillageRenderers {
    /** Outfit / accessory bones of the mofu model hidden unless listed in hcShown(). */
    public static final String[] MOFU_TOGGLES = {"hat_chef", "apron", "ladle", "kasa", "beard", "staff", "haori", "scarf"};
    /** Gear bones of the mofu_gear model (drawn per slot with the tier texture). */
    public static final String[] GEAR_TOGGLES = {"w_blade", "w_horn", "w_bomb", "pack", "helm", "armor"};
    private static final ResourceLocation[] MOFU_TEX = new ResourceLocation[MofuData.PATTERNS];
    private static final ResourceLocation[] GEAR_TEX = new ResourceLocation[MofuGear.Tier.values().length];

    static {
        for (int i = 0; i < MOFU_TEX.length; i++) MOFU_TEX[i] = HuntCraft.id("textures/entity/mofu/mofu_" + MofuEntity.PATTERN_NAMES[i] + ".png");
        for (MofuGear.Tier t : MofuGear.Tier.values()) GEAR_TEX[t.ordinal()] = HuntCraft.id("textures/entity/mofu/gear_" + t.id + ".png");
    }

    private VillageRenderers() {
    }

    public static ResourceLocation mofuTexture(int pattern) {
        return MOFU_TEX[Math.floorMod(pattern, MOFU_TEX.length)];
    }

    public static ResourceLocation gearTexture(int tier) {
        return GEAR_TEX[Math.max(0, Math.min(GEAR_TEX.length - 1, tier))];
    }

    /** Floating marker above an NPC ("!" when the local hunter has something to pick up there). */
    static void marker(MarkerAccess r, VillageNpc e, PoseStack ps, MultiBufferSource buffers, int light) {
        Player p = Minecraft.getInstance().player;
        if (p == null || e.distanceToSqr(p) > 32 * 32) return;
        boolean show = switch (e.getRole()) {
            case ELDER -> ElderStory.unclaimed(p) > 0;
            case MOFU_ELDER -> !ClientVillage.village().getBoolean("firstMofu");
            default -> false;
        };
        if (!show) return;
        float bob = (float) Math.sin((e.tickCount + Minecraft.getInstance().getFrameTime()) * 0.15F) * 0.06F;
        ps.pushPose();
        ps.translate(0, 0.38 + bob, 0);
        r.hcMarker(e, Component.literal("!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), ps, buffers, light);
        ps.popPose();
    }

    /** Gives the static marker helper access to the protected renderNameTag. */
    interface MarkerAccess {
        void hcMarker(VillageNpc e, Component text, PoseStack ps, MultiBufferSource buffers, int light);
    }

    // ------------------------------------------------------------------ mofu companion
    public static class Mofu extends MobRenderer<MofuEntity, HcEntityModel<MofuEntity>> {
        public Mofu(EntityRendererProvider.Context ctx) {
            super(ctx, new HcEntityModel<>(ctx.bakeLayer(HcModels.layer("mofu")), HcModelLoader.get("mofu"), MOFU_TOGGLES), 0.35F);
            this.addLayer(new GearLayer(this, new HcEntityModel<>(ctx.bakeLayer(HcModels.layer("mofu_gear")), HcModelLoader.get("mofu_gear"), GEAR_TOGGLES)));
        }

        @Override
        public ResourceLocation getTextureLocation(MofuEntity e) {
            return mofuTexture(e.getPattern());
        }

        @Override
        protected void scale(MofuEntity e, PoseStack ps, float partialTick) {
            float s = this.model.data().renderScale;
            ps.scale(s, s, s);
        }
    }

    /** Draws the companion's equipment: one pass per slot with that slot's tier texture. */
    public static class GearLayer extends RenderLayer<MofuEntity, HcEntityModel<MofuEntity>> {
        private final HcEntityModel<MofuEntity> gear;

        public GearLayer(RenderLayerParent<MofuEntity, HcEntityModel<MofuEntity>> parent, HcEntityModel<MofuEntity> gear) {
            super(parent);
            this.gear = gear;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buffers, int light, MofuEntity e, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            int w = e.getGear(MofuGear.Slot.WEAPON), h = e.getGear(MofuGear.Slot.HELM), a = e.getGear(MofuGear.Slot.ARMOR);
            if (w < 0 && h < 0 && a < 0) return;
            this.getParentModel().copyPropertiesTo(gear);
            gear.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            int overlay = LivingEntityRenderer.getOverlayCoords(e, 0.0F);
            if (w >= 0) {
                MofuType t = e.getMofuType();
                String wb = switch (t) {
                    case FIGHTER -> "w_blade";
                    case HEALER -> "w_horn";
                    case BOMBER -> "w_bomb";
                };
                pass(ps, buffers, light, overlay, t == MofuType.BOMBER ? List.of(wb, "pack") : List.of(wb), w);
            }
            if (h >= 0) pass(ps, buffers, light, overlay, List.of("helm"), h);
            if (a >= 0) pass(ps, buffers, light, overlay, List.of("armor"), a);
        }

        private void pass(PoseStack ps, MultiBufferSource buffers, int light, int overlay, List<String> bones, int tier) {
            gear.showOnly(bones);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(gearTexture(tier)));
            gear.renderToBuffer(ps, vc, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    // ------------------------------------------------------------------ NPCs
    /** Mofu chef / mofu elder NPCs. */
    public static class MofuNpc extends MobRenderer<VillageNpc, HcEntityModel<VillageNpc>> implements MarkerAccess {
        public MofuNpc(EntityRendererProvider.Context ctx) {
            super(ctx, new HcEntityModel<>(ctx.bakeLayer(HcModels.layer("mofu")), HcModelLoader.get("mofu"), MOFU_TOGGLES), 0.35F);
        }

        @Override
        public ResourceLocation getTextureLocation(VillageNpc e) {
            return mofuTexture(e.getRole() == NpcRole.MOFU_ELDER ? 2 : 0);
        }

        @Override
        protected void scale(VillageNpc e, PoseStack ps, float partialTick) {
            float s = this.model.data().renderScale * (e.getRole() == NpcRole.CHEF ? 1.12F : 1.0F);
            ps.scale(s, s, s);
        }

        @Override
        public void render(VillageNpc e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
            super.render(e, yaw, partialTick, ps, buffers, light);
            marker(this, e, ps, buffers, light);
        }

        @Override
        public void hcMarker(VillageNpc e, Component text, PoseStack ps, MultiBufferSource buffers, int light) {
            this.renderNameTag(e, text, ps, buffers, light);
        }
    }

    /** Humanoid NPCs: one hcmodel + texture per role (npc_receptionist, npc_blacksmith, npc_merchant, npc_elder). */
    public static class HumanNpc extends MobRenderer<VillageNpc, HcEntityModel<VillageNpc>> implements MarkerAccess {
        private final Map<NpcRole, HcEntityModel<VillageNpc>> models = new EnumMap<>(NpcRole.class);
        private final Map<NpcRole, ResourceLocation> textures = new EnumMap<>(NpcRole.class);

        public HumanNpc(EntityRendererProvider.Context ctx) {
            super(ctx, new HcEntityModel<>(ctx.bakeLayer(HcModels.layer("npc_receptionist")), HcModelLoader.get("npc_receptionist")), 0.5F);
            for (NpcRole r : NpcRole.values()) {
                if (r.mofu) continue;
                models.put(r, r == NpcRole.RECEPTIONIST ? this.model
                        : new HcEntityModel<>(ctx.bakeLayer(HcModels.layer(r.modelName())), HcModelLoader.get(r.modelName())));
                textures.put(r, HuntCraft.id("textures/entity/npc/" + r.modelName() + ".png"));
            }
        }

        @Override
        public void render(VillageNpc e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
            HcEntityModel<VillageNpc> m = models.get(e.getRole());
            if (m != null) this.model = m;
            super.render(e, yaw, partialTick, ps, buffers, light);
            marker(this, e, ps, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(VillageNpc e) {
            ResourceLocation t = textures.get(e.getRole());
            return t != null ? t : textures.get(NpcRole.RECEPTIONIST);
        }

        @Override
        protected void scale(VillageNpc e, PoseStack ps, float partialTick) {
            float s = this.model.data().renderScale;
            ps.scale(s, s, s);
        }

        @Override
        public void hcMarker(VillageNpc e, Component text, PoseStack ps, MultiBufferSource buffers, int light) {
            this.renderNameTag(e, text, ps, buffers, light);
        }
    }

    // ------------------------------------------------------------------ training dummy
    /** Straw training dummy (always shows its stats name plate). */
    public static class Dummy extends LivingEntityRenderer<TrainingDummy, HcEntityModel<TrainingDummy>> {
        private static final ResourceLocation TEX = HuntCraft.id("textures/entity/training_dummy.png");

        public Dummy(EntityRendererProvider.Context ctx) {
            super(ctx, new HcEntityModel<>(ctx.bakeLayer(HcModels.layer("training_dummy")), HcModelLoader.get("training_dummy")), 0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(TrainingDummy e) {
            return TEX;
        }

        @Override
        protected boolean shouldShowName(TrainingDummy e) {
            return e.hasCustomName();
        }

        @Override
        protected float getFlipDegrees(TrainingDummy e) {
            return 0.0F;
        }
    }
}
