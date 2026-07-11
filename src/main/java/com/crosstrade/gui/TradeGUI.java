package com.crosstrade.gui;

import com.crosstrade.CrossTrade;
import com.crosstrade.model.TradeSession;
import com.crosstrade.market.gui.GuiItemFactory;
import com.crosstrade.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TradeGUI {
    public static final int[] OWNER_SLOTS = {0,1,2,3,9,10,11,12,18,19,20,21,27,28,29,30};
    public static final int[] OTHER_SLOTS = {5,6,7,8,14,15,16,17,23,24,25,26,32,33,34,35};
    public static final int OWNER_HEAD = 36;
    public static final int CONFIRM = 38;
    public static final int CANCEL = 40;
    public static final int OTHER_CONFIRM = 42;
    public static final int OTHER_HEAD = 44;
    public static final int ADD_1 = 45, ADD_10 = 46, ADD_100 = 47, ADD_1000 = 48;
    public static final int CLEAR = 49, SUB_1 = 50, SUB_10 = 51, SUB_100 = 52, SUB_1000 = 53;

    private final CrossTrade plugin;
    private final TradeSession session;
    private final Player owner;
    private final Player other;
    private final TradeHolder holder;
    private final Inventory inventory;
    private final GuiItemFactory configuredItems;
    private BigDecimal money = BigDecimal.ZERO;

    public TradeGUI(CrossTrade plugin, TradeSession session, Player owner, Player other) {
        this.plugin = plugin;
        this.session = session;
        this.owner = owner;
        this.other = other;
        this.configuredItems = new GuiItemFactory(plugin);
        holder = new TradeHolder(session, owner.getUniqueId());
        String title = plugin.getConfigManager().gui("direct.title", "&0面对面交易 &8| &7{player}")
                .replace("{player}", other.getName());
        inventory = Bukkit.createInventory(holder, 54, title);
        holder.inventory(inventory);
    }

    public void initialize() {
        ItemStack filler = plugin.getGuiConfigManager().filler("direct", configuredItems);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        for (int slot : OWNER_SLOTS) inventory.setItem(slot, null);
        for (int slot : OTHER_SLOTS) inventory.setItem(slot, null);
        ItemStack divider = item(Material.GRAY_STAINED_GLASS_PANE, "&8");
        for (int slot : new int[]{4,13,22,31}) inventory.setItem(slot, divider);
        update();
    }

    public void update() {
        String balance = plugin.getConfig().getBoolean("direct-trade.show-balance", true)
                ? symbol() + format(plugin.getEconomyGateway().balance(owner)) : "已隐藏";
        inventory.setItem(OWNER_HEAD, configuredItems.setAction(head(owner, "&a你的交易内容",
                "&7余额: &f" + balance, "&7支付: &6" + symbol() + format(money)), "direct_owner_info"));
        inventory.setItem(OTHER_HEAD, configuredItems.setAction(head(other, "&e" + other.getName() + " 的交易内容",
                "&7对方支付: &6" + symbol() + format(session.money(other))), "direct_other_info"));
        inventory.setItem(CONFIRM, configuredItems.setAction(item(session.isReady(owner) ? Material.LIME_CONCRETE : Material.WHITE_CONCRETE,
                session.isReady(owner) ? "&a&l已确认" : "&f&l点击确认",
                session.isReady(owner) ? "&7点击撤销确认" : "&7双方确认后开始安全倒计时"), "direct_confirm"));
        inventory.setItem(OTHER_CONFIRM, configuredItems.setAction(item(session.isReady(other) ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                session.isReady(other) ? "&a对方已确认" : "&c等待对方确认"), "direct_other_confirm"));
        inventory.setItem(CANCEL, configuredItems.setAction(item(Material.BARRIER, "&c&l取消交易", "&7所有物品将安全返还"), "direct_cancel"));
        if (plugin.getEconomyGateway().available()) {
            inventory.setItem(ADD_1, configuredItems.setAction(item(Material.LIME_STAINED_GLASS_PANE, "&a+1"), "direct_add_1"));
            inventory.setItem(ADD_10, configuredItems.setAction(item(Material.GREEN_STAINED_GLASS_PANE, "&2+10"), "direct_add_10"));
            inventory.setItem(ADD_100, configuredItems.setAction(item(Material.YELLOW_STAINED_GLASS_PANE, "&e+100"), "direct_add_100"));
            inventory.setItem(ADD_1000, configuredItems.setAction(item(Material.CYAN_STAINED_GLASS_PANE, "&b+1000"), "direct_add_1000"));
            inventory.setItem(CLEAR, configuredItems.setAction(item(Material.BARRIER, "&c金额清零", "&7当前: &f" + symbol() + format(money)), "direct_clear"));
            inventory.setItem(SUB_1, configuredItems.setAction(item(Material.YELLOW_STAINED_GLASS_PANE, "&e-1"), "direct_sub_1"));
            inventory.setItem(SUB_10, configuredItems.setAction(item(Material.ORANGE_STAINED_GLASS_PANE, "&6-10"), "direct_sub_10"));
            inventory.setItem(SUB_100, configuredItems.setAction(item(Material.RED_STAINED_GLASS_PANE, "&c-100"), "direct_sub_100"));
            inventory.setItem(SUB_1000, configuredItems.setAction(item(Material.MAGENTA_STAINED_GLASS_PANE, "&5-1000"), "direct_sub_1000"));
        }
        plugin.getGuiConfigManager().apply("direct", inventory, owner, configuredItems,
                java.util.Map.of("{other_player}", other.getName(), "{money}", format(money)));
    }

    public void updateOtherItems(List<ItemStack> items) {
        for (int slot : OTHER_SLOTS) inventory.setItem(slot, null);
        for (int i = 0; i < Math.min(items.size(), OTHER_SLOTS.length); i++) inventory.setItem(OTHER_SLOTS[i], items.get(i).clone());
    }

    public List<ItemStack> offeredItems() {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : OWNER_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) items.add(item.clone());
        }
        return items;
    }

    public void clearOffered() { for (int slot : OWNER_SLOTS) inventory.setItem(slot, null); }

    public void adjustMoney(int slot) {
        BigDecimal delta = switch (slot) {
            case ADD_1 -> BigDecimal.ONE;
            case ADD_10 -> BigDecimal.TEN;
            case ADD_100 -> BigDecimal.valueOf(100);
            case ADD_1000 -> BigDecimal.valueOf(1000);
            case SUB_1 -> BigDecimal.ONE.negate();
            case SUB_10 -> BigDecimal.valueOf(-10);
            case SUB_100 -> BigDecimal.valueOf(-100);
            case SUB_1000 -> BigDecimal.valueOf(-1000);
            case CLEAR -> money.negate();
            default -> BigDecimal.ZERO;
        };
        BigDecimal candidate = money.add(delta).max(BigDecimal.ZERO);
        BigDecimal maximum = new BigDecimal(plugin.getConfig().getString("direct-trade.max-money", "1000000000"));
        if (candidate.compareTo(maximum) > 0 || candidate.compareTo(plugin.getEconomyGateway().balance(owner)) > 0) {
            plugin.getConfigManager().send(owner, "insufficient-balance");
            return;
        }
        if (candidate.compareTo(money) != 0) {
            money = candidate;
            session.offerChanged(owner);
        }
    }

    public void adjustMoney(String action) {
        int slot = switch (action) {
            case "direct_add_1" -> ADD_1; case "direct_add_10" -> ADD_10;
            case "direct_add_100" -> ADD_100; case "direct_add_1000" -> ADD_1000;
            case "direct_clear" -> CLEAR; case "direct_sub_1" -> SUB_1;
            case "direct_sub_10" -> SUB_10; case "direct_sub_100" -> SUB_100;
            case "direct_sub_1000" -> SUB_1000; default -> -1;
        };
        if (slot >= 0) adjustMoney(slot);
    }

    public BigDecimal money() { return money; }
    public Inventory getInventory() { return inventory; }
    public void open() { owner.openInventory(inventory); }
    public boolean isOwnerSlot(int slot) { return Arrays.stream(OWNER_SLOTS).anyMatch(value -> value == slot); }
    public boolean isMoneySlot(int slot) { return slot >= ADD_1 && slot <= SUB_1000; }
    public String control(ItemStack stack) { return configuredItems.action(stack); }
    public String configuredButton(ItemStack stack) { return configuredItems.configButton(stack); }

    private ItemStack head(Player player, String name, String... lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        if (stack.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            decorate(meta, name, lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            decorate(meta, name, lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void decorate(ItemMeta meta, String name, String... lore) {
        meta.setDisplayName(Text.color(name));
        if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(Text::color).toList());
        meta.addItemFlags(ItemFlag.values());
    }

    private String symbol() { return plugin.getConfig().getString("economy.currency-symbol", "$"); }
    private String format(BigDecimal value) { return Text.money(value, plugin.getConfig().getInt("economy.decimal-places", 2)); }
}
