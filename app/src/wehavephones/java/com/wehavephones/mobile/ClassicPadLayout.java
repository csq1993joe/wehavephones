package com.wehavephones.mobile;

import com.wehavephones.xserver.XKeycode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a controller sends to a WoW client that cannot read one (1.x to 8.x,
 * imported without Battle.net): the key or mouse button of each control, and
 * whether the right stick moves the pointer or turns the camera. Each layout
 * matches the keys a controller add-on for those clients expects, so the
 * add-on works without remapping:
 * <ul>
 * <li>{@link #STANDARD}: no add-on; action bar keys 1 to 8, jump, target and
 *     the mouse on the triggers, for any client.</li>
 * <li>{@link #STEAM_DECK}: the Steam Deck "Keyboard (WASD) and Mouse"
 *     layout that ShaguController (1.12) binds.</li>
 * <li>{@link #CONSOLE_EXPERIENCE}: ConsoleExperienceClassic (1.12): keys 1 to
 *     0, Shift and Ctrl on the triggers, the camera on the right stick.</li>
 * <li>{@link #CONSOLEPORT_LK}: ConsolePortLK (3.3.5a) with WoWpadX's default
 *     keys (F1 to F12, Shift and Ctrl on LB and LT); ConsolePortLK's
 *     calibration records them.</li>
 * </ul>
 * Pure Java so the host self-test can exercise it.
 */
public enum ClassicPadLayout {
    STANDARD("standard", false, -1, -1),
    STEAM_DECK("steam_deck", false, -1, -1),
    CONSOLE_EXPERIENCE("console_experience", true, 0.5f, 0.5f - 50f / 768f),
    CONSOLEPORT_LK("consoleport_lk", false, -1, -1);

    /** Settings value: pick the layout from the client build and its controller add-on. */
    public static final String SETTING_AUTO = "auto";
    /** Settings value: no translation; the guest sees an Xbox controller. */
    public static final String SETTING_XINPUT = "xinput";

    // Controls: the buttons use ExternalController's indices (A = 0 ... R2 = 11), then the D-pad.
    public static final int A = 0;
    public static final int B = 1;
    public static final int X = 2;
    public static final int Y = 3;
    public static final int LB = 4;
    public static final int RB = 5;
    public static final int SELECT = 6;
    public static final int START = 7;
    public static final int L3 = 8;
    public static final int R3 = 9;
    public static final int LT = 10;
    public static final int RT = 11;
    public static final int UP = 12;
    public static final int RIGHT = 13;
    public static final int DOWN = 14;
    public static final int LEFT = 15;
    public static final int CONTROLS = 16;

    static final String CONSOLE_EXPERIENCE_FOLDER = "ConsoleExperienceClassic";
    static final String CONSOLEPORT_FOLDER = "ConsolePort";
    static final String SHAGU_CONTROLLER_FOLDER = "ShaguController";
    /** Read as ISO-8859-1, so a UTF-8 byte order mark shows as "ï»¿". */
    private static final Pattern TOC_INTERFACE = Pattern.compile("(?im)^(?:ï»¿)?\\s*##\\s*Interface\\s*:\\s*(\\d+)");
    private static final Pattern VERSION = Pattern.compile("^\\s*(\\d+)\\.(\\d+)");

    /** One control's output: keys pressed in order (released in reverse), a mouse button or a wheel click. */
    public static final class Action {
        final XKeycode[] keys;
        final int mouseButton;
        final int wheel;

        private Action(XKeycode[] keys, int mouseButton, int wheel) {
            this.keys = keys;
            this.mouseButton = mouseButton;
            this.wheel = wheel;
        }

        static Action keys(XKeycode... keys) {
            return new Action(keys, 0, 0);
        }

        static Action mouse(int button) {
            return new Action(new XKeycode[0], button, 0);
        }

        static Action wheel(int button) {
            return new Action(new XKeycode[0], 0, button);
        }

        /** For logs and the self-test: "KEY_SHIFT_L+KEY_ESC", "mouse 3", "wheel 4". */
        @Override
        public String toString() {
            if (mouseButton != 0) return "mouse " + mouseButton;
            if (wheel != 0) return "wheel " + wheel;
            StringBuilder builder = new StringBuilder();
            for (XKeycode key : keys) builder.append(builder.length() > 0 ? "+" : "").append(key.name());
            return builder.toString();
        }
    }

    /** Value stored in Settings. */
    public final String id;
    /** The right stick turns the camera (holding the right mouse button) instead of moving the pointer. */
    public final boolean camera;
    /** Where clicks should land after a camera turn, as fractions of the screen; negative when the layout has none. */
    public final float crosshairX;
    public final float crosshairY;
    private final Action[] actions = new Action[CONTROLS];

    ClassicPadLayout(String id, boolean camera, float crosshairX, float crosshairY) {
        this.id = id;
        this.camera = camera;
        this.crosshairX = crosshairX;
        this.crosshairY = crosshairY;
    }

    static {
        STANDARD.set(A, Action.keys(XKeycode.KEY_SPACE));
        STANDARD.set(X, Action.keys(XKeycode.KEY_1));
        STANDARD.set(Y, Action.keys(XKeycode.KEY_2));
        STANDARD.set(B, Action.keys(XKeycode.KEY_3));
        STANDARD.set(UP, Action.keys(XKeycode.KEY_4));
        STANDARD.set(RIGHT, Action.keys(XKeycode.KEY_5));
        STANDARD.set(DOWN, Action.keys(XKeycode.KEY_6));
        STANDARD.set(LEFT, Action.keys(XKeycode.KEY_7));
        STANDARD.set(RB, Action.keys(XKeycode.KEY_8));
        STANDARD.set(LB, Action.keys(XKeycode.KEY_TAB));
        STANDARD.set(LT, Action.mouse(ClassicPad.MOUSE_RIGHT));
        STANDARD.set(RT, Action.mouse(ClassicPad.MOUSE_LEFT));
        STANDARD.set(L3, Action.keys(XKeycode.KEY_NUM_LOCK));
        STANDARD.set(R3, Action.mouse(ClassicPad.MOUSE_LEFT));
        STANDARD.set(SELECT, Action.keys(XKeycode.KEY_M));
        STANDARD.set(START, Action.keys(XKeycode.KEY_ESC));

        // Valve's "Keyboard (WASD) and Mouse" template, which ShaguController rebinds in game.
        STEAM_DECK.set(A, Action.keys(XKeycode.KEY_SPACE));
        STEAM_DECK.set(B, Action.keys(XKeycode.KEY_E));
        STEAM_DECK.set(X, Action.keys(XKeycode.KEY_R));
        STEAM_DECK.set(Y, Action.keys(XKeycode.KEY_F));
        STEAM_DECK.set(UP, Action.keys(XKeycode.KEY_UP));
        STEAM_DECK.set(RIGHT, Action.keys(XKeycode.KEY_RIGHT));
        STEAM_DECK.set(DOWN, Action.keys(XKeycode.KEY_DOWN));
        STEAM_DECK.set(LEFT, Action.keys(XKeycode.KEY_LEFT));
        STEAM_DECK.set(LB, Action.wheel(ClassicPad.WHEEL_DOWN));
        STEAM_DECK.set(RB, Action.wheel(ClassicPad.WHEEL_UP));
        STEAM_DECK.set(LT, Action.mouse(ClassicPad.MOUSE_RIGHT));
        STEAM_DECK.set(RT, Action.mouse(ClassicPad.MOUSE_LEFT));
        STEAM_DECK.set(L3, Action.keys(XKeycode.KEY_SHIFT_L));
        STEAM_DECK.set(R3, Action.mouse(ClassicPad.MOUSE_LEFT));
        STEAM_DECK.set(SELECT, Action.keys(XKeycode.KEY_TAB));
        STEAM_DECK.set(START, Action.keys(XKeycode.KEY_ESC));

        // ConsoleExperienceClassic's Bindings: 1-0, Shift (LT) and Ctrl (RT) pages, Shift+Esc radial menu.
        CONSOLE_EXPERIENCE.set(A, Action.keys(XKeycode.KEY_1));
        CONSOLE_EXPERIENCE.set(X, Action.keys(XKeycode.KEY_2));
        CONSOLE_EXPERIENCE.set(Y, Action.keys(XKeycode.KEY_3));
        CONSOLE_EXPERIENCE.set(B, Action.keys(XKeycode.KEY_4));
        CONSOLE_EXPERIENCE.set(DOWN, Action.keys(XKeycode.KEY_5));
        CONSOLE_EXPERIENCE.set(LEFT, Action.keys(XKeycode.KEY_6));
        CONSOLE_EXPERIENCE.set(UP, Action.keys(XKeycode.KEY_7));
        CONSOLE_EXPERIENCE.set(RIGHT, Action.keys(XKeycode.KEY_8));
        CONSOLE_EXPERIENCE.set(RB, Action.keys(XKeycode.KEY_9));
        CONSOLE_EXPERIENCE.set(LB, Action.keys(XKeycode.KEY_0));
        CONSOLE_EXPERIENCE.set(LT, Action.keys(XKeycode.KEY_SHIFT_L));
        CONSOLE_EXPERIENCE.set(RT, Action.keys(XKeycode.KEY_CTRL_L));
        CONSOLE_EXPERIENCE.set(START, Action.keys(XKeycode.KEY_SHIFT_L, XKeycode.KEY_ESC));
        CONSOLE_EXPERIENCE.set(SELECT, Action.keys(XKeycode.KEY_M));
        CONSOLE_EXPERIENCE.set(L3, Action.mouse(ClassicPad.MOUSE_LEFT));
        CONSOLE_EXPERIENCE.set(R3, Action.mouse(ClassicPad.MOUSE_RIGHT));

        // WoWpadX's defaults (modifier style 0), which ConsolePortLK's calibration records.
        CONSOLEPORT_LK.set(Y, Action.keys(XKeycode.KEY_F9));
        CONSOLEPORT_LK.set(B, Action.keys(XKeycode.KEY_F10));
        CONSOLEPORT_LK.set(A, Action.keys(XKeycode.KEY_F11));
        CONSOLEPORT_LK.set(X, Action.keys(XKeycode.KEY_F12));
        CONSOLEPORT_LK.set(UP, Action.keys(XKeycode.KEY_F1));
        CONSOLEPORT_LK.set(RIGHT, Action.keys(XKeycode.KEY_F2));
        CONSOLEPORT_LK.set(DOWN, Action.keys(XKeycode.KEY_F3));
        CONSOLEPORT_LK.set(LEFT, Action.keys(XKeycode.KEY_F4));
        CONSOLEPORT_LK.set(SELECT, Action.keys(XKeycode.KEY_F5));
        CONSOLEPORT_LK.set(START, Action.keys(XKeycode.KEY_F6));
        CONSOLEPORT_LK.set(LB, Action.keys(XKeycode.KEY_SHIFT_L));
        CONSOLEPORT_LK.set(RB, Action.keys(XKeycode.KEY_F7));
        CONSOLEPORT_LK.set(LT, Action.keys(XKeycode.KEY_CTRL_L));
        CONSOLEPORT_LK.set(RT, Action.keys(XKeycode.KEY_F8));
        CONSOLEPORT_LK.set(L3, Action.mouse(ClassicPad.MOUSE_LEFT));
        CONSOLEPORT_LK.set(R3, Action.mouse(ClassicPad.MOUSE_RIGHT));
    }

    private void set(int control, Action action) {
        actions[control] = action;
    }

    /** The output of {@code control}; null when the layout leaves it unused. */
    public Action action(int control) {
        return control >= 0 && control < CONTROLS ? actions[control] : null;
    }

    public boolean hasCrosshair() {
        return crosshairX >= 0 && crosshairY >= 0;
    }

    /** The layout stored as {@code id}; null for "auto", "xinput" and unknown values. */
    public static ClassicPadLayout fromId(String id) {
        for (ClassicPadLayout layout : values()) {
            if (layout.id.equals(id)) return layout;
        }
        return null;
    }

    // ---- choice ------------------------------------------------------------------------

    /**
     * The layout for a client: Settings can fix one or turn translation off
     * ("xinput", null); "auto" (and anything unknown) uses {@link #automatic}.
     */
    public static ClassicPadLayout choose(String setting, String version, File clientDir) {
        return forSetting(setting, automatic(version, clientDir));
    }

    /** {@code automatic} unless Settings fixes a layout or turns translation off. */
    public static ClassicPadLayout forSetting(String setting, ClassicPadLayout automatic) {
        if (SETTING_XINPUT.equals(setting)) return null;
        ClassicPadLayout fixed = fromId(setting);
        return fixed != null ? fixed : automatic;
    }

    /**
     * What "auto" picks: null for builds that read a gamepad themselves (the
     * controller then reaches the game as an Xbox controller, for ConsolePort),
     * else the layout of the controller add-on in the client, else
     * {@link #STANDARD}.
     */
    public static ClassicPadLayout automatic(String version, File clientDir) {
        if (hasNativeGamepad(version)) return null;
        return forAddons(clientDir);
    }

    /**
     * WoW's own gamepad support: 9.0 on, and the Classic re-releases (1.13+,
     * 2.5+, 3.4+, 4.4+, 5.5+). Unknown versions count as older clients.
     */
    public static boolean hasNativeGamepad(String version) {
        Matcher matcher = VERSION.matcher(version != null ? version : "");
        if (!matcher.find()) return false;
        int major;
        int minor;
        try {
            major = Integer.parseInt(matcher.group(1));
            minor = Integer.parseInt(matcher.group(2));
        }
        catch (NumberFormatException e) {
            return false;
        }
        if (major >= 9) return true;
        switch (major) {
            case 1: return minor >= 13;
            case 2: return minor >= 5;
            case 3: return minor >= 4;
            case 4: return minor >= 4;
            case 5: return minor >= 5;
            default: return false;
        }
    }

    /**
     * The layout of the controller add-on installed in the client (its
     * Interface\AddOns; a folder renamed to Name.disabled does not count):
     * ConsoleExperienceClassic, ConsolePortLK (a ConsolePort built for 3.x),
     * ShaguController; else {@link #STANDARD}.
     */
    public static ClassicPadLayout forAddons(File clientDir) {
        if (clientDir == null) return STANDARD;
        File addons = child(child(clientDir, "Interface"), "AddOns");
        if (tocFile(addons, CONSOLE_EXPERIENCE_FOLDER) != null) return CONSOLE_EXPERIENCE;
        int consolePort = tocInterface(tocFile(addons, CONSOLEPORT_FOLDER));
        if (consolePort >= 30000 && consolePort < 40000) return CONSOLEPORT_LK;
        if (tocFile(addons, SHAGU_CONTROLLER_FOLDER) != null) return STEAM_DECK;
        return STANDARD;
    }

    /** The add-on's {@code <name>.toc} in {@code addons}, ignoring case as Wine does; null when absent. */
    static File tocFile(File addons, String name) {
        File folder = child(addons, name);
        if (!folder.isDirectory()) return null;
        File toc = child(folder, name + ".toc");
        return toc.isFile() ? toc : null;
    }

    /** The {@code ## Interface:} number of a TOC file; 0 when absent or unreadable. */
    static int tocInterface(File toc) {
        if (toc == null) return 0;
        byte[] head = new byte[8192];
        int length = 0;
        try (InputStream input = new FileInputStream(toc)) {
            int read;
            while (length < head.length && (read = input.read(head, length, head.length - length)) > 0) length += read;
        }
        catch (IOException e) {
            return 0;
        }
        Matcher matcher = TOC_INTERFACE.matcher(new String(head, 0, length, StandardCharsets.ISO_8859_1));
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The child of {@code directory} named {@code name} ignoring case (Wine paths are case-insensitive). */
    static File child(File directory, String name) {
        String[] names = directory.list();
        if (names != null) {
            for (String existing : names) {
                if (existing.equalsIgnoreCase(name)) return new File(directory, existing);
            }
        }
        return new File(directory, name);
    }
}
