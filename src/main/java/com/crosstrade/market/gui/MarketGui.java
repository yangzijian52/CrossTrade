package com.crosstrade.market.gui;

import com.crosstrade.CrossTrade;
import com.crosstrade.input.MarketInputManager;
import com.crosstrade.market.model.Listing;
import com.crosstrade.market.model.MailboxEntry;
import com.crosstrade.market.model.MarketStatus;
import com.crosstrade.market.model.Payout;
import com.crosstrade.market.model.SaleRecord;
import com.crosstrade.market.model.SellerSummary;
import com.crosstrade.market.repository.MarketRepository;
import com.crosstrade.market.service.MarketService;
import com.crosstrade.util.InventoryUtil;
import com.crosstrade.util.ItemCodec;
import com.crosstrade.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MarketGui {
    private static final int PAGE_SIZE = 45;
    private final CrossTrade plugin;
    private final MarketRepository repository;
    private final ItemCodec codec;
    private final GuiItemFactory items;

    public MarketGui(CrossTrade plugin, MarketRepository repository, ItemCodec codec) {
        this.plugin = plugin; this.repository = repository; this.codec = codec; this.items = new GuiItemFactory(plugin);
    }

    public GuiItemFactory items() { return items; }

    public void openHome(Player player) {
        if (plugin.isBedrockPlayer(player) && plugin.getConfig().getBoolean("bedrock.prefer-forms", true)) {
            plugin.getBedrockFormUtil().showMarketHome(player); return;
        }
        MarketHolder holder = holder(MarketHolder.Type.HOME, player, null, 0, "", "LATEST", 0);
        Inventory inventory = inventory(holder, "home.title", "&0CrossTrade &8| &7交易中心");
        inventory.setItem(11, items.item(Material.PLAYER_HEAD, "&6&l交易广场", "plaza",
                "&7按卖家浏览所有有效商品", "", "&e点击进入"));
        inventory.setItem(13, items.item(Material.CHEST, "&a&l上架新商品", "sell",
                "&7从背包选择一种完整物品", "&7支持有内容的潜影盒", "", "&e点击开始"));
        inventory.setItem(15, items.item(Material.BOOK, "&b&l我的上架", "mine", "&7管理、查看或下架商品"));
        inventory.setItem(22, items.item(Material.ENDER_EYE, "&d&l玩家交易", "player_trade",
                "&7不需要站在对方身边", "&7查看所有在线玩家并发送交易请求", "", "&e点击选择玩家"));
        inventory.setItem(29, items.item(Material.ENDER_CHEST, "&d&l待领取物品", "mailbox",
                "&7到期、下架和失败返还都保存在这里", "&7待领取记录: &f" + repository.mailboxCount(player.getUniqueId())));
        inventory.setItem(31, items.item(Material.EMERALD, "&a&l货款保障", "earnings",
                "&7余额: &f" + symbol() + money(plugin.getEconomyGateway().balance(player)),
                "&7正常成交会自动到账",
                "&7离线或入账失败时保留: &6" + symbol() + money(plugin.getPayoutService().pending(player.getUniqueId()))));
        inventory.setItem(33, items.item(Material.WRITABLE_BOOK, "&e&l交易记录", "history", "&7查看购买与售出记录"));
        inventory.setItem(49, items.item(Material.BARRIER, "&c关闭", "close"));
        finish(player, holder, inventory);
    }

    public void openPlayerTrade(Player player, int requestedPage) {
        List<Player> online = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) if (!target.equals(player)) online.add(target);
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int pages = pages(online.size()); int page = clamp(requestedPage, pages);
        MarketHolder holder = holder(MarketHolder.Type.PLAYER_TRADE, player, null, page, "", "LATEST", 0);
        Inventory inventory = inventory(holder, title("player-trade.title", "&0在线玩家交易 &8| &7第 {page}/{pages} 页", page, pages));
        int from = page * PAGE_SIZE; int to = Math.min(from + PAGE_SIZE, online.size());
        for (int i = from; i < to; i++) {
            Player target = online.get(i);
            ItemStack head = items.head(target, "&a" + target.getName(), "direct_request",
                    "&7客户端: &f" + (plugin.isBedrockPlayer(target) ? "基岩版" : "Java版"),
                    "&7状态: &a在线", "", "&e点击发送交易请求");
            inventory.setItem(i - from, items.stringValue(head, target.getUniqueId().toString()));
        }
        nav(inventory, page, pages, "home", "player_trade_prev", "player_trade_next", online.size() + " 位在线玩家", "");
        finish(player, holder, inventory);
    }

    public void openPlaza(Player player, int requestedPage, String search, String sort) {
        plugin.getMarketService().expire();
        int count = repository.sellerCount(search, System.currentTimeMillis());
        int pages = pages(count); int page = clamp(requestedPage, pages);
        List<SellerSummary> sellers = repository.sellers(search, sort, PAGE_SIZE, page * PAGE_SIZE, System.currentTimeMillis());
        MarketHolder holder = holder(MarketHolder.Type.PLAZA, player, null, page, search, sort, 0);
        Inventory inventory = inventory(holder, title("plaza.title", "&0交易广场 &8| &7第 {page}/{pages} 页", page, pages));
        int slot = 0;
        for (SellerSummary seller : sellers) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(seller.sellerId());
            ItemStack head = items.head(offline, "&6" + seller.sellerName(), "seller",
                    "&7有效上架: &f" + seller.listings(), "&7剩余商品: &f" + seller.totalItems(),
                    "&7最低单价: &a" + symbol() + money(seller.minimumPrice()), "&7最近上架: &f" + ago(seller.latestListing()),
                    "", "&e点击进入该玩家店铺");
            inventory.setItem(slot++, items.stringValue(head, seller.sellerId().toString()));
        }
        nav(inventory, page, pages, "home", "plaza_prev", "plaza_next", count + " 位卖家", sort);
        finish(player, holder, inventory);
    }

    public void openSeller(Player player, UUID sellerId, int requestedPage, String search, String sort) {
        plugin.getMarketService().expire();
        int count = repository.activeCountBySeller(sellerId, search, System.currentTimeMillis());
        int pages = pages(count); int page = clamp(requestedPage, pages);
        List<Listing> listings = repository.activeBySeller(sellerId, search, sort, PAGE_SIZE, page * PAGE_SIZE, System.currentTimeMillis());
        String sellerName = listings.isEmpty() ? Bukkit.getOfflinePlayer(sellerId).getName() : listings.getFirst().sellerName();
        MarketHolder holder = holder(MarketHolder.Type.SELLER, player, sellerId, page, search, sort, 0);
        Inventory inventory = inventory(holder, Text.color(plugin.getConfigManager().gui("seller.title", "&0{seller} 的店铺 &8| &7第 {page}/{pages} 页")
                .replace("{seller}", sellerName == null ? sellerId.toString() : sellerName)
                .replace("{page}", String.valueOf(page + 1)).replace("{pages}", String.valueOf(pages))));
        int slot = 0;
        for (Listing listing : listings) inventory.setItem(slot++, listingItem(listing, "buy", "&e点击选择购买数量"));
        nav(inventory, page, pages, "plaza", "seller_prev", "seller_next", count + " 件商品", sort);
        finish(player, holder, inventory);
    }

    public void openBuyQuantity(Player player, long listingId) {
        Listing listing = repository.find(listingId).orElse(null);
        if (listing == null || !listing.active(System.currentTimeMillis())) { plugin.getConfigManager().send(player, "listing-unavailable"); return; }
        MarketHolder holder = holder(MarketHolder.Type.BUY_QUANTITY, player, listing.sellerId(), 0, "", "LATEST", listingId);
        Inventory inventory = inventory(holder, "buy-quantity.title", "&0选择购买数量");
        inventory.setItem(13, listingItem(listing, "container_preview", "&7点击可查看容器内容"));
        int[] amounts = {1,5,10,20,32,64}; int[] slots = {19,20,21,22,23,24};
        for (int i = 0; i < amounts.length; i++) if (amounts[i] <= listing.remainingQuantity()) {
            BigDecimal total = listing.unitPrice().multiply(BigDecimal.valueOf(amounts[i]));
            ItemStack button = items.item(Material.LIME_STAINED_GLASS_PANE, "&a购买 " + amounts[i] + " 个", "buy_amount",
                    "&7总价: &6" + symbol() + money(total));
            inventory.setItem(slots[i], items.intValue(button, amounts[i]));
        }
        inventory.setItem(31, items.item(Material.WRITABLE_BOOK, "&b自定义数量", "buy_custom",
                "&7范围: 1 - " + listing.remainingQuantity()));
        inventory.setItem(45, items.item(Material.ARROW, "&e返回店铺", "seller_back"));
        inventory.setItem(53, items.item(Material.BARRIER, "&c取消", "close"));
        finish(player, holder, inventory);
    }

    public void openBuyConfirm(Player player, long listingId, int quantity) {
        Listing listing = repository.find(listingId).orElse(null);
        if (listing == null || !listing.active(System.currentTimeMillis()) || quantity <= 0 || quantity > listing.remainingQuantity()) {
            plugin.getConfigManager().send(player, "listing-unavailable"); return;
        }
        ItemStack prototype = codec.decode(listing.itemBytes());
        BigDecimal total = listing.unitPrice().multiply(BigDecimal.valueOf(quantity));
        MarketHolder holder = holder(MarketHolder.Type.BUY_CONFIRM, player, listing.sellerId(), 0, "", "LATEST", listingId);
        Inventory inventory = inventory(holder, "buy-confirm.title", "&0确认购买");
        inventory.setItem(13, listingItem(listing, "container_preview", "&7本次购买: &f" + quantity,
                "&7总价: &6" + symbol() + money(total), "&7背包可完整容纳: "
                        + (InventoryUtil.canFit(player.getInventory(), prototype, quantity) ? "&a是" : "&c否")));
        ItemStack confirm = items.item(Material.EMERALD_BLOCK, "&a&l确认购买", "buy_confirm",
                "&7数量: &f" + quantity, "&7总价: &6" + symbol() + money(total), "", "&a点击后立即扣款");
        inventory.setItem(29, items.intValue(confirm, quantity));
        inventory.setItem(33, items.item(Material.BARRIER, "&c返回", "buy_back"));
        finish(player, holder, inventory);
    }

    public void openSellSelect(Player player) {
        MarketHolder holder = holder(MarketHolder.Type.SELL_SELECT, player, null, 0, "", "LATEST", 0);
        Inventory inventory = inventory(holder, "sell-select.title", "&0选择要上架的物品");
        ItemStack[] storage = player.getInventory().getStorageContents();
        int display = 0;
        for (int sourceSlot = 0; sourceSlot < storage.length && display < PAGE_SIZE; sourceSlot++) {
            ItemStack source = storage[sourceSlot];
            if (source == null || source.getType().isAir()) continue;
            ItemStack icon = appendLore(source.clone(), "", "&e点击选择此类物品", "&7背包槽位: &f" + sourceSlot);
            icon = items.intValue(items.action(icon, icon.getItemMeta().getDisplayName(), "sell_select",
                    icon.getItemMeta().hasLore() ? icon.getItemMeta().getLore().toArray(String[]::new) : new String[0]), sourceSlot);
            inventory.setItem(display++, icon);
        }
        inventory.setItem(49, items.item(Material.BARRIER, "&c取消", "home"));
        finish(player, holder, inventory);
    }

    public void openSellQuantity(Player player) {
        MarketInputManager.PartialDraft draft = plugin.getMarketInputManager().partial(player);
        if (draft == null) return;
        int available = Math.min(InventoryUtil.countSimilar(player.getInventory(), draft.prototype()),
                plugin.getConfig().getInt("limits.max-listing-quantity", 2304));
        MarketHolder holder = holder(MarketHolder.Type.SELL_QUANTITY, player, null, 0, "", "LATEST", 0);
        Inventory inventory = inventory(holder, "sell-quantity.title", "&0选择上架数量");
        inventory.setItem(13, items.action(draft.prototype(), "&f" + draft.marketName(), null,
                "&7背包中完全相同: &f" + available));
        int[] amounts = {1,5,10,20,32,64}; int[] slots = {19,20,21,22,23,24};
        for (int i = 0; i < amounts.length; i++) if (amounts[i] <= available) {
            inventory.setItem(slots[i], items.intValue(items.item(Material.LIME_STAINED_GLASS_PANE,
                    "&a上架 " + amounts[i] + " 个", "sell_amount"), amounts[i]));
        }
        inventory.setItem(31, items.item(Material.WRITABLE_BOOK, "&b自定义数量", "sell_custom",
                "&7范围: 1 - " + available));
        inventory.setItem(49, items.item(Material.BARRIER, "&c取消", "cancel_input"));
        finish(player, holder, inventory);
    }

    public void openSellDuration(Player player) {
        MarketInputManager.PartialDraft draft = plugin.getMarketInputManager().partial(player);
        if (draft == null) return;
        MarketHolder holder = holder(MarketHolder.Type.SELL_DURATION, player, null, 0, "", "LATEST", 0);
        Inventory inventory = inventory(holder, "sell-duration.title", "&0选择上架时间");
        inventory.setItem(13, items.action(draft.prototype(), "&f" + draft.marketName(), null,
                "&7数量: &f" + draft.quantity(), "&7单价: &6" + symbol() + money(draft.unitPrice())));
        int max = plugin.getConfig().getInt("market.max-listing-days", 10);
        int[] slots = {19,20,21,22,23,24,25,28,29,30};
        for (int day = 1; day <= Math.min(max, slots.length); day++) inventory.setItem(slots[day - 1],
                items.intValue(items.item(Material.CLOCK, "&e" + day + " 天", "sell_days", "&7到期后自动进入待领取物品"), day));
        inventory.setItem(49, items.item(Material.BARRIER, "&c取消", "cancel_input"));
        finish(player, holder, inventory);
    }

    public void openSellConfirm(Player player) {
        MarketService.ListingDraft draft = plugin.getMarketInputManager().draft(player);
        if (draft == null) return;
        MarketHolder holder = holder(MarketHolder.Type.SELL_CONFIRM, player, null, 0, "", "LATEST", 0);
        Inventory inventory = inventory(holder, "sell-confirm.title", "&0确认上架");
        inventory.setItem(13, items.action(draft.prototype(), "&f" + draft.marketName(), null,
                "&7数量: &f" + draft.quantity(), "&7单价: &6" + symbol() + money(draft.unitPrice()),
                "&7总价值: &6" + symbol() + money(draft.unitPrice().multiply(BigDecimal.valueOf(draft.quantity()))),
                "&7上架时间: &f" + draft.days() + " 天"));
        inventory.setItem(29, items.item(Material.EMERALD_BLOCK, "&a&l确认上架", "sell_confirm",
                "&7点击后将从背包扣除准确数量"));
        inventory.setItem(33, items.item(Material.BARRIER, "&c取消", "cancel_input"));
        finish(player, holder, inventory);
    }

    public void openMine(Player player, int requestedPage) {
        int count = repository.mineCount(player.getUniqueId()); int pages = pages(count); int page = clamp(requestedPage, pages);
        List<Listing> rows = repository.mine(player.getUniqueId(), PAGE_SIZE, page * PAGE_SIZE);
        MarketHolder holder = holder(MarketHolder.Type.MINE, player, player.getUniqueId(), page, "", "LATEST", 0);
        Inventory inventory = inventory(holder, title("mine.title", "&0我的上架 &8| &7第 {page}/{pages} 页", page, pages));
        int slot = 0; for (Listing listing : rows) inventory.setItem(slot++, listingItem(listing, "manage", "&e点击管理"));
        nav(inventory, page, pages, "home", "mine_prev", "mine_next", count + " 条记录", "");
        inventory.setItem(51, items.item(Material.LAVA_BUCKET, "&c清除已下架商品", "mine_clear_confirm",
                "&7清除下架、到期、售罄等记录", "&7仍在托管的剩余物品会进入邮箱"));
        finish(player, holder, inventory);
    }

    public void openMineClearConfirm(Player player) {
        MarketHolder holder = holder(MarketHolder.Type.MINE_CLEAR, player, player.getUniqueId(), 0, "", "LATEST", 0);
        Inventory inventory = inventory(holder, "mine-clear.title", "&0确认清除已下架商品");
        inventory.setItem(22, items.item(Material.LAVA_BUCKET, "&c&l确认清除", "mine_clear",
                "&7不会删除活跃、草稿或复核中的商品", "&7托管中的剩余物品会安全转入邮箱"));
        inventory.setItem(31, items.item(Material.ARROW, "&e返回", "mine"));
        finish(player, holder, inventory);
    }

    public void openManage(Player player, long listingId) {
        Listing listing = repository.find(listingId).orElse(null);
        if (listing == null || !listing.sellerId().equals(player.getUniqueId())) return;
        MarketHolder holder = holder(MarketHolder.Type.MANAGE, player, player.getUniqueId(), 0, "", "LATEST", listingId);
        Inventory inventory = inventory(holder, plugin.getConfigManager().gui("manage.title", "&0管理商品 #{listing_id}")
                .replace("{listing_id}", String.valueOf(listingId)));
        inventory.setItem(13, listingItem(listing, "container_preview", "&7点击查看容器内容"));
        if ("ACTIVE".equals(listing.status())) {
            inventory.setItem(28, items.longValue(items.item(Material.RED_CONCRETE, "&c&l下架商品", "listing_cancel_confirm",
                    "&7下架后商品停止展示", "&7物品继续安全托管，可补货或重新上架"), listingId));
            inventory.setItem(30, items.longValue(items.item(Material.CHEST, "&a补货", "listing_restock",
                    "&7仅接受与原商品完全相同的物品"), listingId));
        } else if ("CANCELLED".equals(listing.status()) && "HELD".equals(listing.escrowState())) {
            inventory.setItem(28, items.longValue(items.item(Material.LIME_CONCRETE, "&a重新上架", "listing_relist",
                    "&7价格保持不变，重新选择 1-10 天"), listingId));
            inventory.setItem(30, items.longValue(items.item(Material.CHEST, "&a补货", "listing_restock",
                    "&7补货后仍保持下架状态"), listingId));
            inventory.setItem(32, items.longValue(items.item(Material.LAVA_BUCKET, "&c删除记录并返还物品", "listing_delete_confirm",
                    "&7剩余托管物品将进入待领取邮箱"), listingId));
        } else if (!"DRAFT".equals(listing.status()) && !"REVIEW".equals(listing.status())) {
            inventory.setItem(32, items.longValue(items.item(Material.LAVA_BUCKET, "&c删除下架记录", "listing_delete_confirm",
                    "&7已返还的物品不会重复返还"), listingId));
        }
        inventory.setItem(40, items.item(Material.ARROW, "&e返回我的上架", "mine"));
        finish(player, holder, inventory);
    }

    public void openCancelListingConfirm(Player player, long listingId) {
        Listing listing = repository.find(listingId).orElse(null);
        if (listing == null || !listing.sellerId().equals(player.getUniqueId())) return;
        MarketHolder holder = holder(MarketHolder.Type.LISTING_CANCEL, player, player.getUniqueId(), 0, "", "LATEST", listingId);
        Inventory inventory = inventory(holder, plugin.getConfigManager().gui("listing-cancel.title", "&0确认下架商品 #{listing_id}")
                .replace("{listing_id}", String.valueOf(listingId)));
        inventory.setItem(13, listingItem(listing, null, "&7下架后可补货、重新上架或删除返还"));
        inventory.setItem(29, items.longValue(items.item(Material.RED_CONCRETE, "&c&l确认下架", "listing_cancel"), listingId));
        inventory.setItem(33, items.longValue(items.item(Material.ARROW, "&e返回", "manage"), listingId));
        finish(player, holder, inventory);
    }

    public void openDeleteListingConfirm(Player player, long listingId) {
        Listing listing = repository.find(listingId).orElse(null);
        if (listing == null || !listing.sellerId().equals(player.getUniqueId())) return;
        MarketHolder holder = holder(MarketHolder.Type.LISTING_DELETE, player, player.getUniqueId(), 0, "", "LATEST", listingId);
        Inventory inventory = inventory(holder, plugin.getConfigManager().gui("listing-delete.title", "&0确认删除商品 #{listing_id}")
                .replace("{listing_id}", String.valueOf(listingId)));
        inventory.setItem(13, listingItem(listing, null, "&7托管中的剩余物品将进入邮箱", "&7已返还物品不会重复返还"));
        inventory.setItem(29, items.longValue(items.item(Material.LAVA_BUCKET, "&c&l确认删除", "listing_delete"), listingId));
        inventory.setItem(33, items.longValue(items.item(Material.ARROW, "&e返回", "manage"), listingId));
        finish(player, holder, inventory);
    }

    public void openRestock(Player player, long listingId) {
        Listing listing = repository.find(listingId).orElse(null);
        if (listing == null || !listing.sellerId().equals(player.getUniqueId())) return;
        ItemStack prototype = codec.decode(listing.itemBytes());
        int available = InventoryUtil.countSimilar(player.getInventory(), prototype);
        MarketHolder holder = holder(MarketHolder.Type.RESTOCK, player, player.getUniqueId(), 0, "", "LATEST", listingId);
        Inventory inventory = inventory(holder, plugin.getConfigManager().gui("restock.title", "&0补货商品 #{listing_id}")
                .replace("{listing_id}", String.valueOf(listingId)));
        inventory.setItem(13, listingItem(listing, null, "&7背包中完全相同: &f" + available));
        int[] amounts = {1, 5, 10, 20, 32, 64}; int[] slots = {19, 20, 21, 22, 23, 24};
        for (int i = 0; i < amounts.length; i++) if (amounts[i] <= available) inventory.setItem(slots[i],
                items.intValue(items.item(Material.LIME_STAINED_GLASS_PANE, "&a补货 " + amounts[i] + " 个", "restock_amount"), amounts[i]));
        inventory.setItem(31, items.item(Material.WRITABLE_BOOK, "&b自定义数量", "restock_custom",
                "&7当前可用: &f" + available));
        inventory.setItem(45, items.longValue(items.item(Material.ARROW, "&e返回", "manage"), listingId));
        finish(player, holder, inventory);
    }

    public void openRelistDuration(Player player, long listingId) {
        Listing listing = repository.find(listingId).orElse(null);
        if (listing == null || !listing.sellerId().equals(player.getUniqueId())) return;
        MarketHolder holder = holder(MarketHolder.Type.RELIST_DURATION, player, player.getUniqueId(), 0, "", "LATEST", listingId);
        Inventory inventory = inventory(holder, plugin.getConfigManager().gui("relist-duration.title", "&0重新上架 #{listing_id}")
                .replace("{listing_id}", String.valueOf(listingId)));
        inventory.setItem(13, listingItem(listing, null, "&7请选择新的上架时间"));
        int max = plugin.getConfig().getInt("market.max-listing-days", 10);
        int[] slots = {19,20,21,22,23,24,25,28,29,30};
        for (int day = 1; day <= Math.min(max, slots.length); day++) inventory.setItem(slots[day - 1],
                items.intValue(items.item(Material.CLOCK, "&e重新上架 " + day + " 天", "relist_days"), day));
        inventory.setItem(45, items.longValue(items.item(Material.ARROW, "&e返回", "manage"), listingId));
        finish(player, holder, inventory);
    }

    public void openMailbox(Player player, int requestedPage) {
        int count = repository.mailboxCount(player.getUniqueId()); int pages = pages(count); int page = clamp(requestedPage, pages);
        List<MailboxEntry> rows = repository.mailbox(player.getUniqueId(), PAGE_SIZE, page * PAGE_SIZE);
        MarketHolder holder = holder(MarketHolder.Type.MAILBOX, player, player.getUniqueId(), page, "", "LATEST", 0);
        Inventory inventory = inventory(holder, title("mailbox.title", "&0待领取物品 &8| &7第 {page}/{pages} 页", page, pages));
        int slot = 0;
        for (MailboxEntry entry : rows) {
            ItemStack prototype = codec.decode(entry.itemBytes());
            ItemStack icon = items.action(prototype, displayName(prototype), "mail_claim",
                    "&7数量: &f" + entry.quantity(), "&7来源: &f" + MarketStatus.reason(entry.reason()),
                    "&7商品编号: &f" + (entry.listingId() == null ? "-" : "#" + entry.listingId()), "", "&e点击领取可容纳数量");
            inventory.setItem(slot++, items.longValue(icon, entry.id()));
        }
        nav(inventory, page, pages, "home", "mail_prev", "mail_next", count + " 条待领取", "");
        inventory.setItem(50, items.item(Material.CHEST, "&a全部领取", "mail_claim_all"));
        finish(player, holder, inventory);
    }

    public void openEarnings(Player player) {
        List<Payout> rows = repository.payouts(player.getUniqueId(), "");
        MarketHolder holder = holder(MarketHolder.Type.EARNINGS, player, player.getUniqueId(), 0, "", "LATEST", 0);
        Inventory inventory = inventory(holder, "earnings.title", "&0货款保障");
        BigDecimal pending = rows.stream().filter(row -> "PENDING".equals(row.state())).map(Payout::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        inventory.setItem(13, items.item(Material.EMERALD, "&a保障中货款: &f" + symbol() + money(pending), null,
                "&7当前余额: &f" + symbol() + money(plugin.getEconomyGateway().balance(player)),
                "&7正常成交的货款会自动进入余额",
                "&7这里只保留离线、失败或补偿款项",
                "&7复核中记录: &f" + rows.stream().filter(row -> "REVIEW".equals(row.state())).count()));
        inventory.setItem(31, items.item(Material.EMERALD_BLOCK, "&a&l领取全部货款", "earnings_claim"));
        inventory.setItem(45, items.item(Material.ARROW, "&e返回", "home"));
        finish(player, holder, inventory);
    }

    public void openHistory(Player player, int requestedPage) {
        int count = repository.historyCount(player.getUniqueId()); int pages = pages(count); int page = clamp(requestedPage, pages);
        List<SaleRecord> rows = repository.history(player.getUniqueId(), PAGE_SIZE, page * PAGE_SIZE);
        MarketHolder holder = holder(MarketHolder.Type.HISTORY, player, player.getUniqueId(), page, "", "LATEST", 0);
        Inventory inventory = inventory(holder, title("history.title", "&0交易记录 &8| &7第 {page}/{pages} 页", page, pages));
        int slot = 0;
        for (SaleRecord sale : rows) {
            boolean buyer = sale.buyerId().equals(player.getUniqueId());
            inventory.setItem(slot++, items.item(buyer ? Material.PAPER : Material.MAP,
                    (buyer ? "&a购买 " : "&6售出 ") + sale.marketName(), null,
                    "&7交易编号: &f#" + sale.id(), "&7商品编号: &f#" + sale.listingId(),
                    "&7数量: &f" + sale.quantity(), "&7总价: &6" + symbol() + money(sale.total()),
                    "&7状态: &f" + MarketStatus.sale(sale.state()), "&7时间: &f" + ago(sale.createdAt())));
        }
        nav(inventory, page, pages, "home", "history_prev", "history_next", count + " 条记录", "");
        inventory.setItem(51, items.item(Material.LAVA_BUCKET, "&c清除交易记录", "history_clear_confirm",
                "&7只从你的界面隐藏记录", "&7不会删除审计数据或对方记录"));
        finish(player, holder, inventory);
    }

    public void openHistoryClearConfirm(Player player) {
        MarketHolder holder = holder(MarketHolder.Type.HISTORY_CLEAR, player, player.getUniqueId(), 0, "", "LATEST", 0);
        Inventory inventory = inventory(holder, "history-clear.title", "&0确认清除交易记录");
        inventory.setItem(22, items.item(Material.LAVA_BUCKET, "&c&l确认清除", "history_clear",
                "&7记录仅对你隐藏，服务器审计记录仍会保留"));
        inventory.setItem(31, items.item(Material.ARROW, "&e返回", "history"));
        finish(player, holder, inventory);
    }

    public void openContainer(Player player, long listingId, String backAction) {
        Listing listing = repository.find(listingId).orElse(null); if (listing == null) return;
        ItemStack prototype = codec.decode(listing.itemBytes());
        if (!(prototype.getItemMeta() instanceof BlockStateMeta meta) || !(meta.getBlockState() instanceof ShulkerBox box)) return;
        MarketHolder holder = holder(MarketHolder.Type.CONTAINER, player, listing.sellerId(), 0, backAction, "LATEST", listingId);
        Inventory inventory = inventory(holder, plugin.getConfigManager().gui("container.title", "&0只读容器预览 &8| &7#{listing_id}")
                .replace("{listing_id}", String.valueOf(listingId)));
        ItemStack[] contents = box.getInventory().getContents();
        for (int i = 0; i < Math.min(contents.length, 45); i++) if (contents[i] != null) inventory.setItem(i, contents[i].clone());
        inventory.setItem(49, items.item(Material.ARROW, "&e返回", "container_back"));
        finish(player, holder, inventory);
    }

    private ItemStack listingItem(Listing listing, String action, String... extra) {
        ItemStack prototype = codec.decode(listing.itemBytes());
        List<String> lore = new ArrayList<>();
        if (prototype.hasItemMeta() && prototype.getItemMeta().hasLore()) lore.addAll(prototype.getItemMeta().getLore());
        lore.add(""); lore.add(Text.color("&6市场商品: &f" + listing.marketName()));
        lore.add(Text.color("&7卖家: &f" + listing.sellerName()));
        lore.add(Text.color("&7剩余数量: &f" + listing.remainingQuantity() + " / " + listing.initialQuantity()));
        lore.add(Text.color("&7单价: &a" + symbol() + money(listing.unitPrice())));
        lore.add(Text.color("&7剩余时间: &f" + Text.duration(listing.expiresAt() - System.currentTimeMillis())));
        lore.add(Text.color("&7状态: &f" + MarketStatus.listing(listing.status())));
        lore.add(Text.color("&7商品编号: &f#" + listing.id()));
        for (String line : extra) lore.add(Text.color(line));
        ItemStack icon = items.action(prototype, "&f" + listing.marketName(), action, lore.toArray(String[]::new));
        return items.longValue(icon, listing.id());
    }

    private ItemStack appendLore(ItemStack stack, String... lines) {
        ItemMeta meta = stack.getItemMeta(); if (meta == null) return stack;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        for (String line : lines) lore.add(Text.color(line)); meta.setLore(lore); stack.setItemMeta(meta); return stack;
    }

    private void nav(Inventory inventory, int page, int pages, String homeAction, String previous, String next,
                     String count, String sort) {
        if (page > 0) inventory.setItem(45, items.item(Material.ARROW, "&a上一页", previous));
        if (sort != null && !sort.isBlank()) inventory.setItem(47, items.item(Material.HOPPER,
                "&b切换排序", "sort_next", "&7当前: &f" + sort));
        inventory.setItem(48, items.item(Material.NETHER_STAR, "&e返回", homeAction));
        inventory.setItem(49, items.item(Material.PAPER, "&f第 " + (page + 1) + "/" + pages + " 页", null,
                "&7" + count, sort == null || sort.isBlank() ? "" : "&7排序: &f" + sort));
        inventory.setItem(50, items.item(Material.BARRIER, "&c关闭", "close"));
        if (page + 1 < pages) inventory.setItem(53, items.item(Material.ARROW, "&a下一页", next));
    }

    private MarketHolder holder(MarketHolder.Type type, Player viewer, UUID subject, int page, String search, String sort, long listingId) {
        return new MarketHolder(type, viewer.getUniqueId(), subject, page, search, sort, listingId);
    }

    private Inventory inventory(MarketHolder holder, String configPath, String fallback) {
        return inventory(holder, plugin.getConfigManager().gui(configPath, fallback));
    }
    private Inventory inventory(MarketHolder holder, String title) {
        Inventory inventory = Bukkit.createInventory(holder, 54, title); holder.inventory(inventory); return inventory;
    }
    private void finish(Player player, MarketHolder holder, Inventory inventory) {
        String page = GuiConfigManager.pageId(holder.type());
        Map<String, String> values = Map.of(
                "{listing_id}", String.valueOf(holder.listingId()),
                "{page}", String.valueOf(holder.page() + 1),
                "{seller_uuid}", holder.subject() == null ? "" : holder.subject().toString());
        plugin.getGuiConfigManager().apply(page, inventory, player, items, values);
        plugin.getGuiConfigManager().fill(page, inventory, items);
        player.openInventory(inventory);
    }
    private String title(String path, String fallback, int page, int pages) {
        return plugin.getConfigManager().gui(path, fallback).replace("{page}", String.valueOf(page + 1)).replace("{pages}", String.valueOf(pages));
    }
    private int pages(int count) { return Math.max(1, (int)Math.ceil(count / (double)PAGE_SIZE)); }
    private int clamp(int page, int pages) { return Math.max(0, Math.min(page, pages - 1)); }
    private String symbol() { return plugin.getConfig().getString("economy.currency-symbol", "$"); }
    private String money(BigDecimal value) { return Text.money(value, plugin.getConfig().getInt("economy.decimal-places", 2)); }
    private String ago(long timestamp) { long delta = System.currentTimeMillis() - timestamp; return delta < 60_000 ? "刚刚" : Text.duration(delta) + "前"; }
    private String displayName(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName()
                : "&f" + plugin.getItemNameService().displayName(item);
    }
}
