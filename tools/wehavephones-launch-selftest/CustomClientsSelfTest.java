package com.wehavephones.mobile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Clients imported without Battle.net, on real temporary folders: executable
 * choice, safe folder names, realmlist reading and writing for every client
 * layout (1.x top level, 3.x Data\locale, 4.x+ Config.wtf), display CVars,
 * the start batch file, the PE version reader, the marker and the import copy.
 */
public final class CustomClientsSelfTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("custom-clients-test-");
        try {
            executables();
            folderNames(temporary);
            realmlistInput();
            realmlistLines();
            realmlistFiles(temporary);
            displayConfig(temporary);
            clientsAndLaunch(temporary);
            versions(temporary);
            gameList(temporary);
            importCopy(temporary);
            System.out.println("[custom-clients-selftest] PASS: " + checks + " checks");
        }
        finally {
            delete(temporary);
        }
    }

    private static void executables() {
        check(WowFlavor.pickClientExecutable(Arrays.asList("WowError.exe", "Wow.exe", "Repair.exe")).equals("Wow.exe"),
                "3.3.5a: Wow.exe, not the WowError.exe crash reporter");
        check(WowFlavor.pickClientExecutable(Arrays.asList("WowError.exe", "Launcher.exe")).isEmpty(),
                "a folder with only the crash reporter is not a client");
        check(WowFlavor.pickClientExecutable(Arrays.asList("WoW.exe", "Data")).equals("WoW.exe"), "1.12 WoW.exe keeps its case");
        check(WowFlavor.pickClientExecutable(Arrays.asList("Wow-64.exe", "WowVoiceProxy.exe")).equals("Wow-64.exe"),
                "Wow-64.exe accepted, voice helper ignored");
        check(WowFlavor.pickClientExecutable(Arrays.asList("Wow.exe", "Wow-ARM64.exe")).equals("Wow-ARM64.exe"), "ARM64 build first");
        check(WowFlavor.pickClientExecutable(Arrays.asList("WowVoiceProxy.exe", "WowUp.exe")).isEmpty(), "helpers only: none");
        check(WowFlavor.pickClientExecutable(null).isEmpty(), "null list");
        check(WowFlavor.isGameExecutable("Wow.exe") && WowFlavor.isGameExecutable("WoW.exe")
                && WowFlavor.isGameExecutable("Wow-64.exe"), "custom client processes are recognized (return to menu)");
        check(!WowFlavor.isGameExecutable("WowVoiceProxy.exe") && !WowFlavor.isGameExecutable("Launcher.exe"),
                "helpers are not the game");
    }

    private static void folderNames(Path temporary) throws IOException {
        File root = temporary.resolve("names").toFile();
        root.mkdirs();
        check(CustomClients.folderNameFor("WoW 3.3.5a", root).equals("WoW 3.3.5a"), "plain name kept");
        check(CustomClients.folderNameFor("\u9b54\u517d\u4e16\u754c", root).equals("Client"), "Chinese-only name -> Client");
        check(CustomClients.folderNameFor("\u9b54\u517d\u4e16\u754c 3.3.5a", root).equals("3.3.5a"), "unsafe characters dropped");
        check(CustomClients.folderNameFor("a&b|c%d", root).equals("a b c d"), "batch metacharacters removed");
        check(CustomClients.folderNameFor("..hidden.", root).equals("hidden"), "no leading or trailing dots");
        check(CustomClients.folderNameFor("con", root).equals("Client"), "reserved Windows name avoided");
        String longName = CustomClients.folderNameFor("abcdefghij abcdefghij abcdefghij abcdefghij abcdefghij", root);
        check(longName.length() <= 48 && CustomClients.isSafeFolderName(longName), "long names shortened");
        new File(root, "client").mkdirs();
        check(CustomClients.folderNameFor("\u9b54\u517d", root).equals("Client 2"), "unique ignoring case");
        new File(root, "Client 2").mkdirs();
        check(CustomClients.folderNameFor("Client", root).equals("Client 3"), "next free number");
        check(!CustomClients.isSafeFolderName("a&b") && !CustomClients.isSafeFolderName(".wehavephones-client-import")
                && !CustomClients.isSafeFolderName("trailing.") && CustomClients.isSafeFolderName("WoW (3.3.5a)"),
                "safe folder rule");
    }

    private static void realmlistInput() {
        check("logon.example.com".equals(CustomClients.normalizeRealmlist(" logon.example.com ")), "host trimmed");
        check("logon.example.com".equals(CustomClients.normalizeRealmlist("set realmlist logon.example.com")), "set realmlist prefix");
        check("10.0.0.2".equals(CustomClients.normalizeRealmlist("SET realmList \"10.0.0.2\"")), "Config.wtf syntax");
        check("10.0.0.2:3725".equals(CustomClients.normalizeRealmlist("10.0.0.2:3725")), "port kept");
        check(CustomClients.normalizeRealmlist("two words") == null, "spaces refused");
        check(CustomClients.normalizeRealmlist("") == null && CustomClients.normalizeRealmlist(null) == null, "empty refused");
        check(CustomClients.normalizeRealmlist("a\"b") == null && CustomClients.normalizeRealmlist("x;y") == null
                && CustomClients.normalizeRealmlist("\u670d\u52a1\u5668") == null, "quotes, separators, non-ASCII refused");
    }

    private static void realmlistLines() {
        check(CustomClients.withRealmlist(null, "h").equals("set realmlist h\r\n"), "new file, CRLF");
        check(CustomClients.withRealmlist("set realmlist old\r\nset patchlist p\r\n", "h")
                .equals("set realmlist h\r\nset patchlist p\r\n"), "patchlist kept");
        check(CustomClients.withRealmlist("set realmlist old\nset patchlist p\n", "h")
                .equals("set realmlist h\nset patchlist p\n"), "LF file stays LF");
        check(CustomClients.withRealmlist("SET realmlist \"old\"\r\n", "h").equals("set realmlist h\r\n"), "quoted, any case");
        check(CustomClients.withRealmlist("set realmlistbn \"x\"\r\n", "h")
                .equals("set realmlist h\r\nset realmlistbn \"x\"\r\n"), "realmlistbn is another setting");
        check(CustomClients.withRealmlist("\u00ef\u00bb\u00bfset realmlist old\r\n", "h").equals("set realmlist h\r\n"),
                "UTF-8 BOM line replaced");
        check(CustomClients.withRealmlist("set realmlist a\r\nset realmlist b\r\n\r\n\r\n", "h").equals("set realmlist h\r\n"),
                "duplicates collapse, trailing blanks dropped");
    }

    private static void realmlistFiles(Path temporary) throws IOException {
        File client = temporary.resolve("wotlk").toFile();
        write(new File(client, "Wow.exe"), "MZ");
        write(new File(client, "Data/zhCN/realmlist.wtf"), "set realmlist old.example\r\nset patchlist p\r\n");
        new File(client, "Data/enUS").mkdirs();
        new File(client, "Data/Interface").mkdirs();
        byte[] chineseRealm = "SET realmName \"\u51b0\u5c01\"\r\n".getBytes(StandardCharsets.UTF_8);
        File config = new File(client, "WTF/Config.wtf");
        config.getParentFile().mkdirs();
        Files.write(config.toPath(), concat("SET realmList \"old.example\"\r\n".getBytes(StandardCharsets.US_ASCII), chineseRealm));
        check(CustomClients.readRealmlist(client).equals("old.example"), "3.x realmlist read from Data\\locale");

        CustomClients.writeRealmlist(client, "new.example:3724");
        check(read(new File(client, "Data/zhCN/realmlist.wtf")).equals("set realmlist new.example:3724\r\nset patchlist p\r\n"),
                "existing locale file updated, patchlist kept");
        check(read(new File(client, "Data/enUS/realmlist.wtf")).equals("set realmlist new.example:3724\r\n"), "locale file created");
        check(read(new File(client, "realmlist.wtf")).equals("set realmlist new.example:3724\r\n"), "top-level file for 1.x/2.x");
        check(!new File(client, "Data/Interface/realmlist.wtf").exists(), "non-locale folders untouched");
        byte[] configBytes = Files.readAllBytes(config.toPath());
        String configText = new String(configBytes, StandardCharsets.ISO_8859_1);
        check(configText.contains("SET realmList \"new.example:3724\"") && !configText.contains("old.example"), "Config.wtf realmList");
        check(indexOf(configBytes, chineseRealm) >= 0, "other Config.wtf bytes (UTF-8 realm name) preserved");
        check(CustomClients.readRealmlist(client).equals("new.example:3724"), "read back");
        check(!new File(client, "Data/zhCN/.realmlist.wtf.wehavephones-tmp").exists(), "no temporary files left");

        File cataclysm = temporary.resolve("cata").toFile();
        write(new File(cataclysm, "Wow.exe"), "MZ");
        write(new File(cataclysm, "wtf/config.wtf"), "SET realmList \"cata.example\"\r\n");
        check(CustomClients.readRealmlist(cataclysm).equals("cata.example"), "4.x realmlist from Config.wtf (any case)");
        CustomClients.writeRealmlist(cataclysm, "other.example");
        check(read(new File(cataclysm, "wtf/config.wtf")).contains("SET realmList \"other.example\""), "lower-case WTF folder reused");
        check(!new File(cataclysm, "WTF").exists() || new File(cataclysm, "WTF").getCanonicalFile()
                .equals(new File(cataclysm, "wtf").getCanonicalFile()), "no second WTF folder");

        try {
            CustomClients.writeRealmlist(cataclysm, "bad host");
            throw new AssertionError("invalid host accepted");
        }
        catch (IOException expected) {
            check(read(new File(cataclysm, "wtf/config.wtf")).contains("other.example"), "invalid host changes nothing");
        }
    }

    private static void displayConfig(Path temporary) throws IOException {
        File client = temporary.resolve("display").toFile();
        client.mkdirs();
        check(CustomClients.prepareConfig(client, "1024x576"), "Config.wtf created");
        String created = read(new File(client, "WTF/Config.wtf"));
        check(created.equals("SET gxWindow \"1\"\nSET gxMaximize \"1\"\nSET gxResolution \"1024x576\"\n"), "display CVars");
        check(!CustomClients.prepareConfig(client, "1024x576"), "idempotent");
        write(new File(client, "WTF/Config.wtf"), "SET gxResolution \"1920x1080\"\r\nSET gxWindow \"0\"\r\nSET realmName \"X\"\r\n");
        check(CustomClients.prepareConfig(client, "1280x720"), "PC settings replaced");
        String updated = read(new File(client, "WTF/Config.wtf"));
        check(updated.contains("SET gxResolution \"1280x720\"") && updated.contains("SET gxWindow \"1\"")
                && !updated.contains("1920x1080") && updated.contains("SET realmName \"X\"") && updated.contains("\r\n"),
                "only display CVars change; CRLF kept");
    }

    private static void clientsAndLaunch(Path temporary) throws IOException {
        File root = temporary.resolve("WoW Clients").toFile();
        File wotlk = new File(root, "WoW 3.3.5a");
        write(new File(wotlk, "Wow.exe"), "MZ");
        write(new File(wotlk, "WowError.exe"), "MZ");
        write(new File(wotlk, "Data/enUS/realmlist.wtf"), "set realmlist logon.example.com\r\n");
        Properties marker = new Properties();
        marker.setProperty("name", "\u9b54\u517d\u4e16\u754c 3.3.5a");
        marker.setProperty("version", "3.3.5.12340");
        storeMarker(wotlk, marker);

        File vanilla = new File(root, "Vanilla");
        write(new File(vanilla, "WoW.exe"), "MZ");
        Properties vanillaMarker = new Properties();
        vanillaMarker.setProperty("args", "-console");
        storeMarker(vanilla, vanillaMarker);

        File legion = new File(root, "Legion");
        write(new File(legion, "Wow-64.exe"), "MZ");
        write(new File(legion, "Arctium Launcher.exe"), "MZ");
        Properties legionMarker = new Properties();
        legionMarker.setProperty("exe", "Arctium Launcher.exe");
        legionMarker.setProperty("args", "& del x");
        storeMarker(legion, legionMarker);

        write(new File(root, "Tools/readme.txt"), "not a client");
        write(new File(root, "a&b/Wow.exe"), "MZ");
        write(new File(root, CustomClients.STAGING_NAME + "/Wow.exe"), "MZ");

        List<WowProduct> clients = CustomClients.scan(root);
        List<String> names = new ArrayList<>();
        for (WowProduct product : clients) names.add(product.directoryName);
        check(names.equals(Arrays.asList("Legion", "Vanilla", "WoW 3.3.5a")), "only usable client folders, by name: " + names);

        WowProduct product = clients.get(2);
        check(product.isCustom() && product.getDisplayName().equals("\u9b54\u517d\u4e16\u754c 3.3.5a"), "marker name (UTF-8)");
        check(product.getTitle().equals(product.getDisplayName()), "title is the client's name");
        check(product.getExecutable().equals("Wow.exe") && product.getVersion().equals("3.3.5.12340"), "exe and marker version");
        check(product.getRealmlist().equals("logon.example.com"), "realmlist on Play");
        check(product.getEdition().equals("Wrath of the Lich King \u00b7 logon.example.com"), "edition: expansion and server");
        check(product.getFlavor() == WowFlavor.UNKNOWN, "never mistaken for a Battle.net game");
        String bat = CustomClients.launchBat(product);
        check(bat.equals("@echo off\r\nrem WeHavePhones: start a client imported without Battle.net\r\n"
                + "cd /d \"C:\\WoW Clients\\WoW 3.3.5a\"\r\n\"Wow.exe\"\r\n"), "start batch file: " + bat);

        WowProduct vanillaProduct = clients.get(1);
        check(vanillaProduct.getDisplayName().equals("Vanilla") && vanillaProduct.getExecutable().equals("WoW.exe"), "folder name default");
        check(CustomClients.launchBat(vanillaProduct).endsWith("\"WoW.exe\" -console\r\n"), "marker arguments");

        WowProduct legionProduct = clients.get(0);
        check(legionProduct.getExecutable().equals("Arctium Launcher.exe"), "marker executable");
        check(legionProduct.getLaunchArguments().isEmpty(), "unsafe marker arguments ignored");
        check(CustomClients.read(new File(root, "Tools")) == null, "folder without a client");
    }

    private static void versions(Path temporary) throws IOException {
        int high = (3 << 16) | 3, low = (5 << 16) | 12340;
        File pe32 = temporary.resolve("pe32/Wow.exe").toFile();
        pe32.getParentFile().mkdirs();
        Files.write(pe32.toPath(), portableExecutable(false, high, low));
        check(CustomClients.exeVersion(pe32).equals("3.3.5.12340"), "PE32 version resource");
        File pe64 = temporary.resolve("pe64/Wow-64.exe").toFile();
        pe64.getParentFile().mkdirs();
        Files.write(pe64.toPath(), portableExecutable(true, (7 << 16) | 3, (5 << 16) | 26972));
        check(CustomClients.exeVersion(pe64).equals("7.3.5.26972"), "PE32+ version resource");
        File text = temporary.resolve("pe32/readme.txt").toFile();
        Files.write(text.toPath(), "not an executable at all, but long enough to be read".getBytes(StandardCharsets.US_ASCII));
        check(CustomClients.exeVersion(text).isEmpty(), "not PE: unknown");
        File truncated = temporary.resolve("pe32/cut.exe").toFile();
        Files.write(truncated.toPath(), Arrays.copyOf(portableExecutable(false, high, low), 0x190));
        check(CustomClients.exeVersion(truncated).isEmpty(), "truncated: unknown");
        check(CustomClients.exeVersion(temporary.resolve("missing.exe").toFile()).isEmpty(), "missing: unknown");

        File client = temporary.resolve("marker").toFile();
        client.mkdirs();
        Files.write(new File(client, "Wow.exe").toPath(), portableExecutable(false, high, low));
        CustomClients.writeMarker(client, "\u9b54\u517d\u4e16\u754c", "\u9b54\u517d\u4e16\u754c");
        String markerText = new String(Files.readAllBytes(new File(client, CustomClients.MARKER).toPath()), StandardCharsets.UTF_8);
        check(markerText.contains("name=\u9b54\u517d\u4e16\u754c") && markerText.contains("version=3.3.5.12340"),
                "marker is readable UTF-8 with the build");
        WowProduct product = CustomClients.read(client);
        check(product != null && product.getDisplayName().equals("\u9b54\u517d\u4e16\u754c")
                && product.getVersion().equals("3.3.5.12340"), "marker read back");
    }

    private static void gameList(Path temporary) throws IOException {
        File client = temporary.resolve("list/WoW 3.3.5a").toFile();
        write(new File(client, "Wow.exe"), "MZ");
        write(new File(client, "realmlist.wtf"), "set realmlist host.example\r\n");
        WowProduct custom = CustomClients.read(client);
        List<BattleNetGames.Game> games = BattleNetGames.merge(Collections.singletonList(custom), null, null, null);
        check(games.size() == 1, "custom client listed");
        BattleNetGames.Game game = games.get(0);
        check(game.isCustom() && game.installed && game.selectionKey().equals("WoW 3.3.5a"), "playable, keyed by folder");
        check(game.getTitle().equals("WoW 3.3.5a") && game.getEdition().equals("host.example"), "Play title and edition");
        check(game.bnetGameId().isEmpty(), "no Battle.net id");
        check(BattleNetGames.pick(games, "WoW 3.3.5a") == game && BattleNetGames.pick(games, "_retail_") == game,
                "selected, and the fallback when nothing else is installed");
    }

    private static void importCopy(Path temporary) throws IOException {
        File downloads = temporary.resolve("Downloads").toFile();
        File source = new File(downloads, "WoW 3.3.5a");
        write(new File(source, "Wow.exe"), "MZ-client");
        write(new File(source, "Data/common.MPQ"), "mpq");
        new File(source, "Interface/AddOns").mkdirs();
        write(new File(downloads, "Notes/readme.txt"), "x");
        write(new File(downloads, ".hidden/Wow.exe"), "MZ");
        write(new File(downloads, "World of Warcraft/_retail_/Wow.exe"), "MZ");
        List<File> candidates = CustomClients.candidates(downloads);
        check(candidates.size() == 1 && candidates.get(0).equals(source), "only folders with Wow*.exe at the top: " + candidates);

        File root = temporary.resolve("prefix/WoW Clients").toFile();
        File destination = new File(root, CustomClients.folderNameFor(source.getName(), root));
        AtomicBoolean hookSawStaging = new AtomicBoolean();
        WowFileImporter.copyClient(source, destination, (copied, total) -> {
            check(!destination.exists(), "destination hidden while copying");
        }, () -> false, staging -> {
            hookSawStaging.set(new File(staging, "Wow.exe").isFile() && !destination.exists());
            CustomClients.writeMarker(staging, "Imported", source.getName());
        });
        check(hookSawStaging.get(), "marker written into the complete staging copy");
        check(new File(destination, "Data/common.MPQ").isFile() && new File(destination, "Interface/AddOns").isDirectory(),
                "files and empty folders copied");
        check(new File(destination, CustomClients.MARKER).isFile() && new File(source, "Wow.exe").isFile(), "marker added, source kept");
        check(!new File(root, CustomClients.STAGING_NAME).exists(), "staging consumed");
        WowProduct imported = CustomClients.read(destination);
        check(imported != null && imported.getDisplayName().equals("Imported"), "imported client is listed");

        expect(WowFileImporter.Problem.ALREADY_INSTALLED, () -> WowFileImporter.copyClient(source, destination,
                (copied, total) -> {}, () -> false, null));
        File second = new File(root, CustomClients.folderNameFor(source.getName(), root));
        check(second.getName().equals("WoW 3.3.5a 2"), "a second import gets its own folder");
        expect(WowFileImporter.Problem.INVALID_SOURCE, () -> WowFileImporter.copyClient(new File(downloads, "Notes"),
                second, (copied, total) -> {}, () -> false, null));
        expect(WowFileImporter.Problem.MISSING_SOURCE, () -> WowFileImporter.copyClient(new File(downloads, "gone"),
                second, (copied, total) -> {}, () -> false, null));
        try {
            WowFileImporter.copyClient(source, second, (copied, total) -> {}, () -> false, null, () -> 1);
            throw new AssertionError("expected a free space failure");
        }
        catch (WowFileImporter.ImportException failure) {
            check(failure.problem == WowFileImporter.Problem.NOT_ENOUGH_SPACE, "low space refused");
        }
        expect(WowFileImporter.Problem.COPY_FAILED, () -> WowFileImporter.copyClient(source, second,
                (copied, total) -> {}, () -> false, staging -> { throw new IOException("marker failed"); }));
        expect(WowFileImporter.Problem.CANCELLED, () -> WowFileImporter.copyClient(source, second,
                (copied, total) -> {}, () -> true, null));
        check(!second.exists() && !new File(root, CustomClients.STAGING_NAME).exists(), "failures leave no partial client");
        check(CustomClients.scan(root).size() == 1, "only the complete import is listed");
    }

    // ---- fixtures -------------------------------------------------------------------------

    /** A minimal PE32/PE32+ file with one .rsrc section holding a VS_FIXEDFILEINFO at an odd offset. */
    private static byte[] portableExecutable(boolean plus, int versionHigh, int versionLow) {
        int peOffset = 0x80;
        int optionalSize = plus ? 240 : 224;
        int sectionTable = peOffset + 24 + optionalSize;
        int rawPointer = 0x400;
        int virtualAddress = 0x2000;
        byte[] resource = new byte[0x200];
        int at = 0x53;
        put32(resource, at, 0xFEEF04BD);
        put32(resource, at + 4, 0x00010000);
        put32(resource, at + 8, versionHigh);
        put32(resource, at + 12, versionLow);
        byte[] file = new byte[rawPointer + resource.length];
        file[0] = 'M';
        file[1] = 'Z';
        put32(file, 0x3C, peOffset);
        put32(file, peOffset, 0x00004550);
        put16(file, peOffset + 4, plus ? 0x8664 : 0x14C);
        put16(file, peOffset + 6, 1);
        put16(file, peOffset + 20, optionalSize);
        int optional = peOffset + 24;
        put16(file, optional, plus ? 0x20B : 0x10B);
        put32(file, optional + (plus ? 108 : 92), 16);
        int directories = optional + (plus ? 112 : 96);
        put32(file, directories + 16, virtualAddress);
        put32(file, directories + 20, resource.length);
        byte[] name = ".rsrc".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, file, sectionTable, name.length);
        put32(file, sectionTable + 8, resource.length);
        put32(file, sectionTable + 12, virtualAddress);
        put32(file, sectionTable + 16, resource.length);
        put32(file, sectionTable + 20, rawPointer);
        System.arraycopy(resource, 0, file, rawPointer, resource.length);
        return file;
    }

    private static void put32(byte[] data, int offset, int value) {
        for (int i = 0; i < 4; i++) data[offset + i] = (byte)(value >>> (8 * i));
    }

    private static void put16(byte[] data, int offset, int value) {
        data[offset] = (byte)value;
        data[offset + 1] = (byte)(value >>> 8);
    }

    private static void storeMarker(File directory, Properties properties) throws IOException {
        directory.mkdirs();
        try (java.io.Writer writer = Files.newBufferedWriter(new File(directory, CustomClients.MARKER).toPath(), StandardCharsets.UTF_8)) {
            properties.store(writer, null);
        }
    }

    private static void write(File file, String text) throws IOException {
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static int indexOf(byte[] data, byte[] part) {
        outer:
        for (int i = 0; i + part.length <= data.length; i++) {
            for (int j = 0; j < part.length; j++) {
                if (data[i + j] != part[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private interface Action { void run() throws IOException; }

    private static void expect(WowFileImporter.Problem problem, Action action) throws IOException {
        try {
            action.run();
        }
        catch (WowFileImporter.ImportException failure) {
            check(failure.problem == problem, "expected " + problem + ", got " + failure.problem);
            return;
        }
        throw new AssertionError("expected " + problem);
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
