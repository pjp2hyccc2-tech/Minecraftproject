package jp.archiquest;

import com.mojang.logging.LogUtils;
import jp.archiquest.config.ArchiConfig;
import jp.archiquest.knowledge.KnowledgeBase;
import jp.archiquest.registry.ModCreativeTabs;
import jp.archiquest.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * ArchiQuest - 一級建築士への道
 * <p>
 * マイクラで建築しながら一級建築士試験（学科5科目＋設計製図）の内容を楽しく学べるMOD。
 * <ul>
 *   <li>建築中のアドバイス（置いたブロックに合わせた豆知識）</li>
 *   <li>建築診断スケール（面積・高さ・天井高・採光・換気・建ぺい率などを診断）</li>
 *   <li>クイズ・模擬試験・称号</li>
 *   <li>教科書GUI（かみくだき解説）</li>
 *   <li>AI先生（Claude API などに接続するとAIが講評・質問に回答。未設定でも内蔵知識で動作）</li>
 * </ul>
 */
@Mod(ArchiQuest.MODID)
public class ArchiQuest {
    public static final String MODID = "archiquest";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArchiQuest() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ArchiConfig.SPEC, "archiquest-common.toml");
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        String summary = KnowledgeBase.reload();
        LOGGER.info("[ArchiQuest] ready: {}", summary);
    }
}
