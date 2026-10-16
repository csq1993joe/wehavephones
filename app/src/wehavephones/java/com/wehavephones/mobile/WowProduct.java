package com.wehavephones.mobile;

import java.io.File;

/**
 * One installed WoW game: a Battle.net {@code World of Warcraft\_<product>_}
 * directory, or a client imported without Battle.net under
 * {@code C:\WoW Clients\<folder>} ({@link #isCustom()}).
 */
public final class WowProduct {
    public final String directoryName;
    public final File directory;
    private WowFlavor flavor = WowFlavor.UNKNOWN;
    private String productCode = "";
    private String version = "";
    private String branch = "";
    private String buildKey = "";
    private String executable = "";
    private boolean custom;
    private String customName = "";
    private String realmlist = "";
    private String launchArguments = "";

    WowProduct(String directoryName, File directory) {
        this.directoryName = directoryName;
        this.directory = directory;
    }

    /** A client imported without Battle.net: Play starts its executable directly. */
    public boolean isCustom() {
        return custom;
    }

    void setCustom(String name) {
        custom = true;
        customName = name != null ? name.trim() : "";
    }

    /** Server address from the client's realmlist.wtf or Config.wtf; empty when none is set. */
    public String getRealmlist() {
        return realmlist;
    }

    void setRealmlist(String realmlist) {
        this.realmlist = realmlist != null ? realmlist : "";
    }

    /** Extra command-line arguments for a custom client, from its marker file; usually empty. */
    public String getLaunchArguments() {
        return launchArguments;
    }

    void setLaunchArguments(String arguments) {
        launchArguments = arguments != null ? arguments.trim() : "";
    }

    public WowFlavor getFlavor() {
        return flavor;
    }

    void setFlavor(WowFlavor flavor) {
        this.flavor = flavor != null ? flavor : WowFlavor.UNKNOWN;
    }

    /** Short catalog name, e.g. "MoP Classic" or "Forever Beta"; a custom client's own name. */
    public String getDisplayName() {
        if (custom) return !customName.isEmpty() ? customName : directoryName;
        return flavor.isKnown() ? flavor.displayName : WowFlavor.prettyDirectory(directoryName);
    }

    /** Big title, e.g. "World of Warcraft: Forever"; a custom client's own name. */
    public String getTitle() {
        if (custom) return getDisplayName();
        return WowFlavor.gameTitle(flavor, directoryName);
    }

    /**
     * Expansion and channel, e.g. "Mists of Pandaria · PTR"; may be empty.
     * For a custom client: the expansion of its build and its realmlist.
     */
    public String getEdition() {
        if (custom) {
            String era = WowFlavor.eraForVersion(version);
            if (realmlist.isEmpty()) return era;
            return era.isEmpty() ? realmlist : era + " · " + realmlist;
        }
        return WowFlavor.edition(flavor, version);
    }

    /** Battle.net product code from .flavor.info (e.g. wow_classic_beta); may be empty. */
    public String getProductCode() {
        return productCode;
    }

    void setProductCode(String productCode) {
        this.productCode = productCode != null ? productCode : "";
    }

    public String getVersion() {
        return version;
    }

    void setVersion(String version) {
        this.version = version != null ? version : "";
    }

    public String getBranch() {
        return branch;
    }

    void setBranch(String branch) {
        this.branch = branch != null ? branch : "";
    }

    public String getBuildKey() {
        return buildKey;
    }

    void setBuildKey(String buildKey) {
        this.buildKey = buildKey != null ? buildKey : "";
    }

    public String getExecutable() {
        return executable;
    }

    void setExecutable(String executable) {
        this.executable = executable != null ? executable : "";
    }

    public boolean hasBuildInfo() {
        return !version.isEmpty() || !branch.isEmpty() || !buildKey.isEmpty();
    }

    public String describe() {
        StringBuilder builder = new StringBuilder(getDisplayName());
        if (!version.isEmpty()) builder.append(" ").append(version);
        return builder.toString();
    }
}
