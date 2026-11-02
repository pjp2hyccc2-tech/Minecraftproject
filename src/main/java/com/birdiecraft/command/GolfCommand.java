package com.birdiecraft.command;

import com.birdiecraft.course.Course;
import com.birdiecraft.course.CourseBuilder;
import com.birdiecraft.course.CoursePreset;
import com.birdiecraft.course.CourseTheme;
import com.birdiecraft.course.GolfCourseData;
import com.birdiecraft.golf.engine.Difficulty;
import com.birdiecraft.golf.engine.PlayStyle;
import com.birdiecraft.match.MatchManager;
import com.birdiecraft.match.MatchMode;
import com.birdiecraft.match.MenuActions;
import com.birdiecraft.match.PlayerProfiles;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /golf command tree (also reachable as /birdie). */
public final class GolfCommand {
    private GolfCommand() {
    }

    private static final SuggestionProvider<CommandSourceStack> COURSES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(GolfCourseData.get(ctx.getSource().getServer()).ids(), builder);

    private static final SuggestionProvider<CommandSourceStack> PRESETS = (ctx, builder) -> {
        List<String> ids = new ArrayList<>();
        for (CoursePreset p : CoursePreset.VALUES) ids.add(p.id);
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> THEMES = (ctx, builder) -> {
        List<String> ids = new ArrayList<>();
        for (CourseTheme t : CourseTheme.values()) ids.add(t.name().toLowerCase(Locale.ROOT));
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> DIFFICULTIES = (ctx, builder) -> {
        List<String> ids = new ArrayList<>();
        for (Difficulty d : Difficulty.VALUES) ids.add(d.name().toLowerCase(Locale.ROOT));
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> STYLES = (ctx, builder) -> {
        List<String> ids = new ArrayList<>();
        for (PlayStyle s : PlayStyle.VALUES) ids.add(s.name().toLowerCase(Locale.ROOT));
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("golf")
                .then(Commands.literal("preset")
                        .then(Commands.argument("preset", StringArgumentType.word()).suggests(PRESETS)
                                .executes(GolfCommand::buildPreset)))
                .then(Commands.literal("generate")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> generate(ctx, 9, null))
                                .then(Commands.argument("holes", IntegerArgumentType.integer(1, 9))
                                        .executes(ctx -> generate(ctx, IntegerArgumentType.getInteger(ctx, "holes"), null))
                                        .then(Commands.argument("theme", StringArgumentType.word()).suggests(THEMES)
                                                .executes(ctx -> generate(ctx, IntegerArgumentType.getInteger(ctx, "holes"),
                                                        StringArgumentType.getString(ctx, "theme")))))))
                .then(Commands.literal("courses").executes(GolfCommand::listCourses))
                .then(Commands.literal("delete").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("course", StringArgumentType.word()).suggests(COURSES)
                                .executes(GolfCommand::deleteCourse)))
                .then(Commands.literal("play")
                        .then(Commands.argument("course", StringArgumentType.word()).suggests(COURSES)
                                .executes(ctx -> play(ctx, MatchMode.SINGLE, 0, Difficulty.AMATEUR))))
                .then(Commands.literal("vs")
                        .then(Commands.argument("course", StringArgumentType.word()).suggests(COURSES)
                                .executes(ctx -> play(ctx, MatchMode.VS_CPU, 1, Difficulty.AMATEUR))
                                .then(Commands.argument("cpus", IntegerArgumentType.integer(1, 7))
                                        .executes(ctx -> play(ctx, MatchMode.VS_CPU, IntegerArgumentType.getInteger(ctx, "cpus"), Difficulty.AMATEUR))
                                        .then(Commands.argument("difficulty", StringArgumentType.word()).suggests(DIFFICULTIES)
                                                .executes(ctx -> play(ctx, MatchMode.VS_CPU, IntegerArgumentType.getInteger(ctx, "cpus"),
                                                        parseDifficulty(StringArgumentType.getString(ctx, "difficulty"))))))))
                .then(Commands.literal("host")
                        .then(Commands.argument("course", StringArgumentType.word()).suggests(COURSES)
                                .executes(ctx -> play(ctx, MatchMode.MULTI, 0, Difficulty.AMATEUR))))
                .then(Commands.literal("join")
                        .then(Commands.argument("match", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    MatchManager.join(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "match"));
                                    return 1;
                                })))
                .then(Commands.literal("start").executes(ctx -> {
                    MatchManager.begin(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("leave").executes(ctx -> {
                    MatchManager.leave(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("style")
                        .then(Commands.argument("style", StringArgumentType.word()).suggests(STYLES)
                                .executes(GolfCommand::setStyle)));
        dispatcher.register(root);
        dispatcher.register(Commands.literal("birdie").redirect(dispatcher.getRoot().getChild("golf")));
    }

    private static Difficulty parseDifficulty(String s) {
        for (Difficulty d : Difficulty.VALUES) {
            if (d.name().equalsIgnoreCase(s)) return d;
        }
        return Difficulty.AMATEUR;
    }

    private static int buildPreset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CoursePreset preset = CoursePreset.byId(StringArgumentType.getString(ctx, "preset"));
        if (preset == null) {
            ctx.getSource().sendFailure(Component.translatable("msg.birdiecraft.no_preset"));
            return 0;
        }
        if (!MenuActions.canBuild(player)) {
            ctx.getSource().sendFailure(Component.translatable("msg.birdiecraft.no_permission"));
            return 0;
        }
        if (CourseBuilder.isBusy()) {
            ctx.getSource().sendFailure(Component.translatable("msg.birdiecraft.builder_busy"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("msg.birdiecraft.build_start",
                Component.translatable(preset.translationKey())).withStyle(ChatFormatting.YELLOW), false);
        CourseBuilder.buildPreset(player, preset);
        return 1;
    }

    private static int generate(CommandContext<CommandSourceStack> ctx, int holes, String themeName) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!MenuActions.canBuild(player)) {
            ctx.getSource().sendFailure(Component.translatable("msg.birdiecraft.no_permission"));
            return 0;
        }
        if (CourseBuilder.isBusy()) {
            ctx.getSource().sendFailure(Component.translatable("msg.birdiecraft.builder_busy"));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        CourseTheme theme = CourseTheme.MEADOW;
        if (themeName != null) {
            for (CourseTheme t : CourseTheme.values()) {
                if (t.name().equalsIgnoreCase(themeName)) theme = t;
            }
        } else {
            CourseTheme[] all = CourseTheme.values();
            theme = all[new java.util.Random().nextInt(all.length)];
        }
        String id = Course.sanitizeId(name);
        ctx.getSource().sendSuccess(() -> Component.translatable("msg.birdiecraft.build_start", Component.literal(name))
                .withStyle(ChatFormatting.YELLOW), false);
        CourseBuilder.buildRandom(player, id, name, holes, theme);
        return 1;
    }

    private static int listCourses(CommandContext<CommandSourceStack> ctx) {
        GolfCourseData data = GolfCourseData.get(ctx.getSource().getServer());
        if (data.all().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("msg.birdiecraft.no_courses").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (Course c : data.all()) {
            ctx.getSource().sendSuccess(() -> Component.literal(" - " + c.id + ": ").withStyle(ChatFormatting.GREEN)
                    .append(c.title().copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + c.holes.size() + "H / PAR " + c.totalPar()).withStyle(ChatFormatting.GRAY)), false);
        }
        return data.all().size();
    }

    private static int deleteCourse(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "course");
        boolean ok = GolfCourseData.get(ctx.getSource().getServer()).remove(id);
        if (ok) ctx.getSource().sendSuccess(() -> Component.translatable("msg.birdiecraft.deleted", id), true);
        else ctx.getSource().sendFailure(Component.translatable("msg.birdiecraft.no_course", id));
        return ok ? 1 : 0;
    }

    private static int play(CommandContext<CommandSourceStack> ctx, MatchMode mode, int cpus, Difficulty difficulty) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "course");
        Course course = GolfCourseData.get(ctx.getSource().getServer()).get(id);
        if (course == null) {
            ctx.getSource().sendFailure(Component.translatable("msg.birdiecraft.no_course", id));
            return 0;
        }
        MatchManager.create(player, mode, course, 0, course.holes.size(), cpus, difficulty);
        return 1;
    }

    private static int setStyle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String s = StringArgumentType.getString(ctx, "style");
        for (PlayStyle style : PlayStyle.VALUES) {
            if (style.name().equalsIgnoreCase(s)) {
                PlayerProfiles.setStyle(player, style);
                ctx.getSource().sendSuccess(() -> Component.translatable("msg.birdiecraft.style_set", Component.translatable(style.translationKey())), false);
                return 1;
            }
        }
        ctx.getSource().sendFailure(Component.literal(s));
        return 0;
    }
}
