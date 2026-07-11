package com.crosstrade.gui;

import com.crosstrade.model.TradeSession;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class TradeHolder implements InventoryHolder {
    private final TradeSession session;
    private final UUID ownerId;
    private Inventory inventory;

    public TradeHolder(TradeSession session, UUID ownerId) {
        this.session = session;
        this.ownerId = ownerId;
    }

    public TradeSession session() { return session; }
    public UUID ownerId() { return ownerId; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
