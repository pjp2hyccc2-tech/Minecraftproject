package com.eternalrealm.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Drives a {@link BossBrain} tick by tick and records every command with the tick it was issued at. */
final class FightLog {
    record Entry(long tick, BossCommand command) {
    }

    final BossBrain brain;
    final List<Entry> entries = new ArrayList<>();
    long now;
    double hp = 1.0;
    BossPosition self = BossFixtures.AT_ORIGIN;
    List<TargetSnapshot> targets;

    FightLog(BossBrain brain, List<TargetSnapshot> targets, long start) {
        this.brain = brain;
        this.targets = targets;
        this.now = start - 1;
    }

    /** Runs the next tick and returns its commands. */
    List<BossCommand> step() {
        now++;
        List<BossCommand> commands = brain.tick(now, hp, self, targets);
        for (BossCommand command : commands) {
            entries.add(new Entry(now, command));
        }
        return commands;
    }

    /** Runs ticks up to and including {@code target}. */
    void runTo(long target) {
        while (now < target) {
            step();
        }
    }

    <T extends BossCommand> List<T> all(Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Entry entry : entries) {
            if (type.isInstance(entry.command())) {
                found.add(type.cast(entry.command()));
            }
        }
        return found;
    }

    List<Long> ticksOf(Predicate<BossCommand> filter) {
        List<Long> ticks = new ArrayList<>();
        for (Entry entry : entries) {
            if (filter.test(entry.command())) {
                ticks.add(entry.tick());
            }
        }
        return ticks;
    }

    List<Long> startTicks(String abilityId) {
        return ticksOf(command -> command instanceof BossCommand.AbilityStarted started
                && started.ability().id().equals(abilityId));
    }

    List<Long> startTicks() {
        return ticksOf(command -> command instanceof BossCommand.AbilityStarted);
    }
}
