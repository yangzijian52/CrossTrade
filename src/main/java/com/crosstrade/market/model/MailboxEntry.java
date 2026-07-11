package com.crosstrade.market.model;

import java.util.UUID;

public record MailboxEntry(long id, UUID ownerId, Long listingId, byte[] itemBytes, int quantity,
                           String reason, long createdAt) {}
