package com.crosstrade.util;

import com.crosstrade.CrossTrade;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.io.File;
import java.nio.charset.StandardCharsets;

import java.util.Map;

public final class ConfigManager {
    private final CrossTrade plugin;

    public ConfigManager(CrossTrade plugin) {
        this.plugin = plugin;
    }

    public void mergeDefaults() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        try {
            YamlConfiguration current = new YamlConfiguration();
            current.options().parseComments(true);
            current.load(file);
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.options().parseComments(true);
            defaults.load(new InputStreamReader(plugin.getResource("config.yml"), StandardCharsets.UTF_8));
            for (String path : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(path) || current.contains(path)) continue;
                current.set(path, defaults.get(path));
                current.setComments(path, defaults.getComments(path));
                current.setInlineComments(path, defaults.getInlineComments(path));
            }
            current.save(file);
            plugin.reloadConfig();
        } catch (Exception exception) {
            plugin.getLogger().severe("合并 config.yml 默认项失败: " + exception.getMessage());
        }
    }

    public String message(String key) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&6CrossTrade&8] &r");
        String value = plugin.getConfig().getString("messages." + key, "&c缺少消息配置: " + key);
        return Text.color(prefix + value);
    }

    public String message(String key, Map<String, String> values) {
        return Text.color(Text.apply(message(key), values));
    }

    public void send(Player player, String key) {
        player.sendMessage(message(key));
    }

    public void send(Player player, String key, Map<String, String> values) {
        player.sendMessage(message(key, values));
    }

    public String gui(String path, String fallback) {
        String legacy = plugin.getConfig().getString("gui." + path, fallback);
        return Text.color(plugin.getGuiConfigManager() == null ? legacy : plugin.getGuiConfigManager().text(path, legacy));
    }

    // 1.x 源码兼容入口。
    public String getMessage(String path) {
        return message(path);
    }

    public String getGuiMessage(String path) {
        return gui(path, path);
    }
}
