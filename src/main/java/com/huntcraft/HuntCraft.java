package com.huntcraft;

import com.huntcraft.core.CoreEvents;
import com.huntcraft.core.ShopRegistry;
import com.huntcraft.data.HCCapabilities;
import com.huntcraft.gear.armor.ArmorModule;
import com.huntcraft.gear.weapon.WeaponsModule;
import com.huntcraft.monster.ignivald.IgnivaldModule;
import com.huntcraft.monster.large.a.LargeMonstersA;
import com.huntcraft.monster.large.b.LargeMonstersB;
import com.huntcraft.monster.small.SmallMonsters;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.quest.QuestModule;
import com.huntcraft.registry.HCBlocks;
import com.huntcraft.registry.HCCreativeTabs;
import com.huntcraft.registry.HCEffects;
import com.huntcraft.registry.HCEntities;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import com.huntcraft.village.VillageModule;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * HuntCraft ～狩猟奇譚～ : a hunting-action game system for Minecraft Forge 1.20.1.
 * <p>
 * Module layout (each module registers its own content in init()):
 * <ul>
 *   <li>core (this package + registry/core/data/network/entity/client): shared systems</li>
 *   <li>monster.ignivald, monster.large.a, monster.large.b: large monsters</li>
 *   <li>monster.small: small monsters</li>
 *   <li>gear.weapon: 8 weapon types; gear.armor: armor sets, skills, charms</li>
 *   <li>village: hub dimension, NPCs, GUIs, companions</li>
 *   <li>quest: quests, quest maps, gathering, base camps</li>
 * </ul>
 */
@Mod(HuntCraft.MODID)
public class HuntCraft {
    public static final String MODID = "huntcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    public HuntCraft() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        HCSounds.SOUNDS.register(modBus);
        HCEffects.EFFECTS.register(modBus);
        HCBlocks.BLOCKS.register(modBus);
        HCItems.ITEMS.register(modBus);
        HCEntities.ENTITIES.register(modBus);
        HCCreativeTabs.TABS.register(modBus);
        modBus.addListener(HCCapabilities::register);

        HCNetwork.init();
        CoreEvents.registerActions();
        ShopRegistry.registerDefaults();

        // content modules (order matters only for network action names, which are string keyed)
        IgnivaldModule.init(modBus);
        LargeMonstersA.init(modBus);
        LargeMonstersB.init(modBus);
        SmallMonsters.init(modBus);
        WeaponsModule.init(modBus);
        ArmorModule.init(modBus);
        VillageModule.init(modBus);
        QuestModule.init(modBus);
        LOGGER.info("HuntCraft loaded");
    }
}
