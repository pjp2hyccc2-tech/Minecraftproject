package com.eternalrealm.job.fx;

import com.eternalrealm.job.JobDefinition;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.job.SkillKind;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders contact sheets of the skill shows (one row per skill, six moments each) with {@link FxRaster}:
 * <pre>java -cp .offline-build/main:.offline-build/test com.eternalrealm.job.fx.SkillFxPreview OUT_DIR [job|skill...] [--day]</pre>
 * Not a test: a tool to look at the light before it reaches the game.
 */
public final class SkillFxPreview {
    private static boolean FIRST_PERSON;
    private static int CW = 300;
    private static int CH = 190;

    private SkillFxPreview() {
    }

    public static void main(String[] args) throws IOException {
        File out = new File(args.length > 0 ? args[0] : "/tmp/fxprev");
        out.mkdirs();
        boolean day = false;
        List<SkillDefinition> skills = new ArrayList<>();
        String sheet = "all";
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--day")) {
                day = true;
                continue;
            }
            if (a.equals("--fp")) {
                FIRST_PERSON = true;
                continue;
            }
            if (a.equals("--big")) {
                CW = 600;
                CH = 380;
                continue;
            }
            if (a.equals("fields") || a.equals("blasts") || a.equals("trails")) {
                sheetOfThemes(out, a, day);
                continue;
            }
            JobType job = null;
            for (JobType type : JobType.values()) {
                if (type.name().equalsIgnoreCase(a)) {
                    job = type;
                }
            }
            if (job != null) {
                JobDefinition def = Jobs.get(job);
                def.skillsById().values().stream().filter(SkillDefinition::isUsable)
                        .sorted(Comparator.comparing(SkillDefinition::kind).thenComparing(SkillDefinition::tier)
                                .thenComparing(SkillDefinition::id))
                        .forEach(skills::add);
                sheet = a.toLowerCase(java.util.Locale.ROOT);
            } else if (a.equals("ultimates")) {
                Jobs.allSkills().values().stream().filter(s -> s.kind() == SkillKind.ULTIMATE)
                        .sorted(Comparator.comparing(SkillDefinition::id)).forEach(skills::add);
                sheet = "ultimates";
            } else {
                Jobs.skill(a).ifPresent(skills::add);
                sheet = a;
            }
        }
        if (skills.isEmpty()) {
            return;
        }
        int cols = 6;
        int perSheet = 18;
        for (int part = 0; part * perSheet < skills.size(); part++) {
            List<SkillDefinition> slice = skills.subList(part * perSheet, Math.min(skills.size(), (part + 1) * perSheet));
            BufferedImage img = new BufferedImage(CW * cols, CH * slice.size(), BufferedImage.TYPE_INT_RGB);
            Graphics2D gfx = img.createGraphics();
            gfx.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            int row = 0;
            for (SkillDefinition skill : slice) {
                SkillShow show = SkillShows.of(skill);
                double[] moments = moments(show);
                for (int col = 0; col < cols; col++) {
                    BufferedImage cell = render(skill, show, moments[col], day);
                    gfx.drawImage(cell, col * CW, row * CH, null);
                    gfx.setColor(Color.WHITE);
                    gfx.drawString(String.format("%s t=%.0f", col == 0 ? skill.id() : "", moments[col]), col * CW + 4,
                            row * CH + 13);
                }
                row++;
            }
            gfx.dispose();
            File file = new File(out, sheet + (day ? "_day" : "") + (part == 0 ? "" : "_" + part) + ".png");
            ImageIO.write(img, "png", file);
            System.out.println(file + " " + slice.size() + " skills");
        }
    }

    /** One row per theme: its ground area (and a delayed blast), its exploding spell, or its projectiles. */
    static void sheetOfThemes(File out, String what, boolean day) throws IOException {
        com.eternalrealm.job.SkillTheme[] themes = com.eternalrealm.job.SkillTheme.values();
        int cols = 6;
        int rows = themes.length + (what.equals("fields") ? 1 : 0);
        BufferedImage img = new BufferedImage(CW * cols, CH * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D gfx = img.createGraphics();
        gfx.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        for (int row = 0; row < rows; row++) {
            com.eternalrealm.job.SkillTheme theme = themes[Math.min(row, themes.length - 1)];
            boolean delayed = row == themes.length;
            for (int col = 0; col < cols; col++) {
                V3 eye = new V3(-1.6, 3.4, -5.2);
                V3 target = new V3(0.0, 0.8, 5.0);
                FxRaster raster = new FxRaster(CW, CH, eye, target, 66.0);
                raster.backdrop(day, new V3(0.0, 0.0, 0.0));
                Brush brush = new Brush(raster, raster.eye());
                double t;
                if (what.equals("trails")) {
                    t = 2.0 + col * 2.0;
                    boolean arrow = col % 2 == 1;
                    V3[] path = new V3[Trails.LENGTH];
                    for (int i = 0; i < path.length; i++) {
                        double tt = Math.max(0.0, t - i);
                        double x = arrow ? 1.2 : -1.2;
                        path[i] = new V3(x, 1.4 + (arrow ? -0.02 * tt * tt : 0.0), 1.0 + 1.1 * tt);
                    }
                    Trails.draw(brush, theme, arrow, path, t, 7L, 1.0);
                } else {
                    SkillShow show = what.equals("fields")
                            ? (delayed ? Fields.zone(com.eternalrealm.job.SkillTheme.FIRE, 3.5, 24, 1, 10, false, true, 5L)
                            : Fields.zone(theme, 3.5, 0, 6, 10, theme == com.eternalrealm.job.SkillTheme.LIFE
                            || theme == com.eternalrealm.job.SkillTheme.HOLY, true, 5L))
                            : Fields.blast(theme, 3.0, 5L);
                    double[] when = what.equals("fields")
                            ? (delayed ? new double[]{4, 12, 20, 25, 28, 34} : new double[]{2, 6, 12, 25, 40, 58})
                            : new double[]{0.5, 2, 4, 7, 10, 15};
                    t = when[col];
                    Pose at = Fields.at(new V3(0.0, 0.0, 5.0), 0.0);
                    if (what.equals("blasts")) {
                        at = Fields.at(new V3(0.0, 0.0, 5.0), 0.0);
                    }
                    ShowState state = new ShowState(show, at, at.feet());
                    state.update(t, at);
                    Painter.paint(state, t, brush);
                }
                gfx.drawImage(raster.image(), col * CW, row * CH, null);
                gfx.setColor(Color.WHITE);
                gfx.drawString(String.format("%s t=%.0f", col == 0 ? (delayed ? "delayed blast" : theme.name()) : "", t),
                        col * CW + 4, row * CH + 13);
            }
        }
        gfx.dispose();
        File file = new File(out, what + (day ? "_day" : "") + ".png");
        ImageIO.write(img, "png", file);
        System.out.println(file);
    }

    /** Six moments worth seeing: the wind-up, the first hit, a little after, the middle, the last hit, the fading. */
    static double[] moments(SkillShow show) {
        int[] t = show.frameTimes();
        double first = t.length == 0 ? show.duration() * 0.5 : t[0];
        for (int v : t) {
            first = v;
            break;
        }
        double last = t.length == 0 ? first : t[t.length - 1];
        double end = show.duration() + show.tail() * 0.6;
        return new double[]{Math.max(1.0, first * 0.6), first + 1.0, first + 4.0, (first + last) * 0.5 + 2.0, last + 2.0,
                Math.max(last + 7.0, Math.min(end, last + 16.0))};
    }

    static BufferedImage render(SkillDefinition skill, SkillShow show, double t, boolean day) {
        boolean big = show.ultimate();
        // the usual third-person view: behind the shoulder, a little above
        V3 eye = big ? new V3(-5.0, 7.0, -12.0) : new V3(-1.6, 3.0, -5.2);
        V3 target = big ? new V3(0.0, 4.0, 5.0) : new V3(0.0, 1.3, 3.5);
        if (FIRST_PERSON) {
            // through the caster's own eyes, carried along by their dashes and leaps
            Pose now = pose(skill, t);
            eye = now.eye();
            target = eye.plus(now.look(), 5.0);
        }
        FxRaster raster = new FxRaster(CW, CH, eye, target, FIRST_PERSON ? 75.0 : (big ? 72.0 : 66.0));
        raster.backdrop(day, pose(skill, t).feet());
        Pose origin = pose(skill, 0.0);
        ShowState state = new ShowState(show, origin, new V3(0.0, 0.0, 10.0));
        for (double k = 0.0; k <= t; k += 1.0) {
            state.update(k, pose(skill, k));
        }
        state.update(t, pose(skill, t));
        Painter.paint(state, t, new Brush(raster, raster.eye()), FIRST_PERSON);
        return raster.image();
    }

    /** The caster at show time {@code t}: standing at the origin facing +z, carried forward by its dashes. */
    static Pose pose(SkillDefinition skill, double t) {
        double moved = 0.0;
        double lifted = 0.0;
        for (SkillEffect e : skill.effects()) {
            int frame = e.frame();
            double at = frame < 0 ? 0.0 : skill.action().frames().get(Math.min(frame, skill.action().frames().size() - 1))
                    .offsetTicks();
            double u = Math.max(0.0, Math.min(1.0, (t - at) / 3.0));
            if (e instanceof SkillEffect.Dash dash) {
                moved += (dash.backwards() ? -1.0 : 1.0) * dash.distance() * u;
            } else if (e instanceof SkillEffect.Leap leap) {
                moved += leap.distance() * Math.max(0.0, Math.min(1.0, (t - at) / 8.0));
                lifted += leap.height() * Math.sin(Math.PI * Math.max(0.0, Math.min(1.0, (t - at) / 8.0)));
            }
        }
        return Pose.of(new V3(0.0, lifted, moved), 0.0, 8.0, 1.62, 1);
    }
}
