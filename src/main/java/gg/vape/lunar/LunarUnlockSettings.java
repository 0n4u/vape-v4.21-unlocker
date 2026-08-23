package gg.vape.lunar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;


public final class LunarUnlockSettings {
    private static volatile LunarUnlockSettings current;

    private final boolean cosmetics;
    private final boolean badges;
    private final boolean emotes;
    private final boolean sprays;
    private final boolean jams;
    private final boolean lunarPlusAppearance;
    private final boolean debugLogging;

    private LunarUnlockSettings(boolean cosmetics, boolean badges,
                                boolean emotes, boolean sprays, boolean jams,
                                boolean lunarPlusAppearance,
                                boolean debugLogging) {
        this.cosmetics = cosmetics;
        this.badges = badges;
        this.emotes = emotes;
        this.sprays = sprays;
        this.jams = jams;
        this.lunarPlusAppearance = lunarPlusAppearance;
        this.debugLogging = debugLogging;
    }

    public static LunarUnlockSettings current() {
        LunarUnlockSettings snapshot = current;
        if (snapshot == null) {
            synchronized (LunarUnlockSettings.class) {
                snapshot = current;
                if (snapshot == null) {
                    snapshot = load(settingsFile());
                    current = snapshot;
                }
            }
        }
        return snapshot;
    }

    static void resetForTests() {
        synchronized (LunarUnlockSettings.class) {
            current = null;
        }
    }

    private static File settingsFile() {
        String directory = System.getProperty("vape.directory");
        File base = new File(directory == null || directory.trim().isEmpty()
                ? "." : directory);
        return new File(new File(base, ".vapeclient"), "loader.settings");
    }

    public static LunarUnlockSettings load(File file) {
        boolean cosmetics = true;
        boolean badges = true;
        boolean emotes = true;
        boolean sprays = true;
        boolean jams = true;
        boolean lunarPlusAppearance = true;
        boolean debugLogging = false;
        if (file != null && file.isFile()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int separator = line.indexOf('=');
                    if (separator <= 0) {
                        continue;
                    }
                    String key = line.substring(0, separator).trim();
                    String value = line.substring(separator + 1).trim();
                    Boolean parsed = parseBoolean(value);
                    if (parsed == null) {
                        continue;
                    }
                    boolean enabled = parsed.booleanValue();
                    if ("unlock_cosmetics".equals(key)) {
                        cosmetics = enabled;
                    } else if ("unlock_badges".equals(key)) {
                        badges = enabled;
                    } else if ("unlock_emotes".equals(key)) {
                        emotes = enabled;
                    } else if ("unlock_sprays".equals(key)) {
                        sprays = enabled;
                    } else if ("unlock_jams".equals(key)) {
                        jams = enabled;
                    } else if ("lunar_plus_appearance".equals(key)) {
                        lunarPlusAppearance = enabled;
                    } else if ("debug_logging".equals(key)) {
                        debugLogging = enabled;
                    }
                }
            }
            catch (IOException ignored) {
                
            }
        }
        return new LunarUnlockSettings(cosmetics, badges, emotes, sprays, jams,
                lunarPlusAppearance, debugLogging);
    }

    private static Boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    public boolean cosmeticsEnabled() {
        return cosmetics;
    }

    public boolean badgesEnabled() {
        return badges;
    }

    public boolean emotesEnabled() {
        return emotes;
    }

    public boolean spraysEnabled() {
        return sprays;
    }

    public boolean jamsEnabled() {
        return jams;
    }

    public boolean lunarPlusAppearanceEnabled() {
        return lunarPlusAppearance;
    }

    public boolean debugLoggingEnabled() {
        return debugLogging;
    }
}
