package com.aozainkmc.input.client;

import com.aozainkmc.input.AozaiInkInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

public final class AozaiInkClientConfig {
    private static final String FILE = "aozaink_input-client.properties";
    private static final String KEY_HINTS = "contextualHintsEnabled";
    private static Boolean hintsEnabled;

    private AozaiInkClientConfig() {}

    public static boolean hintsEnabled() {
        if (hintsEnabled == null) hintsEnabled = load();
        return hintsEnabled;
    }

    public static void setHintsEnabled(boolean value) {
        hintsEnabled = value;
        save(value);
    }

    public static void toggleHintsEnabled() {
        setHintsEnabled(!hintsEnabled());
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }

    private static boolean load() {
        Path path = path();
        if (!Files.exists(path)) return true;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            AozaiInkInput.LOGGER.warn("read input client config failed: {}", e.getMessage());
            return true;
        }
        return Boolean.parseBoolean(props.getProperty(KEY_HINTS, "true"));
    }

    private static void save(boolean value) {
        Properties props = new Properties();
        props.setProperty(KEY_HINTS, Boolean.toString(value));
        try (OutputStream out = Files.newOutputStream(path())) {
            props.store(out, "aozaink input client settings");
        } catch (IOException e) {
            AozaiInkInput.LOGGER.warn("write input client config failed: {}", e.getMessage());
        }
    }
}
