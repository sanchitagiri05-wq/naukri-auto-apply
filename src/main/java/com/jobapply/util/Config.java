package com.jobapply.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Loads config/config.properties from the project root.
 * Keep all user-specific values (search terms, screening answers, pacing) here
 * rather than hardcoded in the automation logic.
 */
public class Config {

    private static final Properties props = new Properties();
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;
        Path configPath = Path.of("config", "config.properties");
        try (InputStream in = Files.newInputStream(configPath)) {
            props.load(in);
            loaded = true;
        } catch (IOException e) {
            throw new RuntimeException(
                "Could not read config/config.properties. Make sure you're running " +
                "from the project root and the file exists.", e);
        }
    }

    public static String get(String key) {
        load();
        String value = props.getProperty(key);
        if (value == null) {
            throw new RuntimeException("Missing config key: " + key);
        }
        return value.trim();
    }

    public static String get(String key, String fallback) {
        load();
        return props.getProperty(key, fallback).trim();
    }

    public static List<String> getList(String key) {
        return Arrays.stream(get(key).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public static double getDouble(String key) {
        return Double.parseDouble(get(key));
    }

    public static boolean getBool(String key) {
        return Boolean.parseBoolean(get(key));
    }
}
