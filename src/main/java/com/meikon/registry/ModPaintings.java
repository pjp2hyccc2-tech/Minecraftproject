package com.meikon.registry;

import com.meikon.Meikon;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Paintings hung in the mansion (not in the vanilla "placeable" tag, so they never appear at random). */
public final class ModPaintings {
    public static final DeferredRegister<PaintingVariant> PAINTINGS = DeferredRegister.create(Registries.PAINTING_VARIANT, Meikon.MODID);

    public static final RegistryObject<PaintingVariant> MUKASARI = PAINTINGS.register("mukasari", () -> new PaintingVariant(32, 32));
    public static final RegistryObject<PaintingVariant> SAYO = PAINTINGS.register("sayo", () -> new PaintingVariant(16, 32));
    public static final RegistryObject<PaintingVariant> FAMILY = PAINTINGS.register("family", () -> new PaintingVariant(32, 16));
    public static final RegistryObject<PaintingVariant> MANSION = PAINTINGS.register("mansion", () -> new PaintingVariant(32, 16));

    private ModPaintings() {}
}
