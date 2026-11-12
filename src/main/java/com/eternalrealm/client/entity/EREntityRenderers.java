package com.eternalrealm.client.entity;

import com.eternalrealm.client.entity.model.gen.BeastEnemyGeometry;
import com.eternalrealm.client.entity.model.gen.ColossusBossGeometry;
import com.eternalrealm.client.entity.model.gen.DragonBossGeometry;
import com.eternalrealm.client.entity.model.gen.FlyerEnemyGeometry;
import com.eternalrealm.client.entity.model.gen.GolemEnemyGeometry;
import com.eternalrealm.client.entity.model.gen.HumanoidEnemyGeometry;
import com.eternalrealm.client.entity.model.gen.KingBossGeometry;
import com.eternalrealm.client.entity.model.gen.NpcGeometry;
import com.eternalrealm.client.entity.model.gen.QueenBossGeometry;
import com.eternalrealm.client.entity.model.gen.TreantBossGeometry;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.entity.BeastEnemy;
import com.eternalrealm.entity.BossKind;
import com.eternalrealm.entity.BossRig;
import com.eternalrealm.entity.EREntityTypes;
import com.eternalrealm.entity.EnemyBolt;
import com.eternalrealm.entity.EnemyRig;
import com.eternalrealm.entity.FlyerEnemy;
import com.eternalrealm.entity.GolemEnemy;
import com.eternalrealm.entity.HumanoidEnemy;
import com.eternalrealm.npc.forge.NpcEntities;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Function;

/** Client wiring of the enemy, boss and NPC entities: model layers and renderers. */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EREntityRenderers {
    private EREntityRenderers() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ERModelLayers.enemy(EnemyRig.HUMANOID), () -> HumanoidEnemyGeometry.create(0.0F));
        event.registerLayerDefinition(ERModelLayers.enemy(EnemyRig.BEAST), () -> BeastEnemyGeometry.create(0.0F));
        event.registerLayerDefinition(ERModelLayers.enemy(EnemyRig.GOLEM), () -> GolemEnemyGeometry.create(0.0F));
        event.registerLayerDefinition(ERModelLayers.enemy(EnemyRig.FLYER), () -> FlyerEnemyGeometry.create(0.0F));
        boss(event, BossRig.KING, KingBossGeometry::create);
        boss(event, BossRig.TREANT, TreantBossGeometry::create);
        boss(event, BossRig.COLOSSUS, ColossusBossGeometry::create);
        boss(event, BossRig.QUEEN, QueenBossGeometry::create);
        boss(event, BossRig.DRAGON, DragonBossGeometry::create);
        event.registerLayerDefinition(ERModelLayers.npc(), () -> NpcGeometry.create(0.0F));
    }

    private static void boss(EntityRenderersEvent.RegisterLayerDefinitions event, BossRig rig,
                             Function<Float, LayerDefinition> geometry) {
        event.registerLayerDefinition(ERModelLayers.boss(rig), () -> geometry.apply(0.0F));
        event.registerLayerDefinition(ERModelLayers.bossShield(rig), () -> geometry.apply(ERModelLayers.SHIELD_INFLATE));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EREntityTypes.HUMANOID_ENEMY.get(),
                context -> new EnemyRenderer<HumanoidEnemy>(context, EnemyRig.HUMANOID, 2.2F, 0.5F));
        event.registerEntityRenderer(EREntityTypes.BEAST_ENEMY.get(),
                context -> new EnemyRenderer<BeastEnemy>(context, EnemyRig.BEAST, 2.6F, 0.5F));
        event.registerEntityRenderer(EREntityTypes.GOLEM_ENEMY.get(),
                context -> new EnemyRenderer<GolemEnemy>(context, EnemyRig.GOLEM, 1.6F, 0.9F));
        event.registerEntityRenderer(EREntityTypes.FLYER_ENEMY.get(),
                context -> new EnemyRenderer<FlyerEnemy>(context, EnemyRig.FLYER, 1.6F, 0.4F));
        event.registerEntityRenderer(EREntityTypes.ENEMY_BOLT.get(),
                context -> new ThrownItemRenderer<EnemyBolt>(context, 1.1F, true));
        for (BossKind kind : BossKind.values()) {
            // made by the rig's gait so that the feet do not slide (divided by the drawn scale in the model)
            float generated = com.eternalrealm.entity.RigData.walkSpeed(kind.rig().id());
            float walkSpeed = generated > 0.0F ? generated : 1.5F;
            event.registerEntityRenderer(EREntityTypes.bossType(kind).get(),
                    context -> new BossRenderer(context, kind.rig(), walkSpeed));
        }
        event.registerEntityRenderer(NpcEntities.NPC.get(), NpcRenderer::new);
        event.registerEntityRenderer(NpcEntities.WANDERER.get(), NpcRenderer::new);
    }
}
