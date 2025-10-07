package tech.thatgravyboat.optibye.mixin;

import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityLivingBase.class)
public class EntityLivingBaseMixin {

    @Shadow
    private int jumpTicks;

    /**
     * Reduces the delay in ticks between jumps.
     * <p>
     * 10 is the default in vanilla.
     * 2 is the fastest possible without causing issues.
     *
     * @param ci - The callback info.
     */
    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void onUpdate(CallbackInfo ci) {
        jumpTicks = Math.min(jumpTicks, 2);
    }
}
