package net.minecraft.client.renderer.entity;
import net.minecraft.client.model.EntityModel; import net.minecraft.client.renderer.entity.layers.RenderLayer; import net.minecraft.world.entity.LivingEntity;
public abstract class LivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> extends EntityRenderer<T> implements RenderLayerParent<T, M> {
  protected M model;
  public LivingEntityRenderer(EntityRendererProvider.Context c, M m, float s) { super(c); this.model = m; }
  public final boolean addLayer(RenderLayer<T, M> l) { return true; }
  public M getModel() { return this.model; } }
