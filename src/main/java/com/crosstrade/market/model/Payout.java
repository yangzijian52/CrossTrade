package com.crosstrade.market.model;

import java.math.BigDecimal;
import java.util.UUID;

public record Payout(long id, Long saleId, UUID ownerId, BigDecimal amount, String state,
                     long createdAt, String lastError) {}
