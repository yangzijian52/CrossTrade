package com.crosstrade.market.model;

import java.math.BigDecimal;
import java.util.UUID;

public record Listing(long id, UUID sellerId, String sellerName, String marketName, byte[] itemBytes,
                      String fingerprint, int initialQuantity, int remainingQuantity, BigDecimal unitPrice,
                      String status, String escrowState, long createdAt, long expiresAt, int version) {
    public boolean active(long now) {
        return "ACTIVE".equals(status) && remainingQuantity > 0 && expiresAt > now;
    }
}
