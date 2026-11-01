package com.frostbound.command;

import com.frostbound.core.Difficulty;
import com.frostbound.core.city.City;
import com.frostbound.core.events.EventDef;
import com.frostbound.core.events.Events;
import com.frostbound.core.resources.ResourceLedger;
import com.frostbound.core.resources.ResourceType;
import com.frostbound.core.sim.SimClock;
import com.frostbound.core.weather.WeatherState;
import com.frostbound.server.CityService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /frostbound … (operators): status, founding, time control and debug helpers. */
public final class FrostboundCommand {
    private FrostboundCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("frostbound").requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("found")
                        .executes(c -> found(c.getSource(), "NORMAL", 20))
                        .then(Commands.argument("difficulty", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("EASY", "NORMAL", "HARD"), b))
                                .executes(c -> found(c.getSource(), StringArgumentType.getString(c, "difficulty"), 20))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(5, 120))
                                        .executes(c -> found(c.getSource(), StringArgumentType.getString(c, "difficulty"),
                                                IntegerArgumentType.getInteger(c, "minutes"))))))
                .then(Commands.literal("advance").then(Commands.argument("hours", IntegerArgumentType.integer(1, 2400))
                        .executes(c -> advance(c.getSource(), IntegerArgumentType.getInteger(c, "hours")))))
                .then(Commands.literal("speed").then(Commands.argument("speed", IntegerArgumentType.integer(1, 3))
                        .executes(c -> withCity(c, city -> city.setSpeed(IntegerArgumentType.getInteger(c, "speed"))))))
                .then(Commands.literal("pause").executes(c -> withCity(c, city -> city.setSimPaused(true))))
                .then(Commands.literal("resume").executes(c -> withCity(c, city -> city.setSimPaused(false))))
                .then(Commands.literal("daylength").then(Commands.argument("minutes", IntegerArgumentType.integer(5, 120))
                        .executes(c -> withCity(c, city -> {
                            city.setDayLengthMinutes(IntegerArgumentType.getInteger(c, "minutes"));
                            return null;
                        }))))
                .then(Commands.literal("weather").then(Commands.argument("state", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(WeatherState.values()).map(WeatherState::id).toList(), b))
                        .executes(FrostboundCommand::weather)))
                .then(Commands.literal("give").then(Commands.argument("resource", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(ResourceType.values()).map(ResourceType::id).toList(), b))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
                                .executes(FrostboundCommand::give))))
                .then(Commands.literal("event").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Events.all().stream().map(e -> e.id).toList(), b))
                        .executes(FrostboundCommand::event)))
                .then(Commands.literal("tp").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    CityService.sendToPlaza(p);
                    return 1;
                })));
    }

    private interface CityOp {
        Object apply(City city) throws CommandSyntaxException;
    }

    private static int withCity(CommandContext<CommandSourceStack> c, CityOp op) throws CommandSyntaxException {
        City city = CityService.city();
        if (city == null) {
            c.getSource().sendFailure(Component.translatable("frostbound.cmd.no_city"));
            return 0;
        }
        op.apply(city);
        CityService.markDirty();
        c.getSource().sendSuccess(() -> Component.translatable("frostbound.cmd.done"), true);
        return 1;
    }

    private static int status(CommandSourceStack src) {
        City city = CityService.city();
        if (city == null) {
            src.sendSuccess(() -> Component.translatable("frostbound.cmd.status_none",
                    CityService.isFrostboundWorld(), CityService.phase().name()), false);
            return 0;
        }
        SimClock clk = city.clock();
        StringBuilder res = new StringBuilder();
        for (ResourceType t : ResourceType.values()) {
            res.append(t.id()).append('=').append(city.ledger().amountMilli(t) / ResourceLedger.MILLI).append(' ');
        }
        String line = String.format(Locale.ROOT, "%s day %d %02d:%02d | %.1f°C %s | pop %d (sick %d) | hope %.0f disc %.0f | gen L%d %s | %s| %s",
                city.name(), clk.day(), clk.hourOfDay(), clk.minuteOfHour(), city.outsideTemperature(),
                city.weather().current().id(), city.population().alive, city.population().sick, city.population().avgHope,
                city.population().avgDiscontent, city.generator().level(), city.generator().running() ? "on" : "off",
                res, city.phase().name());
        src.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    private static int found(CommandSourceStack src, String difficulty, int minutes) {
        Difficulty d;
        try {
            d = Difficulty.valueOf(difficulty.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("EASY / NORMAL / HARD"));
            return 0;
        }
        if (!CityService.isFrostboundWorld()) {
            src.sendFailure(Component.translatable("frostbound.cmd.not_frostbound"));
            return 0;
        }
        boolean ok = CityService.found(new CityService.FoundingSettings(d, minutes, "FROSTBOUND", true));
        if (!ok) {
            src.sendFailure(Component.translatable("frostbound.cmd.already"));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("frostbound.cmd.done"), true);
        return 1;
    }

    private static int advance(CommandSourceStack src, int hours) {
        if (CityService.city() == null) {
            src.sendFailure(Component.translatable("frostbound.cmd.no_city"));
            return 0;
        }
        CityService.advance(hours * SimClock.STEPS_PER_HOUR);
        return status(src);
    }

    private static int weather(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, "state");
        for (WeatherState w : WeatherState.values()) {
            if (w.id().equals(id)) {
                return withCity(c, city -> {
                    city.forceWeather(w);
                    return null;
                });
            }
        }
        c.getSource().sendFailure(Component.literal(id));
        return 0;
    }

    private static int give(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ResourceType t = ResourceType.byId(StringArgumentType.getString(c, "resource"));
        if (t == null) {
            c.getSource().sendFailure(Component.literal(StringArgumentType.getString(c, "resource")));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(c, "amount");
        return withCity(c, city -> {
            city.give(t, amount);
            return null;
        });
    }

    private static int event(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        EventDef def = Events.get(StringArgumentType.getString(c, "id"));
        if (def == null) {
            c.getSource().sendFailure(Component.literal(StringArgumentType.getString(c, "id")));
            return 0;
        }
        return withCity(c, city -> {
            city.events().trigger(city, def.id, def.scripted ? List.of() : List.of());
            return null;
        });
    }
}
