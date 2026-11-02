package net.minecraft.client.renderer.entity.layers;
import com.mojang.blaze3d.vertex.PoseStack; import net.minecraft.client.model.EntityModel; import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent; import net.minecraft.world.entity.Entity;
public abstract class RenderLayer<T extends Entity, M extends EntityModel<T>> { public RenderLayer(RenderLayerParent<T, M> p) {}
  public abstract void render(PoseStack p, MultiBufferSource b, int light, T e, float ls, float lsa, float pt, float age, float yaw, float pitch); }
