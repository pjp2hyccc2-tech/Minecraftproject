package jp.satsujinki.game;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.util.Locale;

/** Contestant dialogue. The text lives in the lang files (line.satsujinki.&lt;category&gt;.&lt;n&gt;). */
public final class Lines {
    public enum Cat {
        CALM(8), ANXIOUS(8), PANIC(10), BEG(8), BRIBE(4), GRABBED(8), WITNESS(8), HURT(6), FIGHT(6),
        SURVIVE(4), TRAP(6), LOCKED(4), THROWN(4);

        public final int count;

        Cat(int count) {
            this.count = count;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private Lines() {
    }

    public static Component random(Cat cat, RandomSource r) {
        return Component.translatable("line.satsujinki." + cat.key() + "." + r.nextInt(cat.count));
    }

    public static Component intro(ContestantType type, RandomSource r) {
        return Component.translatable("line.satsujinki.intro." + type.id + "." + r.nextInt(2));
    }
}
