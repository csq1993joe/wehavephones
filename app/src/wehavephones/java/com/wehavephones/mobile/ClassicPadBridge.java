package com.wehavephones.mobile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.wehavephones.XServerDisplayActivity;
import com.wehavephones.container.Container;
import com.wehavephones.inputcontrols.ControlsProfile;
import com.wehavephones.inputcontrols.ExternalController;
import com.wehavephones.inputcontrols.GamepadState;
import com.wehavephones.widget.InputControlsView;
import com.wehavephones.winhandler.MouseEventFlags;
import com.wehavephones.winhandler.WinHandler;
import com.wehavephones.xserver.Pointer;
import com.wehavephones.xserver.XKeycode;
import com.wehavephones.xserver.XServer;

import java.io.File;
import java.io.IOException;

/**
 * Plays the controller, or the on-screen gamepad, as keyboard and mouse while
 * a client imported without Battle.net runs that cannot read a gamepad (1.x
 * to 8.x). A poller follows the game process like {@link GlueControllerBridge}
 * and picks the {@link ClassicPadLayout} from Settings, the client's build and
 * its controller add-on; WinHandler's input then drives a {@link ClassicPad}
 * on the UI thread. Retail-era clients, Battle.net games and the "Xbox
 * controller" setting are left alone: the guest keeps its virtual Xbox
 * controller, which it gets in every case.
 */
public final class ClassicPadBridge {
    private static final String TAG = "WeHavePhonesClassicPad";
    private static final long POLL_MS = 1000;
    private static final long TICK_MS = 16;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable TICK = ClassicPadBridge::tick;
    private static final ClassicPad.Input INPUT = new ClassicPad.Input();
    private static Thread poller;
    private static volatile boolean importedClientRunning;
    // UI thread only.
    private static ClassicPad pad;
    private static XServerDisplayActivity padActivity;
    private static boolean ticking;

    private ClassicPadBridge() {}

    /** Session start; the poller stops when the session activity is replaced or destroyed. */
    public static synchronized void start(XServerDisplayActivity activity) {
        if (poller != null && poller.isAlive()) poller.interrupt();
        final Context context = activity.getApplicationContext();
        poller = new Thread(() -> poll(context, activity), "wehavephones-classic-pad");
        poller.setDaemon(true);
        poller.start();
    }

    /** Session end (UI thread): lets go of every key the pad holds. */
    public static void stop(XServerDisplayActivity activity) {
        if (padActivity == activity) deactivate();
    }

    /** The session went to the background (UI thread): no key may stay held while the player is away. */
    public static void onSessionPaused(XServerDisplayActivity activity) {
        if (pad == null || padActivity != activity) return;
        pad.releaseAll();
        HANDLER.removeCallbacks(TICK);
        ticking = false;
        // Buttons let go of while the session was hidden never send an event.
        WinHandler winHandler = activity.getWinHandler();
        ExternalController controller = winHandler != null ? winHandler.getCurrentController() : null;
        if (controller != null) controller.releaseInput();
    }

    /** Physical or on-screen gamepad input that WinHandler consumed; runs on the UI thread. */
    public static void onControllerInput(XServerDisplayActivity activity) {
        ClassicPad current = pad;
        if (current == null || padActivity != activity) return;
        if (!read(activity, INPUT)) {
            // The controller is gone: nothing may stay pressed.
            current.releaseAll();
            return;
        }
        current.update(INPUT);
        scheduleTicks();
    }

    /** The layout in use, or null while the controller reaches the game as an Xbox controller. */
    public static ClassicPadLayout activeLayout() {
        ClassicPad current = pad;
        return current != null ? current.layout() : null;
    }

    /** A client imported without Battle.net is running in the session, whatever the controller does. */
    public static boolean isImportedClientRunning() {
        return importedClientRunning;
    }

    // ---- input --------------------------------------------------------------------------

    /**
     * Fills {@code input} with what the guest's gamepad shows. A physical
     * controller also gives its D-pad without the left-stick guard and its
     * analog triggers. False when there is no gamepad.
     */
    private static boolean read(XServerDisplayActivity activity, ClassicPad.Input input) {
        WinHandler winHandler = activity.getWinHandler();
        GamepadState state = winHandler != null ? winHandler.getGamepadState() : null;
        if (state == null) return false;
        ExternalController controller = winHandler.getCurrentController();
        InputControlsView view = activity.getInputControlsView();
        ControlsProfile profile = view != null ? view.getProfile() : null;
        boolean physical = controller != null && state == controller.state
                && (profile == null || !profile.isVirtualGamepad());
        input.buttons = state.buttons & 0xFFFF;
        boolean[] dpad = physical ? controller.rawDpad : state.dpad;
        System.arraycopy(dpad, 0, input.dpad, 0, 4);
        input.leftX = state.thumbLX;
        input.leftY = state.thumbLY;
        input.rightX = state.thumbRX;
        input.rightY = state.thumbRY;
        input.leftTrigger = physical ? controller.leftTrigger : 0;
        input.rightTrigger = physical ? controller.rightTrigger : 0;
        return true;
    }

    private static void scheduleTicks() {
        if (ticking || pad == null || !pad.needsTicks()) return;
        ticking = true;
        HANDLER.post(TICK);
    }

    private static void tick() {
        ticking = false;
        ClassicPad current = pad;
        if (current == null) return;
        current.tick(SystemClock.uptimeMillis());
        if (current.needsTicks()) {
            ticking = true;
            HANDLER.postDelayed(TICK, TICK_MS);
        }
    }

    // ---- activation ---------------------------------------------------------------------

    private static void poll(Context context, XServerDisplayActivity activity) {
        int pid = -1;
        WowProduct client = null;
        ClassicPadLayout automatic = null;
        String applied = null;
        try {
            while (!Thread.currentThread().isInterrupted() && XServerDisplayActivity.getActiveInstance() == activity) {
                if (pid != -1 && !new File("/proc/" + pid).exists()) {
                    pid = -1;
                    client = null;
                    automatic = null;
                }
                if (pid == -1) {
                    ProcessScanner.Entry wow = ProcessScanner.findWowProcess();
                    if (wow != null) {
                        pid = wow.pid;
                        client = importedClient(context, wow.pid);
                        // Add-ons only load at game start, so the automatic choice holds until the game exits.
                        automatic = client != null ? ClassicPadLayout.automatic(client.getVersion(), client.directory) : null;
                        if (client != null) {
                            Log.i(TAG, client.getDisplayName() + " " + client.getVersion() + ": automatic layout "
                                    + (automatic != null ? automatic.id : "none (reads the gamepad itself)"));
                        }
                    }
                }
                importedClientRunning = client != null;
                ClassicPadLayout layout = client != null
                        ? ClassicPadLayout.forSetting(WeHavePhonesConfig.getClassicPadSetting(context), automatic) : null;
                String id = layout != null ? layout.id : "";
                if (!id.equals(applied)) {
                    applied = id;
                    final ClassicPadLayout chosen = layout;
                    HANDLER.post(() -> apply(activity, chosen));
                }
                Thread.sleep(POLL_MS);
            }
        }
        catch (InterruptedException ignored) {}
        catch (Throwable throwable) {
            Log.w(TAG, "poller stopped", throwable);
        }
        // A newer session's poller owns the flag once this activity is no longer the active one.
        if (XServerDisplayActivity.getActiveInstance() == activity || XServerDisplayActivity.getActiveInstance() == null) {
            importedClientRunning = false;
        }
        HANDLER.post(() -> {
            if (padActivity == activity) deactivate();
        });
    }

    /** The imported client the process runs (Wine keeps the game's folder as its cwd); null for anything else. */
    private static WowProduct importedClient(Context context, int pid) {
        Container container = WeHavePhonesContainers.resolve(context);
        if (container == null) return null;
        File cwd;
        try {
            cwd = new File("/proc/" + pid + "/cwd").getCanonicalFile();
        }
        catch (IOException e) {
            return null;
        }
        for (WowProduct product : WowInstallationScanner.scan(container)) {
            if (!product.isCustom() || product.directory == null) continue;
            try {
                if (product.directory.getCanonicalFile().equals(cwd)) return product;
            }
            catch (IOException ignored) {
                // An unreadable client folder is not the running one.
            }
        }
        return null;
    }

    private static void apply(XServerDisplayActivity activity, ClassicPadLayout layout) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (pad != null && padActivity == activity && pad.layout() == layout) return;
        deactivate();
        if (layout == null) return;
        XServer xServer = activity.getXServer();
        if (xServer == null) return;
        pad = new ClassicPad(layout, new XServerOutput(xServer, activity.getWinHandler()), pixelsPerTick(activity, xServer));
        padActivity = activity;
        pad.start();
        Log.i(TAG, "controller as keyboard and mouse: " + layout.id);
        onControllerInput(activity);
    }

    private static void deactivate() {
        HANDLER.removeCallbacks(TICK);
        ticking = false;
        if (pad != null) {
            pad.releaseAll();
            Log.i(TAG, "controller as keyboard and mouse: off");
        }
        pad = null;
        padActivity = null;
    }

    /** Full tilt crosses the screen height in about 40 ticks (0.7 s), scaled by the app's cursor speed. */
    private static float pixelsPerTick(Context context, XServer xServer) {
        float speed = PreferenceManager.getDefaultSharedPreferences(context).getFloat("cursor_speed", 1.0f);
        return Math.max(6f, xServer.screenInfo.height / 40f) * Math.max(0.25f, speed);
    }

    /** Keys and buttons go to the X server like the touch controls'; pointer motion like the touchpad's. */
    private static final class XServerOutput implements ClassicPad.Output {
        private final XServer xServer;
        private final WinHandler winHandler;

        XServerOutput(XServer xServer, WinHandler winHandler) {
            this.xServer = xServer;
            this.winHandler = winHandler;
        }

        @Override
        public void key(XKeycode key, boolean down) {
            if (down) xServer.injectKeyPress(key);
            else xServer.injectKeyRelease(key);
        }

        @Override
        public void mouseButton(int button, boolean down) {
            Pointer.Button pointerButton = pointerButton(button);
            if (pointerButton == null) return;
            if (down) xServer.injectPointerButtonPress(pointerButton);
            else xServer.injectPointerButtonRelease(pointerButton);
        }

        @Override
        public void moveCursor(int dx, int dy) {
            if (xServer.isRelativeMouseMovement() && winHandler != null) {
                winHandler.mouseEvent(MouseEventFlags.MOVE, dx, dy, 0);
            }
            else xServer.injectPointerMoveDelta(dx, dy);
        }

        @Override
        public void placeCursor(float x, float y) {
            // With relative mouse movement the game, not the X pointer, owns the cursor.
            if (xServer.isRelativeMouseMovement()) return;
            xServer.injectPointerMove(Math.round(x * xServer.screenInfo.width), Math.round(y * xServer.screenInfo.height));
        }

        private static Pointer.Button pointerButton(int button) {
            switch (button) {
                case ClassicPad.MOUSE_LEFT: return Pointer.Button.BUTTON_LEFT;
                case ClassicPad.MOUSE_RIGHT: return Pointer.Button.BUTTON_RIGHT;
                case ClassicPad.WHEEL_UP: return Pointer.Button.BUTTON_SCROLL_UP;
                case ClassicPad.WHEEL_DOWN: return Pointer.Button.BUTTON_SCROLL_DOWN;
                default: return null;
            }
        }
    }
}
