package com.huntcraft.village.client.render;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.model.HcModelLoader;
import com.huntcraft.client.model.HcModels;
import com.huntcraft.village.client.model.HcEntityModel;
import com.huntcraft.village.companion.MofuData;
import com.huntcraft.village.companion.MofuEntity;
import com.huntcraft.village.npc.NpcRole;
import com.huntcraft.village.npc.TrainingDummy;
import com.huntcraft.village.npc.VillageNpc;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Entity renderers of the village module. */
@OnlyIn(Dist.CLIENT)
public final class VillageRenderers {
    /** Accessory bones of the mofu model that are hidden unless an entity lists them in hcShown(). */
    public static final String[] MOFU_TOGGLES = {"hat_chef", "apron", "ladle", "kasa", "beard", "staff", "haori", "w_blade", "w_horn",
            "w_bomb", "pack", "vest", "armor_leather", "armor_iron", "bandana", "helm_iron"};
    private static final ResourceLocation[] MOFU_TEX = new ResourceLocation[MofuData.PATTERNS];

    static {
        for (int i = 0; i < MOFU_TEX.length; i++) MOFU_TEX[i] = HuntCraft.id("textures/entity/mofu_" + MofuEntity.PATTERN_NAMES[i] + ".png");
    }

    private VillageRenderers() {
    }

    public static ResourceLocation mofuTexture(int pattern) {
        return MOFU_TEX[Math.floorMod(pattern, MOFU_TEX.length)];
    }

    /** Mofu companion. */
    public static class Mofu extends MobRenderer<MofuEntity, HcEntityModel<MofuEntity>> {
        public Mofu(EntityRendererProvider.Context ctx) {
            super(ctx, new HcEntityModel<>(ctx.bakeLayer(HcModels.layer("mofu")), HcModelLoader.get("mofu"), MOFU_TOGGLES), 0.35F);
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

    /** Mofu chef / elder NPCs. */
    public static class MofuNpc extends MobRenderer<VillageNpc, HcEntityModel<VillageNpc>> {
        public MofuNpc(EntityRendererProvider.Context ctx) {
            super(ctx, new HcEntityModel<>(ctx.bakeLayer(HcModels.layer("mofu")), HcModelLoader.get("mofu"), MOFU_TOGGLES), 0.35F);
        }

        @Override
        public ResourceLocation getTextureLocation(VillageNpc e) {
            return mofuTexture(e.getRole() == NpcRole.ELDER ? 2 : 0);
        }

        @Override
        protected void scale(VillageNpc e, PoseStack ps, float partialTick) {
            float s = this.model.data().renderScale * (e.getRole() == NpcRole.CHEF ? 1.12F : 1.0F);
            ps.scale(s, s, s);
        }
    }

    /** Humanoid NPCs with 64x64 player-style skins (slim arms for the receptionist). */
    public static class HumanNpc extends HumanoidMobRenderer<VillageNpc, PlayerModel<VillageNpc>> {
        private final PlayerModel<VillageNpc> wide;
        private final PlayerModel<VillageNpc> slim;

        public HumanNpc(EntityRendererProvider.Context ctx) {
            super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
            this.wide = this.model;
            this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }

        @Override
        public void render(VillageNpc e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
            this.model = e.getRole().slim ? slim : wide;
            super.render(e, yaw, partialTick, ps, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(VillageNpc e) {
            return HuntCraft.id("textures/entity/npc/" + e.getRole().id + ".png");
        }

        @Override
        protected void scale(VillageNpc e, PoseStack ps, float partialTick) {
            float s = e.getRole() == NpcRole.BLACKSMITH ? 1.02F : 0.9375F;
            ps.scale(s, s, s);
        }
    }

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
            return e.hasCustomName() && super.shouldShowName(e);
        }

        @Override
        protected float getFlipDegrees(TrainingDummy e) {
            return 0.0F;
        }
    }
}
