package net.minecraft.client.model.geom;
import java.util.stream.Stream;
import com.mojang.blaze3d.vertex.PoseStack; import com.mojang.blaze3d.vertex.VertexConsumer;
public final class ModelPart { public float x, y, z, xRot, yRot, zRot; public float xScale = 1, yScale = 1, zScale = 1; public boolean visible = true; public boolean skipDraw;
  public ModelPart getChild(String n) { return null; } public boolean hasChild(String n) { return false; }
  public void resetPose() {} public PartPose getInitialPose() { return null; } public void loadPose(PartPose p) {} public PartPose storePose() { return null; }
  public void setPos(float a, float b, float c) {} public void setRotation(float a, float b, float c) {} public void copyFrom(ModelPart o) {}
  public Stream<ModelPart> getAllParts() { return Stream.empty(); }
  public void render(PoseStack p, VertexConsumer c, int l, int o) {} public void render(PoseStack p, VertexConsumer c, int l, int o, float r, float g, float b, float a) {}
  public void translateAndRotate(PoseStack p) {} }
