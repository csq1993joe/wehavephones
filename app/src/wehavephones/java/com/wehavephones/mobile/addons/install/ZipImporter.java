package com.wehavephones.mobile.addons.install;

import com.wehavephones.mobile.addons.model.TocData;
import com.wehavephones.mobile.WowFlavor;
import com.wehavephones.mobile.addons.scan.TocParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Discovers installable addon folders inside an extracted archive/staging
 * directory. Pure Java (host-testable).
 */
public final class ZipImporter {
    private static final int MAX_DEPTH = 5;
    private static final int MAX_SCAN_DIRECTORIES = 5000;
    /** GitHub archives name their top folder repository-branch or repository-tag. */
    private static final Pattern ARCHIVE_SUFFIX = Pattern.compile("main|master|v?\\d[\\w.\\-]*",
            Pattern.CASE_INSENSITIVE);

    private ZipImporter() {}

    public static List<DiscoveredAddon> discoverAddons(File staging, WowFlavor flavor) {
        ArrayList<DiscoveredAddon> discovered = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        walk(staging, staging, flavor, 0, discovered, seen, new int[]{0});
        return discovered;
    }

    private static void walk(File staging, File current, WowFlavor flavor, int depth,
                             List<DiscoveredAddon> discovered, HashSet<String> seen, int[] scanned) {
        if (depth > MAX_DEPTH || scanned[0] > MAX_SCAN_DIRECTORIES) return;
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (!child.isDirectory()) continue;
            if (scanned[0]++ > MAX_SCAN_DIRECTORIES) return;
            if (!isAddonDirectoryName(child.getName())) {
                if (isWrapperName(child.getName())) {
                    walk(staging, child, flavor, depth + 1, discovered, seen, scanned);
                }
                continue;
            }
            String name = TocParser.hasTocFor(child) ? child.getName() : archiveAddonName(child);
            if (name != null) {
                File tocFile = TocParser.chooseBestToc(child, name, flavor);
                TocData toc = null;
                if (tocFile != null) {
                    try {
                        toc = TocParser.parse(tocFile, name);
                    }
                    catch (IOException ignored) {
                        toc = null;
                    }
                }
                String key = name.toLowerCase(Locale.US);
                if (seen.add(key)) {
                    discovered.add(new DiscoveredAddon(child, name, toc,
                            TocParser.versionFromName(child.getName())));
                }
                continue;
            }
            walk(staging, child, flavor, depth + 1, discovered, seen, scanned);
        }
    }

    /**
     * The add-on in a GitHub source archive folder, "Name-branch" or
     * "Name-tag" (ShaguController-master, ConsoleExperienceClassic-0.17.3),
     * whose TOC carries the name without the suffix: WoW only loads it from a
     * folder named like its TOC. Null for any other folder.
     */
    public static String archiveAddonName(File folder) {
        String folderName = folder.getName();
        File[] children = folder.listFiles();
        if (children == null) return null;
        // Every dash that starts a branch or tag suffix: Questie-335-master may hold Questie.toc or Questie-335.toc.
        for (int dash = folderName.indexOf('-'); dash > 0; dash = folderName.indexOf('-', dash + 1)) {
            if (!ARCHIVE_SUFFIX.matcher(folderName.substring(dash + 1)).matches()) continue;
            String name = folderName.substring(0, dash);
            if (!isAddonDirectoryName(name)) continue;
            String tocName = tocNamed(children, name);
            if (tocName != null) return tocName;
        }
        return null;
    }

    /** {@code name} as the TOC files spell it, when one of them belongs to an add-on of that name; else null. */
    private static String tocNamed(File[] children, String name) {
        String found = null;
        for (File child : children) {
            String fileName = child.getName();
            if (!child.isFile() || !fileName.toLowerCase(Locale.US).endsWith(".toc")) continue;
            String tocBase = fileName.substring(0, fileName.length() - 4);
            // The TOC's own spelling, as the archive folder may differ in case.
            if (tocBase.equalsIgnoreCase(name)) return tocBase;
            if (TocParser.baseMatchesFolder(tocBase, name)) found = name;
        }
        return found;
    }

    private static boolean isWrapperName(String name) {
        return name != null && (name.equalsIgnoreCase("Interface") || name.equalsIgnoreCase("AddOns"));
    }

    /** Rejects folder names that are unsafe on Windows/Wine/Android filesystems. */
    public static boolean isAddonDirectoryName(String name) {
        if (name == null || name.isEmpty() || name.length() > 128) return false;
        if (name.equals(".") || name.equals("..")) return false;
        if (name.equalsIgnoreCase("Interface") || name.equalsIgnoreCase("AddOns")) return false;
        if (name.startsWith(".")) return false;
        if (name.endsWith(".") || name.endsWith(" ")) return false;
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (character == '/' || character == '\\' || character == '<' || character == '>'
                    || character == ':' || character == '"' || character == '|'
                    || character == '?' || character == '*' || character < 0x20) {
                return false;
            }
        }
        return true;
    }

    public static List<String> folderNames(List<DiscoveredAddon> addons) {
        ArrayList<String> names = new ArrayList<>();
        for (DiscoveredAddon addon : addons) {
            if (!names.contains(addon.folderName)) names.add(addon.folderName);
        }
        return names;
    }
}
