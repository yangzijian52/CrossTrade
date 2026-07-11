package com.crosstrade.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.util.Locale;

public final class SoundResolver {
    private SoundResolver() {}

    public static Sound resolve(String configured) {
        if (configured == null || configured.isBlank()) return null;
        String value = configured.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = value.contains(":") ? NamespacedKey.fromString(value) : NamespacedKey.minecraft(value);
        return key == null ? null : Registry.SOUNDS.get(key);
    }
}
