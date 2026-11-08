package com.eternalrealm.client;

import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.combat.forge.WeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;

/**
 * Twin blades are one item but two swords: whenever a player holds them in the main hand with an empty off hand, the
 * same blade is also drawn in the off hand (third person for every player, first person for the local player), and the
 * off arm holds it ready. The item model shows a single blade in the hands and the crossed pair as the icon
 * ({@code forge:separate_transforms}); combo animations alternate the two blades ({@link WeaponAnimations}).
 */
public final class DualWield {
    private DualWield() {
    }

    /** Main hand: twin blades, off hand: empty. */
    public static boolean wieldsTwinBlades(LivingEntity entity) {
        if (!entity.getOffhandItem().isEmpty()) {
            return false;
        }
        WeaponProfile profile = WeaponItem.profileOf(entity.getMainHandItem());
        return profile != null && profile.type() == WeaponType.DUAL_BLADES;
    }

    // ------------------------------------------------------------------ registration (mod bus)

    /** Adds the off-hand blade layer to both player skins. */
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            EntityRenderer<?> renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer player) {
                player.addLayer(new OffhandBladeLayer(player, event.getContext().getItemInHandRenderer()));
            }
        }
    }

    // ------------------------------------------------------------------ third person (Forge bus)

    /** The arm without an item would hang down: hold the second blade ready instead. */
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!wieldsTwinBlades(event.getEntity())) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        boolean offIsLeft = event.getEntity().getMainArm() == HumanoidArm.RIGHT;
        if (offIsLeft && model.leftArmPose == HumanoidModel.ArmPose.EMPTY) {
            model.leftArmPose = HumanoidModel.ArmPose.ITEM;
        } else if (!offIsLeft && model.rightArmPose == HumanoidModel.ArmPose.EMPTY) {
            model.rightArmPose = HumanoidModel.ArmPose.ITEM;
        }
    }

    /** Draws the main-hand stack a second time in the off hand, exactly like vanilla's item-in-hand layer. */
    static final class OffhandBladeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final ItemInHandRenderer itemInHand;

        OffhandBladeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                          ItemInHandRenderer itemInHand) {
            super(parent);
            this.itemInHand = itemInHand;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                           float headPitch) {
            if (!wieldsTwinBlades(player)) {
                return;
            }
            ItemStack blade = player.getMainHandItem();
            HumanoidArm arm = player.getMainArm().getOpposite();
            boolean left = arm == HumanoidArm.LEFT;
            pose.pushPose();
            getParentModel().translateToHand(arm, pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
            itemInHand.renderItem(player, blade, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, pose, buffer, light);
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ first person (Forge bus)

    /** Vanilla draws nothing for an empty off hand; draw the second blade there. */
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (event.getHand() != InteractionHand.OFF_HAND || player == null || player.isInvisible()
                || !wieldsTwinBlades(player)) {
            return;
        }
        ItemStack blade = player.getMainHandItem();
        HumanoidArm arm = player.getMainArm().getOpposite();
        boolean left = arm == HumanoidArm.LEFT;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        WeaponAnimations.firstPersonOffhand(pose, player, arm, event.getPartialTick(), event.getEquipProgress());
        minecraft.gameRenderer.itemInHandRenderer.renderItem(player, blade,
                left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, left,
                pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }
}
