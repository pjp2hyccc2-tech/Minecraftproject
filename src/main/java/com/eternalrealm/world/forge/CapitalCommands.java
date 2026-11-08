package com.eternalrealm.world.forge;

import com.eternalrealm.world.forge.capital.CapitalBuilder;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Optional;

/** {@code /eternalrealm capital status|rebuild} (operators): watch or restart the build of the blueprint capital. */
public final class CapitalCommands {
    private CapitalCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("eternalrealm")
                .then(Commands.literal("capital").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(ctx -> {
                            Optional<ServerLevel> astoria = AstoriaTravel.astoria(ctx.getSource().getServer());
                            if (astoria.isEmpty()) {
                                ctx.getSource().sendFailure(Component.literal("No Astoria dimension"));
                                return 0;
                            }
                            String built = AstoriaWorldState.get(astoria.get()).capitalBuild();
                            String shipped = Capital.blueprint(ctx.getSource().getServer())
                                    .map(blueprint -> blueprint.build()).orElse("(none: classic town)");
                            int percent = CapitalBuilder.progressPercent();
                            ctx.getSource().sendSuccess(() -> Component.literal("Capital blueprint " + shipped
                                    + ", built: " + (built.isEmpty() ? "none" : built)
                                    + (percent >= 0 ? ", building " + percent + "%" : "")), false);
                            return 1;
                        }))
                        .then(Commands.literal("rebuild").executes(ctx -> {
                            Optional<ServerLevel> astoria = AstoriaTravel.astoria(ctx.getSource().getServer());
                            if (astoria.isEmpty() || Capital.blueprint(ctx.getSource().getServer()).isEmpty()) {
                                ctx.getSource().sendFailure(Component.literal("No capital blueprint to build"));
                                return 0;
                            }
                            AstoriaWorldState.get(astoria.get()).resetCapitalBuild();
                            Capital.layout(astoria.get());
                            ctx.getSource().sendSuccess(() -> Component.literal("Rebuilding the capital"), true);
                            return 1;
                        }))));
    }
}
