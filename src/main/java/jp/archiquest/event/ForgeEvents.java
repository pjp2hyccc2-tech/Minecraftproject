package jp.archiquest.event;

import jp.archiquest.ArchiQuest;
import jp.archiquest.advice.BuildAdvisor;
import jp.archiquest.ai.AiSensei;
import jp.archiquest.analysis.BuildingAnalyzer;
import jp.archiquest.command.ArchiCommand;
import jp.archiquest.config.ArchiConfig;
import jp.archiquest.knowledge.KnowledgeBase;
import jp.archiquest.progress.PlayerProgress;
import jp.archiquest.quiz.QuizManager;
import jp.archiquest.registry.ModItems;
import jp.archiquest.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Random;
import java.util.UUID;

/** Forge（ゲーム側）のイベント */
@Mod.EventBusSubscriber(modid = ArchiQuest.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeEvents {
    private static final Random RNG = new Random();

    private ForgeEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ArchiCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        try {
            BuildAdvisor.onPlace(player, level, event.getPos(), event.getPlacedBlock());
        } catch (Exception e) {
            ArchiQuest.LOGGER.warn("[ArchiQuest] advice failed", e);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!PlayerProgress.welcomed(p)) {
            PlayerProgress.setWelcomed(p);
            if (ArchiConfig.GIVE_STARTER_ITEMS.get()) {
                ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(ModItems.TEXTBOOK.get()));
                ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(ModItems.INSPECTOR.get()));
            }
            Msg.header(p, "ようこそ ArchiQuest へ！", ChatFormatting.AQUA);
            Msg.say(p, "ぼくは建築AIの「アーキ先生」。マイクラで建てながら、一緒に一級建築士をめざそう！");
            Msg.send(p, " ・教科書を右クリック or Kキー → かみくだき教科書", ChatFormatting.WHITE);
            Msg.send(p, " ・建築診断スケールで建物を右クリック → 天井高・採光・換気などを診断", ChatFormatting.WHITE);
            Msg.send(p, " ・ブロックを置くと、ときどき建築の豆知識を教えるよ", ChatFormatting.WHITE);
            p.sendSystemMessage(Component.literal(" ")
                    .append(Msg.button("コマンド一覧", "/archi help", "使い方を見る", ChatFormatting.AQUA))
                    .append(" ").append(Msg.button("最初のチャレンジ", "/archi challenge start first_room", "はじめての居室をつくろう", ChatFormatting.LIGHT_PURPLE))
                    .append(" ").append(Msg.button("クイズ", "/archi quiz", "まずは1問！", ChatFormatting.YELLOW)));
        } else if (ArchiConfig.LOGIN_TIP.get()) {
            String tip = KnowledgeBase.randomTip(null, RNG);
            if (tip != null) {
                Msg.say(p, Component.literal("今日の豆知識: ").withStyle(ChatFormatting.GOLD).append(Component.literal(tip).withStyle(ChatFormatting.WHITE)));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        QuizManager.forget(id);
        BuildAdvisor.forget(id);
        AiSensei.forget(id);
        BuildingAnalyzer.forget(id);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        PlayerProgress.copy(event.getOriginal(), event.getEntity());
    }
}
