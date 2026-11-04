package jp.satsujinki.client.sound;

import jp.satsujinki.block.entity.TrapBlockEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Looping machine noise that follows a running trap and stops with it. */
public class TrapLoopSound extends AbstractTickableSoundInstance {
    private final TrapBlockEntity be;
    private int fade = 10;

    public TrapLoopSound(TrapBlockEntity be, SoundEvent sound) {
        super(sound, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.be = be;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.1F;
        this.pitch = 1.0F;
        BlockPos p = be.getBlockPos();
        this.x = p.getX() + 0.5D;
        this.y = p.getY() + 1.0D;
        this.z = p.getZ() + 0.5D;
    }

    @Override
    public void tick() {
        if (this.be.isRemoved() || !this.be.isRunning()) {
            if (--this.fade <= 0) this.stop();
            this.volume = Math.max(0.0F, this.fade / 10.0F * 1.3F);
            return;
        }
        this.volume = Math.min(1.3F, this.volume + 0.1F);
    }
}
