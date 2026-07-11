package com.crosstrade.model;

import com.crosstrade.CrossTrade;
import com.crosstrade.economy.EconomyGateway;
import com.crosstrade.gui.TradeGUI;
import com.crosstrade.util.InventoryUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class TradeSession {
    private final CrossTrade plugin;
    private final Player player1;
    private final Player player2;
    private final TradeGUI gui1;
    private final TradeGUI gui2;
    private boolean player1Ready;
    private boolean player2Ready;
    private boolean ended;
    private boolean completed;
    private BukkitTask countdownTask;

    public TradeSession(CrossTrade plugin, Player player1, Player player2) {
        this.plugin = plugin;
        this.player1 = player1;
        this.player2 = player2;
        this.gui1 = new TradeGUI(plugin, this, player1, player2);
        this.gui2 = new TradeGUI(plugin, this, player2, player1);
        this.gui1.initialize();
        this.gui2.initialize();
    }

    public void open() { gui1.open(); gui2.open(); }

    public void setReady(Player player, boolean ready) {
        if (ended) return;
        if (player.equals(player1)) player1Ready = ready;
        else if (player.equals(player2)) player2Ready = ready;
        refresh();
        if (player1Ready && player2Ready) startCountdown(); else stopCountdown();
    }

    public void offerChanged(Player player) {
        if (ended) return;
        boolean hadReady = player1Ready || player2Ready;
        player1Ready = false;
        player2Ready = false;
        stopCountdown();
        gui1.updateOtherItems(gui2.offeredItems());
        gui2.updateOtherItems(gui1.offeredItems());
        refresh();
        if (hadReady) {
            plugin.getConfigManager().send(player1, "trade-changed");
            plugin.getConfigManager().send(player2, "trade-changed");
        }
    }

    private void startCountdown() {
        if (countdownTask != null || ended) return;
        int seconds = plugin.getConfig().getInt("direct-trade.confirm-countdown-seconds",
                plugin.getConfig().getInt("trade.confirm-countdown", 3));
        countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = seconds;
            @Override public void run() {
                if (ended || !player1Ready || !player2Ready) { stopCountdown(); return; }
                if (remaining-- > 0) {
                    Map<String, String> values = Map.of("{seconds}", String.valueOf(remaining + 1));
                    plugin.getConfigManager().send(player1, "trade-countdown", values);
                    plugin.getConfigManager().send(player2, "trade-countdown", values);
                    player1.playSound(player1.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8F, 1.0F);
                    player2.playSound(player2.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8F, 1.0F);
                } else {
                    stopCountdown();
                    complete();
                }
            }
        }, 0L, 20L);
    }

    private void stopCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void complete() {
        if (ended) return;
        List<ItemStack> offer1 = gui1.offeredItems();
        List<ItemStack> offer2 = gui2.offeredItems();
        BigDecimal money1 = gui1.money();
        BigDecimal money2 = gui2.money();
        if (offer1.isEmpty() && offer2.isEmpty() && money1.signum() == 0 && money2.signum() == 0) {
            plugin.getConfigManager().send(player1, "both-empty");
            plugin.getConfigManager().send(player2, "both-empty");
            resetReady();
            return;
        }
        boolean content1 = !offer1.isEmpty() || money1.signum() > 0;
        boolean content2 = !offer2.isEmpty() || money2.signum() > 0;
        if (!plugin.getConfig().getBoolean("direct-trade.allow-one-sided",
                plugin.getConfig().getBoolean("trade.allow-one-sided", true)) && (!content1 || !content2)) {
            player1.sendMessage(com.crosstrade.util.Text.color("&c当前配置要求双方都提供物品或金额。"));
            player2.sendMessage(com.crosstrade.util.Text.color("&c当前配置要求双方都提供物品或金额。"));
            resetReady();
            return;
        }
        if (!InventoryUtil.canFit(player1.getInventory(), offer2) || !InventoryUtil.canFit(player2.getInventory(), offer1)) {
            plugin.getConfigManager().send(player1, "inventory-full");
            plugin.getConfigManager().send(player2, "inventory-full");
            resetReady();
            return;
        }
        BigDecimal net = money1.subtract(money2);
        Player payer = net.signum() > 0 ? player1 : player2;
        Player payee = net.signum() > 0 ? player2 : player1;
        BigDecimal amount = net.abs();
        if (amount.signum() > 0) {
            if (!plugin.getEconomyGateway().available()) {
                plugin.getConfigManager().send(player1, "economy-unavailable");
                plugin.getConfigManager().send(player2, "economy-unavailable");
                resetReady();
                return;
            }
            if (!plugin.getEconomyGateway().has(payer, amount)) {
                plugin.getConfigManager().send(payer, "insufficient-balance");
                resetReady();
                return;
            }
            EconomyGateway.Result withdrawn = plugin.getEconomyGateway().withdraw(payer, amount);
            if (!withdrawn.success()) { economyFailure(); return; }
            EconomyGateway.Result deposited = plugin.getEconomyGateway().deposit(payee, amount);
            if (!deposited.success()) {
                EconomyGateway.Result refund = plugin.getEconomyGateway().deposit(payer, amount);
                if (!refund.success()) {
                    plugin.getMarketRepository().addPayout(payer.getUniqueId(), amount,
                            "面对面交易入账失败后的待补偿退款: " + refund.error());
                    plugin.getLogger().severe("面对面交易退款失败，已加入待领取货款: " + payer.getUniqueId() + " " + amount);
                }
                economyFailure();
                return;
            }
        }
        completed = true;
        ended = true;
        stopCountdown();
        gui1.clearOffered();
        gui2.clearOffered();
        giveOrMailbox(player1, offer2, "DIRECT_TRADE_RECEIVE");
        giveOrMailbox(player2, offer1, "DIRECT_TRADE_RECEIVE");
        plugin.getTradeManager().endTrade(this);
        player1.closeInventory();
        player2.closeInventory();
        plugin.getConfigManager().send(player1, "trade-completed");
        plugin.getConfigManager().send(player2, "trade-completed");
        player1.playSound(player1.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1F);
        player2.playSound(player2.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1F);
        plugin.getAuditLog().write("DIRECT_COMPLETE", player1.getUniqueId() + " <-> " + player2.getUniqueId()
                + " money1=" + money1 + " money2=" + money2);
    }

    private void giveOrMailbox(Player receiver, List<ItemStack> items, String reason) {
        for (ItemStack stack : items) {
            Map<Integer, ItemStack> leftovers = receiver.getInventory().addItem(stack.clone());
            for (ItemStack leftover : leftovers.values()) plugin.getMailboxService().store(receiver, leftover, leftover.getAmount(), reason, null);
        }
    }

    private void economyFailure() {
        plugin.getConfigManager().send(player1, "economy-failed");
        plugin.getConfigManager().send(player2, "economy-failed");
        resetReady();
    }

    private void resetReady() { player1Ready = false; player2Ready = false; stopCountdown(); refresh(); }

    public void cancel() { cancel("PLAYER_CANCELLED"); }
    public void cancel(String reason) {
        if (ended) return;
        ended = true;
        stopCountdown();
        List<ItemStack> offer1 = gui1.offeredItems();
        List<ItemStack> offer2 = gui2.offeredItems();
        gui1.clearOffered();
        gui2.clearOffered();
        giveOrMailbox(player1, offer1, reason);
        giveOrMailbox(player2, offer2, reason);
        plugin.getTradeManager().endTrade(this);
        player1.closeInventory();
        player2.closeInventory();
        plugin.getConfigManager().send(player1, "trade-cancelled");
        plugin.getConfigManager().send(player2, "trade-cancelled");
        plugin.getAuditLog().write("DIRECT_CANCEL", player1.getUniqueId() + " <-> " + player2.getUniqueId() + " reason=" + reason);
    }

    private void refresh() { gui1.update(); gui2.update(); }
    public boolean isReady(Player player) { return player.equals(player1) ? player1Ready : player.equals(player2) && player2Ready; }
    public BigDecimal money(Player player) {
        TradeGUI gui = getGUI(player);
        return gui == null ? BigDecimal.ZERO : gui.money();
    }
    public TradeGUI getGUI(Player player) { return player.equals(player1) ? gui1 : gui2; }
    public Player getPlayer1() { return player1; }
    public Player getPlayer2() { return player2; }
    public boolean ended() { return ended; }
    public boolean completed() { return completed; }
}
