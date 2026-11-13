package com.eternalrealm.social.phone.forge;

import com.eternalrealm.core.ModInfo;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * {@code /phone}: opens the 魔導スマホ (at a view: {@code friends}, {@code messages}, {@code feed}, {@code activity});
 * {@code /phone chat <name>}, {@code /phone post <id>}, {@code /phone accept|decline <name>} for the buttons in chat;
 * {@code /phone admin delete <post> [comment]} for moderators (permission 2).
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PhoneCommands {
    private PhoneCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("phone")
                .executes(c -> open(c, "home", ""))
                .then(Commands.literal("friends").executes(c -> open(c, "friends", "")))
                .then(Commands.literal("messages").executes(c -> open(c, "messages", "")))
                .then(Commands.literal("feed").executes(c -> open(c, "feed", "")))
                .then(Commands.literal("activity").executes(c -> open(c, "activity", "")))
                .then(Commands.literal("chat").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> withService(c, (service, player) -> service.openChat(player,
                                StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("post").then(Commands.argument("id", LongArgumentType.longArg(1))
                        .executes(c -> open(c, "post", Long.toString(LongArgumentType.getLong(c, "id"))))))
                .then(Commands.literal("accept").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> withService(c, (service, player) -> service.answer(player,
                                StringArgumentType.getString(c, "name"), true)))))
                .then(Commands.literal("decline").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> withService(c, (service, player) -> service.answer(player,
                                StringArgumentType.getString(c, "name"), false)))))
                .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("delete").then(Commands.argument("post", LongArgumentType.longArg(1))
                                .executes(c -> moderate(c, LongArgumentType.getLong(c, "post"), 0L))
                                .then(Commands.argument("comment", LongArgumentType.longArg(1))
                                        .executes(c -> moderate(c, LongArgumentType.getLong(c, "post"),
                                                LongArgumentType.getLong(c, "comment"))))))));
    }

    private interface Action {
        void run(PhoneService service, ServerPlayer player);
    }

    private static int withService(CommandContext<CommandSourceStack> context, Action action)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<PhoneService> service = PhoneService.current();
        if (service.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("phone.eternalrealm.toast.offline"));
            return 0;
        }
        action.run(service.get(), player);
        return 1;
    }

    private static int open(CommandContext<CommandSourceStack> context, String view, String id)
            throws CommandSyntaxException {
        return withService(context, (service, player) -> service.open(player, view, id));
    }

    private static int moderate(CommandContext<CommandSourceStack> context, long post, long comment) {
        Optional<PhoneService> service = PhoneService.current();
        boolean done = service.isPresent() && service.get().moderate(post, comment);
        if (done) {
            context.getSource().sendSuccess(() -> Component.translatable("phone.eternalrealm.admin.deleted", post)
                    .withStyle(ChatFormatting.GRAY), true);
            return 1;
        }
        context.getSource().sendFailure(Component.translatable("phone.eternalrealm.admin.missing", post));
        return 0;
    }
}
