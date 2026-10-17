package com.wehavephones.mobile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * World of Warcraft clients imported without Battle.net (any version, for a
 * server the player picks with its realmlist): where they live, how they are
 * found, how their realmlist is read and written, the display CVars and the
 * batch file that starts them. Pure Java so the host self-test can exercise it.
 *
 * <p>Each client is a folder under {@code C:\WoW Clients} with its game
 * executable at the top level. The folder name may only use letters, digits,
 * spaces and {@code . _ ( ) -} because it goes into a batch file. An optional
 * marker file ({@value #MARKER}, UTF-8 Java properties) keeps the name shown on
 * Play ({@code name}) and the client build ({@code version}); for unusual
 * clients it can also name the executable ({@code exe}) and extra command-line
 * arguments ({@code args}).
 */
public final class CustomClients {
    public static final String ROOT_NAME = "WoW Clients";
    public static final String GUEST_ROOT = "C:\\" + ROOT_NAME;
    public static final String MARKER = ".wehavephones-client";
    /** Staging folder of an import in progress, next to the clients; never listed. */
    public static final String STAGING_NAME = ".wehavephones-client-import";
    public static final String REALMLIST_FILE = "realmlist.wtf";

    static final String KEY_NAME = "name";
    static final String KEY_VERSION = "version";
    static final String KEY_SOURCE = "source";
    static final String KEY_EXE = "exe";
    static final String KEY_ARGS = "args";

    private static final Pattern UNSAFE_FOLDER_CHARS = Pattern.compile("[^A-Za-z0-9 ._()-]");
    private static final Pattern SAFE_FOLDER = Pattern.compile("[A-Za-z0-9 ._()-]{1,64}");
    private static final Pattern RESERVED_FOLDER = Pattern.compile("(?i)(con|prn|aux|nul|com[0-9]|lpt[0-9])(\\..*)?");
    private static final Pattern SAFE_EXE = Pattern.compile("[A-Za-z0-9 ._()-]+\\.(?i:exe)");
    private static final Pattern SAFE_ARGS = Pattern.compile("[A-Za-z0-9 ._:/=\\\\-]*");
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9._:\\[\\]-]{1,253}");
    private static final Pattern REALMLIST_LINE = Pattern.compile("(?i)^\\s*set\\s+realmlist(\\s|\"|$).*");
    private static final Pattern REALMLIST_VALUE = Pattern.compile("(?i)^\\s*set\\s+realmlist\\s+\"?([^\"\\s]+)\"?.*");
    private static final String UTF8_BOM_LATIN1 = "\u00ef\u00bb\u00bf";
    private static final List<String> LOCALES = Arrays.asList("enUS", "enGB", "enCN", "enTW", "deDE", "esES",
            "esMX", "frFR", "itIT", "koKR", "ptBR", "ptPT", "ruRU", "zhCN", "zhTW");
    private static final int MAX_FOLDER_LENGTH = 48;
    private static final long MAX_RESOURCE_BYTES = 32L * 1024 * 1024;
    private static final Map<String, String> VERSION_CACHE = new ConcurrentHashMap<>();

    private CustomClients() {}

    // ---- clients ------------------------------------------------------------------------

    /** Clients under {@code root} (the container's C:\WoW Clients), sorted by name. */
    public static List<WowProduct> scan(File root) {
        ArrayList<WowProduct> products = new ArrayList<>();
        File[] children = root != null ? root.listFiles() : null;
        if (children == null) return products;
        for (File child : children) {
            WowProduct product = read(child);
            if (product != null) products.add(product);
        }
        Collections.sort(products, (first, second) -> first.getDisplayName().compareToIgnoreCase(second.getDisplayName()));
        return products;
    }

    /** The client in {@code directory}, or null when it is not a usable client folder. */
    public static WowProduct read(File directory) {
        if (directory == null || !directory.isDirectory() || !isSafeFolderName(directory.getName())) return null;
        String[] names = directory.list();
        if (names == null) return null;
        Properties marker = readMarker(directory);
        String executable = marker.getProperty(KEY_EXE, "").trim();
        if (executable.isEmpty() || !SAFE_EXE.matcher(executable).matches() || !new File(directory, executable).isFile()) {
            executable = WowFlavor.pickClientExecutable(Arrays.asList(names));
        }
        if (executable.isEmpty()) return null;
        WowProduct product = new WowProduct(directory.getName(), directory);
        product.setCustom(marker.getProperty(KEY_NAME, directory.getName()));
        product.setExecutable(executable);
        String version = marker.getProperty(KEY_VERSION, "").trim();
        product.setVersion(!version.isEmpty() ? version : exeVersion(new File(directory, executable)));
        product.setRealmlist(readRealmlist(directory));
        String arguments = marker.getProperty(KEY_ARGS, "").trim();
        if (SAFE_ARGS.matcher(arguments).matches()) product.setLaunchArguments(arguments);
        return product;
    }

    /** Folder names that are safe in a batch file and on Windows. */
    public static boolean isSafeFolderName(String name) {
        return name != null && SAFE_FOLDER.matcher(name).matches() && !name.startsWith(".")
                && !name.endsWith(".") && !name.endsWith(" ") && !RESERVED_FOLDER.matcher(name).matches();
    }

    /**
     * Folder name for a client imported from {@code sourceName}: unsafe
     * characters dropped (a Chinese name becomes "Client"), unique under
     * {@code root} ignoring case, as Windows paths are.
     */
    public static String folderNameFor(String sourceName, File root) {
        String cleaned = UNSAFE_FOLDER_CHARS.matcher(sourceName != null ? sourceName : "").replaceAll(" ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim().replaceAll("^[ ._-]+|[ ._-]+$", "");
        if (cleaned.length() > MAX_FOLDER_LENGTH) cleaned = cleaned.substring(0, MAX_FOLDER_LENGTH).replaceAll("[ ._-]+$", "");
        if (cleaned.isEmpty() || RESERVED_FOLDER.matcher(cleaned).matches()) cleaned = "Client";
        String[] taken = root != null ? root.list() : null;
        String candidate = cleaned;
        for (int number = 2; contains(taken, candidate); number++) candidate = cleaned + " " + number;
        return candidate;
    }

    private static boolean contains(String[] names, String name) {
        if (names == null) return false;
        for (String existing : names) {
            if (existing.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Folders in {@code downloads} with a game executable at their top level, sorted by name. */
    public static List<File> candidates(File downloads) {
        ArrayList<File> result = new ArrayList<>();
        File[] children = downloads != null ? downloads.listFiles() : null;
        if (children == null) return result;
        for (File child : children) {
            if (!child.isDirectory() || child.getName().startsWith(".")) continue;
            String[] names = child.list();
            if (names != null && !WowFlavor.pickClientExecutable(Arrays.asList(names)).isEmpty()) result.add(child);
        }
        Collections.sort(result, (first, second) -> first.getName().compareToIgnoreCase(second.getName()));
        return result;
    }

    // ---- marker -------------------------------------------------------------------------

    static Properties readMarker(File directory) {
        Properties properties = new Properties();
        File file = new File(directory, MARKER);
        if (!file.isFile()) return properties;
        try (InputStream input = new FileInputStream(file)) {
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        catch (IOException | IllegalArgumentException ignored) {
            properties.clear();
        }
        return properties;
    }

    /**
     * Records the name shown on Play, where the client came from and its
     * build (read once here so Play does not open the executable again).
     */
    public static void writeMarker(File directory, String name, String source) throws IOException {
        Properties properties = readMarker(directory);
        if (name != null && !name.trim().isEmpty()) properties.setProperty(KEY_NAME, name.trim());
        if (source != null && !source.isEmpty()) properties.setProperty(KEY_SOURCE, source);
        String[] names = directory.list();
        String executable = names != null ? WowFlavor.pickClientExecutable(Arrays.asList(names)) : "";
        String version = executable.isEmpty() ? "" : exeVersion(new File(directory, executable));
        if (!version.isEmpty()) properties.setProperty(KEY_VERSION, version);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(new File(directory, MARKER)), StandardCharsets.UTF_8)) {
            properties.store(writer, "WeHavePhones client imported without Battle.net (name, version; optional exe, args)");
        }
    }

    // ---- realmlist ----------------------------------------------------------------------

    /**
     * Server address from what the player typed: a host, host:port or IP,
     * also accepted as {@code set realmlist host} or in quotes. Null when it
     * is not a usable address.
     */
    public static String normalizeRealmlist(String input) {
        if (input == null) return null;
        String value = input.trim();
        if (value.regionMatches(true, 0, "set realmlist", 0, 13)) value = value.substring(13).trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return HOST.matcher(value).matches() ? value : null;
    }

    /** The client's server address: realmlist.wtf (Data\<locale>, then top level), then Config.wtf. Empty when none. */
    public static String readRealmlist(File clientDir) {
        for (File file : realmlistFiles(clientDir, false)) {
            String text = readText(file);
            if (text == null) continue;
            for (String line : text.split("\r\n|\r|\n")) {
                Matcher matcher = REALMLIST_VALUE.matcher(line.replace(UTF8_BOM_LATIN1, ""));
                if (matcher.matches()) return matcher.group(1);
            }
        }
        String config = readText(configFile(clientDir));
        if (config != null) {
            String value = WowClientFiles.cvarValue(config, "realmList");
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    /**
     * Points the client at {@code host}, whatever its version reads: the
     * top-level realmlist.wtf (1.x, 2.x), Data\<locale>\realmlist.wtf (3.x)
     * and {@code SET realmList} in WTF\Config.wtf (4.x on). Missing files are
     * created; other lines (such as patchlist) are kept.
     */
    public static void writeRealmlist(File clientDir, String host) throws IOException {
        String value = normalizeRealmlist(host);
        if (value == null) throw new IOException("Invalid realmlist: " + host);
        for (File file : realmlistFiles(clientDir, true)) {
            String existing = file.isFile() ? readText(file) : null;
            String updated = withRealmlist(existing, value);
            if (!updated.equals(existing)) writeText(file, updated);
        }
        File config = configFile(clientDir);
        String original = config.isFile() ? readText(config) : "";
        // Never replace a Config.wtf that exists but could not be read.
        if (original == null) throw new IOException("Cannot read " + config);
        LinkedHashMap<String, String> settings = new LinkedHashMap<>();
        settings.put("realmList", value);
        String updated = MemoryTransforms.editConfigWtf(original, settings);
        if (!updated.equals(original)) writeText(config, updated);
    }

    /** realmlist.wtf content with its {@code set realmlist} line pointing at {@code host}. */
    static String withRealmlist(String existing, String host) {
        String newline = existing != null && existing.contains("\n") && !existing.contains("\r\n") ? "\n" : "\r\n";
        ArrayList<String> lines = new ArrayList<>();
        boolean replaced = false;
        if (existing != null && !existing.isEmpty()) {
            for (String line : existing.split("\r\n|\r|\n")) {
                if (REALMLIST_LINE.matcher(line.replace(UTF8_BOM_LATIN1, "")).matches()) {
                    if (!replaced) lines.add("set realmlist " + host);
                    replaced = true;
                    continue;
                }
                lines.add(line);
            }
        }
        if (!replaced) lines.add(0, "set realmlist " + host);
        while (lines.size() > 1 && lines.get(lines.size() - 1).trim().isEmpty()) lines.remove(lines.size() - 1);
        return String.join(newline, lines) + newline;
    }

    /** realmlist.wtf in each Data\<locale> folder, then the top-level one; only existing files unless {@code includeMissing}. */
    static List<File> realmlistFiles(File clientDir, boolean includeMissing) {
        ArrayList<File> files = new ArrayList<>();
        File[] children = child(clientDir, "Data").listFiles();
        if (children != null) {
            Arrays.sort(children);
            for (File locale : children) {
                if (!locale.isDirectory() || !isLocale(locale.getName())) continue;
                File file = child(locale, REALMLIST_FILE);
                if (includeMissing || file.isFile()) files.add(file);
            }
        }
        File top = child(clientDir, REALMLIST_FILE);
        if (includeMissing || top.isFile()) files.add(top);
        return files;
    }

    private static boolean isLocale(String name) {
        for (String locale : LOCALES) {
            if (locale.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    // ---- display and launch -------------------------------------------------------------

    /**
     * Windowed at the session's screen size and maximized, so the game fills
     * the screen without a mode switch: CVars every client from 1.x on knows.
     */
    public static LinkedHashMap<String, String> displayCvars(String screenSize) {
        LinkedHashMap<String, String> settings = new LinkedHashMap<>();
        settings.put("gxWindow", "1");
        settings.put("gxMaximize", "1");
        settings.put("gxResolution", screenSize);
        return settings;
    }

    /** Writes {@link #displayCvars} into WTF\Config.wtf (created when missing); true when the file changed. */
    public static boolean prepareConfig(File clientDir, String screenSize) throws IOException {
        File config = configFile(clientDir);
        String original = config.isFile() ? readText(config) : "";
        if (original == null) throw new IOException("Cannot read " + config);
        String updated = MemoryTransforms.editConfigWtf(original, displayCvars(screenSize));
        if (updated.equals(original)) return false;
        writeText(config, updated);
        return true;
    }

    /** Batch file that starts a client directly (no Battle.net) from its own folder. */
    public static String launchBat(WowProduct product) {
        String arguments = product.getLaunchArguments();
        return "@echo off\r\n"
                + "rem WeHavePhones: start a client imported without Battle.net\r\n"
                + "cd /d \"" + GUEST_ROOT + "\\" + product.directory.getName() + "\"\r\n"
                + "\"" + product.getExecutable() + "\"" + (arguments.isEmpty() ? "" : " " + arguments) + "\r\n";
    }

    static File configFile(File clientDir) {
        return child(child(clientDir, "WTF"), "Config.wtf");
    }

    /** The client's Interface\AddOns, reusing the folders it has whatever their letter case; it may not exist yet. */
    public static File addonsDir(File clientDir) {
        return child(child(clientDir, "Interface"), "AddOns");
    }

    // ---- executable version -------------------------------------------------------------

    /** File version from the executable's version resource (for example 3.3.5.12340); empty when unknown. */
    public static String exeVersion(File exe) {
        if (exe == null || !exe.isFile()) return "";
        String key = exe.getPath() + "|" + exe.length() + "|" + exe.lastModified();
        String cached = VERSION_CACHE.get(key);
        if (cached != null) return cached;
        String version = readExeVersion(exe);
        VERSION_CACHE.put(key, version);
        return version;
    }

    /** Reads only the PE headers and the resource section, not the whole executable. */
    static String readExeVersion(File exe) {
        try (RandomAccessFile file = new RandomAccessFile(exe, "r")) {
            long length = file.length();
            if (length < 0x40) return "";
            long pe = u32(file, 0x3C);
            if (pe < 0x40 || pe + 24 > length || u32(file, pe) != 0x00004550L) return "";
            int sections = u16(file, pe + 6);
            int optionalSize = u16(file, pe + 20);
            long optional = pe + 24;
            int magic = u16(file, optional);
            boolean plus = magic == 0x20B;
            if (!plus && magic != 0x10B) return "";
            if (u32(file, optional + (plus ? 108 : 92)) < 3) return "";
            long resourceEntry = optional + (plus ? 112 : 96) + 2 * 8;
            long rva = u32(file, resourceEntry);
            long size = u32(file, resourceEntry + 4);
            if (rva == 0 || size == 0) return "";
            long table = optional + optionalSize;
            for (int i = 0; i < sections; i++) {
                long header = table + 40L * i;
                if (header + 40 > length) break;
                long virtualSize = u32(file, header + 8);
                long virtualAddress = u32(file, header + 12);
                long rawSize = u32(file, header + 16);
                long rawPointer = u32(file, header + 20);
                if (rva < virtualAddress || rva >= virtualAddress + Math.max(virtualSize, rawSize)) continue;
                long offset = rawPointer + (rva - virtualAddress);
                long bytes = Math.min(Math.min(size, Math.min(rawPointer + rawSize, length) - offset), MAX_RESOURCE_BYTES);
                if (bytes <= 0) return "";
                byte[] data = new byte[(int)bytes];
                file.seek(offset);
                file.readFully(data);
                return fixedFileVersion(data);
            }
        }
        catch (IOException | RuntimeException ignored) {
            // Not a readable PE file: the version stays unknown.
        }
        return "";
    }

    /** "major.minor.patch.build" from the first VS_FIXEDFILEINFO in {@code data}; empty when absent. */
    static String fixedFileVersion(byte[] data) {
        for (int i = 0; i + 16 <= data.length; i++) {
            if (le32(data, i) != 0xFEEF04BD) continue;
            int structure = le32(data, i + 4);
            if (structure != 0x00010000 && structure != 0) continue;
            int high = le32(data, i + 8);
            int low = le32(data, i + 12);
            return (high >>> 16) + "." + (high & 0xFFFF) + "." + (low >>> 16) + "." + (low & 0xFFFF);
        }
        return "";
    }

    private static int le32(byte[] data, int offset) {
        return (data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8
                | (data[offset + 2] & 0xFF) << 16 | (data[offset + 3] & 0xFF) << 24;
    }

    private static long u32(RandomAccessFile file, long offset) throws IOException {
        byte[] bytes = new byte[4];
        file.seek(offset);
        file.readFully(bytes);
        return le32(bytes, 0) & 0xFFFFFFFFL;
    }

    private static int u16(RandomAccessFile file, long offset) throws IOException {
        byte[] bytes = new byte[2];
        file.seek(offset);
        file.readFully(bytes);
        return (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8;
    }

    // ---- files --------------------------------------------------------------------------

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

    /** Byte-preserving text: Config.wtf may hold UTF-8 or a local code page, and only ASCII lines are changed. */
    static String readText(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
        }
        catch (IOException e) {
            return null;
        }
    }

    static void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        File temporary = new File(parent, "." + file.getName() + ".wehavephones-tmp");
        Files.write(temporary.toPath(), text.getBytes(StandardCharsets.ISO_8859_1));
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
