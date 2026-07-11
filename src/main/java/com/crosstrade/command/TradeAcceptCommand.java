package com.crosstrade.command;

import com.crosstrade.CrossTrade;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class TradeAcceptCommand implements CommandExecutor {
    private final CrossTrade plugin;
    public TradeAcceptCommand(CrossTrade plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("只有玩家可以接受交易。"); return true; }
        if (!plugin.getTradeManager().acceptTradeRequest(player)) plugin.getConfigManager().send(player, "no-request");
        return true;
    }
}
