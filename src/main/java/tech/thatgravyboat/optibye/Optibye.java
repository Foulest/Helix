package tech.thatgravyboat.optibye;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mod(modid = "optibye", version = "1.0.0", clientSideOnly = true)
public class Optibye {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static boolean enabled = true;

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    });

    public Optibye() {
        MinecraftForge.EVENT_BUS.register(this);
    }

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

    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        // Only run if the mod is enabled
        if (!enabled) {
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

        // Returns if the target is silent (armor stands, paintings, etc.)
        if (target.isSilent()) {
            return;
        }

        // Simulates a quick tap of the forward key to perform a 'W-tap'
        int code = mc.gameSettings.keyBindForward.getKeyCode();
        SCHEDULER.schedule(() -> mc.addScheduledTask(() -> KeyBinding.setKeyBindState(code, false)), 0L, TimeUnit.MILLISECONDS);
        SCHEDULER.schedule(() -> mc.addScheduledTask(() -> KeyBinding.setKeyBindState(code, true)), 50L, TimeUnit.MILLISECONDS);
    }
}
