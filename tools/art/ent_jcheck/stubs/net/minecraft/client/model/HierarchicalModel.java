package net.minecraft.client.model;
import java.util.function.Function;
import com.mojang.blaze3d.vertex.PoseStack; import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType; import net.minecraft.resources.ResourceLocation; import net.minecraft.world.entity.Entity;
public abstract class HierarchicalModel<E extends Entity> extends EntityModel<E> {
  public HierarchicalModel() { this(RenderType::entityCutoutNoCull); }
  public HierarchicalModel(Function<ResourceLocation, RenderType> r) { super(r); }
  public void renderToBuffer(PoseStack p, VertexConsumer c, int light, int overlay, float r, float g, float b, float a) {}
  public abstract ModelPart root(); }
