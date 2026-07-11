package com.crosstrade.market.gui;

import com.crosstrade.CrossTrade;
import com.crosstrade.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GuiConfigManager {
    private final CrossTrade plugin;
    private File file;
    private YamlConfiguration config;

    public GuiConfigManager(CrossTrade plugin) { this.plugin = plugin; }

    public void load() {
        plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "gui.yml");
        if (!file.isFile()) plugin.saveResource("gui.yml", false);
        config = new YamlConfiguration();
        config.options().parseComments(true);
        try { config.load(file); }
        catch (Exception exception) {
            plugin.getLogger().warning("读取 gui.yml 失败，已保留原文件且跳过自动写入: " + exception.getMessage());
            return;
        }
        if (plugin.getResource("gui.yml") != null) {
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.options().parseComments(true);
            try {
                defaults.load(new InputStreamReader(plugin.getResource("gui.yml"), StandardCharsets.UTF_8));
                for (String path : defaults.getKeys(true)) {
                    if (defaults.isConfigurationSection(path) || config.contains(path)) continue;
                    config.set(path, defaults.get(path));
                    config.setComments(path, defaults.getComments(path));
                    config.setInlineComments(path, defaults.getInlineComments(path));
                }
                config.save(file);
            } catch (Exception exception) { plugin.getLogger().warning("合并 gui.yml 默认项失败: " + exception.getMessage()); }
        }
    }

    public void reload() { load(); }

    public String text(String legacyPath, String fallback) {
        if (config == null) return fallback;
        String normalized = legacyPath.startsWith("pages.") ? legacyPath : "pages." + legacyPath;
        return config.getString(normalized, fallback);
    }

    public String title(String page, String fallback, Map<String, String> values) {
        return Text.color(apply(config == null ? fallback : config.getString("pages." + page + ".title", fallback), null, values));
    }

    public void apply(String page, Inventory inventory, Player player, GuiItemFactory factory,
                      Map<String, String> placeholders) {
        if (config == null) return;
        ConfigurationSection buttons = config.getConfigurationSection("pages." + page + ".buttons");
        if (buttons == null) return;
        for (String id : buttons.getKeys(false)) {
            ConfigurationSection button = buttons.getConfigurationSection(id);
            if (button == null) continue;
            String action = button.getString("action");
            ItemStack previous = null;
            if (action != null && !action.isBlank()) {
                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    ItemStack candidate = inventory.getItem(slot);
                    if (action.equals(factory.action(candidate))) {
                        if (previous == null) previous = candidate.clone();
                        inventory.setItem(slot, null);
                    }
                }
            }
            if (!button.getBoolean("enabled", true)) continue;
            if (action != null && !action.isBlank() && previous == null && button.getBoolean("require-existing", true)) continue;
            String permission = button.getString("permission", "");
            if (!permission.isBlank() && !player.hasPermission(permission)) continue;
            int slot = button.getInt("slot", -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                plugin.getLogger().warning("gui.yml 无效槽位: pages." + page + ".buttons." + id + ".slot=" + slot);
                continue;
            }
            ItemStack icon = previous;
            Long previousLong = factory.longValue(previous);
            Integer previousInt = factory.intValue(previous);
            String previousString = factory.stringValue(previous);
            String previousName = previous != null && previous.hasItemMeta() && previous.getItemMeta().hasDisplayName()
                    ? previous.getItemMeta().getDisplayName() : null;
            List<String> previousLore = previous != null && previous.hasItemMeta() && previous.getItemMeta().hasLore()
                    ? new ArrayList<>(previous.getItemMeta().getLore()) : null;
            String materialName = button.getString("material");
            if (materialName != null && !materialName.isBlank()) {
                Material material = Material.matchMaterial(materialName);
                if (material == null) {
                    plugin.getLogger().warning("gui.yml 无效材质: " + materialName + "，按钮 " + page + "/" + id + " 使用石头。");
                    material = Material.STONE;
                }
                icon = new ItemStack(material);
            }
            if (icon == null) icon = new ItemStack(Material.STONE);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                if (button.contains("name")) meta.setDisplayName(Text.color(apply(button.getString("name", "&f按钮"), player, placeholders)));
                else if (previousName != null) meta.setDisplayName(previousName);
                if (button.contains("lore")) {
                    List<String> lore = new ArrayList<>();
                    for (String line : button.getStringList("lore")) lore.add(Text.color(apply(line, player, placeholders)));
                    meta.setLore(lore);
                } else if (previousLore != null) meta.setLore(previousLore);
                icon.setItemMeta(meta);
            }
            if (action != null && !action.isBlank()) icon = factory.setAction(icon, action);
            if (previousLong != null) icon = factory.longValue(icon, previousLong);
            if (previousInt != null) icon = factory.intValue(icon, previousInt);
            if (previousString != null) icon = factory.stringValue(icon, previousString);
            icon = factory.configButton(icon, page + "/" + id);
            inventory.setItem(slot, icon);
        }
    }

    public void fill(String page, Inventory inventory, GuiItemFactory factory) {
        ItemStack filler = filler(page, factory);
        for (int slot = 0; slot < inventory.getSize(); slot++) if (inventory.getItem(slot) == null) inventory.setItem(slot, filler);
    }

    public ItemStack filler(String page, GuiItemFactory factory) {
        String base = "pages." + page + ".filler.";
        String materialName = config == null ? "BLACK_STAINED_GLASS_PANE" : config.getString(base + "material", "BLACK_STAINED_GLASS_PANE");
        Material material = Material.matchMaterial(materialName == null ? "BLACK_STAINED_GLASS_PANE" : materialName);
        if (material == null) material = Material.BLACK_STAINED_GLASS_PANE;
        String name = config == null ? "&8" : config.getString(base + "name", "&8");
        return factory.item(material, name, null);
    }

    public boolean runCommands(Player player, String reference, MarketHolder holder) {
        return runCommands(player, reference, holderValues(holder));
    }

    public boolean runCommands(Player player, String reference, Map<String, String> values) {
        if (config == null || reference == null || !reference.contains("/")) return true;
        String[] parts = reference.split("/", 2);
        String path = "pages." + parts[0] + ".buttons." + parts[1];
        for (String raw : config.getStringList(path + ".commands")) {
            String command = apply(raw, player, values).trim();
            String lower = command.toLowerCase(Locale.ROOT);
            if (lower.startsWith("[command] ") || lower.startsWith("[player] ")) {
                player.performCommand(command.substring(command.indexOf(']') + 1).trim().replaceFirst("^/", ""));
            } else if (lower.startsWith("[console] ")) {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command.substring(10).trim().replaceFirst("^/", ""));
            } else if (lower.startsWith("[message] ")) {
                player.sendMessage(Text.color(command.substring(10).trim()));
            } else if (lower.equals("[close]")) player.closeInventory();
        }
        return config.getBoolean(path + ".run-internal-action", true);
    }

    public static String pageId(MarketHolder.Type type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static Map<String, String> holderValues(MarketHolder holder) {
        Map<String, String> values = new HashMap<>();
        values.put("{page}", String.valueOf(holder.page() + 1));
        values.put("{listing_id}", String.valueOf(holder.listingId()));
        values.put("{seller_uuid}", holder.subject() == null ? "" : holder.subject().toString());
        values.put("{search}", holder.search());
        return values;
    }

    private static String apply(String input, Player player, Map<String, String> values) {
        String result = input == null ? "" : input;
        if (player != null) result = result.replace("{player}", player.getName()).replace("%player%", player.getName());
        if (values != null) for (Map.Entry<String, String> entry : values.entrySet()) result = result.replace(entry.getKey(), entry.getValue());
        return result;
    }
}
