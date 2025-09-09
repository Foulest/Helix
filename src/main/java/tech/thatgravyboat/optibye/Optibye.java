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

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// Helix by Foulest
// Version 1.0.2 (not reflected in mod ID)

@Mod(modid = "optibye", version = "1.0.0", clientSideOnly = true)
public class Optibye {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int forwardKey = mc.gameSettings.keyBindForward.getKeyCode();
    private static final int rightClickKey = mc.gameSettings.keyBindUseItem.getKeyCode();

    // Module toggles
    private static boolean blockEnabled = true;
    private static boolean tapEnabled = true;

    private static final ScheduledThreadPoolExecutor BLOCK_SCHEDULER = new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private static final ScheduledThreadPoolExecutor TAP_SCHEDULER = new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    static {
        BLOCK_SCHEDULER.setRemoveOnCancelPolicy(true);
        TAP_SCHEDULER.setRemoveOnCancelPolicy(true);
    }

    public Optibye() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    // Inactivity timeout to reset the W-tap pattern (in milliseconds)
    private static final long INACTIVITY_MS = 250L;

    // 50 ms grid (metronome) in nanoseconds
    private static final long SLOT_NS = TimeUnit.MILLISECONDS.toNanos(51L);
    private static final long GUARD_NS = TimeUnit.MILLISECONDS.toNanos(2L); // if <2ms to slot, use next slot

    // Grid origin and tail slot index (>=0 means the last scheduled slot)
    private static final AtomicLong gridOriginNs = new AtomicLong(0L);
    private static final AtomicLong tailSlotIdx = new AtomicLong(-1L);

    // Invalidates any queued key tasks when incremented
    private static final AtomicLong epoch = new AtomicLong(1L);

    // Atomic states for scheduling W-tap patterns
    private static final AtomicLong tailAtNanos = new AtomicLong(0L);
    private static final AtomicLong resetGen = new AtomicLong(0L);
    private static final AtomicLong resetDeadlineNanos = new AtomicLong(0L);

    // Last scheduled state: -1 = none, 0 = RELEASE, 1 = PRESS
    private static final AtomicInteger lastState = new AtomicInteger(-1);

    // Use a lock to make enqueue decisions atomically
    private static final Object ENQ_LOCK = new Object();

//    // Track last sprinting state for debug messages
//    private static boolean lastSprinting;

    /**
     * Handles key input events to toggle the mod on and off.
     *
     * @param event - The KeyInputEvent triggered by keyboard input.
     */
    @SubscribeEvent
    public void onKeyInputEvent(InputEvent.KeyInputEvent event) {
        // Makes sure the player is in game and not in a menu
        if (mc.thePlayer == null || mc.currentScreen != null) {
            return;
        }

        if (mc.gameSettings.thirdPersonView == 1) {
            // Toggles the block hit feature on/off with the apostrophe key
            if (Keyboard.isKeyDown(Keyboard.KEY_APOSTROPHE)) {
                blockEnabled = !blockEnabled;
                mc.gameSettings.thirdPersonView = 2;
            }

            // Toggles the W-Tap feature on/off with the semicolon key
            if (Keyboard.isKeyDown(Keyboard.KEY_SEMICOLON)) {
                tapEnabled = !tapEnabled;
                mc.gameSettings.thirdPersonView = 2;
            }
        }
    }

    /**
     * Handles mouse events for the Auto Block Hit module.
     *
     * @param event - The MouseEvent triggered by mouse input.
     */
    @SubscribeEvent
    public void onSwordHit(MouseEvent event) {
        // Makes sure the mod is enabled
        if (!blockEnabled) {
            return;
        }

        // Makes sure the player is in game and not in a menu
        if (mc.thePlayer == null || mc.currentScreen != null) {
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

        // Returns if the player isn't left-clicking
        if (!(event.button == 0 && event.buttonstate)) {
            return;
        }

        boolean holdingSword = mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem().getRegistryName().contains("sword");

        // Release right-click if holding a sword
        if (holdingSword) {
            KeyBinding.setKeyBindState(rightClickKey, false);
        }

        // Simulate right-click press
        BLOCK_SCHEDULER.schedule(() -> mc.addScheduledTask(() -> {
            if (holdingSword) {
                KeyBinding.setKeyBindState(rightClickKey, true);

//                // Debug message
//                mc.thePlayer.addChatMessage(new ChatComponentText("§e[Helix] §aBlock hitting..."));
            }
        }), 0L, TimeUnit.MILLISECONDS);

        // Simulate right-click release
        BLOCK_SCHEDULER.schedule(() -> mc.addScheduledTask(() -> {
            KeyBinding.setKeyBindState(rightClickKey, false);
        }), 50L, TimeUnit.MILLISECONDS);
    }

    /**
     * Handles GUI open events to reset states when entering a GUI.
     *
     * @param event - The GuiOpenEvent triggered when a GUI is opened.
     */
    @SubscribeEvent
    public void onOpenGUI(GuiOpenEvent event) {
        // Reset states when a player enters a GUI
        if (mc != null && mc.thePlayer != null) {
            resetTickStates();
        }
    }

    /**
     * Handles mouse events for the Auto W-Tap module.
     *
     * @param event - The MouseEvent triggered by mouse input.
     */
    @SubscribeEvent
    public void onSprintHit(MouseEvent event) {
        // Makes sure the mod is enabled
        if (!tapEnabled) {
            return;
        }

        // Makes sure the player is in game and not in a menu
        if (mc.thePlayer == null || mc.currentScreen != null) {
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

        // Returns if the player isn't left-clicking
        if (!(event.button == 0 && event.buttonstate)) {
            return;
        }

        // Enqueue the W-tap pattern
        enqueueWTapPattern();
    }

    /**
     * Enqueues the W-tap pattern based on the current time and state.
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
            long origin = ensureGridOrigin(now);
            boolean startingBurst = (tailAtNanos.get() == 0L && lastState.get() == -1);
            long tailSlot = tailSlotIdx.get(); // -1 means no slots used yet

            if (startingBurst) {
                // First enqueue of a burst:
                // RELEASE at next safe grid slot, PRESS at the following slot (exact +50 ms).
                long releaseSlot = nextSafeSlot(now, origin, slotIndexAt(now, origin));
                long pressSlot = releaseSlot + 1;

                long releaseAt = slotToTime(releaseSlot, origin);
                long pressAt = slotToTime(pressSlot, origin);

                timesToSchedule = new long[]{releaseAt, pressAt};
                statesToSchedule = new boolean[]{false, true};

                tailSlotIdx.set(pressSlot);
                tailAtNanos.set(pressAt);
                lastState.set(1);
            } else {
                // We’re mid-burst: append on the 50ms grid, not "now".
                long baseSlot = Math.max(tailSlot, slotIndexAt(now, origin));

                if (lastState.get() == 0) {
                    // last was RELEASE -> only PRESS next
                    long pressSlot = nextSafeSlot(now, origin, baseSlot + 1);
                    long pressAt = slotToTime(pressSlot, origin);

                    timesToSchedule = new long[]{pressAt};
                    statesToSchedule = new boolean[]{true};

                    tailSlotIdx.set(pressSlot);
                    tailAtNanos.set(pressAt);
                    lastState.set(1);
                } else {
                    // last was PRESS -> RELEASE then PRESS on successive slots
                    long releaseSlot = nextSafeSlot(now, origin, baseSlot + 1);
                    long pressSlot = releaseSlot + 1;

                    long releaseAt = slotToTime(releaseSlot, origin);
                    long pressAt = slotToTime(pressSlot, origin);

                    timesToSchedule = new long[]{releaseAt, pressAt};
                    statesToSchedule = new boolean[]{false, true};

                    tailSlotIdx.set(pressSlot);
                    tailAtNanos.set(pressAt);
                    lastState.set(1);
                }
            }
        }

        // Schedule outside the lock
        for (int i = 0; i < timesToSchedule.length; i++) {
            long when = timesToSchedule[i];
            boolean down = statesToSchedule[i];
            long delay = Math.max(0L, when - System.nanoTime());
            long myEpoch = epoch.get();

            TAP_SCHEDULER.schedule(() -> mc.addScheduledTask(() -> {
                // If we were reset after scheduling, drop this task.
                if (myEpoch != epoch.get()) {
                    return;
                }

                KeyBinding.setKeyBindState(forwardKey, down);
//                boolean sprinting = mc.thePlayer.isSprinting();
//
//                // Debug message
//                mc.thePlayer.addChatMessage(new ChatComponentText("§e[Helix] §f["
//                        + (delay / 1000000) + "ms] " + (down ? "§aPRESS" : "§cRELEASE")
//                        + " §7(" + sprinting + ") "
//                        + (lastSprinting == sprinting ? "§c✘" : "§a✔")
//                ));
//
//                lastSprinting = sprinting;
            }), delay, TimeUnit.NANOSECONDS);
        }

        // Inactivity watchdog - resets the burst so the next hit restarts with the single 0ms RELEASE
        TAP_SCHEDULER.schedule(() -> {
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
     * Resets the tick states related to the W-tap pattern.
     */
    public static void resetTickStates() {
        // Makes sure the player is in game
        if (mc.thePlayer == null) {
            return;
        }

        // Makes sure there's even a need to reset the W-tap pattern
        if (tailAtNanos.get() == 0L && lastState.get() == -1) {
            return;
        }

//        // Debug message
//        mc.thePlayer.addChatMessage(new ChatComponentText("§e[Helix] §7§oResetting W-tap pattern..."));

        // Invalidate all previously scheduled key tasks
        epoch.incrementAndGet();

        // Reset the tail and last state
        tailAtNanos.set(0L);
        lastState.set(-1);

        // Reset the slot index and grid origin
        tailSlotIdx.set(-1L);
        gridOriginNs.set(0L);

        // Sets the key to false if in a GUI, otherwise to the physical state
        if (mc.currentScreen == null) {
            boolean physicalDown = Keyboard.isKeyDown(forwardKey);
            mc.addScheduledTask(() -> KeyBinding.setKeyBindState(forwardKey, physicalDown));
        } else {
            mc.addScheduledTask(() -> KeyBinding.setKeyBindState(forwardKey, false));
        }
    }

    /**
     * Ensures that the grid origin is set, aligning it to the nearest SLOT_NS interval.
     *
     * @param now - The current time in nanoseconds.
     * @return The grid origin time in nanoseconds.
     */
    private static long ensureGridOrigin(long now) {
        long cur = gridOriginNs.get();

        if (cur != 0L) {
            return cur;
        }

        long origin = now - (now % SLOT_NS); // align origin to the 50ms grid
        gridOriginNs.compareAndSet(0L, origin);
        return gridOriginNs.get();
    }

    /**
     * Calculates the slot index for a given time based on the origin.
     *
     * @param now - Current time in nanoseconds.
     * @param origin - Origin time in nanoseconds.
     * @return The slot index.
     */
    private static long slotIndexAt(long now, long origin) {
        return (now - origin) / SLOT_NS;
    }

    /**
     * Converts a slot index back to a time in nanoseconds based on the origin.
     *
     * @param slotIdx - The slot index.
     * @param origin - The origin time in nanoseconds.
     * @return The corresponding time in nanoseconds.
     */
    private static long slotToTime(long slotIdx, long origin) {
        return origin + slotIdx * SLOT_NS;
    }

    /**
     * Finds the next safe slot index that is at least `atLeastSlot` and not too close to `now`.
     *
     * @param now - Current time in nanoseconds.
     * @param origin - Origin time in nanoseconds.
     * @param atLeastSlot - Minimum slot index to consider.
     * @return The next safe slot index.
     */
    private static long nextSafeSlot(long now, long origin, long atLeastSlot) {
        long slot = Math.max(slotIndexAt(now, origin), atLeastSlot);
        long t = slotToTime(slot, origin);

        // If we are too close to the slot time, move to the next slot
        if (t - now <= GUARD_NS) {
            slot += 1;
        }
        return slot;
    }
}
