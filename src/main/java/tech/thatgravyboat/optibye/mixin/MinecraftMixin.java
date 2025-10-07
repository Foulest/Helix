package tech.thatgravyboat.optibye.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Shadow
    private int rightClickDelayTimer;

    /**
     * Reduces the delay in ticks between right clicks.
     * <p>
     * 4 is the default in vanilla.
     * 2 is the fastest possible without causing issues.
     *
     * @param ci - The callback info.
     */
    @Inject(method = "runTick", at = @At("HEAD"))
    private void onRunTick(CallbackInfo ci) {
        if (Minecraft.getMinecraft().thePlayer != null) {
            ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();

            // Check if the held item isn't a sword or fishing rod
            if (held != null
                    && !held.getItem().getRegistryName().contains("sword")
                    && !held.getItem().getRegistryName().contains("rod")) {
                rightClickDelayTimer = Math.min(rightClickDelayTimer, 2);
            }
        }
    }
}
