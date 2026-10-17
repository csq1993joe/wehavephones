package com.wehavephones.mobile;

import com.wehavephones.xserver.XKeycode;

import java.util.Arrays;

/**
 * Plays a controller as keyboard and mouse for a WoW client that cannot read
 * one, following a {@link ClassicPadLayout}. Pressing a control holds its
 * keys or mouse button until it is released. Holds are counted, so two
 * controls that share a key (a trigger's Shift and Start's Shift+Esc) release
 * it only when both are up. The left stick is W/A/S/D. The right stick moves
 * the pointer, or for a camera layout turns the camera: the right mouse
 * button is held while the stick is pushed, after the pointer is put on the
 * crosshair, so the game's mouse look restores it there.
 *
 * <p>{@link #update} takes each controller reading; {@link #tick} moves the
 * pointer and must run about every 16 ms while {@link #needsTicks()}. Pure
 * Java so the host self-test can exercise it; not thread-safe (the app uses it
 * on the UI thread).
 */
public final class ClassicPad {
    /** X11 button numbers, as Pointer.Button codes. */
    public static final int MOUSE_LEFT = 1;
    public static final int MOUSE_RIGHT = 3;
    public static final int WHEEL_UP = 4;
    public static final int WHEEL_DOWN = 5;

    /** Where keys and pointer motion go: the X server in the app, a recorder in the self-test. */
    public interface Output {
        void key(XKeycode key, boolean down);

        void mouseButton(int button, boolean down);

        void moveCursor(int dx, int dy);

        /** Absolute pointer position, as fractions of the screen. */
        void placeCursor(float x, float y);
    }

    /** One controller reading. Buttons use ExternalController's bit indices (A = 0 ... R2 = 11). */
    public static final class Input {
        public int buttons;
        /** Up, right, down, left. */
        public final boolean[] dpad = new boolean[4];
        /** -1 to 1; right and down are positive, as Android reports them. */
        public float leftX;
        public float leftY;
        public float rightX;
        public float rightY;
        /** 0 to 1; analog triggers, used besides the L2/R2 buttons. */
        public float leftTrigger;
        public float rightTrigger;

        public void clear() {
            buttons = 0;
            Arrays.fill(dpad, false);
            leftX = leftY = rightX = rightY = 0;
            leftTrigger = rightTrigger = 0;
        }
    }

    /** Left stick: a direction key goes down past 0.5 and up below 0.35, so diagonals hold two keys. */
    static final float MOVE_PRESS = 0.5f;
    static final float MOVE_RELEASE = 0.35f;
    static final float TRIGGER_PRESS = 0.45f;
    static final float TRIGGER_RELEASE = 0.3f;
    static final float POINTER_DEAD_ZONE = 0.12f;
    /** A camera turn starts past 0.3 and ends after the stick stays under 0.2 for 150 ms. */
    static final float CAMERA_START = 0.3f;
    static final float CAMERA_HOLD = 0.2f;
    static final long CAMERA_RELEASE_MS = 150;

    private static final XKeycode[] MOVE_KEYS = {XKeycode.KEY_W, XKeycode.KEY_D, XKeycode.KEY_S, XKeycode.KEY_A};

    private final ClassicPadLayout layout;
    private final Output out;
    private final float pixelsPerTick;
    private final int[] keyHolds = new int[XKeycode.values().length];
    private final int[] mouseHolds = new int[6];
    private final boolean[] controls = new boolean[ClassicPadLayout.CONTROLS];
    private final boolean[] moving = new boolean[4];
    private float rightX;
    private float rightY;
    private float remainderX;
    private float remainderY;
    private boolean cameraTurn;
    private long cameraPushedAt;

    /** {@code pixelsPerTick}: pointer speed at full stick, in pixels per {@link #tick}. */
    public ClassicPad(ClassicPadLayout layout, Output out, float pixelsPerTick) {
        this.layout = layout;
        this.out = out;
        this.pixelsPerTick = pixelsPerTick;
    }

    public ClassicPadLayout layout() {
        return layout;
    }

    /** Puts the pointer on the layout's crosshair, so the first click lands on what it aims at. */
    public void start() {
        if (layout.hasCrosshair()) out.placeCursor(layout.crosshairX, layout.crosshairY);
    }

    /** Applies one controller reading: buttons, D-pad, triggers and the left stick; keeps the right stick for {@link #tick}. */
    public void update(Input input) {
        for (int control = 0; control < ClassicPadLayout.CONTROLS; control++) {
            boolean pressed = isPressed(input, control);
            if (pressed == controls[control]) continue;
            controls[control] = pressed;
            apply(layout.action(control), pressed);
        }
        float[] directions = {-input.leftY, input.leftX, input.leftY, -input.leftX};
        boolean[] wanted = new boolean[4];
        for (int direction = 0; direction < 4; direction++) {
            wanted[direction] = directions[direction] > (moving[direction] ? MOVE_RELEASE : MOVE_PRESS);
        }
        // Up before down, so a flick from left to right never holds A and D together.
        for (int pass = 0; pass < 2; pass++) {
            for (int direction = 0; direction < 4; direction++) {
                if (wanted[direction] == moving[direction] || wanted[direction] != (pass == 1)) continue;
                moving[direction] = wanted[direction];
                if (wanted[direction]) holdKey(MOVE_KEYS[direction]);
                else releaseKey(MOVE_KEYS[direction]);
            }
        }
        rightX = finite(input.rightX);
        rightY = finite(input.rightY);
    }

    /** True while the right stick needs {@link #tick}: pushed, or a camera turn is still held. */
    public boolean needsTicks() {
        float magnitude = magnitude();
        if (layout.camera) return cameraTurn || magnitude >= CAMERA_START;
        return magnitude > POINTER_DEAD_ZONE;
    }

    /** Moves the pointer for the right stick; for a camera layout also starts and ends the turn. */
    public void tick(long nowMs) {
        float magnitude = magnitude();
        if (layout.camera) {
            if (!cameraTurn) {
                if (magnitude < CAMERA_START) return;
                if (layout.hasCrosshair()) out.placeCursor(layout.crosshairX, layout.crosshairY);
                holdMouse(MOUSE_RIGHT);
                cameraTurn = true;
                cameraPushedAt = nowMs;
                remainderX = remainderY = 0;
            }
            else if (magnitude >= CAMERA_HOLD) {
                cameraPushedAt = nowMs;
            }
            else if (nowMs - cameraPushedAt >= CAMERA_RELEASE_MS) {
                cameraTurn = false;
                releaseMouse(MOUSE_RIGHT);
                remainderX = remainderY = 0;
                return;
            }
        }
        movePointer(magnitude);
    }

    /** Lets go of every key and button the pad holds (pause, game exit, layout change). */
    public void releaseAll() {
        Arrays.fill(controls, false);
        Arrays.fill(moving, false);
        rightX = rightY = 0;
        remainderX = remainderY = 0;
        cameraTurn = false;
        for (int button = 0; button < mouseHolds.length; button++) {
            if (mouseHolds[button] > 0) out.mouseButton(button, false);
            mouseHolds[button] = 0;
        }
        XKeycode[] keys = XKeycode.values();
        // Plain keys before modifiers, the way a player lets go of Shift+Esc.
        for (int pass = 0; pass < 2; pass++) {
            for (XKeycode key : keys) {
                if (keyHolds[key.ordinal()] == 0 || isModifier(key) != (pass == 1)) continue;
                keyHolds[key.ordinal()] = 0;
                out.key(key, false);
            }
        }
    }

    /** True while any key or mouse button is held (the self-test checks releases). */
    public boolean isHolding() {
        for (int holds : keyHolds) if (holds > 0) return true;
        for (int holds : mouseHolds) if (holds > 0) return true;
        return false;
    }

    // ---- input --------------------------------------------------------------------------

    private boolean isPressed(Input input, int control) {
        switch (control) {
            case ClassicPadLayout.LT:
                return bit(input, control) || input.leftTrigger > (controls[control] ? TRIGGER_RELEASE : TRIGGER_PRESS);
            case ClassicPadLayout.RT:
                return bit(input, control) || input.rightTrigger > (controls[control] ? TRIGGER_RELEASE : TRIGGER_PRESS);
            case ClassicPadLayout.UP:
            case ClassicPadLayout.RIGHT:
            case ClassicPadLayout.DOWN:
            case ClassicPadLayout.LEFT:
                return input.dpad[control - ClassicPadLayout.UP];
            default:
                return bit(input, control);
        }
    }

    private static boolean bit(Input input, int index) {
        return (input.buttons & (1 << index)) != 0;
    }

    private static float finite(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 0 : value;
    }

    private float magnitude() {
        return (float)Math.sqrt(rightX * rightX + rightY * rightY);
    }

    // ---- output -------------------------------------------------------------------------

    private void apply(ClassicPadLayout.Action action, boolean pressed) {
        if (action == null) return;
        if (action.wheel != 0) {
            // A wheel notch per press, as a scroll wheel sends it.
            if (pressed) {
                out.mouseButton(action.wheel, true);
                out.mouseButton(action.wheel, false);
            }
            return;
        }
        if (pressed) {
            for (XKeycode key : action.keys) holdKey(key);
            if (action.mouseButton != 0) holdMouse(action.mouseButton);
        }
        else {
            if (action.mouseButton != 0) releaseMouse(action.mouseButton);
            for (int i = action.keys.length - 1; i >= 0; i--) releaseKey(action.keys[i]);
        }
    }

    private void movePointer(float magnitude) {
        if (magnitude <= POINTER_DEAD_ZONE) {
            remainderX = remainderY = 0;
            return;
        }
        // Slow near the center for aiming, quick at full tilt.
        float scaled = Math.min(1f, (magnitude - POINTER_DEAD_ZONE) / (1f - POINTER_DEAD_ZONE));
        float speed = pixelsPerTick * (0.3f * scaled + 0.7f * scaled * scaled);
        float dx = rightX / magnitude * speed + remainderX;
        float dy = rightY / magnitude * speed + remainderY;
        int moveX = (int)dx;
        int moveY = (int)dy;
        remainderX = dx - moveX;
        remainderY = dy - moveY;
        if (moveX != 0 || moveY != 0) out.moveCursor(moveX, moveY);
    }

    private void holdKey(XKeycode key) {
        if (keyHolds[key.ordinal()]++ == 0) out.key(key, true);
    }

    private void releaseKey(XKeycode key) {
        int index = key.ordinal();
        if (keyHolds[index] == 0) return;
        if (--keyHolds[index] == 0) out.key(key, false);
    }

    private void holdMouse(int button) {
        if (button <= 0 || button >= mouseHolds.length) return;
        if (mouseHolds[button]++ == 0) out.mouseButton(button, true);
    }

    private void releaseMouse(int button) {
        if (button <= 0 || button >= mouseHolds.length || mouseHolds[button] == 0) return;
        if (--mouseHolds[button] == 0) out.mouseButton(button, false);
    }

    private static boolean isModifier(XKeycode key) {
        switch (key) {
            case KEY_SHIFT_L:
            case KEY_SHIFT_R:
            case KEY_CTRL_L:
            case KEY_CTRL_R:
            case KEY_ALT_L:
            case KEY_ALT_R:
                return true;
            default:
                return false;
        }
    }
}
