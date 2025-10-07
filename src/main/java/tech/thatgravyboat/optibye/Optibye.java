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

import lombok.AllArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

// Helix by Foulest
// Version 1.0.3 (not reflected in mod ID)

@SuppressWarnings("MethodMayBeStatic")
@Mod(modid = "optibye", version = "1.0.0", clientSideOnly = true)
public class Optibye {

    // Feature toggles
    private static boolean tapEnabled = true;
    private static boolean blockEnabled = true;

    // Maximum number of actions in each queue
    private static final int MAX_QUEUE = 12;

    // Ensures MAX_QUEUE is even
    static {
        if ((MAX_QUEUE & 1) != 0) {
            throw new IllegalStateException("MAX_QUEUE must be even");
        }
    }

    /**
     * An action to press or release a key at a specific tick.
     */
    @AllArgsConstructor
    private static final class Action {

        final long tick;
        final boolean down;
    }

    // Action queues
    private static final Deque<Action> TAP_QUEUE = new ArrayDeque<>(MAX_QUEUE);
    private static final Deque<Action> BLOCK_QUEUE = new ArrayDeque<>(MAX_QUEUE);

    // Last applied logical state: -1 none, 0 RELEASE, 1 PRESS
    private static int tapAppliedState = -1;
    private static int blockAppliedState = -1;

    // Last scheduled tick for each queue
    private static long tapTailTick = -1;
    private static long blockTailTick = -1;

    // Last scheduled logical state: -1 none, 0 RELEASE, 1 PRESS
    private static int tapLastEnqState = -1;
    private static int blockLastEnqState = -1;

    // Jitter probability
    private static final double JITTER_PROB = 0.05;

    // Inactivity tracking
    private static long ticksSinceLastClick;
    private static final int MAX_INACTIVITY_TICKS = 3;
    private static long tapInactivityDeadline = -1;
    private static long blockInactivityDeadline = -1;

    /**
     * Mod pre-initialization event handler.
     *
     * @param event - The pre-initialization event
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Get the Minecraft instance.
     *
     * @return The Minecraft instance
     */
    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    /**
     * Get the forward key code.
     *
     * @return The forward key code, or Keyboard.KEY_W if not available
     */
    private static int forwardKey() {
        return mc().gameSettings != null ? mc().gameSettings.keyBindForward.getKeyCode() : Keyboard.KEY_W;
    }

    /**
     * Get the block key code.
     *
     * @return The block key code, or -99 if not available
     */
    private static int blockKey() {
        return mc().gameSettings != null ? mc().gameSettings.keyBindUseItem.getKeyCode() : -99;
    }

    /**
     * Get the physical key state for a given key code.
     *
     * @param keyCode - The key code to check
     * @return True if the key is physically down, false otherwise
     */
    private static boolean physicalDown(int keyCode) {
        if (keyCode < 0) {
            int button = keyCode + 100;

            if (button >= 0 && button < Mouse.getButtonCount()) {
                return Mouse.isButtonDown(button);
            }
        } else {
            if (keyCode < Keyboard.getKeyCount()) {
                return Keyboard.isKeyDown(keyCode);
            }
        }
        return false;
    }

    /**
     * Get the current world tick, or -1 if not available.
     *
     * @return The current world tick, or -1 if not available
     */
    private static long nowTick() {
        return mc().theWorld == null ? -1 : mc().theWorld.getTotalWorldTime();
    }

    /**
     * Offer a pair of actions to the deque if there is capacity.
     *
     * @param deque - The deque to offer to
     * @param a1 - The first action
     * @param a2 - The second action
     * @return True if the actions were added, false if there was insufficient capacity
     */
    private static boolean offerPair(@NotNull Deque<Action> deque, Action a1, Action a2) {
        if (deque.size() + 2 <= MAX_QUEUE) {
            deque.addLast(a1);
            deque.addLast(a2);
            return true;
        }
        return false;
    }

    /**
     * Process the action queue for a given key.
     *
     * @param queue - The action queue
     * @param keyCode - The key code to process
     * @param enabled - Whether the feature is enabled
     * @param nowTick - The current world tick
     * @param isTap - Whether this is the tap queue (true) or block queue (false)
     */
    private static void processQueue(Deque<Action> queue, int keyCode, boolean enabled,
                                     long nowTick, boolean isTap) {
        // Clear the queue if disabled
        if (!enabled) {
            queue.clear();
            return;
        }

        while (!queue.isEmpty() && queue.peekFirst().tick <= nowTick) {
            Action action = queue.removeFirst();
            int current = isTap ? tapAppliedState : blockAppliedState;
            int next = action.down ? 1 : 0;

            // Ignores duplicate states
            if (current == next) {
                continue;
            }

            // Applies the key state
            KeyBinding.setKeyBindState(keyCode, action.down);

            // Updates the applied state
            if (isTap) {
                tapAppliedState = next;
            } else {
                blockAppliedState = next;
            }

//            // Debug message
//            if (mc.thePlayer != null) {
//                mc.thePlayer.addChatMessage(new ChatComponentText((isTap ? "[W-Tap] " : "[Block Hit] ") +
//                        "tick=" + nowTick + " " + (action.down ? "PRESS" : "RELEASE")
//                ));
//            }
        }
    }

    /**
     * Reset all tick states and clear queues.
     */
    private void resetTickStates() {
        // Checks for exceptions to ignore
        if (!tapEnabled && !blockEnabled || mc().thePlayer == null) {
            return;
        }

        // Clear scheduled actions
        TAP_QUEUE.clear();
        BLOCK_QUEUE.clear();

        // Reset enqueue tails and logical state
        tapTailTick = -1;
        blockTailTick = -1;
        tapLastEnqState = -1;
        blockLastEnqState = -1;

        // Reset applied states
        tapAppliedState = -1;
        blockAppliedState = -1;

        // Reset inactivity
        tapInactivityDeadline = -1;
        blockInactivityDeadline = -1;

        // Reset key states to physical state
        if (mc().currentScreen == null) {
            KeyBinding.setKeyBindState(forwardKey(), physicalDown(forwardKey()));
            KeyBinding.setKeyBindState(blockKey(), physicalDown(blockKey()));
        } else {
            KeyBinding.setKeyBindState(forwardKey(), false);
            KeyBinding.setKeyBindState(blockKey(), false);
        }
    }

    /**
     * Client tick event handler.
     *
     * @param event - The client tick event
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // Checks for exceptions to ignore
        if (!tapEnabled && !blockEnabled
                || event.phase != TickEvent.Phase.END
                || mc().thePlayer == null) {
            return;
        }

        // Inactivity reset
        if (ticksSinceLastClick >= MAX_INACTIVITY_TICKS
                && (tapInactivityDeadline != -1 || blockInactivityDeadline != -1)) {
            resetTickStates();
        } else {
            ticksSinceLastClick++;
        }

        long now = nowTick();

        // Ignores if world tick is not available
        if (now < 0) {
            mc().thePlayer.addChatMessage(new ChatComponentText("Ignoring onClientTick"));
            return;
        }

        // Process queues
        processQueue(TAP_QUEUE, forwardKey(), tapEnabled, now, true);
        processQueue(BLOCK_QUEUE, blockKey(), blockEnabled, now, false);
    }

    /**
     * GUI open event handler to reset states.
     *
     * @param event - The GUI open event
     */
    @SubscribeEvent
    public void onOpenGUI(GuiOpenEvent event) {
        // Checks for exceptions to ignore
        if (!tapEnabled && !blockEnabled) {
            return;
        }

        // Resets states when opening a GUI
        if (mc() != null && mc().thePlayer != null) {
            resetTickStates();
        }
    }

    /**
     * Key input event handler to toggle features.
     *
     * @param event - The key input event
     */
    @SubscribeEvent
    public void onKeyInputEvent(InputEvent.KeyInputEvent event) {
        // Checks for exceptions to ignore
        if (mc().thePlayer == null || mc().currentScreen != null) {
            return;
        }

        // Toggles features when in third-person view
        if (mc().gameSettings.thirdPersonView == 1) {
            if (physicalDown(Keyboard.KEY_SEMICOLON)) {
                tapEnabled = !tapEnabled;
                mc().gameSettings.thirdPersonView = 2;
            }

            if (physicalDown(Keyboard.KEY_APOSTROPHE)) {
                blockEnabled = !blockEnabled;
                mc().gameSettings.thirdPersonView = 2;
            }
        }
    }

    /**
     * Global mouse event handler for on-click events.
     *
     * @param event - The mouse event
     */
    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        // Checks for exceptions to ignore
        if (mc().thePlayer == null
                || mc().currentScreen != null
                || !(event.button == 0 && event.buttonstate)
                || mc().objectMouseOver == null
                || !(mc().objectMouseOver.entityHit instanceof EntityPlayer)) {
            return;
        }

        ticksSinceLastClick = 0;
        long tick = nowTick();

        // Ignores if world tick is not available
        if (tick < 0) {
            mc().thePlayer.addChatMessage(new ChatComponentText("Ignoring onMouseEvent"));
            return;
        }

        EntityPlayer player = mc().thePlayer;

        // Handles W-Tap
        if (tapEnabled && player.isSprinting()) {
            handleWTap(tick);
        }

        // Handles Block Hit
        if (blockEnabled
                && player.getCurrentEquippedItem() != null
                && player.getCurrentEquippedItem().getUnlocalizedName().contains("sword")) {
            handleBlockHit(tick);
        }
    }

    /**
     * Handles W-Tap logic.
     *
     * @param tick - The current world tick
     */
    private static void handleWTap(long tick) {
        long tail = tapTailTick;
        boolean starting = tail < 0 && tapLastEnqState == -1;
        long base = starting ? tick : Math.max(tail, tick);

        // Adds jitter to avoid robotic timing
        if (!starting && ThreadLocalRandom.current().nextDouble() < JITTER_PROB) {
            base += 1;
        }

        // Enqueue pattern: RELEASE, then PRESS (base+1, base+2)
        long t1 = base + 1;
        long t2 = base + 2;
        boolean enq = offerPair(TAP_QUEUE, new Action(t1, false), new Action(t2, true));

        // Updates state if enqueued
        if (enq) {
            tapTailTick = base + 2;
            tapLastEnqState = 1;
            tapInactivityDeadline = tapTailTick + 3;
        }
    }

    /**
     * Handles Block Hit logic.
     *
     * @param tick - The current world tick
     */
    private static void handleBlockHit(long tick) {
        long tail = blockTailTick;
        boolean starting = tail < 0 && blockLastEnqState == -1;
        long base = starting ? tick : Math.max(tail, tick);

        // Enqueue pattern: PRESS, then RELEASE (base+1, base+2)
        long t1 = base + 1;
        long t2 = base + 2;
        boolean enq = offerPair(BLOCK_QUEUE, new Action(t1, true), new Action(t2, false));

        // Updates state if enqueued
        if (enq) {
            blockTailTick = base + 2;
            blockLastEnqState = 1;
            blockInactivityDeadline = blockTailTick + 3;
        }
    }
}
