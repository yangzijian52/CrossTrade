package com.crosstrade.util;

import com.crosstrade.CrossTrade;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

public final class ItemNameService {
    private final CrossTrade plugin;
    private final Properties chineseNames = new Properties();

    public ItemNameService(CrossTrade plugin) {
        this.plugin = plugin;
        try (InputStream stream = plugin.getResource("item-names-zh_cn.properties")) {
            if (stream == null) throw new IOException("缺少 item-names-zh_cn.properties");
            chineseNames.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            plugin.getLogger().info("已加载 " + chineseNames.size() + " 个 Paper 26.2 中文物品名称。");
        } catch (IOException exception) {
            plugin.getLogger().warning("中文物品名称加载失败，将使用可读英文名称: " + exception.getMessage());
        }
    }

    public String displayName(ItemStack item) {
        if (item == null || item.getType().isAir()) return "空气";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String custom = ChatColor.stripColor(meta.getDisplayName());
            if (custom != null && !custom.isBlank()) return custom;
        }
        return materialName(item.getType());
    }

    public String materialName(Material material) {
        String translated = chineseNames.getProperty(material.name());
        if (translated != null && !translated.isBlank()) return translated;
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder fallback = new StringBuilder();
        for (String part : parts) {
            if (!fallback.isEmpty()) fallback.append(' ');
            fallback.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return fallback.toString();
    }
}
