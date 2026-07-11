package com.crosstrade.input;

import com.crosstrade.CrossTrade;
import com.crosstrade.market.service.MarketService;
import com.crosstrade.util.InventoryUtil;
import com.crosstrade.util.Money;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MarketInputManager {
    public enum Stage { NAME, QUANTITY, PRICE, BUY_QUANTITY, RESTOCK_QUANTITY }
    private final CrossTrade plugin;
    private final Map<UUID, InputSession> sessions = new HashMap<>();

    public MarketInputManager(CrossTrade plugin) { this.plugin = plugin; }

    public void beginListing(Player player, ItemStack prototype) {
        cancel(player, false);
        InputSession session = new InputSession(prototype.clone());
        sessions.put(player.getUniqueId(), session);
        prompt(player, session, Stage.NAME, "&e请在聊天栏输入市场商品名，输入 &c取消 &e可终止操作。", null);
    }

    public void customSellQuantity(Player player) {
        InputSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        prompt(player, session, Stage.QUANTITY, "&e请输入上架数量，范围 1 - "
                + Math.min(InventoryUtil.countSimilar(player.getInventory(), session.prototype),
                plugin.getConfig().getInt("limits.max-listing-quantity", 2304)) + "。", null);
    }

    public void selectSellQuantity(Player player, int quantity) {
        InputSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (quantity <= 0 || quantity > InventoryUtil.countSimilar(player.getInventory(), session.prototype))
            throw new IllegalArgumentException("上架数量超出背包中的同类物品数量");
        session.quantity = quantity;
        prompt(player, session, Stage.PRICE, "&e请输入每件商品的单价。", null);
    }

    public void beginBuyQuantity(Player player, long listingId) {
        cancel(player, false);
        InputSession session = new InputSession(null);
        session.listingId = listingId;
        sessions.put(player.getUniqueId(), session);
        prompt(player, session, Stage.BUY_QUANTITY, "&e请输入购买数量，输入 &c取消 &e可终止操作。", null);
    }

    public void beginRestockQuantity(Player player, long listingId) {
        cancel(player, false);
        InputSession session = new InputSession(null);
        session.listingId = listingId;
        sessions.put(player.getUniqueId(), session);
        prompt(player, session, Stage.RESTOCK_QUANTITY,
                "&e请输入补货数量，输入 &c取消 &e可终止操作。", null);
    }

    public boolean has(Player player) { return sessions.containsKey(player.getUniqueId()); }

    public void handle(Player player, String input) {
        InputSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if ("取消".equalsIgnoreCase(input.trim()) || "cancel".equalsIgnoreCase(input.trim())) {
            cancel(player, true); return;
        }
        try {
            switch (session.stage) {
                case NAME -> {
                    String name = plugin.getMarketService().sanitizeName(input);
                    if (name.isBlank()) throw new IllegalArgumentException("商品名无效");
                    session.marketName = name;
                    clearTimeout(session);
                    plugin.getMarketGui().openSellQuantity(player);
                }
                case QUANTITY -> {
                    int quantity = Integer.parseInt(input.trim());
                    selectSellQuantity(player, quantity);
                }
                case PRICE -> {
                    BigDecimal price = Money.parse(input, plugin.getConfig().getInt("economy.decimal-places", 2));
                    session.unitPrice = price;
                    clearTimeout(session);
                    plugin.getMarketGui().openSellDuration(player);
                }
                case BUY_QUANTITY -> {
                    int quantity = Integer.parseInt(input.trim());
                    long listingId = session.listingId;
                    clear(player);
                    plugin.getMarketGui().openBuyConfirm(player, listingId, quantity);
                }
                case RESTOCK_QUANTITY -> {
                    int quantity = Integer.parseInt(input.trim());
                    long listingId = session.listingId;
                    clear(player);
                    MarketService.Result result = plugin.getMarketService().restock(player, listingId, quantity);
                    if (result.success()) plugin.getConfigManager().send(player, "listing-restocked",
                            Map.of("{amount}", String.valueOf(quantity)));
                    else if (result.messageKey() != null) plugin.getConfigManager().send(player, result.messageKey());
                    else player.sendMessage(com.crosstrade.util.Text.color("&c" + result.rawMessage()));
                    plugin.getMarketGui().openManage(player, listingId);
                }
            }
        } catch (RuntimeException exception) {
            player.sendMessage(com.crosstrade.util.Text.color("&c输入无效：" + exception.getMessage()));
            armTimeout(player, session);
        }
    }

    public void selectDays(Player player, int days) {
        InputSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        session.days = days;
        plugin.getMarketGui().openSellConfirm(player);
    }

    public MarketService.ListingDraft draft(Player player) {
        InputSession session = sessions.get(player.getUniqueId());
        if (session == null || session.prototype == null || session.marketName == null || session.quantity <= 0
                || session.unitPrice == null || session.days <= 0) return null;
        return new MarketService.ListingDraft(session.prototype.clone(), session.marketName, session.quantity, session.unitPrice, session.days);
    }

    public PartialDraft partial(Player player) {
        InputSession session = sessions.get(player.getUniqueId());
        return session == null || session.prototype == null ? null
                : new PartialDraft(session.prototype.clone(), session.marketName, session.quantity, session.unitPrice, session.days);
    }

    public void clear(Player player) { InputSession removed = sessions.remove(player.getUniqueId()); if (removed != null) clearTimeout(removed); }

    public void cancel(Player player, boolean notify) {
        clear(player);
        if (notify) plugin.getConfigManager().send(player, "input-cancelled");
    }

    private void prompt(Player player, InputSession session, Stage stage, String message, Runnable after) {
        session.stage = stage;
        player.closeInventory();
        player.sendMessage(com.crosstrade.util.Text.color(message));
        armTimeout(player, session);
        if (after != null) after.run();
    }

    private void armTimeout(Player player, InputSession session) {
        clearTimeout(session);
        long ticks = plugin.getConfig().getLong("market.input-timeout-seconds", 60L) * 20L;
        session.timeout = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (sessions.remove(player.getUniqueId(), session)) plugin.getConfigManager().send(player, "input-timeout");
        }, ticks);
    }

    private void clearTimeout(InputSession session) { if (session.timeout != null) { session.timeout.cancel(); session.timeout = null; } }

    private static final class InputSession {
        final ItemStack prototype;
        Stage stage;
        String marketName;
        int quantity;
        BigDecimal unitPrice;
        int days;
        long listingId;
        BukkitTask timeout;
        InputSession(ItemStack prototype) { this.prototype = prototype; }
    }

    public record PartialDraft(ItemStack prototype, String marketName, int quantity, BigDecimal unitPrice, int days) {}
}
