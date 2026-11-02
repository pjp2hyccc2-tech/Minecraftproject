package net.minecraft.client.model.geom.builders;
public class CubeListBuilder { public static CubeListBuilder create() { return null; }
  public CubeListBuilder texOffs(int u, int v) { return this; } public CubeListBuilder mirror() { return this; } public CubeListBuilder mirror(boolean m) { return this; }
  public CubeListBuilder addBox(float x, float y, float z, float w, float h, float d) { return this; }
  public CubeListBuilder addBox(float x, float y, float z, float w, float h, float d, boolean m) { return this; }
  public CubeListBuilder addBox(float x, float y, float z, float w, float h, float d, CubeDeformation g) { return this; }
  public CubeListBuilder addBox(String n, float x, float y, float z, float w, float h, float d) { return this; } }
