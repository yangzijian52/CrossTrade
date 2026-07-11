package com.crosstrade.listener;

import com.crosstrade.CrossTrade;
import com.crosstrade.gui.TradeGUI;
import com.crosstrade.gui.TradeHolder;
import com.crosstrade.model.TradeSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Set;

public final class TradeListener implements Listener {
    private static final Set<InventoryAction> SAFE_OFFER_ACTIONS = Set.of(
            InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF,
            InventoryAction.PLACE_ALL, InventoryAction.PLACE_ONE, InventoryAction.PLACE_SOME,
            InventoryAction.SWAP_WITH_CURSOR, InventoryAction.NOTHING);
    private final CrossTrade plugin;

    public TradeListener(CrossTrade plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) return;
        Player player = event.getPlayer();
        if (!player.isSneaking() || !plugin.getConfig().getBoolean("direct-trade.sneak-right-click",
                plugin.getConfig().getBoolean("trade.sneak-to-trade", true))) return;
        event.setCancelled(true);
        if (!player.hasPermission("crosstrade.direct")) { plugin.getConfigManager().send(player, "no-permission"); return; }
        if (plugin.getTradeManager().isTrading(player) || plugin.getTradeManager().isTrading(target)) {
            plugin.getConfigManager().send(player, "already-trading");
            return;
        }
        plugin.getTradeManager().sendTradeRequest(player, target);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TradeHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.ownerId().equals(player.getUniqueId())) return;
        TradeSession session = holder.session();
        if (session.ended() || plugin.getTradeManager().getTradeSession(player) != session) return;
        TradeGUI gui = session.getGUI(player);
        int raw = event.getRawSlot();
        if (raw >= 0 && raw < event.getView().getTopInventory().getSize()) {
            String control = gui.control(event.getCurrentItem());
            if (control != null) {
                String configured = gui.configuredButton(event.getCurrentItem());
                if (configured != null && !plugin.getGuiConfigManager().runCommands(player, configured,
                        java.util.Map.of("{other_player}", session.getPlayer1().equals(player)
                                ? session.getPlayer2().getName() : session.getPlayer1().getName()))) return;
                switch (control) {
                    case "direct_confirm" -> session.setReady(player, !session.isReady(player));
                    case "direct_cancel" -> session.cancel("CANCEL_BUTTON");
                    case "direct_add_1", "direct_add_10", "direct_add_100", "direct_add_1000",
                         "direct_clear", "direct_sub_1", "direct_sub_10", "direct_sub_100", "direct_sub_1000" -> gui.adjustMoney(control);
                    default -> { }
                }
                return;
            }
            if (gui.isOwnerSlot(raw) && SAFE_OFFER_ACTIONS.contains(event.getAction())
                    && !event.isShiftClick() && event.getClick() != org.bukkit.event.inventory.ClickType.NUMBER_KEY
                    && event.getClick() != org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                    && event.getClick() != org.bukkit.event.inventory.ClickType.DROP
                    && event.getClick() != org.bukkit.event.inventory.ClickType.CONTROL_DROP
                    && event.getClick() != org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
                event.setCancelled(false);
                plugin.getServer().getScheduler().runTask(plugin, () -> session.offerChanged(player));
                return;
            }
            return;
        }
        // 玩家背包允许普通拿取和放置，但屏蔽所有可把物品快速送入顶部 GUI 的旁路。
        if (!event.isShiftClick() && event.getClick().isLeftClick() || !event.isShiftClick() && event.getClick().isRightClick()) {
            if (event.getClick() != org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TradeHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.ownerId().equals(player.getUniqueId())) return;
        TradeGUI gui = holder.session().getGUI(player);
        int topSize = event.getView().getTopInventory().getSize();
        boolean safe = event.getRawSlots().stream().filter(slot -> slot < topSize).allMatch(gui::isOwnerSlot);
        if (safe) {
            event.setCancelled(false);
            plugin.getServer().getScheduler().runTask(plugin, () -> holder.session().offerChanged(player));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeHolder holder) || !(event.getPlayer() instanceof Player player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            TradeSession active = plugin.getTradeManager().getTradeSession(player);
            if (active == holder.session() && !active.ended()) active.cancel("INVENTORY_CLOSED");
        });
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { cancel(event.getPlayer(), "PLAYER_QUIT"); }
    @EventHandler public void onDeath(PlayerDeathEvent event) {
        if (plugin.getConfig().getBoolean("direct-trade.cancel-on-death", true)) cancel(event.getEntity(), "PLAYER_DEATH");
    }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        if (plugin.getConfig().getBoolean("direct-trade.cancel-on-world-change", true)) cancel(event.getPlayer(), "WORLD_CHANGE");
    }

    private void cancel(Player player, String reason) {
        TradeSession session = plugin.getTradeManager().getTradeSession(player);
        if (session != null) session.cancel(reason);
        if (plugin.getMarketInputManager() != null) plugin.getMarketInputManager().cancel(player, false);
    }
}
