package com.crosstrade.market.gui;

import com.crosstrade.CrossTrade;
import com.crosstrade.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public final class GuiItemFactory {
    private final CrossTrade plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey longKey;
    private final NamespacedKey intKey;
    private final NamespacedKey stringKey;

    public GuiItemFactory(CrossTrade plugin) {
        this.plugin = plugin;
        actionKey = new NamespacedKey(plugin, "market_action");
        longKey = new NamespacedKey(plugin, "market_long");
        intKey = new NamespacedKey(plugin, "market_int");
        stringKey = new NamespacedKey(plugin, "market_string");
    }

    public ItemStack item(Material material, String name, String action, String... lore) {
        ItemStack stack = new ItemStack(material);
        return decorate(stack, name, action, lore);
    }

    public ItemStack action(ItemStack source, String name, String action, String... lore) {
        return decorate(source.clone(), name, action, lore);
    }

    public ItemStack setAction(ItemStack stack, String action) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public ItemStack head(OfflinePlayer player, String name, String action, String... lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        if (stack.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            stack.setItemMeta(meta);
        }
        return decorate(stack, name, action, lore);
    }

    private ItemStack decorate(ItemStack stack, String name, String action, String... lore) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(Text.color(name));
        if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(Text::color).toList());
        meta.addItemFlags(ItemFlag.values());
        if (action != null) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack longValue(ItemStack stack, long value) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(longKey, PersistentDataType.LONG, value);
        stack.setItemMeta(meta); return stack;
    }
    public ItemStack intValue(ItemStack stack, int value) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(intKey, PersistentDataType.INTEGER, value);
        stack.setItemMeta(meta); return stack;
    }
    public ItemStack stringValue(ItemStack stack, String value) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(stringKey, PersistentDataType.STRING, value);
        stack.setItemMeta(meta); return stack;
    }
    public ItemStack configButton(ItemStack stack, String value) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "gui_config_button"), PersistentDataType.STRING, value);
        stack.setItemMeta(meta); return stack;
    }
    public String action(ItemStack stack) { return string(stack, actionKey); }
    public Long longValue(ItemStack stack) { return stack == null || !stack.hasItemMeta() ? null : stack.getItemMeta().getPersistentDataContainer().get(longKey, PersistentDataType.LONG); }
    public Integer intValue(ItemStack stack) { return stack == null || !stack.hasItemMeta() ? null : stack.getItemMeta().getPersistentDataContainer().get(intKey, PersistentDataType.INTEGER); }
    public String stringValue(ItemStack stack) { return string(stack, stringKey); }
    public String configButton(ItemStack stack) { return string(stack, new NamespacedKey(plugin, "gui_config_button")); }
    private String string(ItemStack stack, NamespacedKey key) {
        return stack == null || !stack.hasItemMeta() ? null : stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
