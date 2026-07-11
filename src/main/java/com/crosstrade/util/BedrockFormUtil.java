package com.crosstrade.util;

import com.crosstrade.CrossTrade;
import com.crosstrade.market.model.Listing;
import com.crosstrade.market.model.MailboxEntry;
import com.crosstrade.market.model.MarketStatus;
import com.crosstrade.market.model.SaleRecord;
import com.crosstrade.market.model.SellerSummary;
import com.crosstrade.market.service.MarketService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BedrockFormUtil {
    private final CrossTrade plugin;

    public BedrockFormUtil(CrossTrade plugin) { this.plugin = plugin; }

    public boolean available() {
        return plugin.getServer().getPluginManager().isPluginEnabled("floodgate");
    }

    public boolean isBedrockPlayer(Player player) {
        if (!available()) return false;
        try { return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId()); }
        catch (RuntimeException exception) { return false; }
    }

    public void showTradePlayerSelector(Player player) {
        List<Player> targets = new ArrayList<>();
        for (Player target : plugin.getServer().getOnlinePlayers()) if (!target.equals(player)) targets.add(target);
        targets.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        if (targets.isEmpty()) { player.sendMessage(Text.color("&c当前没有其他在线玩家。")); return; }
        SimpleForm.Builder builder = SimpleForm.builder().title("面对面交易").content("请选择交易玩家");
        for (Player target : targets) builder.button((isBedrockPlayer(target) ? "[基岩] " : "[Java] ") + target.getName());
        builder.validResultHandler(response -> sync(() -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < targets.size()) plugin.getTradeManager().sendTradeRequest(player, targets.get(index));
        }));
        send(player, builder.build());
    }

    public void showTradeRequestForm(Player target, Player sender, UUID requestId) {
        ModalForm form = ModalForm.builder().title("交易请求")
                .content(sender.getName() + " 想与你进行面对面交易。")
                .button1("接受").button2("拒绝")
                .validResultHandler(response -> sync(() -> {
                    if (response.clickedFirst()) {
                        if (!plugin.getTradeManager().acceptTradeRequest(target, requestId)) plugin.getConfigManager().send(target, "no-request");
                    } else plugin.getTradeManager().denyTradeRequest(target);
                })).build();
        send(target, form);
    }

    public void showMarketHome(Player player) {
        SimpleForm form = SimpleForm.builder().title("CrossTrade 交易中心")
                .content("余额: " + symbol() + money(plugin.getEconomyGateway().balance(player)))
                .button("交易广场").button("玩家交易").button("上架新商品").button("我的上架")
                .button("待领取物品").button("货款保障").button("交易记录")
                .validResultHandler(response -> sync(() -> {
                    switch (response.clickedButtonId()) {
                        case 0 -> showPlaza(player, 0, "");
                        case 1 -> showTradePlayerSelector(player);
                        case 2 -> showSellSelect(player);
                        case 3 -> showMine(player);
                        case 4 -> showMailbox(player);
                        case 5 -> showEarnings(player);
                        case 6 -> showHistory(player);
                        default -> {}
                    }
                })).build();
        send(player, form);
    }

    public void showPlaza(Player player, int requestedPage, String search) {
        int size = 20;
        int count = plugin.getMarketRepository().sellerCount(search, System.currentTimeMillis());
        int pages = Math.max(1, (int)Math.ceil(count / (double)size));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        List<SellerSummary> sellers = plugin.getMarketRepository().sellers(search, "LATEST", size, page * size, System.currentTimeMillis());
        SimpleForm.Builder builder = SimpleForm.builder().title("交易广场 " + (page + 1) + "/" + pages)
                .content("共有 " + count + " 位卖家");
        List<String> actions = new ArrayList<>();
        for (SellerSummary seller : sellers) {
            builder.button(seller.sellerName() + "\n" + seller.listings() + " 件商品 | 最低 " + symbol() + money(seller.minimumPrice()));
            actions.add("seller:" + seller.sellerId());
        }
        if (page > 0) { builder.button("上一页"); actions.add("page:" + (page - 1)); }
        if (page + 1 < pages) { builder.button("下一页"); actions.add("page:" + (page + 1)); }
        builder.button("返回交易中心"); actions.add("home");
        builder.validResultHandler(response -> sync(() -> {
            int index = response.clickedButtonId(); if (index < 0 || index >= actions.size()) return;
            String action = actions.get(index);
            if (action.startsWith("seller:")) showSeller(player, UUID.fromString(action.substring(7)), 0);
            else if (action.startsWith("page:")) showPlaza(player, Integer.parseInt(action.substring(5)), search);
            else showMarketHome(player);
        }));
        send(player, builder.build());
    }

    public void showSeller(Player player, UUID seller, int requestedPage) {
        int size = 20;
        int count = plugin.getMarketRepository().activeCountBySeller(seller, "", System.currentTimeMillis());
        int pages = Math.max(1, (int)Math.ceil(count / (double)size)); int page = Math.max(0, Math.min(requestedPage, pages - 1));
        List<Listing> listings = plugin.getMarketRepository().activeBySeller(seller, "", "LATEST", size, page * size, System.currentTimeMillis());
        String name = listings.isEmpty() ? String.valueOf(Bukkit.getOfflinePlayer(seller).getName()) : listings.getFirst().sellerName();
        SimpleForm.Builder builder = SimpleForm.builder().title(name + " 的店铺 " + (page + 1) + "/" + pages)
                .content("选择要购买的商品");
        List<Long> ids = new ArrayList<>();
        for (Listing listing : listings) {
            builder.button(listing.marketName() + "\n" + symbol() + money(listing.unitPrice()) + " | 剩余 " + listing.remainingQuantity());
            ids.add(listing.id());
        }
        builder.button("返回交易广场");
        builder.validResultHandler(response -> sync(() -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < ids.size()) showBuyQuantity(player, ids.get(index));
            else showPlaza(player, 0, "");
        }));
        send(player, builder.build());
    }

    private void showBuyQuantity(Player player, long listingId) {
        Listing listing = plugin.getMarketRepository().find(listingId).orElse(null);
        if (listing == null || !listing.active(System.currentTimeMillis())) { plugin.getConfigManager().send(player, "listing-unavailable"); return; }
        CustomForm.Builder builder = CustomForm.builder().title("选择购买数量")
                .label(listing.marketName() + "\n单价: " + symbol() + money(listing.unitPrice()) + "\n剩余: " + listing.remainingQuantity())
                .input("购买数量", "1 - " + listing.remainingQuantity(), "1");
        builder.validResultHandler(response -> sync(() -> {
            try { showBuyConfirm(player, listingId, Integer.parseInt(response.asInput(1).trim())); }
            catch (RuntimeException exception) { player.sendMessage(Text.color("&c购买数量无效。")); }
        }));
        send(player, builder.build());
    }

    private void showBuyConfirm(Player player, long listingId, int quantity) {
        Listing listing = plugin.getMarketRepository().find(listingId).orElse(null);
        if (listing == null || quantity <= 0 || quantity > listing.remainingQuantity()) { plugin.getConfigManager().send(player, "listing-unavailable"); return; }
        BigDecimal total = listing.unitPrice().multiply(BigDecimal.valueOf(quantity));
        ModalForm form = ModalForm.builder().title("确认购买")
                .content(listing.marketName() + "\n数量: " + quantity + "\n总价: " + symbol() + money(total)
                        + "\n余额: " + symbol() + money(plugin.getEconomyGateway().balance(player)))
                .button1("确认购买").button2("返回")
                .validResultHandler(response -> sync(() -> {
                    if (!response.clickedFirst()) { showBuyQuantity(player, listingId); return; }
                    MarketService.Result result = plugin.getMarketService().purchase(player, listingId, quantity);
                    if (result.success()) plugin.getConfigManager().send(player, "purchase-success", Map.of(
                            "{amount}", String.valueOf(quantity), "{item}", listing.marketName(), "{symbol}", symbol(), "{total}", money(total)));
                    else if (result.messageKey() != null) plugin.getConfigManager().send(player, result.messageKey(), Map.of("{id}", String.valueOf(result.id())));
                    else player.sendMessage(Text.color("&c" + result.rawMessage()));
                })).build();
        send(player, form);
    }

    private void showSellSelect(Player player) {
        SimpleForm.Builder builder = SimpleForm.builder().title("选择上架物品").content("从背包选择一种物品");
        List<Integer> slots = new ArrayList<>(); ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack stack = storage[i]; if (stack == null || stack.getType().isAir()) continue;
            builder.button(plugin.getItemNameService().displayName(stack) + " x" + stack.getAmount()); slots.add(i);
        }
        builder.button("返回");
        builder.validResultHandler(response -> sync(() -> {
            int index = response.clickedButtonId();
            if (index < 0 || index >= slots.size()) { showMarketHome(player); return; }
            ItemStack selected = player.getInventory().getItem(slots.get(index));
            if (selected != null) showSellDetails(player, selected.clone());
        }));
        send(player, builder.build());
    }

    private void showSellDetails(Player player, ItemStack prototype) {
        int available = com.crosstrade.util.InventoryUtil.countSimilar(player.getInventory(), prototype);
        CustomForm.Builder builder = CustomForm.builder().title("填写上架信息")
                .label(plugin.getItemNameService().displayName(prototype) + "\n背包中完全相同: " + available)
                .input("市场商品名", "最多 32 个字符", plugin.getItemNameService().displayName(prototype))
                .input("上架数量", "1 - " + available, "1")
                .input("单价", "例如 10.00", "1.00")
                .input("上架天数", "1 - 10", "1");
        builder.validResultHandler(response -> sync(() -> {
            try {
                String name = plugin.getMarketService().sanitizeName(response.asInput(1));
                int quantity = Integer.parseInt(response.asInput(2).trim());
                BigDecimal price = Money.parse(response.asInput(3), plugin.getConfig().getInt("economy.decimal-places", 2));
                int days = Integer.parseInt(response.asInput(4).trim());
                showSellConfirm(player, new MarketService.ListingDraft(prototype, name, quantity, price, days));
            } catch (RuntimeException exception) { player.sendMessage(Text.color("&c上架信息无效：" + exception.getMessage())); }
        }));
        send(player, builder.build());
    }

    private void showSellConfirm(Player player, MarketService.ListingDraft draft) {
        ModalForm form = ModalForm.builder().title("确认上架")
                .content(draft.marketName() + "\n数量: " + draft.quantity() + "\n单价: " + symbol() + money(draft.unitPrice())
                        + "\n上架: " + draft.days() + " 天")
                .button1("确认上架").button2("取消")
                .validResultHandler(response -> sync(() -> {
                    if (!response.clickedFirst()) { showMarketHome(player); return; }
                    MarketService.Result result = plugin.getMarketService().createListing(player, draft);
                    if (result.success()) plugin.getConfigManager().send(player, "listing-created", Map.of("{id}", String.valueOf(result.id())));
                    else if (result.messageKey() != null) plugin.getConfigManager().send(player, result.messageKey());
                    else player.sendMessage(Text.color("&c" + result.rawMessage()));
                })).build();
        send(player, form);
    }

    private void showMine(Player player) {
        List<Listing> rows = plugin.getMarketRepository().mine(player.getUniqueId(), 50, 0);
        SimpleForm.Builder builder = SimpleForm.builder().title("我的上架").content("点击商品进行下架、补货、重上架或删除");
        List<Long> ids = new ArrayList<>();
        for (Listing listing : rows) { builder.button(listing.marketName() + "\n" + MarketStatus.listing(listing.status()) + " | 剩余 " + listing.remainingQuantity()); ids.add(listing.id()); }
        builder.button("清除所有已下架商品"); ids.add(-1L);
        builder.button("返回"); ids.add(-2L);
        builder.validResultHandler(response -> sync(() -> {
            int index = response.clickedButtonId();
            if (index < 0 || index >= ids.size()) return;
            long id = ids.get(index);
            if (id == -1) showClearInactive(player);
            else if (id == -2) showMarketHome(player);
            else showManageListing(player, id);
        })); send(player, builder.build());
    }

    private void showManageListing(Player player, long id) {
        Listing listing = plugin.getMarketRepository().find(id).orElse(null); if (listing == null) return;
        SimpleForm.Builder builder = SimpleForm.builder().title("管理商品 #" + id)
                .content(listing.marketName() + "\n状态: " + MarketStatus.listing(listing.status()) + "\n剩余: " + listing.remainingQuantity());
        List<String> actions = new ArrayList<>();
        if ("ACTIVE".equals(listing.status())) {
            builder.button("下架商品"); actions.add("cancel");
            builder.button("补货"); actions.add("restock");
        } else if ("CANCELLED".equals(listing.status()) && "HELD".equals(listing.escrowState())) {
            builder.button("重新上架"); actions.add("relist");
            builder.button("补货"); actions.add("restock");
            builder.button("删除并返还剩余物品"); actions.add("delete");
        } else if (!"DRAFT".equals(listing.status()) && !"REVIEW".equals(listing.status())) {
            builder.button("删除下架记录"); actions.add("delete");
        }
        builder.button("返回我的上架"); actions.add("back");
        builder.validResultHandler(response -> sync(() -> {
            int index = response.clickedButtonId(); if (index < 0 || index >= actions.size()) return;
            switch (actions.get(index)) {
                case "cancel" -> showCancelListing(player, id);
                case "restock" -> showRestock(player, id);
                case "relist" -> showRelist(player, id);
                case "delete" -> showDeleteListing(player, id);
                default -> showMine(player);
            }
        })); send(player, builder.build());
    }

    private void showCancelListing(Player player, long id) {
        Listing listing = plugin.getMarketRepository().find(id).orElse(null); if (listing == null) return;
        ModalForm form = ModalForm.builder().title("确认下架").content(listing.marketName() + "\n剩余 " + listing.remainingQuantity())
                .button1("确认下架").button2("返回")
                .validResultHandler(response -> sync(() -> {
                    if (response.clickedFirst() && plugin.getMarketService().cancelListing(player, id, false)) plugin.getConfigManager().send(player, "listing-cancelled");
                    showMine(player);
                })).build(); send(player, form);
    }

    private void showRestock(Player player, long id) {
        Listing listing = plugin.getMarketRepository().find(id).orElse(null); if (listing == null) return;
        ItemStack prototype = plugin.getItemCodec().decode(listing.itemBytes());
        int available = com.crosstrade.util.InventoryUtil.countSimilar(player.getInventory(), prototype);
        CustomForm form = CustomForm.builder().title("补货商品")
                .label(listing.marketName() + "\n背包中完全相同: " + available)
                .input("补货数量", "1 - " + available, "1")
                .validResultHandler(response -> sync(() -> {
                    try {
                        int quantity = Integer.parseInt(response.asInput(1).trim());
                        MarketService.Result result = plugin.getMarketService().restock(player, id, quantity);
                        if (result.success()) plugin.getConfigManager().send(player, "listing-restocked",
                                Map.of("{amount}", String.valueOf(quantity)));
                        else if (result.messageKey() != null) plugin.getConfigManager().send(player, result.messageKey());
                        else player.sendMessage(Text.color("&c" + result.rawMessage()));
                    } catch (RuntimeException exception) { player.sendMessage(Text.color("&c补货数量无效。")); }
                    showManageListing(player, id);
                })).build();
        send(player, form);
    }

    private void showRelist(Player player, long id) {
        CustomForm form = CustomForm.builder().title("重新上架")
                .input("上架天数", "1 - 10", "1")
                .validResultHandler(response -> sync(() -> {
                    try {
                        int days = Integer.parseInt(response.asInput(0).trim());
                        if (plugin.getMarketService().relist(player, id, days)) plugin.getConfigManager().send(player, "listing-relisted");
                        else plugin.getConfigManager().send(player, "listing-unavailable");
                    } catch (RuntimeException exception) { player.sendMessage(Text.color("&c上架天数无效。")); }
                    showManageListing(player, id);
                })).build();
        send(player, form);
    }

    private void showDeleteListing(Player player, long id) {
        ModalForm form = ModalForm.builder().title("确认删除商品")
                .content("删除后记录不再显示，仍在托管的剩余物品会进入待领取邮箱。")
                .button1("确认删除").button2("返回")
                .validResultHandler(response -> sync(() -> {
                    if (response.clickedFirst()) {
                        if (plugin.getMarketService().deleteListing(player, id)) plugin.getConfigManager().send(player, "listing-deleted");
                        else plugin.getConfigManager().send(player, "listing-unavailable");
                        showMine(player);
                    } else showManageListing(player, id);
                })).build();
        send(player, form);
    }

    private void showClearInactive(Player player) {
        ModalForm form = ModalForm.builder().title("确认清除已下架商品")
                .content("只清除下架、到期、售罄和管理员下架记录；托管中的剩余物品会进入邮箱。")
                .button1("确认清除").button2("返回")
                .validResultHandler(response -> sync(() -> {
                    if (response.clickedFirst()) {
                        int changed = plugin.getMarketService().clearInactive(player);
                        plugin.getConfigManager().send(player, "inactive-cleared", Map.of("{amount}", String.valueOf(changed)));
                    }
                    showMine(player);
                })).build();
        send(player, form);
    }

    private void showMailbox(Player player) {
        List<MailboxEntry> rows = plugin.getMarketRepository().mailbox(player.getUniqueId(), 50, 0);
        SimpleForm.Builder builder = SimpleForm.builder().title("待领取物品").content("点击领取背包可容纳的数量");
        List<Long> ids = new ArrayList<>();
        for (MailboxEntry row : rows) { ItemStack item = plugin.getItemCodec().decode(row.itemBytes()); builder.button(plugin.getItemNameService().displayName(item) + " x" + row.quantity() + "\n" + MarketStatus.reason(row.reason())); ids.add(row.id()); }
        builder.button("全部领取"); ids.add(-1L); builder.button("返回"); ids.add(-2L);
        builder.validResultHandler(response -> sync(() -> {
            int index = response.clickedButtonId(); if (index < 0 || index >= ids.size()) return;
            long id = ids.get(index); if (id == -1) plugin.getMailboxService().claimAll(player); else if (id == -2) showMarketHome(player); else plugin.getMailboxService().claim(player, id);
            if (id >= -1) showMailbox(player);
        })); send(player, builder.build());
    }

    private void showEarnings(Player player) {
        BigDecimal pending = plugin.getPayoutService().pending(player.getUniqueId());
        ModalForm form = ModalForm.builder().title("货款保障")
                .content("正常成交会自动到账。\n离线、入账失败或补偿货款: " + symbol() + money(pending))
                .button1("领取全部").button2("返回")
                .validResultHandler(response -> sync(() -> { if (response.clickedFirst()) plugin.getPayoutService().claim(player); showMarketHome(player); })).build();
        send(player, form);
    }

    private void showHistory(Player player) {
        List<SaleRecord> rows = plugin.getMarketRepository().history(player.getUniqueId(), 40, 0);
        SimpleForm.Builder builder = SimpleForm.builder().title("交易记录").content("最近 " + rows.size() + " 条");
        for (SaleRecord row : rows) builder.button(row.marketName() + " x" + row.quantity() + "\n" + symbol() + money(row.total()) + " | " + MarketStatus.sale(row.state()));
        builder.button("清除交易记录");
        builder.button("返回").validResultHandler(response -> sync(() -> {
            int index = response.clickedButtonId();
            if (index == rows.size()) showClearHistory(player); else showMarketHome(player);
        }));
        send(player, builder.build());
    }

    private void showClearHistory(Player player) {
        ModalForm form = ModalForm.builder().title("确认清除交易记录")
                .content("记录只会从你的页面隐藏，不会删除服务器审计数据或对方的记录。")
                .button1("确认清除").button2("返回")
                .validResultHandler(response -> sync(() -> {
                    if (response.clickedFirst()) {
                        int changed = plugin.getMarketRepository().clearHistory(player.getUniqueId());
                        plugin.getConfigManager().send(player, "history-cleared", Map.of("{amount}", String.valueOf(changed)));
                    }
                    showHistory(player);
                })).build();
        send(player, form);
    }

    private void send(Player player, org.geysermc.cumulus.form.Form form) {
        try { FloodgateApi.getInstance().sendForm(player.getUniqueId(), form); }
        catch (RuntimeException exception) { plugin.getLogger().warning("发送基岩表单失败: " + exception.getMessage()); }
    }
    private void sync(Runnable runnable) { plugin.getServer().getScheduler().runTask(plugin, runnable); }
    private String symbol() { return plugin.getConfig().getString("economy.currency-symbol", "$"); }
    private String money(BigDecimal value) { return Text.money(value, plugin.getConfig().getInt("economy.decimal-places", 2)); }
}
