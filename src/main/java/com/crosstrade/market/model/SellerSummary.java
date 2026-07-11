package com.crosstrade.market.model;

import java.math.BigDecimal;
import java.util.UUID;

public record SellerSummary(UUID sellerId, String sellerName, int listings, int totalItems,
                            BigDecimal minimumPrice, long latestListing) {}
