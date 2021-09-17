package nrl.actorsim.minecraft.mixin;

import nrl.actorsim.minecraft.Overclocker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.util.TickDurationMonitor;
import net.minecraft.util.Util;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.apache.logging.log4j.Logger;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class ServerTickAcceleration extends ReentrantThreadExecutor<ServerTask> {

    @Shadow private volatile boolean running;
    @Shadow private long timeReference;

    public ServerTickAcceleration(String string) {
        super(string);
    }

    @Shadow protected abstract void tick(BooleanSupplier booleanSupplier_1);

    @Shadow @Final private static Logger LOGGER;

    @Shadow private volatile boolean loading;

    @Shadow private long lastTimeReference;

    @Shadow protected abstract boolean shouldKeepTicking();

    @Shadow private Profiler profiler;

    @Shadow private boolean waitingForNextTick;

    @Shadow protected abstract void startMonitor(@Nullable TickDurationMonitor monitor);

    @Shadow protected abstract void endMonitor(@Nullable TickDurationMonitor monitor);

    @Shadow private long field_19248;

    @Shadow protected abstract void method_16208();

    @Redirect(method = "runServer", at = @At(value = "FIELD", target = "Lnet/minecraft/server/MinecraftServer;running:Z"))
    private boolean cancelRunLoop(MinecraftServer server)
    {
        return false;
    } // target run()

    @Inject(method = "runServer", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/server/MinecraftServer;setFavicon(Lnet/minecraft/server/ServerMetadata;)V"))
    private void modifiedRunLoop(CallbackInfo ci)
    {
        while (this.running)
        {
            long speed = Overclocker.getClockMs();
            long l = Util.getMeasuringTimeMs() - this.timeReference;
            if (l > 2000L && this.timeReference - this.lastTimeReference >= 15000L) {
                long m = l / speed;
                LOGGER.warn("Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind", l, m);
                this.timeReference += m * speed;
                this.lastTimeReference = this.timeReference;
            }
            this.timeReference += speed;
            TickDurationMonitor tickDurationMonitor = TickDurationMonitor.create("Server");
            this.startMonitor(tickDurationMonitor);
            this.profiler.startTick();
            this.profiler.push("tick");
            this.tick(this::shouldKeepTicking);
            this.profiler.swap("nextTickWait");
            this.waitingForNextTick = true;
            this.field_19248 = Math.max(Util.getMeasuringTimeMs() + speed, this.timeReference);
            this.method_16208();
            this.profiler.pop();
            this.profiler.endTick();
            this.endMonitor(tickDurationMonitor);
            this.loading = true;
        }

    }
}
