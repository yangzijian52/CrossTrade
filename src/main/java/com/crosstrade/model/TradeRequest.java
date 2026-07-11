package com.crosstrade.model;

import java.util.UUID;

public record TradeRequest(UUID id, UUID senderId, UUID targetId, long createdAt, long expiresAt) {}
