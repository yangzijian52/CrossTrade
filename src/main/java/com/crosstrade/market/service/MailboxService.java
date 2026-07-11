package com.crosstrade.market.service;

import com.crosstrade.CrossTrade;
import com.crosstrade.market.model.MailboxEntry;
import com.crosstrade.market.repository.MarketRepository;
import com.crosstrade.util.InventoryUtil;
import com.crosstrade.util.ItemCodec;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class MailboxService {
    private final CrossTrade plugin;
    private final MarketRepository repository;
    private final ItemCodec codec;

    public MailboxService(CrossTrade plugin, MarketRepository repository, ItemCodec codec) {
        this.plugin = plugin;
        this.repository = repository;
        this.codec = codec;
    }

    public void store(Player player, ItemStack item, int quantity, String reason, Long listingId) {
        store(player.getUniqueId(), item, quantity, reason, listingId);
    }

    public void store(java.util.UUID owner, ItemStack item, int quantity, String reason, Long listingId) {
        repository.addMailbox(owner, listingId, codec.encodePrototype(item), quantity, reason);
        plugin.getAuditLog().write("MAILBOX", owner + " listing=" + listingId + " quantity=" + quantity + " reason=" + reason);
    }

    public int claim(Player player, long entryId) {
        MailboxEntry entry = repository.mailbox(player.getUniqueId(), 10000, 0).stream()
                .filter(row -> row.id() == entryId).findFirst().orElse(null);
        if (entry == null) return 0;
        ItemStack prototype = codec.decode(entry.itemBytes());
        int capacity = capacity(player, prototype, entry.quantity());
        if (capacity <= 0) return 0;
        int amount = Math.min(capacity, entry.quantity());
        List<ItemStack> leftovers = InventoryUtil.add(player.getInventory(), prototype, amount);
        int failed = leftovers.stream().mapToInt(ItemStack::getAmount).sum();
        int delivered = amount - failed;
        if (delivered > 0) repository.updateMailboxQuantity(entry.id(), player.getUniqueId(), entry.quantity() - delivered);
        return delivered;
    }

    public int claimAll(Player player) {
        int total = 0;
        for (MailboxEntry entry : repository.mailbox(player.getUniqueId(), 10000, 0)) total += claim(player, entry.id());
        return total;
    }

    private int capacity(Player player, ItemStack prototype, int upperBound) {
        int low = 0, high = upperBound;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (InventoryUtil.canFit(player.getInventory(), prototype, middle)) low = middle; else high = middle - 1;
        }
        return low;
    }
}
