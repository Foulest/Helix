/*
 * Helix - a combat assistance mod for Minecraft 1.8.9.
 * Copyright (C) 2025 Foulest (https://github.com/Foulest)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
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
