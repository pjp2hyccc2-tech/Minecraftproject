package com.reisatsu.game;

import com.reisatsu.block.Props;
import com.reisatsu.entity.Manager;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.entity.Waitress;
import com.reisatsu.registry.ModItems;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Circuit;
import com.reisatsu.world.FixtureEvents;
import com.reisatsu.world.Fixtures;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** What the restaurant tells the game (installed as {@link FixtureEvents#LISTENER} at startup). */
public final class GameFixtureListener implements FixtureEvents.Listener {
    @Override
    public void interact(ServerPlayer player, String key, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!GameManager.inGame(player)) return;
        GameData data = GameData.get(player.server);
        switch (key) {
            case "breaker" -> Breaker.open(player);
            case "phone" -> {
                // the world already cleared the ringing: the director remembers whether it rang
                if (Director.answerPhone()) {
                    Fixtures.setDevice(level, "phone", false);
                    GameManager.privateSound(player, ModSounds.FIXTURE_PHONE_VOICE.get(), player.getEyePosition(), 0.9f, 1.0f);
                    Story.say(player, "phone", 90, "dialogue.reisatsu.phone.1", "dialogue.reisatsu.phone.2");
                    Director.witness(player, "phone");
                    GameManager.addFear(player, 0.2f);
                }
            }
            case "register_drawer" -> {
                // film pack 1 lies in the register drawer (the world toggled the drawer before telling us)
                BlockState s = level.getBlockState(pos);
                boolean open = !s.hasProperty(Props.OPEN) || s.getValue(Props.OPEN);
                if (open) giveFilm(player, data, 1);
            }
            case "menu_board" -> GameManager.event(player, GameManager.EV_THOUGHT, 0,
                    data.happened("menu_board") ? "thought.reisatsu.menu_board_2" : "thought.reisatsu.menu_board");
            case "time_recorder" -> GameManager.event(player, GameManager.EV_THOUGHT, 0, "thought.reisatsu.time_recorder");
            default -> {
            }
        }
    }

    /** Film pack n (1..3, docs §2 film_n) found: once per night. */
    static void giveFilm(ServerPlayer player, GameData data, int n) {
        int bit = 1 << n;
        if ((data.filmTaken & bit) != 0) return;
        data.filmTaken |= bit;
        data.setDirty();
        GameManager.give(player, new ItemStack(ModItems.FILM_PACK.get()));
        GameManager.event(player, GameManager.EV_THOUGHT, 0, "thought.reisatsu.film_found");
    }

    @Override
    public void noise(ServerLevel level, Vec3 pos, float radius, String cause, @Nullable Entity source) {
        NoiseSystem.emit(level, pos, radius, cause, source);
    }

    @Override
    public boolean hide(ServerPlayer player, BlockPos locker) {
        // outside the game a locker is just a locker
        if (!GameManager.inGame(player)) return true;
        if (!Hiding.enter(player, locker)) return false;
        // film pack 2 waits in one of the staff lockers
        if (com.reisatsu.world.Layout.has("film_2") && com.reisatsu.world.Layout.marker("film_2").pos().equals(locker)) {
            giveFilm(player, GameData.get(player.server), 2);
        }
        return true;
    }

    @Override
    public void unhide(ServerPlayer player, BlockPos locker) {
        // the world plays the door, the noise and the flap
        Hiding.exit(player, false);
    }

    @Override
    public void note(ServerPlayer player, String noteId) {
        GameManager.openNote(player, noteId);
    }

    @Override
    public boolean canUnlock(ServerPlayer player, String lock) {
        if (player.isCreative()) return true;
        return switch (lock) {
            case "office" -> GameManager.has(player, ModItems.OFFICE_KEY.get());
            case "back_door", "door_switch" -> GameManager.has(player, ModItems.SHOP_KEY.get());
            default -> true;
        };
    }

    @Override
    public void autoDoorOpened(ServerLevel level, String door, @Nullable Entity cause) {
        if (!GameManager.isGameLevel(level)) return;
        // the waitress goes to greet whoever came in
        if (Spirits.get(level, SpiritKind.WAITRESS) instanceof Waitress w) w.onDoorChime();
        if (Spirits.get(level, SpiritKind.MANAGER) instanceof Manager m) m.onDoorChime(level);
        if (cause == null) {
            for (ServerPlayer p : GameManager.players(level)) if (p.distanceToSqr(Vec3.atCenterOf(com.reisatsu.world.Layout.marker("door_inner").pos())) < 24 * 24) Director.witness(p, "door");
        }
    }

    @Override
    public void circuitChanged(ServerLevel level, Circuit circuit, boolean on) {
        circuitSwitched(level, circuit, on);
    }

    /** The game's reaction to a circuit switching (idempotent: may be reported twice). */
    static void circuitSwitched(ServerLevel level, Circuit circuit, boolean on) {
        if (!GameManager.isGameLevel(level)) return;
        if (circuit == Circuit.CAMERAS && Spirits.get(level, SpiritKind.MANAGER) instanceof Manager m) m.camerasSwitched(on);
        if (!on) {
            for (ServerPlayer p : GameManager.players(level)) {
                if (GameManager.inGame(p)) GameManager.addFear(p, 0.06f);
            }
        }
    }
}
