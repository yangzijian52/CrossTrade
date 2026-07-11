package com.crosstrade.market.service;

import com.crosstrade.CrossTrade;
import com.crosstrade.economy.EconomyGateway;
import com.crosstrade.market.model.Listing;
import com.crosstrade.market.model.SaleRecord;
import com.crosstrade.market.repository.MarketRepository;
import com.crosstrade.util.InventoryUtil;
import com.crosstrade.util.ItemCodec;
import com.crosstrade.util.Money;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MarketService {
    private final CrossTrade plugin;
    private final MarketRepository repository;
    private final ItemCodec codec;
    private final EconomyGateway economy;
    private final MailboxService mailbox;
    private final PayoutService payouts;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();
    private final Map<Long, Object> listingLocks = new ConcurrentHashMap<>();

    public MarketService(CrossTrade plugin, MarketRepository repository, ItemCodec codec, EconomyGateway economy,
                         MailboxService mailbox, PayoutService payouts) {
        this.plugin = plugin;
        this.repository = repository;
        this.codec = codec;
        this.economy = economy;
        this.mailbox = mailbox;
        this.payouts = payouts;
    }

    public Result createListing(Player seller, ListingDraft draft) {
        requireMainThread();
        if (!plugin.marketAvailable()) return Result.fail("database-unavailable");
        if (!seller.hasPermission("crosstrade.market.sell")) return Result.fail("no-permission");
        String violation = validateDraft(seller, draft);
        if (violation != null) return Result.failRaw(violation);
        int limit = maxListings(seller);
        if (repository.activeCount(seller.getUniqueId(), System.currentTimeMillis()) >= limit) {
            return Result.failRaw("你已经达到 " + limit + " 个有效上架商品的限制。");
        }
        if (InventoryUtil.countSimilar(seller.getInventory(), draft.prototype()) < draft.quantity()) {
            return Result.failRaw("背包中的同类物品数量已经发生变化，请重新选择。");
        }
        byte[] bytes = codec.encodePrototype(draft.prototype());
        long now = System.currentTimeMillis();
        long expires = now + draft.days() * 86_400_000L;
        BigDecimal fee = configuredMoney("market.listing-fee", BigDecimal.ZERO);
        long listingId = 0L;
        boolean removed = false;
        boolean feeCharged = false;
        try {
            listingId = repository.createDraft(seller.getUniqueId(), seller.getName(), draft.marketName(), bytes,
                    codec.fingerprint(draft.prototype()), draft.quantity(), draft.unitPrice(), now, expires);
            if (!InventoryUtil.removeSimilar(seller.getInventory(), draft.prototype(), draft.quantity())) {
                repository.deleteDraft(listingId);
                return Result.failRaw("扣除上架物品失败，请重新操作。");
            }
            removed = true;
            if (fee.signum() > 0) {
                EconomyGateway.Result charged = economy.withdraw(seller, fee);
                if (!charged.success()) {
                    safeReturn(seller, draft.prototype(), draft.quantity(), "LISTING_FEE_FAILED", listingId);
                    removed = false;
                    repository.deleteDraft(listingId);
                    return Result.failRaw("上架费扣除失败：" + charged.error());
                }
                feeCharged = true;
            }
            if (!repository.activate(listingId)) throw new IllegalStateException("无法激活商品草稿");
        } catch (RuntimeException exception) {
            if (removed) safeReturn(seller, draft.prototype(), draft.quantity(), "LISTING_DATABASE_FAILED", listingId == 0 ? null : listingId);
            if (feeCharged) {
                EconomyGateway.Result refund = economy.deposit(seller, fee);
                if (!refund.success()) repository.addPayout(seller.getUniqueId(), fee, "上架失败后的费用退款");
            }
            if (listingId != 0) try { repository.deleteDraft(listingId); } catch (RuntimeException ignored) {}
            plugin.getLogger().severe("创建市场商品失败: " + exception.getMessage());
            return Result.failRaw("商品写入数据库失败，已执行安全返还流程。");
        }
        plugin.getAuditLog().write("LISTING_CREATED", "id=" + listingId + " seller=" + seller.getUniqueId()
                + " quantity=" + draft.quantity() + " unit=" + draft.unitPrice() + " expires=" + expires);
        return Result.ok(listingId);
    }

    public Result purchase(Player buyer, long listingId, int quantity) {
        requireMainThread();
        if (!processing.add(buyer.getUniqueId())) return Result.fail("purchase-processing");
        try {
            synchronized (listingLocks.computeIfAbsent(listingId, ignored -> new Object())) {
                Listing listing = repository.find(listingId).orElse(null);
                long now = System.currentTimeMillis();
                if (listing == null || !listing.active(now) || quantity <= 0 || quantity > listing.remainingQuantity()) {
                    return Result.fail("listing-unavailable");
                }
                if (!buyer.hasPermission("crosstrade.market.buy")) return Result.fail("no-permission");
                if (!plugin.getConfig().getBoolean("market.allow-own-purchase", false)
                        && listing.sellerId().equals(buyer.getUniqueId())) return Result.fail("cannot-buy-own");
                if (!economy.available()) return Result.fail("economy-unavailable");
                ItemStack prototype = codec.decode(listing.itemBytes());
                if (!InventoryUtil.canFit(buyer.getInventory(), prototype, quantity)) return Result.fail("inventory-full");
                BigDecimal total = listing.unitPrice().multiply(BigDecimal.valueOf(quantity)).setScale(scale(), RoundingMode.HALF_UP);
                if (!economy.has(buyer, total)) return Result.fail("insufficient-balance");
                MarketRepository.Reservation reservation = repository.reserve(listingId, quantity, listing.version(),
                        buyer.getUniqueId(), now);
                if (reservation == null) return Result.fail("listing-unavailable");
                if (!repository.changeSaleState(reservation.saleId(), "PREPARED", "WITHDRAWING", null)) {
                    repository.rollbackReservation(reservation, "无法进入扣款状态");
                    return Result.failRaw("无法锁定交易状态，请重试。");
                }
                EconomyGateway.Result withdrawal = economy.withdraw(buyer, total);
                if (!withdrawal.success()) {
                    repository.rollbackReservation(reservation, withdrawal.error());
                    return Result.failRaw("扣款失败：" + withdrawal.error());
                }
                if (!repository.changeSaleState(reservation.saleId(), "WITHDRAWING", "DEBITED", null)) {
                    EconomyGateway.Result refund = economy.deposit(buyer, total);
                    if (refund.success()) repository.rollbackReservation(reservation, "扣款后状态写入失败，已退款");
                    else {
                        repository.markSaleReview(reservation.saleId(), "状态写入失败且退款失败: " + refund.error());
                        repository.addRefund(reservation.saleId(), buyer.getUniqueId(), total, refund.error());
                    }
                    return Result.review(reservation.saleId());
                }
                ItemStack[] snapshot = InventoryUtil.cloneItems(buyer.getInventory().getStorageContents());
                List<ItemStack> leftovers = InventoryUtil.add(buyer.getInventory(), prototype, quantity);
                if (!leftovers.isEmpty()) {
                    buyer.getInventory().setStorageContents(snapshot);
                    EconomyGateway.Result refund = economy.deposit(buyer, total);
                    if (refund.success()) repository.rollbackDebited(reservation, "发放失败，已退款");
                    else {
                        repository.markSaleReview(reservation.saleId(), "发放失败且退款失败: " + refund.error());
                        repository.addRefund(reservation.saleId(), buyer.getUniqueId(), total, refund.error());
                    }
                    return refund.success() ? Result.failRaw("物品发放失败，款项已退回。") : Result.review(reservation.saleId());
                }
                BigDecimal taxPercent = configuredMoney("market.transaction-tax-percent", BigDecimal.ZERO)
                        .max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
                BigDecimal taxRate = taxPercent
                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
                BigDecimal sellerAmount = total.multiply(BigDecimal.ONE.subtract(taxRate)).setScale(scale(), RoundingMode.HALF_UP);
                repository.completeSale(reservation, sellerAmount, System.currentTimeMillis());
                plugin.getAuditLog().write("PURCHASE_COMPLETE", "sale=" + reservation.saleId() + " listing=" + listingId
                        + " buyer=" + buyer.getUniqueId() + " seller=" + listing.sellerId() + " quantity=" + quantity
                        + " total=" + total + " payout=" + sellerAmount);
                payouts.attemptOnline(listing.sellerId());
                notifySellerPurchase(buyer, listing, reservation.saleId(), quantity, total);
                return Result.purchase(reservation.saleId(), total, listing.marketName(), quantity);
            }
        } finally {
            processing.remove(buyer.getUniqueId());
        }
    }

    public boolean cancelListing(Player actor, long listingId, boolean admin) {
        requireMainThread();
        synchronized (listingLocks.computeIfAbsent(listingId, ignored -> new Object())) {
            boolean result = repository.cancelListing(listingId, actor.getUniqueId(), admin,
                    admin ? "ADMIN_REMOVED" : "SELLER_CANCELLED");
            if (result) plugin.getAuditLog().write("LISTING_CANCELLED", "id=" + listingId + " actor=" + actor.getUniqueId() + " admin=" + admin);
            return result;
        }
    }

    public Result restock(Player owner, long listingId, int quantity) {
        requireMainThread();
        if (quantity <= 0) return Result.failRaw("补货数量必须大于 0。");
        synchronized (listingLocks.computeIfAbsent(listingId, ignored -> new Object())) {
            Listing listing = repository.find(listingId).orElse(null);
            if (listing == null || !listing.sellerId().equals(owner.getUniqueId())
                    || !("ACTIVE".equals(listing.status()) || "CANCELLED".equals(listing.status()))
                    || !"HELD".equals(listing.escrowState())) return Result.fail("listing-unavailable");
            int maximum = plugin.getConfig().getInt("limits.max-listing-quantity", 2304);
            if ((long) listing.remainingQuantity() + quantity > maximum) {
                return Result.failRaw("补货后剩余数量不能超过 " + maximum + "。");
            }
            ItemStack prototype = codec.decode(listing.itemBytes());
            if (InventoryUtil.countSimilar(owner.getInventory(), prototype) < quantity) {
                return Result.failRaw("背包中完全相同的物品数量不足。");
            }
            if (!InventoryUtil.removeSimilar(owner.getInventory(), prototype, quantity)) {
                return Result.failRaw("扣除补货物品失败，请重试。");
            }
            if (!repository.restock(listingId, owner.getUniqueId(), quantity)) {
                safeReturn(owner, prototype, quantity, "RESTOCK_FAILED", listingId);
                return Result.failRaw("商品状态已变化，补货物品已安全返还。");
            }
            plugin.getAuditLog().write("LISTING_RESTOCKED", "id=" + listingId + " owner=" + owner.getUniqueId()
                    + " quantity=" + quantity);
            return Result.ok(listingId);
        }
    }

    public boolean relist(Player owner, long listingId, int days) {
        requireMainThread();
        int maximumDays = plugin.getConfig().getInt("market.max-listing-days", 10);
        if (days < 1 || days > maximumDays) return false;
        synchronized (listingLocks.computeIfAbsent(listingId, ignored -> new Object())) {
            Listing listing = repository.find(listingId).orElse(null);
            if (listing == null || !listing.sellerId().equals(owner.getUniqueId())
                    || !"CANCELLED".equals(listing.status()) || !"HELD".equals(listing.escrowState())
                    || listing.remainingQuantity() <= 0) return false;
            if (repository.activeCount(owner.getUniqueId(), System.currentTimeMillis()) >= maxListings(owner)) return false;
            long now = System.currentTimeMillis();
            boolean result = repository.relist(listingId, owner.getUniqueId(), now, now + days * 86_400_000L);
            if (result) plugin.getAuditLog().write("LISTING_RELISTED", "id=" + listingId + " owner="
                    + owner.getUniqueId() + " days=" + days);
            return result;
        }
    }

    public boolean deleteListing(Player owner, long listingId) {
        requireMainThread();
        synchronized (listingLocks.computeIfAbsent(listingId, ignored -> new Object())) {
            boolean result = repository.deleteListing(listingId, owner.getUniqueId());
            if (result) plugin.getAuditLog().write("LISTING_DELETED", "id=" + listingId + " owner=" + owner.getUniqueId());
            return result;
        }
    }

    public int clearInactive(Player owner) {
        requireMainThread();
        int changed = repository.clearInactive(owner.getUniqueId());
        if (changed > 0) plugin.getAuditLog().write("LISTINGS_CLEARED", "owner=" + owner.getUniqueId() + " count=" + changed);
        return changed;
    }

    private void notifySellerPurchase(Player buyer, Listing listing, long saleId, int quantity, BigDecimal total) {
        Player seller = plugin.getServer().getPlayer(listing.sellerId());
        if (seller == null || !seller.isOnline()) return;
        sendSellerNotification(seller, buyer.getName(), listing.marketName(), quantity, total);
        repository.markSellerNotified(saleId);
    }

    public void notifyPendingSales(Player seller) {
        for (SaleRecord sale : repository.unnotifiedSales(seller.getUniqueId(), 50)) {
            String buyerName = java.util.Optional.ofNullable(plugin.getServer().getOfflinePlayer(sale.buyerId()).getName())
                    .orElse(sale.buyerId().toString());
            sendSellerNotification(seller, buyerName, sale.marketName(), sale.quantity(), sale.total());
            repository.markSellerNotified(sale.id());
        }
    }

    private void sendSellerNotification(Player seller, String buyerName, String itemName, int quantity, BigDecimal total) {
        plugin.getConfigManager().send(seller, "seller-sale-notification", Map.of(
                "{buyer}", buyerName,
                "{item}", itemName,
                "{amount}", String.valueOf(quantity),
                "{symbol}", plugin.getConfig().getString("economy.currency-symbol", "$"),
                "{total}", com.crosstrade.util.Text.money(total, scale())));
    }

    public int expire() {
        List<Listing> expired = repository.expireListings(System.currentTimeMillis());
        int count = expired.size();
        if (plugin.getConfig().getBoolean("market.auto-return-expired-items", true)) {
            expired.stream().map(Listing::sellerId).distinct().forEach(owner -> {
                Player online = plugin.getServer().getPlayer(owner);
                if (online != null && online.isOnline()) {
                    int returned = mailbox.claimAll(online);
                    if (returned > 0) plugin.getConfigManager().send(online, "mailbox-claimed",
                            Map.of("{amount}", String.valueOf(returned)));
                }
            });
        }
        if (count > 0) plugin.getAuditLog().write("EXPIRE_SCAN", "expired=" + count);
        return count;
    }

    public void safeReturn(Player player, ItemStack prototype, int quantity, String reason, Long listingId) {
        if (InventoryUtil.canFit(player.getInventory(), prototype, quantity)) {
            List<ItemStack> leftovers = InventoryUtil.add(player.getInventory(), prototype, quantity);
            int failed = leftovers.stream().mapToInt(ItemStack::getAmount).sum();
            if (failed > 0) mailbox.store(player, prototype, failed, reason, listingId);
        } else mailbox.store(player, prototype, quantity, reason, listingId);
    }

    private String validateDraft(Player player, ListingDraft draft) {
        if (draft == null || draft.prototype() == null || draft.prototype().getType().isAir()) return "没有选择有效物品。";
        if (draft.days() < 1 || draft.days() > plugin.getConfig().getInt("market.max-listing-days", 10)) return "上架天数必须在允许范围内。";
        if (draft.quantity() < 1 || draft.quantity() > plugin.getConfig().getInt("limits.max-listing-quantity", 2304)) return "上架数量超出限制。";
        int maxName = plugin.getConfig().getInt("limits.max-market-name-length", 32);
        if (draft.marketName() == null || draft.marketName().isBlank() || draft.marketName().codePointCount(0, draft.marketName().length()) > maxName)
            return "商品名不能为空且最多 " + maxName + " 个字符。";
        BigDecimal min = configuredMoney("limits.min-unit-price", new BigDecimal("0.01"));
        BigDecimal max = configuredMoney("limits.max-unit-price", new BigDecimal("1000000000"));
        if (!player.hasPermission("crosstrade.market.bypass.price-limit") && !Money.valid(draft.unitPrice(), min, max))
            return "商品单价必须在 " + min + " 到 " + max + " 之间。";
        if (!player.hasPermission("crosstrade.market.bypass.blacklist")) {
            Set<String> blocked = new HashSet<>();
            for (String value : plugin.getConfig().getStringList("item-rules.blocked-materials")) blocked.add(value.toUpperCase(Locale.ROOT));
            if (blocked.contains(draft.prototype().getType().name())) return "该物品被市场黑名单禁止。";
            ItemMeta itemMeta = draft.prototype().getItemMeta();
            if (!plugin.getConfig().getBoolean("item-rules.allow-damaged-items", true)
                    && itemMeta instanceof Damageable damageable && damageable.hasDamage()) return "不允许出售损坏物品。";
            if (!plugin.getConfig().getBoolean("item-rules.allow-container-items", true)
                    && draft.prototype().getItemMeta() instanceof BlockStateMeta) return "不允许出售容器物品。";
            if (!plugin.getConfig().getBoolean("item-rules.allow-filled-shulker-boxes", true)
                    && itemMeta instanceof BlockStateMeta blockStateMeta
                    && blockStateMeta.getBlockState() instanceof ShulkerBox shulker
                    && !shulker.getInventory().isEmpty()) return "不允许出售装有物品的潜影盒。";
            ItemMeta meta = itemMeta;
            if (meta != null) {
                if (!plugin.getConfig().getBoolean("item-rules.allow-custom-items", true)
                        && (meta.hasDisplayName() || meta.hasLore() || meta.hasCustomModelData()
                        || !meta.getPersistentDataContainer().getKeys().isEmpty())) return "不允许出售自定义物品。";
                for (String keyword : plugin.getConfig().getStringList("item-rules.blocked-lore-keywords")) {
                    if (meta.hasLore() && meta.getLore().stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))))
                        return "该物品包含被禁止的 Lore 关键词。";
                }
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                Set<String> blockedNamespaces = new HashSet<>(plugin.getConfig().getStringList("item-rules.blocked-pdc-namespaces"));
                if (pdc.getKeys().stream().anyMatch(key -> blockedNamespaces.contains(key.getNamespace()))) return "该物品包含被禁止的自定义数据。";
            }
        }
        byte[] bytes = codec.encodePrototype(draft.prototype());
        if (bytes.length > plugin.getConfig().getInt("limits.max-serialized-item-bytes", 2_097_152)) return "物品数据过大，无法安全上架。";
        return null;
    }

    public String sanitizeName(String input) {
        if (input == null) return "";
        String value = input.replaceAll("(?i)[&§][0-9A-FK-ORX]", "")
                .replaceAll("<[^>]{1,64}>", "")
                .replaceAll("[\\p{Cntrl}\\r\\n]", " ").trim().replaceAll("\\s{2,}", " ");
        int max = plugin.getConfig().getInt("limits.max-market-name-length", 32);
        int points = value.codePointCount(0, value.length());
        if (points > max) value = value.substring(0, value.offsetByCodePoints(0, max));
        return value;
    }

    private int maxListings(Player player) {
        int result = plugin.getConfig().getInt("market.default-max-listings", 10);
        for (org.bukkit.permissions.PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String prefix = "crosstrade.market.listings.";
            if (info.getValue() && info.getPermission().startsWith(prefix)) {
                try { result = Math.max(result, Integer.parseInt(info.getPermission().substring(prefix.length()))); }
                catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    private BigDecimal configuredMoney(String path, BigDecimal fallback) {
        String raw = plugin.getConfig().getString(path, fallback.toPlainString());
        try { return new BigDecimal(raw); } catch (NumberFormatException exception) { return fallback; }
    }

    private int scale() { return plugin.getConfig().getInt("economy.decimal-places", 2); }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("市场物品和经济操作必须在主线程执行");
    }

    public record ListingDraft(ItemStack prototype, String marketName, int quantity, BigDecimal unitPrice, int days) {}

    public record Result(boolean success, String messageKey, String rawMessage, long id, BigDecimal total,
                         String itemName, int quantity, boolean review) {
        public static Result ok(long id) { return new Result(true, null, null, id, null, null, 0, false); }
        public static Result purchase(long id, BigDecimal total, String item, int quantity) {
            return new Result(true, null, null, id, total, item, quantity, false);
        }
        public static Result fail(String key) { return new Result(false, key, null, 0, null, null, 0, false); }
        public static Result failRaw(String message) { return new Result(false, null, message, 0, null, null, 0, false); }
        public static Result review(long id) { return new Result(false, "unsafe-review", null, id, null, null, 0, true); }
    }
}
