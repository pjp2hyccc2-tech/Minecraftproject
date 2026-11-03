package com.reisatsu.game;

import com.reisatsu.Reisatsu;
import com.reisatsu.entity.Spirit;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.item.GameItem;
import com.reisatsu.network.C2S;
import com.reisatsu.network.S2C;
import com.reisatsu.registry.ModItems;
import com.reisatsu.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * 霊写機 on the server (docs/DESIGN.md §3.1). The client sends the shot with its view and zoom; the server decides:
 * a spirit is captured when its head is inside the central 80 % of the finder frame, within 12 blocks (20 at full
 * zoom), in line of sight (glass, the mirror and the pass window do not block), its face shows (≤ 110° between its
 * facing and the camera), it has not noticed you, it is in a photographable state and not captured yet. ★1..3 from
 * centring, distance and how frontal the face is. The flash alerts every other spirit within 14 blocks that can see you.
 */
public final class Camera {
    public static final double ASPECT = 4.0 / 3.0;
    public static final double FRAME = 0.8;

    private Camera() {}

    /** A verdict: result (GameData.RESULT_*), the spirit concerned, ★, and the reason (logs, self test). */
    public record Shot(int result, @Nullable Spirit spirit, int stars, String reason) {}

    public static double maxDistance(float zoom) {
        return Mth.lerp(Mth.clamp(zoom, 0.0f, 1.0f), 12.0, 20.0);
    }

    // ================================================================================================ the shutter
    public static void shutter(ServerPlayer p, C2S.Shutter msg) {
        PlayerState ps = GameManager.state(p);
        ServerLevel level = p.serverLevel();
        if (!GameManager.inGame(p) || ps.caught() || ps.locked || ps.introTicks >= 0 || ps.shutterCooldown > 0) return;
        ItemStack cam = p.getMainHandItem();
        if (!GameItem.is(cam, GameItem.Kind.CAMERA_FINDER)) return;
        int film = GameItem.film(cam);
        if (film <= 0) return;
        GameItem.setFilm(cam, film - 1);
        ps.shutterCooldown = 22;
        // trust the client's view only near the server's
        float yaw = Math.abs(Mth.wrapDegrees(msg.yaw() - p.getYRot())) < 12.0f ? msg.yaw() : p.getYRot();
        float pitch = Math.abs(msg.pitch() - p.getXRot()) < 12.0f ? msg.pitch() : p.getXRot();
        float fov = Mth.clamp(msg.fov(), 15.0f, 110.0f);
        float zoom = Mth.clamp(msg.zoom(), 0.0f, 1.0f);
        Vec3 eye = p.getEyePosition();
        level.playSound(null, eye.x, eye.y, eye.z, ModSounds.CAMERA_SHUTTER.get(), SoundSource.PLAYERS, 1.0f, 0.97f + level.random.nextFloat() * 0.06f);
        NoiseSystem.emit(level, eye, 10.0f, "shutter", p);
        Shot shot = validate(level, p, eye, yaw, pitch, fov, zoom);
        String id = sanitize(msg.id());
        take(level, p, id, shot);
    }

    /** Records a verdict: the album, the evidence photo, the spirit bound, the flash alerting the others, the client. */
    public static void take(ServerLevel level, ServerPlayer p, String id, Shot shot) {
        GameData data = GameData.get(p.server);
        Spirit target = shot.spirit();
        // the flash: every other spirit within 14 blocks that has a line of sight to you
        for (Spirit s : new ArrayList<>(Spirits.all(level))) {
            if (shot.result() == GameData.RESULT_CAPTURED && s == target) continue;
            if (s.mode() == Spirit.Mode.BOUND || s.mode() == Spirit.Mode.EXORCISM) continue;
            if (s.distanceTo(p) <= 14.0 && Stealth.lineOfSight(s, p)) s.raise(p, 0.6f);
        }
        int ghost = 0;
        if (shot.result() == GameData.RESULT_EMPTY && level.random.nextFloat() < 0.16f) ghost = 1 + level.random.nextInt(3);
        int spiritIdx = target == null ? -1 : target.kind().ordinal();
        data.photos.add(new GameData.Photo(id, shot.result() == GameData.RESULT_EMPTY ? -1 : spiritIdx, shot.stars(), data.playTicks,
                shot.result(), ghost));
        data.setDirty();
        if (shot.result() == GameData.RESULT_CAPTURED && target != null) {
            SpiritKind kind = target.kind();
            data.captured |= kind.bit();
            data.setDirty();
            ItemStack photo = new ItemStack(ModItems.EVIDENCE_PHOTO.get());
            GameItem.setPhoto(photo, id, kind, shot.stars(), data.playTicks);
            GameManager.give(p, photo);
            target.bind(p.getEyePosition());
            GameManager.event(p, GameManager.EV_CAPTURED, kind.ordinal(), "");
            Reisatsu.LOGGER.info("[reisatsu] {} photographed the {} (★{})", p.getGameProfile().getName(), kind.id, shot.stars());
            Story.onCaptured(level, p, data, kind);
        } else {
            Reisatsu.LOGGER.info("[reisatsu] shot {}: {}", id, shot.reason());
        }
        GameManager.send(p, new S2C.PhotoResult(id, shot.result(), spiritIdx, shot.stars(), ghost));
        syncAlbum(p);
    }

    public static void syncAlbum(ServerPlayer p) {
        GameData data = GameData.get(p.server);
        GameManager.send(p, new S2C.Album(new ArrayList<>(data.photos), new ArrayList<>(data.notes)));
    }

    static String sanitize(String raw) {
        StringBuilder b = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') b.append(c);
            if (b.length() >= 48) break;
        }
        if (b.length() == 0) b.append("shot").append(System.currentTimeMillis());
        return b.toString();
    }

    // ================================================================================================ validation
    /** Where a spirit's face is (its head). */
    public static Vec3 head(Spirit s) {
        return new Vec3(s.getX(), s.getY() + s.getEyeHeight(), s.getZ());
    }

    /** Normalised position of a point in the finder frame (-1..1 on both axes = the photo's edges), or null if behind. */
    @Nullable
    public static double[] project(Vec3 eye, float yaw, float pitch, float fov, Vec3 point) {
        Vec3 f = Vec3.directionFromRotation(pitch, yaw);
        Vec3 r = f.cross(new Vec3(0, 1, 0));
        if (r.lengthSqr() < 1e-6) r = new Vec3(1, 0, 0);
        r = r.normalize();
        Vec3 u = r.cross(f).normalize();
        Vec3 rel = point.subtract(eye);
        double z = rel.dot(f);
        if (z <= 0.05) return null;
        double t = Math.tan(Math.toRadians(fov) / 2.0);
        return new double[]{rel.dot(r) / (z * t * ASPECT), rel.dot(u) / (z * t), z};
    }

    /** Angle between where the spirit's face points and the direction to the camera (degrees, horizontal). */
    public static double faceAngle(Spirit s, Vec3 eye) {
        Vec3 facing = Vec3.directionFromRotation(0.0f, s.getYHeadRot());
        Vec3 to = new Vec3(eye.x - s.getX(), 0.0, eye.z - s.getZ());
        if (to.lengthSqr() < 1e-6) return 0.0;
        return Math.toDegrees(Math.acos(Mth.clamp(facing.dot(to.normalize()), -1.0, 1.0)));
    }

    /** The verdict for a shot from {@code eye} with this view. */
    public static Shot validate(ServerLevel level, Player p, Vec3 eye, float yaw, float pitch, float fov, float zoom) {
        GameData data = GameData.get(level.getServer());
        double maxD = maxDistance(zoom);
        Spirit best = null;
        double bestScore = -1;
        Spirit failed = null;
        String reason = "nothing in frame";
        for (Spirit s : Spirits.all(level)) {
            if (s.mode() == Spirit.Mode.BOUND || s.mode() == Spirit.Mode.EXORCISM) continue;
            Vec3 h = head(s);
            double d = h.distanceTo(eye);
            if (d > 32) continue;
            double[] pr = project(eye, yaw, pitch, fov, h);
            if (pr == null || Math.abs(pr[0]) > FRAME || Math.abs(pr[1]) > FRAME) continue;
            String who = s.kind().id + ": ";
            if (d > maxD) {
                reason = who + "too far (" + String.format(java.util.Locale.ROOT, "%.1f", d) + " > " + String.format(java.util.Locale.ROOT, "%.0f", maxD) + ")";
                continue;
            }
            if (!Sight.clear(level, eye, h)) {
                reason = who + "no line of sight";
                continue;
            }
            if (data.isCaptured(s.kind())) {
                reason = who + "already captured";
                continue;
            }
            if (s.hasNoticed(p)) {
                reason = who + "it had noticed you";
                failed = s;
                continue;
            }
            double angle = faceAngle(s, eye);
            if (angle > s.faceAngle()) {
                reason = who + "the face does not show (" + Math.round(angle) + "°)";
                continue;
            }
            if (!s.photogenic()) {
                reason = who + "not in a photographable state";
                continue;
            }
            double centring = 1.0 - Math.max(Math.abs(pr[0]), Math.abs(pr[1])) / FRAME;
            double near = 1.0 - d / maxD;
            double frontal = 1.0 - angle / s.faceAngle();
            double score = 0.4 * centring + 0.3 * near + 0.3 * frontal;
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (best != null) {
            int stars = bestScore >= 0.62 ? 3 : bestScore >= 0.38 ? 2 : 1;
            return new Shot(GameData.RESULT_CAPTURED, best, stars, best.kind().id + ": captured");
        }
        if (failed != null) return new Shot(GameData.RESULT_FAILED, failed, 0, reason);
        return new Shot(GameData.RESULT_EMPTY, null, 0, reason);
    }
}
