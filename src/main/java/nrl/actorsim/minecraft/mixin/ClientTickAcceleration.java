package nrl.actorsim.minecraft.mixin;

import nrl.actorsim.minecraft.Overclocker;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderTickCounter.class)
public class ClientTickAcceleration {
    @Shadow public float tickDelta;
    @Shadow public float lastFrameDuration;
    @Shadow private long prevTimeMillis;

    //TODO make this use proper mixin rather than overwrite
    /**
     * @author NRL
     */
    @Overwrite
    public int beginRenderTick(long timeMillis) {
        this.lastFrameDuration = (float)(timeMillis - this.prevTimeMillis) / (Overclocker.getClockMs());
        this.prevTimeMillis = timeMillis;
        this.tickDelta += this.lastFrameDuration;
        int i = (int)this.tickDelta;
        this.tickDelta -= (float)i;
        return i;
    }
}
