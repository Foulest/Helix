/*
 * Helix - an auto W-Tap mod for Minecraft 1.8.9.
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
package tech.thatgravyboat.optibye;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// Helix by Foulest
// Version 1.0.1 (not reflected in mod ID)

@Mod(modid = "optibye", version = "1.0.0", clientSideOnly = true)
public class Optibye {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int forwardKey = mc.gameSettings.keyBindForward.getKeyCode();
    private static boolean enabled = true;

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    });

    public Optibye() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    // Inactivity timeout to reset the W-tap pattern (in milliseconds)
    private static final long INACTIVITY_MS = 250L;

    // Atomic states for scheduling W-tap patterns
    private static final AtomicLong tailAtNanos = new AtomicLong(0L);
    private static final AtomicLong resetGen = new AtomicLong(0L);
    private static final AtomicLong resetDeadlineNanos = new AtomicLong(0L);

    // Last scheduled state: -1 = none, 0 = RELEASE, 1 = PRESS
    private static final AtomicInteger lastState = new AtomicInteger(-1);

    // Use a lock to make enqueue decisions atomically
    private static final Object ENQ_LOCK = new Object();

    /**
     * Handles key input events to toggle the mod on and off when the semicolon key is pressed in third person view.
     *
     * @param event - The KeyInputEvent triggered by keyboard input.
     */
    @SubscribeEvent
    public void onKeyInputEvent(InputEvent.KeyInputEvent event) {
        // Makes sure the player is in game and not in a menu
        if (mc.thePlayer == null || mc.currentScreen != null) {
            return;
        }

        // Toggle the mod on/off with the semicolon key when in third person view
        if (mc.gameSettings.thirdPersonView == 1) {
            if (Keyboard.isKeyDown(Keyboard.KEY_SEMICOLON)) {
                enabled = !enabled;
                mc.gameSettings.thirdPersonView = 2;
            }
        }
    }

    /**
     * Handles GUI open events to reset the W-tap states when entering a GUI.
     *
     * @param event - The GuiOpenEvent triggered when a GUI is opened.
     */
    @SubscribeEvent
    public void onOpenGUI(GuiOpenEvent event) {
        // Reset states when entering a GUI
        resetTickStates();
    }

    /**
     * Handles mouse events to perform a 'W-tap' when the player is sprinting and left-clicking an entity.
     *
     * @param event - The MouseEvent triggered by mouse input.
     */
    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        // Makes sure the mod is enabled
        if (!enabled) {
            return;
        }

        // Makes sure the player is in game and not in a menu
        if (mc.thePlayer == null || mc.currentScreen != null) {
            return;
        }

        // Returns if the player isn't left-clicking
        if (!(event.button == 0 && event.buttonstate)) {
            return;
        }

        // Returns if the player isn't sprinting
        if (!mc.thePlayer.isSprinting()) {
            return;
        }

        // Returns if the player isn't hitting an entity
        if (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null) {
            return;
        }

        Entity target = mc.objectMouseOver.entityHit;

        // Returns if the target isn't a player
        if (!(target instanceof EntityPlayer)) {
            return;
        }

        // Enqueue the W-tap pattern
        enqueueWTapPattern();
    }

    /**
     * Enqueues a W-tap pattern of key presses and releases to simulate a quick stop and start while sprinting.
     * <p>
     * The pattern consists of a RELEASE followed by a PRESS, with timing to ensure proper alternation.
     * If the last action was a PRESS, it adds a RELEASE before the PRESS to maintain the pattern.
     * An inactivity watchdog resets the pattern if no new actions are enqueued within a specified timeout.
     */
    private static void enqueueWTapPattern() {
        long now = System.nanoTime();

        // Update the inactivity deadline
        long deadline = now + TimeUnit.MILLISECONDS.toNanos(INACTIVITY_MS);
        resetDeadlineNanos.set(deadline);

        // Increment the reset generation to invalidate old watchdogs
        long myGen = resetGen.incrementAndGet();

        long[] timesToSchedule;
        boolean[] statesToSchedule; // false = RELEASE, true = PRESS

        synchronized (ENQ_LOCK) {
            long tail = tailAtNanos.get();
            boolean startingBurst = (tail == 0L && lastState.get() == -1);
            long base = startingBurst ? now : Math.max(tail, now);

            if (startingBurst) {
                // First enqueue of a burst:
                // 0ms RELEASE (once), +25ms PRESS. Ends on PRESS, alternates properly.
                timesToSchedule = new long[]{base, base + TimeUnit.MILLISECONDS.toNanos(25)};
                statesToSchedule = new boolean[]{false, true};
                tailAtNanos.set(timesToSchedule[1]);   // tail = last scheduled time (PRESS)
                lastState.set(1);                      // last = PRESS
            } else {
                if (lastState.get() == 0) {
                    // Last was RELEASE -> only need a PRESS to end on PRESS, keep alternation.
                    timesToSchedule = new long[]{base + TimeUnit.MILLISECONDS.toNanos(25)};
                    statesToSchedule = new boolean[]{true};
                    tailAtNanos.set(timesToSchedule[0]);
                    lastState.set(1);
                } else {
                    // Last was PRESS -> must insert RELEASE then PRESS (no back-to-back PRESS)
                    timesToSchedule = new long[]{
                            base + TimeUnit.MILLISECONDS.toNanos(25),
                            base + TimeUnit.MILLISECONDS.toNanos(50)
                    };
                    statesToSchedule = new boolean[]{false, true};
                    tailAtNanos.set(timesToSchedule[1]);
                    lastState.set(1);
                }
            }
        }

        // Schedule outside the lock
        for (int i = 0; i < timesToSchedule.length; i++) {
            long delay = Math.max(0L, timesToSchedule[i] - now);
            boolean down = statesToSchedule[i];
            SCHEDULER.schedule(() -> mc.addScheduledTask(() -> KeyBinding.setKeyBindState(forwardKey, down)), delay, TimeUnit.NANOSECONDS);
        }

        // Inactivity watchdog - resets the burst so the next hit restarts with the single 0ms RELEASE
        SCHEDULER.schedule(() -> {
            if (resetGen.get() == myGen) {
                long cur = System.nanoTime();

                // If we are past the deadline, reset the burst state
                if (cur >= resetDeadlineNanos.get()) {
                    resetTickStates();
                }
            }
        }, INACTIVITY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Resets the tick states related to the W-tap pattern,
     * including the tail time, last state, and active aim target.
     */
    public static void resetTickStates() {
        // Reset the tail and last state
        tailAtNanos.set(0L);
        lastState.set(-1);

        // Sets the key to false if in a GUI, otherwise to the physical state
        if (mc.currentScreen == null) {
            boolean physicalDown = Keyboard.isKeyDown(forwardKey);
            mc.addScheduledTask(() -> KeyBinding.setKeyBindState(forwardKey, physicalDown));
        } else {
            mc.addScheduledTask(() -> KeyBinding.setKeyBindState(forwardKey, false));
        }
    }
}
