package com.crosstrade.command;

import com.crosstrade.CrossTrade;
import com.crosstrade.market.model.Listing;
import com.crosstrade.market.model.MarketStatus;
import com.crosstrade.market.model.SaleRecord;
import com.crosstrade.util.Money;
import com.crosstrade.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MarketCommand implements CommandExecutor, TabCompleter {
    private final CrossTrade plugin;
    public MarketCommand(CrossTrade plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!plugin.marketAvailable()) sender.sendMessage(plugin.getConfigManager().message("database-unavailable"));
            else if (sender instanceof Player player) plugin.getMarketGui().openHome(player); else help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("help".equals(sub)) { help(sender); return true; }
        if (!plugin.marketAvailable()) { sender.sendMessage(plugin.getConfigManager().message("database-unavailable")); return true; }
        if ("admin".equals(sub)) return admin(sender, Arrays.copyOfRange(args, 1, args.length));
        if (!(sender instanceof Player player)) { sender.sendMessage("该市场命令只能由玩家使用。"); return true; }
        if (!player.hasPermission("crosstrade.market.use")) { plugin.getConfigManager().send(player, "no-permission"); return true; }
        switch (sub) {
            case "plaza" -> plugin.getMarketGui().openPlaza(player, 0, "", "LATEST");
            case "sell" -> plugin.getMarketGui().openSellSelect(player);
            case "mine" -> plugin.getMarketGui().openMine(player, 0);
            case "mailbox" -> plugin.getMarketGui().openMailbox(player, 0);
            case "earnings" -> plugin.getMarketGui().openEarnings(player);
            case "history" -> plugin.getMarketGui().openHistory(player, 0);
            case "search" -> plugin.getMarketGui().openPlaza(player, 0,
                    args.length < 2 ? "" : String.join(" ", Arrays.copyOfRange(args, 1, args.length)), "LATEST");
            case "seller" -> {
                if (args.length < 2) { player.sendMessage(Text.color("&c用法: /market seller <玩家> [商品名]")); break; }
                OfflinePlayer target = offline(args[1]);
                String query = args.length < 3 ? "" : String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.getMarketGui().openSeller(player, target.getUniqueId(), 0, query, "LATEST");
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Text.color("&8&m------------------------------------------------"));
        sender.sendMessage(Text.color("&6&lCrossTrade 玩家市场指令"));
        sender.sendMessage(Text.color("&e/market &7- 打开交易中心"));
        sender.sendMessage(Text.color("&e/market plaza &7- 打开按卖家排列的交易广场"));
        sender.sendMessage(Text.color("&e/market sell &7- 从背包选择并上架商品"));
        sender.sendMessage(Text.color("&e/market mine &7- 查看和下架自己的商品"));
        sender.sendMessage(Text.color("&e/market mailbox &7- 领取到期、下架或补偿物品"));
        sender.sendMessage(Text.color("&e/market earnings &7- 领取售出商品所得货款"));
        sender.sendMessage(Text.color("&e/market history &7- 查看购买与售出记录"));
        sender.sendMessage(Text.color("&e/market search <玩家名> &7- 搜索卖家"));
        sender.sendMessage(Text.color("&e/market seller <玩家> [商品名] &7- 打开店铺并搜索商品"));
        sender.sendMessage(Text.color("&e/trade <玩家> &7- 发起面对面交易"));
        if (sender.hasPermission("crosstrade.market.admin")) {
            sender.sendMessage(Text.color("&c/market admin reload &7- 重载安全配置项"));
            sender.sendMessage(Text.color("&c/market admin inspect <商品ID> &7- 检查商品"));
            sender.sendMessage(Text.color("&c/market admin remove <商品ID> &7- 强制下架并返还"));
            sender.sendMessage(Text.color("&c/market admin seller <玩家> &7- 查看卖家商品"));
            sender.sendMessage(Text.color("&c/market admin history <玩家> &7- 查询交易历史"));
            sender.sendMessage(Text.color("&c/market admin mailbox <玩家> &7- 查询邮箱数量"));
            sender.sendMessage(Text.color("&c/market admin payouts <玩家> &7- 查询货款"));
            sender.sendMessage(Text.color("&c/market admin review &7- 列出人工复核记录"));
            sender.sendMessage(Text.color("&c/market admin compensate item <玩家> <数量> &7- 以管理员主手物品补偿"));
            sender.sendMessage(Text.color("&c/market admin compensate money <玩家> <金额> &7- 创建待领取货款"));
            sender.sendMessage(Text.color("&c/market admin backup &7- 立即备份数据库"));
        }
        sender.sendMessage(Text.color("&8&m------------------------------------------------"));
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("crosstrade.market.admin")) { sender.sendMessage(plugin.getConfigManager().message("no-permission")); return true; }
        if (args.length == 0) { help(sender); return true; }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "reload" -> {
                    plugin.reloadConfig();
                    plugin.getConfigManager().mergeDefaults();
                    plugin.getGuiConfigManager().reload();
                    sender.sendMessage(Text.color("&a安全配置与 gui.yml 已重载；数据库和线程未重启。"));
                }
                case "inspect" -> inspect(sender, Long.parseLong(args[1]));
                case "remove" -> {
                    long id = Long.parseLong(args[1]);
                    boolean ok = plugin.getMarketRepository().cancelListing(id, sender instanceof Player p ? p.getUniqueId() : new UUID(0,0), true, "ADMIN_REMOVED");
                    sender.sendMessage(Text.color(ok ? "&a商品已强制下架并进入卖家邮箱。" : "&c商品不存在或无法下架。"));
                }
                case "seller" -> {
                    OfflinePlayer target = offline(args[1]);
                    if (sender instanceof Player player) plugin.getMarketGui().openSeller(player, target.getUniqueId(), 0, "", "LATEST");
                    else sender.sendMessage("有效上架: " + plugin.getMarketRepository().activeCount(target.getUniqueId(), System.currentTimeMillis()));
                }
                case "history" -> history(sender, offline(args[1]));
                case "mailbox" -> { OfflinePlayer target = offline(args[1]); sender.sendMessage("邮箱记录数: " + plugin.getMarketRepository().mailboxCount(target.getUniqueId())); }
                case "payouts" -> { OfflinePlayer target = offline(args[1]); sender.sendMessage("待领取货款: " + plugin.getPayoutService().pending(target.getUniqueId())); }
                case "review" -> review(sender);
                case "backup" -> sender.sendMessage(Text.color("&a数据库已备份到: " + plugin.getDatabase().backup().getAbsolutePath()));
                case "compensate" -> compensate(sender, Arrays.copyOfRange(args, 1, args.length));
                default -> help(sender);
            }
        } catch (IndexOutOfBoundsException | NumberFormatException exception) {
            sender.sendMessage(Text.color("&c参数不足或数字格式错误，请使用 /market help。"));
        } catch (IOException exception) {
            sender.sendMessage(Text.color("&c数据库备份失败: " + exception.getMessage()));
        }
        return true;
    }

    private void inspect(CommandSender sender, long id) {
        Listing listing = plugin.getMarketRepository().find(id).orElse(null);
        if (listing == null) { sender.sendMessage(Text.color("&c商品不存在。")); return; }
        sender.sendMessage(Text.color("&6商品 #" + id + " &7卖家=" + listing.sellerName() + " 数量="
                + listing.remainingQuantity() + "/" + listing.initialQuantity() + " 单价=" + listing.unitPrice()
                + " 状态=" + MarketStatus.listing(listing.status()) + " 指纹=" + listing.fingerprint()));
    }

    private void history(CommandSender sender, OfflinePlayer target) {
        List<SaleRecord> rows = plugin.getMarketRepository().history(target.getUniqueId(), 20, 0);
        sender.sendMessage(Text.color("&6" + target.getName() + " 最近交易 " + rows.size() + " 条："));
        for (SaleRecord row : rows) sender.sendMessage("#" + row.id() + " " + row.marketName() + " x" + row.quantity() + " " + row.total() + " " + MarketStatus.sale(row.state()));
    }

    private void review(CommandSender sender) {
        List<SaleRecord> sales = plugin.getMarketRepository().reviewSales();
        List<Listing> listings = plugin.getMarketRepository().reviewListings();
        sender.sendMessage(Text.color("&e待人工复核交易: " + sales.size() + "，待复核上架草稿: " + listings.size()));
        for (SaleRecord sale : sales) sender.sendMessage(Text.color("&c交易 #" + sale.id() + " 商品 #" + sale.listingId() + " 买家=" + sale.buyerId()));
        for (Listing listing : listings) sender.sendMessage(Text.color("&c商品 #" + listing.id() + " 卖家=" + listing.sellerName() + " 数量=" + listing.remainingQuantity()));
    }

    private void compensate(CommandSender sender, String[] args) {
        if (args.length < 3) throw new IndexOutOfBoundsException();
        OfflinePlayer target = offline(args[1]);
        if ("money".equalsIgnoreCase(args[0])) {
            BigDecimal amount = Money.parse(args[2], plugin.getConfig().getInt("economy.decimal-places", 2));
            plugin.getMarketRepository().addPayout(target.getUniqueId(), amount, "ADMIN_COMPENSATION by " + sender.getName());
            sender.sendMessage(Text.color("&a已创建待领取货款 " + amount + "。"));
        } else if ("item".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) { sender.sendMessage("控制台无法提供主手物品。"); return; }
            int quantity = Integer.parseInt(args[2]); ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir() || quantity <= 0) { sender.sendMessage(Text.color("&c请在主手持有补偿物品。")); return; }
            plugin.getMailboxService().store(target.getUniqueId(), hand, quantity, "ADMIN_COMPENSATION", null);
            sender.sendMessage(Text.color("&a物品补偿已进入目标玩家邮箱。"));
        }
    }

    private OfflinePlayer offline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayer(name);
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("help","plaza","sell","mine","mailbox","earnings","history","search","seller","admin"), args[0]);
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) return filter(List.of("reload","inspect","remove","seller","history","mailbox","payouts","review","compensate","backup"), args[1]);
        if (args.length >= 2 && List.of("seller","history","mailbox","payouts").contains(args[0].toLowerCase(Locale.ROOT)))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).toList();
        return List.of();
    }
    private List<String> filter(List<String> values, String prefix) { String lower = prefix.toLowerCase(Locale.ROOT); return values.stream().filter(value -> value.startsWith(lower)).toList(); }
}
