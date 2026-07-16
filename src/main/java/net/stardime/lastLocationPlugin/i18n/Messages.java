package net.stardime.lastLocationPlugin.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Messages {

    private static final String CONFIG_FILE = "config.properties";
    private static final String DEFAULT_LOCALE = "zh_CN";
    private static final String FALLBACK_LOCALE = "en_US";

    private final Properties fallbackMessages;
    private final Properties messages;

    private Messages(Properties fallbackMessages, Properties messages) {
        this.fallbackMessages = fallbackMessages;
        this.messages = messages;
    }

    public static Messages load(Path dataDirectory, ClassLoader classLoader) throws IOException {
        Files.createDirectories(dataDirectory);
        copyResourceIfMissing(classLoader, CONFIG_FILE, dataDirectory.resolve(CONFIG_FILE));
        copyResourceIfMissing(classLoader, resourceName(DEFAULT_LOCALE), dataDirectory.resolve(resourceName(DEFAULT_LOCALE)));
        copyResourceIfMissing(classLoader, resourceName(FALLBACK_LOCALE), dataDirectory.resolve(resourceName(FALLBACK_LOCALE)));

        Properties config = loadProperties(dataDirectory.resolve(CONFIG_FILE));
        String locale = config.getProperty("locale", DEFAULT_LOCALE).trim();
        if (locale.isEmpty()) {
            locale = DEFAULT_LOCALE;
        }

        Properties fallback = loadMessagesForLocale(dataDirectory, classLoader, FALLBACK_LOCALE);
        Properties selected = loadMessagesForLocale(dataDirectory, classLoader, locale);
        return new Messages(fallback, selected);
    }

    public static Messages fallback(ClassLoader classLoader) {
        Properties fallback = loadResourceProperties(classLoader, resourceName(FALLBACK_LOCALE));
        Properties selected = loadResourceProperties(classLoader, resourceName(DEFAULT_LOCALE));
        return new Messages(fallback, selected);
    }

    public static Messages empty() {
        return new Messages(new Properties(), new Properties());
    }

    public String get(String key, Object... placeholders) {
        String value = messages.getProperty(key, fallbackMessages.getProperty(key, key));
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String placeholder = String.valueOf(placeholders[i]);
            String replacement = String.valueOf(placeholders[i + 1]);
            value = value.replace("{" + placeholder + "}", replacement);
        }
        return value;
    }

    private static Properties loadMessagesForLocale(Path dataDirectory, ClassLoader classLoader, String locale) throws IOException {
        String resourceName = resourceName(locale);
        Properties properties = loadResourceProperties(classLoader, resourceName);
        Path externalFile = dataDirectory.resolve(resourceName);
        if (Files.exists(externalFile)) {
            properties.putAll(loadProperties(externalFile));
        }
        return properties;
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Properties loadResourceProperties(ClassLoader classLoader, String resourceName) {
        Properties properties = new Properties();
        try (InputStream input = classLoader.getResourceAsStream(resourceName)) {
            if (input == null) {
                return properties;
            }
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException ignored) {
            return new Properties();
        }
        return properties;
    }

    private static void copyResourceIfMissing(ClassLoader classLoader, String resourceName, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }

        try (InputStream input = classLoader.getResourceAsStream(resourceName)) {
            if (input == null) {
                return;
            }
            Files.copy(input, target);
        }
    }

    private static String resourceName(String locale) {
        return "messages_" + locale + ".properties";
    }
}
