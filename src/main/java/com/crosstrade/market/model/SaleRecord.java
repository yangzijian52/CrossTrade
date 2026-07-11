package com.crosstrade.market.model;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleRecord(long id, long listingId, UUID buyerId, UUID sellerId, String marketName,
                         int quantity, BigDecimal unitPrice, BigDecimal total, String state, long createdAt) {}
