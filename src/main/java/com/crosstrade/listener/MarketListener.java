package com.crosstrade.listener;

import com.crosstrade.CrossTrade;
import com.crosstrade.market.gui.GuiItemFactory;
import com.crosstrade.market.gui.MarketHolder;
import com.crosstrade.market.service.MarketService;
import com.crosstrade.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public final class MarketListener implements Listener {
    private final CrossTrade plugin;

    public MarketListener(CrossTrade plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MarketHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer().equals(player.getUniqueId())) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack clicked = event.getCurrentItem();
        GuiItemFactory factory = plugin.getMarketGui().items();
        String configuredButton = factory.configButton(clicked);
        if (configuredButton != null && !plugin.getGuiConfigManager().runCommands(player, configuredButton, holder)) return;
        String action = factory.action(clicked);
        if (action == null || action.isBlank()) return;
        Long longValue = factory.longValue(clicked);
        Integer intValue = factory.intValue(clicked);
        switch (action) {
            case "close" -> player.closeInventory();
            case "home" -> plugin.getMarketGui().openHome(player);
            case "player_trade" -> plugin.getMarketGui().openPlayerTrade(player, 0);
            case "direct_request" -> {
                Player target = plugin.getServer().getPlayer(UUID.fromString(factory.stringValue(clicked)));
                if (target == null || !target.isOnline()) plugin.getConfigManager().send(player, "player-not-found");
                else plugin.getTradeManager().sendTradeRequest(player, target);
            }
            case "plaza" -> plugin.getMarketGui().openPlaza(player, 0, "", "LATEST");
            case "sell" -> plugin.getMarketGui().openSellSelect(player);
            case "mine" -> plugin.getMarketGui().openMine(player, 0);
            case "mailbox" -> plugin.getMarketGui().openMailbox(player, 0);
            case "earnings" -> plugin.getMarketGui().openEarnings(player);
            case "history" -> plugin.getMarketGui().openHistory(player, 0);
            case "seller" -> plugin.getMarketGui().openSeller(player, UUID.fromString(factory.stringValue(clicked)), 0, "", "LATEST");
            case "buy" -> plugin.getMarketGui().openBuyQuantity(player, longValue);
            case "buy_amount" -> plugin.getMarketGui().openBuyConfirm(player, holder.listingId(), intValue);
            case "buy_custom" -> plugin.getMarketInputManager().beginBuyQuantity(player, holder.listingId());
            case "buy_back" -> plugin.getMarketGui().openBuyQuantity(player, holder.listingId());
            case "buy_confirm" -> handlePurchase(player, holder.listingId(), intValue);
            case "seller_back" -> plugin.getMarketGui().openSeller(player, holder.subject(), 0, "", "LATEST");
            case "sell_select" -> {
                int sourceSlot = intValue == null ? -1 : intValue;
                ItemStack source = sourceSlot < 0 ? null : player.getInventory().getItem(sourceSlot);
                if (source == null || source.getType().isAir()) plugin.getConfigManager().send(player, "invalid-input");
                else plugin.getMarketInputManager().beginListing(player, source);
            }
            case "sell_amount" -> plugin.getMarketInputManager().selectSellQuantity(player, intValue);
            case "sell_custom" -> plugin.getMarketInputManager().customSellQuantity(player);
            case "sell_days" -> plugin.getMarketInputManager().selectDays(player, intValue);
            case "sell_confirm" -> handleListing(player);
            case "cancel_input" -> { plugin.getMarketInputManager().cancel(player, true); plugin.getMarketGui().openHome(player); }
            case "manage" -> plugin.getMarketGui().openManage(player, longValue);
            case "listing_cancel_confirm" -> plugin.getMarketGui().openCancelListingConfirm(player, longValue);
            case "listing_cancel" -> {
                if (plugin.getMarketService().cancelListing(player, longValue, false)) plugin.getConfigManager().send(player, "listing-cancelled");
                else plugin.getConfigManager().send(player, "listing-unavailable");
                plugin.getMarketGui().openMine(player, 0);
            }
            case "listing_restock" -> plugin.getMarketGui().openRestock(player, longValue);
            case "restock_amount" -> handleRestock(player, holder.listingId(), intValue);
            case "restock_custom" -> plugin.getMarketInputManager().beginRestockQuantity(player, holder.listingId());
            case "listing_relist" -> plugin.getMarketGui().openRelistDuration(player, longValue);
            case "relist_days" -> {
                if (plugin.getMarketService().relist(player, holder.listingId(), intValue))
                    plugin.getConfigManager().send(player, "listing-relisted");
                else plugin.getConfigManager().send(player, "listing-unavailable");
                plugin.getMarketGui().openManage(player, holder.listingId());
            }
            case "listing_delete_confirm" -> plugin.getMarketGui().openDeleteListingConfirm(player, longValue);
            case "listing_delete" -> {
                if (plugin.getMarketService().deleteListing(player, longValue)) plugin.getConfigManager().send(player, "listing-deleted");
                else plugin.getConfigManager().send(player, "listing-unavailable");
                plugin.getMarketGui().openMine(player, 0);
            }
            case "mine_clear_confirm" -> plugin.getMarketGui().openMineClearConfirm(player);
            case "mine_clear" -> {
                int changed = plugin.getMarketService().clearInactive(player);
                plugin.getConfigManager().send(player, "inactive-cleared", Map.of("{amount}", String.valueOf(changed)));
                plugin.getMarketGui().openMine(player, 0);
            }
            case "history_clear_confirm" -> plugin.getMarketGui().openHistoryClearConfirm(player);
            case "history_clear" -> {
                int changed = plugin.getMarketRepository().clearHistory(player.getUniqueId());
                plugin.getConfigManager().send(player, "history-cleared", Map.of("{amount}", String.valueOf(changed)));
                plugin.getMarketGui().openHistory(player, 0);
            }
            case "mail_claim" -> {
                int claimed = plugin.getMailboxService().claim(player, longValue);
                if (claimed > 0) plugin.getConfigManager().send(player, "mailbox-claimed", Map.of("{amount}", String.valueOf(claimed)));
                else plugin.getConfigManager().send(player, "inventory-full");
                plugin.getMarketGui().openMailbox(player, holder.page());
            }
            case "mail_claim_all" -> {
                int claimed = plugin.getMailboxService().claimAll(player);
                if (claimed > 0) plugin.getConfigManager().send(player, "mailbox-claimed", Map.of("{amount}", String.valueOf(claimed)));
                else plugin.getConfigManager().send(player, "mailbox-empty");
                plugin.getMarketGui().openMailbox(player, 0);
            }
            case "earnings_claim" -> {
                BigDecimal paid = plugin.getPayoutService().claim(player);
                if (paid.signum() > 0) plugin.getConfigManager().send(player, "earnings-claimed", Map.of(
                        "{symbol}", plugin.getConfig().getString("economy.currency-symbol", "$"),
                        "{amount}", Text.money(paid, plugin.getConfig().getInt("economy.decimal-places", 2))));
                else plugin.getConfigManager().send(player, "earnings-empty");
                plugin.getMarketGui().openEarnings(player);
            }
            case "container_preview" -> plugin.getMarketGui().openContainer(player, holder.listingId(), holder.type().name());
            case "container_back" -> containerBack(player, holder);
            case "plaza_prev" -> plugin.getMarketGui().openPlaza(player, holder.page() - 1, holder.search(), holder.sort());
            case "plaza_next" -> plugin.getMarketGui().openPlaza(player, holder.page() + 1, holder.search(), holder.sort());
            case "player_trade_prev" -> plugin.getMarketGui().openPlayerTrade(player, holder.page() - 1);
            case "player_trade_next" -> plugin.getMarketGui().openPlayerTrade(player, holder.page() + 1);
            case "seller_prev" -> plugin.getMarketGui().openSeller(player, holder.subject(), holder.page() - 1, holder.search(), holder.sort());
            case "seller_next" -> plugin.getMarketGui().openSeller(player, holder.subject(), holder.page() + 1, holder.search(), holder.sort());
            case "mine_prev" -> plugin.getMarketGui().openMine(player, holder.page() - 1);
            case "mine_next" -> plugin.getMarketGui().openMine(player, holder.page() + 1);
            case "mail_prev" -> plugin.getMarketGui().openMailbox(player, holder.page() - 1);
            case "mail_next" -> plugin.getMarketGui().openMailbox(player, holder.page() + 1);
            case "history_prev" -> plugin.getMarketGui().openHistory(player, holder.page() - 1);
            case "history_next" -> plugin.getMarketGui().openHistory(player, holder.page() + 1);
            case "sort_next" -> cycleSort(player, holder);
            default -> {}
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MarketHolder) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getMarketInputManager().has(event.getPlayer())) return;
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getMarketInputManager().handle(event.getPlayer(), input));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getPayoutService().attemptOnline(event.getPlayer().getUniqueId());
            plugin.getMarketService().notifyPendingSales(event.getPlayer());
        }, 40L);
    }

    private void handleListing(Player player) {
        MarketService.ListingDraft draft = plugin.getMarketInputManager().draft(player);
        MarketService.Result result;
        try { result = plugin.getMarketService().createListing(player, draft); }
        catch (RuntimeException exception) {
            plugin.getLogger().severe("上架事务异常: " + exception.getMessage());
            plugin.getConfigManager().send(player, "database-unavailable");
            return;
        }
        if (result.success()) {
            plugin.getMarketInputManager().clear(player);
            plugin.getConfigManager().send(player, "listing-created", Map.of("{id}", String.valueOf(result.id())));
            plugin.getMarketGui().openMine(player, 0);
        } else sendResult(player, result);
    }

    private void handlePurchase(Player player, long listingId, int quantity) {
        MarketService.Result result;
        try { result = plugin.getMarketService().purchase(player, listingId, quantity); }
        catch (RuntimeException exception) {
            plugin.getLogger().severe("购买事务异常，相关预留将保留供复核: " + exception.getMessage());
            plugin.getConfigManager().send(player, "database-unavailable");
            return;
        }
        if (result.success()) {
            plugin.getConfigManager().send(player, "purchase-success", Map.of(
                    "{amount}", String.valueOf(result.quantity()), "{item}", result.itemName(),
                    "{symbol}", plugin.getConfig().getString("economy.currency-symbol", "$"),
                    "{total}", Text.money(result.total(), plugin.getConfig().getInt("economy.decimal-places", 2))));
            plugin.getMarketGui().openSeller(player, plugin.getMarketRepository().find(listingId).map(com.crosstrade.market.model.Listing::sellerId).orElse(player.getUniqueId()), 0, "", "LATEST");
        } else sendResult(player, result);
    }

    private void handleRestock(Player player, long listingId, int quantity) {
        MarketService.Result result = plugin.getMarketService().restock(player, listingId, quantity);
        if (result.success()) plugin.getConfigManager().send(player, "listing-restocked",
                Map.of("{amount}", String.valueOf(quantity)));
        else sendResult(player, result);
        plugin.getMarketGui().openManage(player, listingId);
    }

    private void sendResult(Player player, MarketService.Result result) {
        if (result.messageKey() != null) plugin.getConfigManager().send(player, result.messageKey(), Map.of("{id}", String.valueOf(result.id())));
        else player.sendMessage(Text.color("&8[&6CrossTrade&8] &c" + result.rawMessage()));
    }

    private void containerBack(Player player, MarketHolder holder) {
        String type = holder.search();
        if (MarketHolder.Type.MANAGE.name().equals(type)) plugin.getMarketGui().openManage(player, holder.listingId());
        else if (MarketHolder.Type.BUY_CONFIRM.name().equals(type)) plugin.getMarketGui().openBuyQuantity(player, holder.listingId());
        else plugin.getMarketGui().openBuyQuantity(player, holder.listingId());
    }

    private void cycleSort(Player player, MarketHolder holder) {
        if (holder.type() == MarketHolder.Type.PLAZA) {
            String next = switch (holder.sort()) { case "LATEST" -> "COUNT"; case "COUNT" -> "NAME"; default -> "LATEST"; };
            plugin.getMarketGui().openPlaza(player, 0, holder.search(), next);
        } else if (holder.type() == MarketHolder.Type.SELLER) {
            String next = switch (holder.sort()) {
                case "LATEST" -> "PRICE_ASC"; case "PRICE_ASC" -> "PRICE_DESC";
                case "PRICE_DESC" -> "EXPIRY"; case "EXPIRY" -> "NAME"; default -> "LATEST";
            };
            plugin.getMarketGui().openSeller(player, holder.subject(), 0, holder.search(), next);
        }
    }
}
