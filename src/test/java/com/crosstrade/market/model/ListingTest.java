package com.crosstrade.market.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ListingTest {
    private Listing listing(String status, int remaining, long expiresAt) {
        return new Listing(1, UUID.randomUUID(), "seller", "商品", new byte[]{1}, "hash",
                10, remaining, BigDecimal.ONE, status, "HELD", 1, expiresAt, 0);
    }

    @Test void activeRequiresStatusStockAndFutureExpiry() {
        long now = 1_000;
        assertTrue(listing("ACTIVE", 1, 2_000).active(now));
        assertFalse(listing("SOLD_OUT", 1, 2_000).active(now));
        assertFalse(listing("ACTIVE", 0, 2_000).active(now));
        assertFalse(listing("ACTIVE", 1, 1_000).active(now));
    }
}
