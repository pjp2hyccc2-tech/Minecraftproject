package net.minecraftforge.fml.common;
import java.lang.annotation.*; import net.minecraftforge.api.distmarker.Dist;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface Mod { String value();
  @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
  @interface EventBusSubscriber { Dist[] value() default { Dist.CLIENT, Dist.DEDICATED_SERVER }; String modid() default ""; Bus bus() default Bus.FORGE;
    enum Bus { FORGE, MOD } } }
