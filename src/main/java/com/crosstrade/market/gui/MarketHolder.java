package com.crosstrade.market.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class MarketHolder implements InventoryHolder {
    public enum Type { HOME, PLAYER_TRADE, PLAZA, SELLER, BUY_QUANTITY, BUY_CONFIRM, SELL_SELECT, SELL_QUANTITY,
        SELL_DURATION, SELL_CONFIRM, MINE, MINE_CLEAR, MANAGE, LISTING_CANCEL, LISTING_DELETE, RESTOCK,
        RELIST_DURATION, MAILBOX, EARNINGS, HISTORY,
        HISTORY_CLEAR, CONTAINER }
    private final Type type;
    private final UUID viewer;
    private final UUID subject;
    private final int page;
    private final String search;
    private final String sort;
    private final long listingId;
    private Inventory inventory;

    public MarketHolder(Type type, UUID viewer, UUID subject, int page, String search, String sort, long listingId) {
        this.type = type; this.viewer = viewer; this.subject = subject; this.page = page;
        this.search = search == null ? "" : search; this.sort = sort == null ? "LATEST" : sort; this.listingId = listingId;
    }
    public Type type() { return type; }
    public UUID viewer() { return viewer; }
    public UUID subject() { return subject; }
    public int page() { return page; }
    public String search() { return search; }
    public String sort() { return sort; }
    public long listingId() { return listingId; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
