package com.crosstrade.util;

import org.bukkit.ChatColor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

public final class Text {
    private Text() {}

    public static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    public static String strip(String value) {
        return ChatColor.stripColor(color(value));
    }

    public static String apply(String value, Map<String, String> placeholders) {
        String result = value == null ? "" : value;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static String money(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public static String duration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0) return String.format(Locale.ROOT, "%d天%d小时", days, hours);
        if (hours > 0) return String.format(Locale.ROOT, "%d小时%d分钟", hours, minutes);
        return Math.max(1, minutes) + "分钟";
    }
}
