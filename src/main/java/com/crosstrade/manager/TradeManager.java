package com.crosstrade.manager;

import com.crosstrade.CrossTrade;
import com.crosstrade.model.TradeRequest;
import com.crosstrade.model.TradeSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TradeManager {
    private final CrossTrade plugin;
    private final Map<UUID, TradeSession> activeTrades = new HashMap<>();
    private final Map<UUID, TradeRequest> requestsByTarget = new HashMap<>();
    private final Map<UUID, Long> lastRequestAt = new HashMap<>();

    public TradeManager(CrossTrade plugin) { this.plugin = plugin; }

    public boolean sendTradeRequest(Player sender, Player target) {
        if (!plugin.marketAvailable()) {
            plugin.getConfigManager().send(sender, "database-unavailable");
            return false;
        }
        if (sender.equals(target) || isTrading(sender) || isTrading(target)) return false;
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("direct-trade.request-cooldown-seconds", 5L) * 1000L;
        if (now - lastRequestAt.getOrDefault(sender.getUniqueId(), 0L) < cooldown) {
            plugin.getConfigManager().send(sender, "request-cooldown");
            return false;
        }
        lastRequestAt.put(sender.getUniqueId(), now);
        long timeout = plugin.getConfig().getLong("direct-trade.request-timeout-seconds",
                plugin.getConfig().getLong("trade.request-timeout", 60L)) * 1000L;
        TradeRequest request = new TradeRequest(UUID.randomUUID(), sender.getUniqueId(), target.getUniqueId(), now, now + timeout);
        TradeRequest previous = requestsByTarget.put(target.getUniqueId(), request);
        if (previous != null) plugin.getConfigManager().send(target, "trade-request-replaced");
        plugin.getConfigManager().send(sender, "trade-request-sent", Map.of("{player}", target.getName()));
        plugin.getConfigManager().send(target, "trade-request-received", Map.of("{player}", sender.getName()));
        if (plugin.isBedrockPlayer(target)) plugin.getBedrockFormUtil().showTradeRequestForm(target, sender, request.id());
        else sendClickableRequest(target, sender);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            TradeRequest current = requestsByTarget.get(target.getUniqueId());
            if (current != null && current.id().equals(request.id())) requestsByTarget.remove(target.getUniqueId());
        }, Math.max(1L, timeout / 50L));
        return true;
    }

    private void sendClickableRequest(Player target, Player sender) {
        Component accept = Component.text("[接受]").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tradeaccept"))
                .hoverEvent(HoverEvent.showText(Component.text("点击接受 " + sender.getName() + " 的交易请求")));
        Component deny = Component.text("[拒绝]").color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tradedeny"))
                .hoverEvent(HoverEvent.showText(Component.text("点击拒绝交易请求")));
        target.sendMessage(Component.text("  ").append(accept).append(Component.text("    ")).append(deny));
    }

    public TradeRequest request(Player target) {
        TradeRequest request = requestsByTarget.get(target.getUniqueId());
        if (request != null && request.expiresAt() <= System.currentTimeMillis()) {
            requestsByTarget.remove(target.getUniqueId());
            return null;
        }
        return request;
    }

    public boolean acceptTradeRequest(Player accepter) {
        TradeRequest request = request(accepter);
        if (request == null) return false;
        Player requester = plugin.getServer().getPlayer(request.senderId());
        if (requester == null || !requester.isOnline() || isTrading(requester) || isTrading(accepter)) {
            requestsByTarget.remove(accepter.getUniqueId());
            return false;
        }
        requestsByTarget.remove(accepter.getUniqueId());
        startTrade(requester, accepter);
        plugin.getConfigManager().send(requester, "trade-accepted");
        plugin.getConfigManager().send(accepter, "trade-accepted");
        return true;
    }

    public boolean acceptTradeRequest(Player accepter, UUID requestId) {
        TradeRequest request = request(accepter);
        return request != null && request.id().equals(requestId) && acceptTradeRequest(accepter);
    }

    public boolean denyTradeRequest(Player denier) {
        TradeRequest request = requestsByTarget.remove(denier.getUniqueId());
        if (request == null) return false;
        Player sender = plugin.getServer().getPlayer(request.senderId());
        plugin.getConfigManager().send(denier, "trade-denied");
        if (sender != null) plugin.getConfigManager().send(sender, "trade-denied");
        return true;
    }

    public boolean hasTradeRequest(Player player) { return request(player) != null; }
    public Player getTradeRequester(Player target) {
        TradeRequest request = request(target);
        return request == null ? null : plugin.getServer().getPlayer(request.senderId());
    }

    public void startTrade(Player first, Player second) {
        TradeSession session = new TradeSession(plugin, first, second);
        activeTrades.put(first.getUniqueId(), session);
        activeTrades.put(second.getUniqueId(), session);
        session.open();
    }

    public boolean isTrading(Player player) { return activeTrades.containsKey(player.getUniqueId()); }
    public TradeSession getTradeSession(Player player) { return activeTrades.get(player.getUniqueId()); }
    public void endTrade(TradeSession session) {
        activeTrades.remove(session.getPlayer1().getUniqueId(), session);
        activeTrades.remove(session.getPlayer2().getUniqueId(), session);
    }

    public void closeAllTrades() {
        new ArrayList<>(new java.util.HashSet<>(activeTrades.values())).forEach(session -> session.cancel("PLUGIN_DISABLE"));
        activeTrades.clear();
        requestsByTarget.clear();
    }
}
