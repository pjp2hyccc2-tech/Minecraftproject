package net.minecraftforge.client.event;
import java.util.function.Supplier; import net.minecraft.client.model.geom.ModelLayerLocation; import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider; import net.minecraft.world.entity.Entity; import net.minecraft.world.entity.EntityType;
public abstract class EntityRenderersEvent {
  public static class RegisterLayerDefinitions extends EntityRenderersEvent { public void registerLayerDefinition(ModelLayerLocation l, Supplier<LayerDefinition> s) {} }
  public static class RegisterRenderers extends EntityRenderersEvent { public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> t, EntityRendererProvider<T> p) {} } }
