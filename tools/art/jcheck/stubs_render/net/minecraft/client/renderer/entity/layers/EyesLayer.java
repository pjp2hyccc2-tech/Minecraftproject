package net.minecraft.client.renderer.entity.layers;
import com.mojang.blaze3d.vertex.PoseStack; import net.minecraft.client.model.EntityModel; import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType; import net.minecraft.client.renderer.entity.RenderLayerParent; import net.minecraft.world.entity.Entity;
public abstract class EyesLayer<T extends Entity, M extends EntityModel<T>> extends RenderLayer<T, M> { public EyesLayer(RenderLayerParent<T, M> p) { super(p); }
  @Override public void render(PoseStack p, MultiBufferSource b, int light, T e, float ls, float lsa, float pt, float age, float yaw, float pitch) {}
  public abstract RenderType renderType(); }
