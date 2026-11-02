package net.minecraft.client.renderer.entity;
import net.minecraft.client.model.EntityModel; import net.minecraft.client.renderer.culling.Frustum; import net.minecraft.world.entity.Mob;
public abstract class MobRenderer<T extends Mob, M extends EntityModel<T>> extends LivingEntityRenderer<T, M> {
  public MobRenderer(EntityRendererProvider.Context c, M m, float s) { super(c, m, s); }
  @Override public boolean shouldRender(T e, Frustum f, double x, double y, double z) { return true; } }
