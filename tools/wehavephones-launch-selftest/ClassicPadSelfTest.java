package com.wehavephones.mobile;

import com.wehavephones.xserver.XKeycode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Host test for ClassicPad and ClassicPadLayout: the keys each layout sends
 * (matched to the add-ons' own bindings), counted holds for shared keys,
 * trigger and stick thresholds, wheel clicks, pointer speed, the camera turn
 * with its crosshair, releasing everything, and the automatic layout from the
 * client build and its AddOns folder.
 */
public final class ClassicPadSelfTest {
    private static int checks;

    private static final class Recorder implements ClassicPad.Output {
        final List<String> events = new ArrayList<>();
        int movedX;
        int movedY;

        @Override
        public void key(XKeycode key, boolean down) {
            events.add((down ? "+" : "-") + key.name().replace("KEY_", ""));
        }

        @Override
        public void mouseButton(int button, boolean down) {
            events.add((down ? "+" : "-") + "mouse" + button);
        }

        @Override
        public void moveCursor(int dx, int dy) {
            movedX += dx;
            movedY += dy;
            events.add("move");
        }

        @Override
        public void placeCursor(float x, float y) {
            events.add(String.format(Locale.US, "place %.3f,%.3f", x, y));
        }

        String take() {
            StringBuilder builder = new StringBuilder();
            for (String event : events) {
                if (event.equals("move")) continue;
                builder.append(builder.length() > 0 ? " " : "").append(event);
            }
            events.clear();
            return builder.toString();
        }

        int moves() {
            int count = 0;
            for (String event : events) if (event.equals("move")) count++;
            return count;
        }
    }

    public static void main(String[] args) throws Exception {
        layouts();
        buttonsAndSharedKeys();
        triggersAndSticks();
        pointer();
        camera();
        releaseAll();
        selection();
        System.out.println("[ClassicPadSelfTest] PASS (" + checks + " checks)");
    }

    // ---- layouts ------------------------------------------------------------------------

    private static void layouts() {
        for (ClassicPadLayout layout : ClassicPadLayout.values()) {
            for (int control = 0; control < ClassicPadLayout.CONTROLS; control++) {
                check(layout.action(control) != null, layout + " maps control " + control);
            }
            check(ClassicPadLayout.fromId(layout.id) == layout, layout + " id round-trips");
        }
        check(ClassicPadLayout.fromId(ClassicPadLayout.SETTING_AUTO) == null, "auto is not a layout");
        check(ClassicPadLayout.fromId(ClassicPadLayout.SETTING_XINPUT) == null, "xinput is not a layout");

        // ConsoleExperienceClassic docs/Keybindings.md: A 1, X 2, Y 3, B 4, Down 5, Left 6, Up 7, Right 8, RB 9, LB 0,
        // LT Shift, RT Ctrl, Start the radial menu on Shift+Escape, Select the map.
        ClassicPadLayout ce = ClassicPadLayout.CONSOLE_EXPERIENCE;
        expect(ce, ClassicPadLayout.A, "KEY_1");
        expect(ce, ClassicPadLayout.X, "KEY_2");
        expect(ce, ClassicPadLayout.Y, "KEY_3");
        expect(ce, ClassicPadLayout.B, "KEY_4");
        expect(ce, ClassicPadLayout.DOWN, "KEY_5");
        expect(ce, ClassicPadLayout.LEFT, "KEY_6");
        expect(ce, ClassicPadLayout.UP, "KEY_7");
        expect(ce, ClassicPadLayout.RIGHT, "KEY_8");
        expect(ce, ClassicPadLayout.RB, "KEY_9");
        expect(ce, ClassicPadLayout.LB, "KEY_0");
        expect(ce, ClassicPadLayout.LT, "KEY_SHIFT_L");
        expect(ce, ClassicPadLayout.RT, "KEY_CTRL_L");
        expect(ce, ClassicPadLayout.START, "KEY_SHIFT_L+KEY_ESC");
        expect(ce, ClassicPadLayout.SELECT, "KEY_M");
        check(ce.camera && ce.hasCrosshair(), "ConsoleExperience turns the camera, aimed at its crosshair");
        check(Math.abs(ce.crosshairY - (0.5f - 50f / 768f)) < 1e-6, "crosshair 50 UI units above the center");

        // WoWpadX KeybindDefaults (modifier style 0), recorded by ConsolePortLK's calibration.
        ClassicPadLayout lk = ClassicPadLayout.CONSOLEPORT_LK;
        expect(lk, ClassicPadLayout.Y, "KEY_F9");
        expect(lk, ClassicPadLayout.B, "KEY_F10");
        expect(lk, ClassicPadLayout.A, "KEY_F11");
        expect(lk, ClassicPadLayout.X, "KEY_F12");
        expect(lk, ClassicPadLayout.UP, "KEY_F1");
        expect(lk, ClassicPadLayout.RIGHT, "KEY_F2");
        expect(lk, ClassicPadLayout.DOWN, "KEY_F3");
        expect(lk, ClassicPadLayout.LEFT, "KEY_F4");
        expect(lk, ClassicPadLayout.SELECT, "KEY_F5");
        expect(lk, ClassicPadLayout.START, "KEY_F6");
        expect(lk, ClassicPadLayout.LB, "KEY_SHIFT_L");
        expect(lk, ClassicPadLayout.RB, "KEY_F7");
        expect(lk, ClassicPadLayout.LT, "KEY_CTRL_L");
        expect(lk, ClassicPadLayout.RT, "KEY_F8");
        expect(lk, ClassicPadLayout.L3, "mouse 1");
        expect(lk, ClassicPadLayout.R3, "mouse 3");
        check(!lk.camera, "ConsolePortLK moves the pointer and runs mouse look itself");
        HashSet<String> distinct = new HashSet<>();
        for (int control = 0; control < ClassicPadLayout.CONTROLS; control++) distinct.add(lk.action(control).toString());
        check(distinct.size() == ClassicPadLayout.CONTROLS, "calibration needs a distinct output per control");

        // Steam Deck "Keyboard (WASD) and Mouse", as ShaguController's keybinds.lua expects it.
        ClassicPadLayout deck = ClassicPadLayout.STEAM_DECK;
        expect(deck, ClassicPadLayout.A, "KEY_SPACE");
        expect(deck, ClassicPadLayout.Y, "KEY_F");
        expect(deck, ClassicPadLayout.X, "KEY_R");
        expect(deck, ClassicPadLayout.B, "KEY_E");
        expect(deck, ClassicPadLayout.UP, "KEY_UP");
        expect(deck, ClassicPadLayout.LEFT, "KEY_LEFT");
        expect(deck, ClassicPadLayout.LB, "wheel 5");
        expect(deck, ClassicPadLayout.RB, "wheel 4");
        expect(deck, ClassicPadLayout.LT, "mouse 3");
        expect(deck, ClassicPadLayout.RT, "mouse 1");
        expect(deck, ClassicPadLayout.L3, "KEY_SHIFT_L");
        expect(deck, ClassicPadLayout.SELECT, "KEY_TAB");
        expect(deck, ClassicPadLayout.START, "KEY_ESC");

        ClassicPadLayout standard = ClassicPadLayout.STANDARD;
        expect(standard, ClassicPadLayout.A, "KEY_SPACE");
        expect(standard, ClassicPadLayout.X, "KEY_1");
        expect(standard, ClassicPadLayout.RB, "KEY_8");
        expect(standard, ClassicPadLayout.LB, "KEY_TAB");
        expect(standard, ClassicPadLayout.L3, "KEY_NUM_LOCK");
        expect(standard, ClassicPadLayout.START, "KEY_ESC");
    }

    // ---- buttons ------------------------------------------------------------------------

    private static void buttonsAndSharedKeys() {
        Recorder out = new Recorder();
        ClassicPad pad = new ClassicPad(ClassicPadLayout.CONSOLE_EXPERIENCE, out, 27);
        ClassicPad.Input input = new ClassicPad.Input();

        press(input, ClassicPadLayout.A, true);
        pad.update(input);
        check(out.take().equals("+1"), "A sends 1");
        pad.update(input);
        check(out.take().isEmpty(), "a held button does not repeat");
        press(input, ClassicPadLayout.A, false);
        pad.update(input);
        check(out.take().equals("-1"), "release lets go of 1");

        press(input, ClassicPadLayout.START, true);
        pad.update(input);
        check(out.take().equals("+SHIFT_L +ESC"), "Start presses Shift, then Escape");
        press(input, ClassicPadLayout.START, false);
        pad.update(input);
        check(out.take().equals("-ESC -SHIFT_L"), "and lets go in reverse");

        press(input, ClassicPadLayout.LT, true);
        pad.update(input);
        check(out.take().equals("+SHIFT_L"), "LT holds Shift");
        press(input, ClassicPadLayout.START, true);
        pad.update(input);
        check(out.take().equals("+ESC"), "Start under LT adds only Escape");
        press(input, ClassicPadLayout.LT, false);
        pad.update(input);
        check(out.take().isEmpty(), "Shift stays down while Start still holds it");
        press(input, ClassicPadLayout.START, false);
        pad.update(input);
        check(out.take().equals("-ESC -SHIFT_L"), "the last holder releases Shift");

        // The D-pad works while the left stick runs (the bridge passes the raw D-pad).
        input.leftY = -1f;
        input.dpad[0] = true;
        pad.update(input);
        check(out.take().equals("+7 +W"), "D-pad up while running: 7 and W");
        input.leftY = 0;
        input.dpad[0] = false;
        pad.update(input);
        check(out.take().equals("-7 -W"), "both released");
        check(!pad.isHolding(), "nothing held after the round");

        Recorder deckOut = new Recorder();
        ClassicPad deck = new ClassicPad(ClassicPadLayout.STEAM_DECK, deckOut, 27);
        ClassicPad.Input deckInput = new ClassicPad.Input();
        press(deckInput, ClassicPadLayout.LB, true);
        deck.update(deckInput);
        check(deckOut.take().equals("+mouse5 -mouse5"), "LB sends one wheel-down notch");
        deck.update(deckInput);
        check(deckOut.take().isEmpty(), "held LB does not scroll again");
        press(deckInput, ClassicPadLayout.LB, false);
        deck.update(deckInput);
        check(deckOut.take().isEmpty(), "releasing LB sends nothing");
        press(deckInput, ClassicPadLayout.RT, true);
        press(deckInput, ClassicPadLayout.R3, true);
        deck.update(deckInput);
        check(deckOut.take().equals("+mouse1"), "RT and R3 share the left button");
        press(deckInput, ClassicPadLayout.RT, false);
        deck.update(deckInput);
        check(deckOut.take().isEmpty(), "left button held while R3 is down");
        press(deckInput, ClassicPadLayout.R3, false);
        deck.update(deckInput);
        check(deckOut.take().equals("-mouse1"), "released with the last holder");
    }

    // ---- triggers and sticks ------------------------------------------------------------

    private static void triggersAndSticks() {
        Recorder out = new Recorder();
        ClassicPad pad = new ClassicPad(ClassicPadLayout.CONSOLEPORT_LK, out, 27);
        ClassicPad.Input input = new ClassicPad.Input();

        input.leftTrigger = 0.4f;
        pad.update(input);
        check(out.take().isEmpty(), "a light pull is not a press");
        input.leftTrigger = 0.5f;
        pad.update(input);
        check(out.take().equals("+CTRL_L"), "LT past 0.45 holds Ctrl");
        input.leftTrigger = 0.35f;
        pad.update(input);
        check(out.take().isEmpty(), "hysteresis keeps it held at 0.35");
        input.leftTrigger = 0.25f;
        pad.update(input);
        check(out.take().equals("-CTRL_L"), "released under 0.3");
        input.leftTrigger = 0;
        press(input, ClassicPadLayout.RT, true);
        pad.update(input);
        check(out.take().equals("+F8"), "a digital R2 button press counts");
        press(input, ClassicPadLayout.RT, false);
        pad.update(input);
        check(out.take().equals("-F8"), "and its release");

        input.leftY = -0.45f;
        pad.update(input);
        check(out.take().isEmpty(), "a small push does not walk");
        input.leftY = -0.6f;
        pad.update(input);
        check(out.take().equals("+W"), "forward past 0.5 holds W");
        input.leftY = -0.4f;
        pad.update(input);
        check(out.take().isEmpty(), "W stays down at 0.4");
        input.leftY = -0.3f;
        pad.update(input);
        check(out.take().equals("-W"), "released under 0.35");
        input.leftX = 0.7f;
        input.leftY = -0.7f;
        pad.update(input);
        check(out.take().equals("+W +D"), "a diagonal holds two keys");
        input.leftX = -1f;
        input.leftY = 0;
        pad.update(input);
        check(out.take().equals("-W -D +A"), "swinging straight left: W and D up, A down");
        input.leftX = 1f;
        pad.update(input);
        check(out.take().equals("-A +D"), "a flick to the right swaps A for D in one reading");
        input.leftX = Float.NaN;
        input.rightX = Float.NaN;
        pad.update(input);
        check(out.take().equals("-D") && !pad.needsTicks(), "NaN readings count as centered");
        check(!pad.isHolding(), "nothing held");
    }

    // ---- pointer ------------------------------------------------------------------------

    private static void pointer() {
        Recorder out = new Recorder();
        ClassicPad pad = new ClassicPad(ClassicPadLayout.STANDARD, out, 27);
        ClassicPad.Input input = new ClassicPad.Input();

        input.rightX = 0.1f;
        pad.update(input);
        check(!pad.needsTicks(), "inside the dead zone: no ticks");
        input.rightX = 1f;
        pad.update(input);
        check(pad.needsTicks(), "pushed: ticks");
        pad.tick(0);
        check(out.movedX == 27 && out.movedY == 0, "full right moves 27 px per tick, got " + out.movedX);
        out.movedX = 0;
        input.rightX = 0;
        input.rightY = (ClassicPad.POINTER_DEAD_ZONE + 1f) / 2f;
        pad.update(input);
        for (int tick = 0; tick < 100; tick++) pad.tick(tick * 16L);
        // Half the range past the dead zone: 27 * (0.3 * 0.5 + 0.7 * 0.25) = 8.775 px per tick.
        check(Math.abs(out.movedY - 877) <= 1 && out.movedX == 0, "fractions add up over ticks, got " + out.movedY);
        check(out.take().isEmpty(), "the pointer never clicks by itself");
        input.rightY = 0;
        pad.update(input);
        check(!pad.needsTicks(), "centered: ticks stop");
        int moves = out.moves();
        pad.tick(2000);
        check(out.moves() == moves, "a stray tick does not move");
        new ClassicPad(ClassicPadLayout.STANDARD, out, 27).start();
        check(out.take().isEmpty(), "STANDARD leaves the pointer where it is at start");
    }

    // ---- camera -------------------------------------------------------------------------

    private static void camera() {
        Recorder out = new Recorder();
        ClassicPad pad = new ClassicPad(ClassicPadLayout.CONSOLE_EXPERIENCE, out, 27);
        ClassicPad.Input input = new ClassicPad.Input();
        pad.start();
        check(out.take().equals("place 0.500,0.435"), "ConsoleExperience starts on the crosshair");

        input.rightX = 0.25f;
        pad.update(input);
        check(!pad.needsTicks(), "a nudge under 0.3 does not turn");
        input.rightX = 0.8f;
        pad.update(input);
        check(pad.needsTicks(), "a push turns");
        pad.tick(1000);
        check(out.take().equals("place 0.500,0.435 +mouse3") && out.movedX > 0,
                "a turn aims at the crosshair, then holds the right button and moves");
        pad.tick(1016);
        check(out.take().isEmpty(), "the turn goes on without new presses");
        input.rightX = 0.1f;
        pad.update(input);
        check(pad.needsTicks(), "letting go still ticks until the turn ends");
        int moved = out.movedX;
        pad.tick(1100);
        check(out.take().isEmpty() && out.movedX == moved, "inside the dead zone: held, no motion");
        pad.tick(1170);
        check(out.take().equals("-mouse3"), "the right button goes up 150 ms after the stick centers");
        check(!pad.needsTicks(), "and the ticks stop");

        input.rightX = 0.8f;
        pad.update(input);
        pad.tick(2000);
        press(input, ClassicPadLayout.R3, true);
        pad.update(input);
        check(out.take().equals("place 0.500,0.435 +mouse3"), "R3 during a turn shares the held right button");
        input.rightX = 0;
        pad.update(input);
        pad.tick(2016);
        pad.tick(2200);
        check(out.take().isEmpty(), "the turn ending keeps the right button for R3");
        press(input, ClassicPadLayout.R3, false);
        pad.update(input);
        check(out.take().equals("-mouse3"), "R3 lets go of it");
        check(!pad.isHolding(), "nothing held");
    }

    // ---- release ------------------------------------------------------------------------

    private static void releaseAll() {
        Recorder out = new Recorder();
        ClassicPad pad = new ClassicPad(ClassicPadLayout.CONSOLE_EXPERIENCE, out, 27);
        ClassicPad.Input input = new ClassicPad.Input();
        press(input, ClassicPadLayout.LT, true);
        press(input, ClassicPadLayout.START, true);
        input.leftY = -1f;
        input.rightY = 1f;
        pad.update(input);
        pad.tick(0);
        out.take();
        pad.releaseAll();
        String released = out.take();
        check(released.equals("-mouse3 -ESC -W -SHIFT_L"), "mouse, then plain keys, then modifiers; got " + released);
        check(!pad.isHolding() && !pad.needsTicks(), "nothing held, no ticks");
        pad.releaseAll();
        check(out.take().isEmpty(), "a second release sends nothing");
        pad.update(input);
        check(out.take().equals("+SHIFT_L +ESC +W"), "the next reading presses what is still held");
    }

    // ---- choice -------------------------------------------------------------------------

    private static void selection() throws IOException {
        String[] older = {"1.12.1.5875", "1.12.3.6141", "2.4.3.8606", "3.3.5.12340", "4.3.4.15595", "5.4.8.18414",
                "6.2.4.21742", "7.3.5.26972", "8.3.7.35662", "", null, "unknown", "0.5.3.3368"};
        for (String version : older) check(!ClassicPadLayout.hasNativeGamepad(version), version + " needs the keys");
        String[] newer = {"9.2.7.45745", "11.0.2.56196", "1.13.2.31650", "1.14.4.54070", "1.15.7.61582",
                "2.5.4.44833", "3.4.3.54261", "4.4.0.54737", "5.5.0.61411"};
        for (String version : newer) check(ClassicPadLayout.hasNativeGamepad(version), version + " reads a gamepad itself");

        check(ClassicPadLayout.forSetting("xinput", ClassicPadLayout.CONSOLE_EXPERIENCE) == null, "xinput turns keys off");
        check(ClassicPadLayout.forSetting("auto", ClassicPadLayout.CONSOLE_EXPERIENCE) == ClassicPadLayout.CONSOLE_EXPERIENCE,
                "auto keeps the automatic choice");
        check(ClassicPadLayout.forSetting("auto", null) == null, "auto keeps a native client native");
        check(ClassicPadLayout.forSetting("standard", null) == ClassicPadLayout.STANDARD, "a fixed layout wins over native");
        check(ClassicPadLayout.forSetting("something else", ClassicPadLayout.STEAM_DECK) == ClassicPadLayout.STEAM_DECK,
                "unknown settings act as auto");

        Path root = Files.createTempDirectory("wehavephones-classicpad");
        try {
            File client = root.resolve("Vanilla").toFile();
            check(ClassicPadLayout.forAddons(client) == ClassicPadLayout.STANDARD, "missing client folder: standard");
            check(client.mkdirs(), "client folder");
            check(ClassicPadLayout.forAddons(client) == ClassicPadLayout.STANDARD, "no AddOns: standard");
            check(ClassicPadLayout.forAddons(null) == ClassicPadLayout.STANDARD, "no folder: standard");

            toc(client, "interface/addons/ShaguController/ShaguController.toc", "## Interface: 11200\n");
            check(ClassicPadLayout.forAddons(client) == ClassicPadLayout.STEAM_DECK, "ShaguController: Steam Deck keys");

            toc(client, "interface/addons/ConsolePort/ConsolePort.toc", "## Interface: 110002\n");
            check(ClassicPadLayout.forAddons(client) == ClassicPadLayout.STEAM_DECK, "a retail ConsolePort is not ConsolePortLK");
            toc(client, "interface/addons/ConsolePort/ConsolePort.toc", "ï»¿## Title: Console Port\r\n## Interface: 30300\r\n");
            check(ClassicPadLayout.forAddons(client) == ClassicPadLayout.CONSOLEPORT_LK, "ConsolePort for 3.x: ConsolePortLK");

            toc(client, "interface/addons/ConsoleExperienceClassic.disabled/ConsoleExperienceClassic.toc", "## Interface: 11200\n");
            check(ClassicPadLayout.forAddons(client) == ClassicPadLayout.CONSOLEPORT_LK, "a disabled folder does not count");
            toc(client, "interface/addons/consoleexperienceclassic/ConsoleExperienceClassic.TOC", "## Interface: 11200\n");
            check(ClassicPadLayout.forAddons(client) == ClassicPadLayout.CONSOLE_EXPERIENCE, "ConsoleExperience, any letter case");

            check(ClassicPadLayout.automatic("1.12.1.5875", client) == ClassicPadLayout.CONSOLE_EXPERIENCE, "auto for 1.12.1");
            check(ClassicPadLayout.automatic("9.2.7.45745", client) == null, "a 9.x client stays an Xbox controller");
            check(ClassicPadLayout.choose("consoleport_lk", "9.2.7.45745", client) == ClassicPadLayout.CONSOLEPORT_LK,
                    "Settings can still force a layout");
            check(ClassicPadLayout.choose("xinput", "1.12.1.5875", client) == null, "or turn the keys off");
        }
        finally {
            delete(root);
        }
    }

    // ---- helpers ------------------------------------------------------------------------

    private static void expect(ClassicPadLayout layout, int control, String action) {
        String actual = layout.action(control).toString();
        check(actual.equals(action), layout + " control " + control + ": expected " + action + ", got " + actual);
    }

    private static void press(ClassicPad.Input input, int control, boolean pressed) {
        if (control >= ClassicPadLayout.UP) {
            input.dpad[control - ClassicPadLayout.UP] = pressed;
        }
        else if (pressed) input.buttons |= 1 << control;
        else input.buttons &= ~(1 << control);
    }

    private static void toc(File client, String relative, String content) throws IOException {
        File file = new File(client, relative);
        check(file.getParentFile().isDirectory() || file.getParentFile().mkdirs(), "folder for " + relative);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void delete(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path item : (Iterable<Path>)walk.sorted(Comparator.reverseOrder())::iterator) Files.delete(item);
        }
    }
}
