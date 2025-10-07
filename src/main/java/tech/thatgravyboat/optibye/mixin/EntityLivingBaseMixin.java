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
