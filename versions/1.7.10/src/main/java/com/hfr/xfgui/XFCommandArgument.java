package com.hfr.xfgui;

public class XFCommandArgument {
    public enum Type { FREE_TEXT, INTEGER, DECIMAL, HEX_COLOR, URL, ONLINE_PLAYER, FACTION, FACTION_TOKEN, FACTION_MEMBER, APPLICANT, ALLY, ENEMY, WARP, CITY, FLAG, ENUM, BOOLEAN, OPTIONAL_TEXT }
    public final String nameKey; public final Type type; public final boolean required; public final boolean preserveSpaces; public final String[] options;
    public XFCommandArgument(String nameKey, Type type, boolean required) { this(nameKey, type, required, false, new String[0]); }
    public XFCommandArgument(String nameKey, Type type, boolean required, boolean preserveSpaces, String... options) { this.nameKey=nameKey; this.type=type; this.required=required; this.preserveSpaces=preserveSpaces; this.options=options==null?new String[0]:options; }
    /** Faction names before another argument must occupy one legacy command token. */
    public String formatValue(String value) {
        if (value == null) return "";
        String result = preserveSpaces ? value : value.trim();
        return type == Type.FACTION_TOKEN ? result.replaceAll("\\s+", "_") : result;
    }
}
