package com.hfr.config;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** Server-owned settings; commands publish only after an atomic config replacement succeeds. */
public final class PlayerPresenceConfig {
    public static final String CATEGORY = "XENOFACTIONS_26_PLAYER_PRESENCE";
    public static final String[] SETTINGS = { "enableAfkDetection", "enableAfkAnnouncements", "afkTimeoutSeconds", "enableAdminPrefix" };
    public static boolean enableAfkDetection = true, enableAfkAnnouncements = true, enableAdminPrefix = true;
    public static int afkTimeoutSeconds = 300;
    private static Configuration configuration;

    private PlayerPresenceConfig() { }

    public static void load(Configuration config) {
        configuration = config;
        config.addCustomCategoryComment(CATEGORY, "Server AFK announcements and cosmetic operator chat prefix. /xc presence show|set applies and saves changes immediately.");
        enableAfkDetection = option(config, SETTINGS[0], "Track player input on the server to detect AFK state. Passive movement and keepalives do not count. Disabling clears tracking silently.").getBoolean(true);
        enableAfkAnnouncements = option(config, SETTINGS[1], "Broadcast AFK and return messages once per state transition. Detection continues silently when false.").getBoolean(true);
        Property timeout = config.get(CATEGORY, SETTINGS[2], 300);
        timeout.comment = "Idle seconds before AFK (default five minutes; range 1..86400). Independent of vanilla idle kicking; does not kick players.";
        afkTimeoutSeconds = Math.max(1, Math.min(86400, timeout.getInt(300)));
        enableAdminPrefix = option(config, SETTINGS[3], "Add a red [Admin] before operators' chat names using the server operator check. Cosmetic only; grants no permissions.").getBoolean(true);
    }

    private static Property option(Configuration config, String key, String comment) {
        Property property = config.get(CATEGORY, key, true);
        property.comment = comment;
        return property;
    }

    public static String value(String name) {
        if (SETTINGS[0].equals(name)) return Boolean.toString(enableAfkDetection);
        if (SETTINGS[1].equals(name)) return Boolean.toString(enableAfkAnnouncements);
        if (SETTINGS[2].equals(name)) return Integer.toString(afkTimeoutSeconds);
        if (SETTINGS[3].equals(name)) return Boolean.toString(enableAdminPrefix);
        throw new IllegalArgumentException(name);
    }

    public static void update(String name, String value) throws java.io.IOException {
        if (configuration == null) throw new java.io.IOException("Configuration is unavailable");
        value(name); // Validate the key before touching saved or live state.
        if (SETTINGS[2].equals(name)) {
            int timeout = Integer.parseInt(value);
            if (timeout < 1 || timeout > 86400) throw new IllegalArgumentException(value);
            value = Integer.toString(timeout);
        } else {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) throw new IllegalArgumentException(value);
            value = Boolean.toString(Boolean.parseBoolean(value));
        }
        Property property = configuration.getCategory(CATEGORY).get(name);
        String previous = property.getString();
        Path temporary = null;
        boolean saved = false;
        try {
            property.set(value);
            Path target = configuration.getConfigFile().toPath().toAbsolutePath();
            temporary = Files.createTempFile(target.getParent(), "hfr-presence-", ".cfg");
            // Forge's save() swallows IO failures. Serialize its existing categories ourselves.
            try (FileOutputStream stream = new FileOutputStream(temporary.toFile());
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
                writer.write("# Configuration file\n\n");
                for (String category : configuration.getCategoryNames()) {
                    if (!configuration.getCategory(category).isChild()) configuration.getCategory(category).write(writer, 0);
                }
                writer.flush();
                stream.getFD().sync();
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            saved = true;
        } finally {
            if (!saved) property.set(previous);
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (java.io.IOException ignored) { }
        }
        load(configuration);
        if (!enableAfkDetection) com.hfr.main.PlayerActivityTracker.clear();
    }
}
