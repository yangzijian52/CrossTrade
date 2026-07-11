package com.crosstrade.command;

import com.crosstrade.CrossTrade;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TradeCommand implements CommandExecutor {
    private final CrossTrade plugin;
    public TradeCommand(CrossTrade plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("只有玩家可以发起交易。"); return true; }
        if (!player.hasPermission("crosstrade.direct")) { plugin.getConfigManager().send(player, "no-permission"); return true; }
        if (args.length > 0 && "market".equalsIgnoreCase(args[0])) {
            if (plugin.marketAvailable()) plugin.getMarketGui().openHome(player); else plugin.getConfigManager().send(player, "database-unavailable");
            return true;
        }
        if (args.length == 0) {
            if (plugin.isBedrockPlayer(player)) plugin.getBedrockFormUtil().showTradePlayerSelector(player);
            else player.sendMessage(com.crosstrade.util.Text.color("&e用法: /trade <玩家> &7或 &e/trade market"));
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) { plugin.getConfigManager().send(player, "player-not-found"); return true; }
        if (target.equals(player)) { plugin.getConfigManager().send(player, "cannot-trade-self"); return true; }
        if (plugin.getTradeManager().isTrading(player) || plugin.getTradeManager().isTrading(target)) {
            plugin.getConfigManager().send(player, "already-trading"); return true;
        }
        plugin.getTradeManager().sendTradeRequest(player, target);
        return true;
    }
}
