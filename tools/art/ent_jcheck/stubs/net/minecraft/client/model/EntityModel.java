package net.minecraft.client.model;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType; import net.minecraft.resources.ResourceLocation; import net.minecraft.world.entity.Entity;
public abstract class EntityModel<T extends Entity> extends Model { public float attackTime; public boolean riding; public boolean young = true;
  protected EntityModel() { this(RenderType::entityCutoutNoCull); }
  protected EntityModel(Function<ResourceLocation, RenderType> r) { super(r); }
  public abstract void setupAnim(T e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch);
  public void prepareMobModel(T e, float a, float b, float c) {} }
