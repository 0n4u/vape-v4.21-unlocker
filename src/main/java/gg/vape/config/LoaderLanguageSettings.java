package gg.vape.config;

import gg.vape.ui.font.FontOption;
import gg.vape.ui.font.FontSelector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public final class LoaderLanguageSettings {
    private static final String DEFAULT_LANGUAGE = "English";
    private final String languageName;

    private LoaderLanguageSettings(String languageName) {
        this.languageName = languageName;
    }

    public static void apply(PublicProfileSettings profileSettings,
                             FontSelector fontSelector) {
        LoaderLanguageSettings settings = load(settingsFile());
        FontOption selected = settings.selectFontOption(
                () -> FontSelector.j,
                () -> FontSelector.c,
                () -> FontSelector.S,
                () -> FontSelector.a,
                () -> FontSelector.P);
        profileSettings.language.setValue(selected);
        fontSelector.N((FontOption)profileSettings.language.getValue());
    }

    static File settingsFile() {
        return new File(new File(System.getProperty("vape.directory"),
                ".vapeclient"), "loader.settings");
    }

    static LoaderLanguageSettings load(File file) {
        String languageName = null;
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
                    if ("language".equals(key)) {
                        languageName = line.substring(separator + 1).trim();
                    }
                }
            }
            catch (IOException ignored) {
                
            }
        }
        return new LoaderLanguageSettings(normalizeLanguageName(languageName));
    }

    private static String normalizeLanguageName(String languageName) {
        if ("English".equals(languageName)
                || "Chinese".equals(languageName)
                || "Spanish".equals(languageName)
                || "Portuguese".equals(languageName)
                || "French".equals(languageName)) {
            return languageName;
        }
        return DEFAULT_LANGUAGE;
    }

    <T> T selectFontOption(Supplier<T> english, Supplier<T> chinese,
                           Supplier<T> spanish, Supplier<T> portuguese,
                           Supplier<T> french) {
        if ("Chinese".equals(this.languageName)) {
            return chinese.get();
        }
        if ("Spanish".equals(this.languageName)) {
            return spanish.get();
        }
        if ("Portuguese".equals(this.languageName)) {
            return portuguese.get();
        }
        if ("French".equals(this.languageName)) {
            return french.get();
        }
        return english.get();
    }

    String languageName() {
        return this.languageName;
    }
}
