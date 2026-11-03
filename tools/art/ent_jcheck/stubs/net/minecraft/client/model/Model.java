package net.minecraft.client.model;
import java.util.function.Function;
import com.mojang.blaze3d.vertex.PoseStack; import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType; import net.minecraft.resources.ResourceLocation;
public abstract class Model { protected final Function<ResourceLocation, RenderType> renderType;
  public Model(Function<ResourceLocation, RenderType> r) { this.renderType = r; }
  public final RenderType renderType(ResourceLocation l) { return this.renderType.apply(l); }
  public abstract void renderToBuffer(PoseStack p, VertexConsumer c, int light, int overlay, float r, float g, float b, float a); }
