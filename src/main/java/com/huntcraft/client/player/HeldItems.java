package com.huntcraft.client.player;

import com.huntcraft.core.Element;
import com.huntcraft.core.Sharpness;
import com.huntcraft.core.WeaponClass;
import com.huntcraft.gear.weapon.DualBladesItem;
import com.huntcraft.gear.weapon.HunterWeaponItem;
import com.huntcraft.gear.weapon.action.ActionDefs;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Locale;

/**
 * Held weapons / items on the rig: main hand item from the hand_r wrist bone (same chain as vanilla's
 * ItemInHandLayer so the item models' third person transforms keep the grip in the hand), off hand item from
 * hand_l, dual blades in both hands, and the sheathed weapon on the back / hip / shoulder. Also samples the
 * weapon trail (grip + tip in world space) while an action's trail window is open.
 */
@OnlyIn(Dist.CLIENT)
public final class HeldItems {
    private HeldItems() {
    }

    /** Where a sheathed weapon of a class sits: bone, translation (blocks), rotation Z (deg), extra Y rotation, scale. */
    private record Sheath(String bone, float x, float y, float z, float rotY, float rotZ, float scale) {
    }

    private static Sheath sheath(WeaponClass c) {
        return switch (c) {
            case GREAT_SWORD -> new Sheath("chest", 0.0F, -0.05F, 0.21F, 0, -105, 1.7F);
            case LONG_SWORD -> new Sheath("chest", 0.0F, -0.05F, 0.21F, 0, -105, 1.5F);
            case HAMMER -> new Sheath("chest", 0.0F, -0.02F, 0.22F, 0, -105, 1.5F);
            case HUNTING_HORN -> new Sheath("chest", 0.0F, -0.02F, 0.22F, 0, -105, 1.5F);
            case LANCE -> new Sheath("chest", 0.0F, -0.05F, 0.21F, 0, -105, 1.7F);
            case BOW -> new Sheath("chest", 0.0F, 0.0F, 0.2F, 0, -135, 1.1F);
            case SWORD_AND_SHIELD -> new Sheath("hips", 0.3F, 0.05F, 0.05F, 90, 90, 1.0F);
            case DUAL_BLADES -> new Sheath("hips", 0.3F, 0.05F, 0.05F, 90, 90, 1.0F);
        };
    }

    /** Sharpening: the weapon lies in the left hand, the whetstone rubs along it from the right hand. */
    public static boolean isSharpening(AbstractClientPlayer p, PlayerAnimState st) {
        return "sharpen".equals(HunterAnimator.stance(p, st)) && p.getMainHandItem().getItem() instanceof HunterWeaponItem;
    }

    public static ItemStack whetstone() {
        return new ItemStack(com.huntcraft.gear.weapon.WeaponsModule.WHETSTONE.get());
    }

    public static void render(AbstractClientPlayer p, PlayerAnimState st, HunterModel model, PoseStack ps, MultiBufferSource buffers, int light, float pt) {
        ItemStack main = p.getMainHandItem();
        ItemStack off = p.getOffhandItem();
        ItemStack inHand = main;
        ItemStack onBack = st.sheathed;
        long gt = p.level().getGameTime();
        if (isSharpening(p, st)) {
            ps.pushPose();
            model.translateTo("hand_l", ps);
            handChain(ps, true);
            Minecraft.getInstance().getItemRenderer().renderStatic(p, main, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, ps, buffers, p.level(), light, OverlayTexture.NO_OVERLAY, p.getId());
            ps.popPose();
            ps.pushPose();
            model.translateTo("hand_r", ps);
            handChain(ps, false);
            Minecraft.getInstance().getItemRenderer().renderStatic(p, whetstone(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, ps, buffers, p.level(), light, OverlayTexture.NO_OVERLAY, p.getId());
            ps.popPose();
            return;
        }
        if (st.action != null && st.actionActive(gt)) {
            int swap = st.action.tagInt("swap", -1);
            float t = st.actionTime(gt, pt);
            if (st.action.hasTag("onback") && main.getItem() instanceof HunterWeaponItem) {
                // carving etc.: the weapon rides on the back while both hands are busy
                onBack = main;
                inHand = ItemStack.EMPTY;
            } else if (swap >= 0 && t < swap) {
                if ("unsheathe".equals(st.action.name) && main.getItem() instanceof HunterWeaponItem) {
                    onBack = main;
                    inHand = ItemStack.EMPTY;
                } else if ("sheathe".equals(st.action.name) && !st.sheathed.isEmpty()) {
                    inHand = st.sheathed;
                    onBack = ItemStack.EMPTY;
                }
            }
        }
        if (!inHand.isEmpty()) {
            ps.pushPose();
            model.translateTo("hand_r", ps);
            handChain(ps, false);
            if (inHand.getItem() instanceof HunterWeaponItem w && st.action != null && st.actionActive(gt) && st.action.trailActive(st.actionTime(gt, pt))) {
                sampleTrail(p, st, inHand, w, ps);
            }
            Minecraft.getInstance().getItemRenderer().renderStatic(p, inHand, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, ps, buffers, p.level(), light, OverlayTexture.NO_OVERLAY, p.getId());
            ps.popPose();
        }
        ItemStack left = off;
        if (inHand.getItem() instanceof DualBladesItem && off.isEmpty()) left = inHand;
        if (!left.isEmpty()) {
            ps.pushPose();
            model.translateTo("hand_l", ps);
            handChain(ps, true);
            Minecraft.getInstance().getItemRenderer().renderStatic(p, left, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, ps, buffers, p.level(), light, OverlayTexture.NO_OVERLAY, p.getId());
            ps.popPose();
        }
        if (!onBack.isEmpty() && onBack.getItem() instanceof HunterWeaponItem w) {
            Sheath sh = sheath(w.weaponClass());
            ps.pushPose();
            model.translateTo(sh.bone, ps);
            ps.translate(sh.x, sh.y, sh.z);
            if (sh.rotY != 0) ps.mulPose(Axis.YP.rotationDegrees(sh.rotY));
            ps.mulPose(Axis.ZP.rotationDegrees(sh.rotZ));
            ps.scale(sh.scale, sh.scale, sh.scale);
            Minecraft.getInstance().getItemRenderer().renderStatic(p, onBack, ItemDisplayContext.NONE, false, ps, buffers, p.level(), light, OverlayTexture.NO_OVERLAY, p.getId());
            ps.popPose();
        }
    }

    /** Vanilla ItemInHandLayer chain, re-based on the wrist bone (hand = 10px below the shoulder pivot). */
    public static void handChain(PoseStack ps, boolean left) {
        ps.translate(left ? 1.0F / 16.0F : -1.0F / 16.0F, 0.0F, -0.125F);
        ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
    }

    /** Model space matrix (hand frame -> item sprite space) for the main hand item of this stack. */
    private static Matrix4f itemSpace(AbstractClientPlayer p, ItemStack stack, PoseStack ps, boolean left) {
        BakedModel bm = Minecraft.getInstance().getItemRenderer().getModel(stack, p.level(), p, 0);
        ItemTransform tr = bm.getTransforms().getTransform(left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
        ps.pushPose();
        tr.apply(left, ps);
        ps.translate(-0.5F, -0.5F, -0.5F);
        Matrix4f m = new Matrix4f(ps.last().pose());
        ps.popPose();
        return m;
    }

    /**
     * Samples grip + tip of the held weapon in world space. The entity pose stack is in VIEW space (the level
     * renderer applies the camera rotation before translating by entity - camera), so the points are turned back
     * with the inverse view rotation before the camera position is added.
     */
    private static void sampleTrail(AbstractClientPlayer p, PlayerAnimState st, ItemStack stack, HunterWeaponItem w, PoseStack ps) {
        ActionDefs.WeaponSprite ws = ActionDefs.sprite(w.weaponClass().name().toLowerCase(Locale.ROOT));
        if (ws == null) return;
        Matrix4f m = itemSpace(p, stack, ps, false);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Matrix3f inv = RenderSystem.getInverseViewRotationMatrix();
        Vec3 base = toWorld(new Vector4f(ws.gripX() / 32F, 1F - ws.gripY() / 32F, 0.5F, 1F).mul(m), inv, cam);
        Vec3 tip = toWorld(new Vector4f(ws.tipX() / 32F, 1F - ws.tipY() / 32F, 0.5F, 1F).mul(m), inv, cam);
        if (tip.distanceToSqr(p.position()) > 25.0) return;   // not a world render pass (GUI preview...)
        int color = trailColor(p, stack, w);
        st.trail.add(base, tip, p.level().getGameTime() + Minecraft.getInstance().getPartialTick(), color);
    }

    private static Vec3 toWorld(Vector4f view, Matrix3f inverseViewRotation, Vec3 cam) {
        Vector3f v = new Vector3f(view.x(), view.y(), view.z()).mul(inverseViewRotation);
        return cam.add(v.x(), v.y(), v.z());
    }

    private static int trailColor(AbstractClientPlayer p, ItemStack stack, HunterWeaponItem w) {
        Element el = w.getElement(stack);
        if (el != Element.NONE) return el.color;
        if (w.usesSharpness()) {
            Sharpness s = w.getSharpness(stack, p);
            return s.color;
        }
        return 0xDDE6FF;
    }
}
