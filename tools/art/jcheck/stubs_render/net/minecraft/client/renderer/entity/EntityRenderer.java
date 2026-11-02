package net.minecraft.client.renderer.entity;
import net.minecraft.client.renderer.culling.Frustum; import net.minecraft.resources.ResourceLocation; import net.minecraft.world.entity.Entity;
public abstract class EntityRenderer<T extends Entity> { protected EntityRenderer(EntityRendererProvider.Context c) {}
  public boolean shouldRender(T e, Frustum f, double x, double y, double z) { return true; }
  public abstract ResourceLocation getTextureLocation(T e); }
